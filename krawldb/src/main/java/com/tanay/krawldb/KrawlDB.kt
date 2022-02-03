package com.tanay.krawldb

import android.app.Application
import android.content.Context
import androidx.lifecycle.*
import com.google.gson.Gson
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import kotlin.coroutines.CoroutineContext
import kotlin.reflect.KClass

class KrawlDB<T : Any> private constructor(
    private val context: Context,
    private val dbName: String,
    private val lifecycleOwner: LifecycleOwner,
    private val javaClass: Class<T>
) {
    companion object {
        private const val DEFAULT_DB = "krawldb" // Default db name

        /**
         * [getDB] is a factory method to create database
         *
         * [context] for handling files within the private files dir of application
         *
         * [lifecycleOwner] is a [LifecycleOwner] which is used to run the crud operations asynchronously
         *
         * [dbName] for creating database with name given by user
         *
         * Constraints: [dbName] should be a string which can be assigned to a file name
         */

        suspend fun <T : Any> getDB(
            context: Context,
            lifecycleOwner: LifecycleOwner,
            javaClass: KClass<T>,
            dbName: String = DEFAULT_DB
        ) = withContext(Dispatchers.IO) { KrawlDB(context, dbName, lifecycleOwner, javaClass.java) }

        suspend fun <T : Any> getDB(
            context: Context,
            lifecycleOwner: LifecycleOwner,
            javaClass: Class<T>,
            dbName: String = DEFAULT_DB
        ) = withContext(Dispatchers.IO) { KrawlDB(context, dbName, lifecycleOwner, javaClass) }
    }

    /**
     * [databaseFile] is the database file
     */
    private val databaseFile: File get() = File(context.filesDir, dbName)

    /**
     * [viewModel] is [DBViewModel] which is used to handle CRUD operations
     */
    private var viewModel: DBViewModel<T>

    init {
        viewModel = DBViewModel(
            DBViewModel.DataLiveData(readList(false)),
            context.applicationContext as Application
        )
    }

    /**
     * [_data] is a list of [T] elements present in database
     */
    private var _data: DataList<T> = DataList(viewModel)

    /**
     * [data] is livedata which should be observed in the ui to show data in a ListView or RecyclerView in XML, or LazyColumn in Jetpack Compose
     */
    val data: LiveData<List<T>>
        get() {
            return viewModel.data
        }

    /**
     * [coroutineScope] is the CoroutineScope which is used to do suspended tasks
     */
    private val coroutineScope get() = viewModel.viewModelScope

    /**
     * [DataObserver] is a [Observer] which is used to observe the number of tasks left in queue
     */
    private class DataObserver(val block: DataObserver.(MutableList<String>?) -> Unit) :
        Observer<MutableList<String>> {
        override fun onChanged(t: MutableList<String>?) {
            block(this, t)
        }
    }

    /**
     * [doTask] is a function which is used to do tasks asynchronously
     */
    private fun doTask(listener: OnTaskDoneListener, block: () -> Unit) {
        val task: () -> Unit = block
        viewModel.pendingTasks.apply {
            value = value?.apply { add("Task ${size + 1}") }
        }
        val observer = DataObserver { t ->
            t?.let {
                if (it.size == 1) {
                    viewModel.pendingTasks.removeObserver(this@DataObserver)
                    coroutineScope.launch {
//                        runOnUIThread {
//                            viewModel.pendingTasks.apply {
//                                value = value?.apply { add("Task ${size + 1}") }
//                            }
//                        }
                        withContext(Dispatchers.IO) {
                            runCatching {
                                task.invoke()
                            }.onSuccess {
                                runOnUIThread { listener.onCompleted() }
                            }.onFailure { err ->
                                runOnUIThread { listener.onError(err) }
                            }.also {
                                runOnUIThread {
                                    viewModel.pendingTasks.apply {
                                        value = value?.apply { removeAt(0) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        viewModel.pendingTasks.observe(lifecycleOwner, observer)
    }

    /**
     * [runOnUIThread] to call functions on the Main thread
     */
    private suspend fun runOnUIThread(block: () -> Unit) {
        withContext(Dispatchers.Main) { block() }
    }

    /**
     * [addToDatabaseSync] is a function to add elements to database synchronously
     */
    fun addToDatabaseSync(vararg elements: T) {
        addToList(*elements)
    }

    /**
     * [addToDatabase] is a function to add elements to database asynchronously
     */
    fun addToDatabase(elements: Array<out T>, listener: OnTaskDoneListener) {
        doTask(listener) {
            addToList(*elements)
        }
    }

    /**
     * [getFilteredList] is a function to get filtered list of elements from database
     */
    suspend fun getFilteredList(filter: (T) -> Boolean): List<T>? {
        return withContext(Dispatchers.IO) {
            return@withContext runCatching<List<T>> {
                _data.toList().run { withContext(Dispatchers.Main) { filter(filter) } }
            }.onFailure {
                it.printStackTrace()
            }.getOrNull()
        }
    }

    /**
     * [getFilteredMapIndexed] is a function to get filtered list of indexed elements from database
     */
    suspend fun getFilteredMapIndexed(filter: (T) -> Boolean): Map<Int, T>? {
        return withContext(Dispatchers.IO) {
            return@withContext runCatching<Map<Int, T>> {
                _data.toList().let {
                    it.mapIndexed { index, t -> index to t }.toMap()
                        .filter { item -> filter(item.value) }
                }
            }.onFailure {
                it.printStackTrace()
            }.getOrNull()
        }
    }

    /**
     * [getElementSync] is a function which you can use to get an element from the database by its index
     *
     * Please note that if you are returning the index from a filtered list please use [getIndex] or [getIndexSync] to get the real index from the list
     */
    fun getElementSync(index: Int): T = _data[index]

    fun getElement(index: Int, listener: OnTaskCompletedListener<T>) {
        coroutineScope.launch {
            withContext(Dispatchers.Main) {
                runCatching<T> {
                    return@runCatching getElementSync(index)
                }.onFailure {
                    runOnUIThread { listener.onTaskFailed(it) }
                }.getOrNull()?.let {
                    runOnUIThread { listener.onTaskCompleted(it) }
                }
            }
        }
    }

    /**
     * [updateElementSync] - to update elements synchronously
     */
    fun updateElementSync(index: Int, newElement: T) {
        updateElementInternal(index, newElement)
    }

    /**
     * [updateElement] - to update elements asynchronously
     */
    fun updateElement(index: Int, newElement: T, listener: OnTaskDoneListener) {
        doTask(listener) {
            updateElementInternal(index, newElement)
        }
    }

    /**
     * [deleteDatabaseSync] - to delete database synchronously
     */
    fun deleteDatabaseSync(listener: OnTaskDoneListener) {
        deleteDatabaseInternal()
    }

    /**
     * [deleteDatabase] - to delete database asynchronously
     */
    fun deleteDatabase(listener: OnTaskDoneListener) {
        doTask(listener) {
            deleteDatabaseInternal()
        }
    }

    /**
     * [deleteElement] deletes an element from the database by its [index]
     */
    fun deleteElement(index: Int, listener: OnTaskDoneListener) {
        doTask(listener) {
            _data.deleteElement(index)
        }
    }

    interface OnTaskDoneListener {
        /**
         * [onCompleted] - called when an asynchronous operation is completed
         */
        fun onCompleted()

        /**
         * [onError] - called when an asynchronous operation fails
         *
         * [cause] - cause of failure of operation
         */
        fun onError(cause: Throwable)
    }

    /**
     * [readListSync] is a method which reads a list from database synchronously
     */
    fun readListSync(): List<T> {
        return readList(false)
    }

    /**
     * [readList] is a method which reads a list from database synchronously
     */
    @Throws(IOException::class)
    private fun readList(flags: Boolean = true): List<T> {
        return readMutableList(flags).toList()
    }

    /**
     * [readMutableList] reads a mutable list from database synchronously
     */
    @Throws(IOException::class)
    private fun readMutableList(flags: Boolean = true): MutableList<T> {
        val file = databaseFile
        // If file does not exist, return nothing
        if (!file.exists()) {
            return mutableListOf()
        }
        val bf = BufferedReader(FileReader(file)) // Buffered reader for reading file
        val lines = StringBuilder() // Contains json which contains data from list
        bf.readLines().forEach {
            lines.append(it).append("\n")
        }
        bf.close() // Closing the buffered reader
        val data = mutableListOf<T>()
        // Parsing json from string
        val arr = JSONArray(lines.toString())
        arr.forEach {
            data.add(Gson().fromJson(it.toString(), javaClass))
        }
        if (flags) {
            this._data.changeValue(data)
        }
        return data
    }

    private fun JSONArray.forEach(block: (JSONObject) -> Unit) {
        val length = length()
        for (i in 0 until length) {
            block(getJSONObject(i))
        }
    }

    /**
     * [addToList] adds new data to database
     *
     * [elements] is a parameter which contains the elements which need to be added to the list
     */
    @Throws(IOException::class)
    private fun addToList(vararg elements: T) {
        // Adding the new elements
        elements.forEach { element ->
            _data.add(element)
        }
        // Writing the data to database
        writeToFile(Gson().toJson(_data.value))
    }

    /**
     * [updateElementInternal] updates an element present in the database
     *
     * [index] is the index in the list where the data must be changed
     *
     * [newElement] is the element to replace with the element present in the database at [index]
     */

    @Throws(IOException::class)
    private fun updateElementInternal(index: Int, newElement: T): List<T> {
        val file = databaseFile
        if (!file.exists()) {
            throw IOException("Cannot update database with name $dbName since it does not exist!")
        }
        _data[index] = newElement
        writeToFile(Gson().toJson(_data.value))
        return _data.toList()
    }

    /**
     * [deleteDatabaseInternal] deletes a database
     */
    @Throws(IOException::class)
    private fun deleteDatabaseInternal() {
        databaseFile.let { file ->
            _data.clear()
            file.delete()
        }
    }

    /**
     * [writeToFile] writes data to the database
     *
     * [value] is the string which contains the contents to write to the database
     */
    @Throws(IOException::class)
    private fun writeToFile(value: String) {
        val stream = RandomAccessFile(databaseFile, "rw") // Creating a random access file
        val channel: FileChannel = stream.channel   // Creating a file channel to write to the file
        val strBytes = value.toByteArray() // Getting the bytes from the new data
        val buffer: ByteBuffer = ByteBuffer.allocate(strBytes.size) // Creating byte buffer
        buffer.put(strBytes) // Putting the byte array to the buffer
        buffer.flip()
        channel.write(buffer) // Writing the data to file
        stream.close() // Closing the stream
        channel.close() // Closing the file channel
    }

    class DBViewModel<T : Any>(private val _data: DataLiveData<List<T>>, app: Application) :
        AndroidViewModel(app) {
        internal val pendingTasks = MutableLiveData(mutableListOf<String>())

        internal fun setData(data: List<T>) {
            _data.setData(data)
        }

        val data: LiveData<List<T>> get() = _data

        class DataLiveData<T : Any>(value: T) : LiveData<T>(value) {
            internal fun setData(data: T) {
                value = data
            }

        }
    }

    internal class DataList<T : Any>(private val viewModel: DBViewModel<T>) {
        private var data: MutableList<T> = viewModel.data.value!!.toMutableList()
        fun add(element: T) {
            data.add(element)
            runOnUIThread { changeValue(data.toList()) }
        }

        private fun runOnUIThread(block: () -> Unit) {
            coroutineScope.launch {
                withContext(Dispatchers.Main) {
                    block()
                }
            }
        }

        private val coroutineScope: CoroutineScope get() = viewModel.viewModelScope

        val value get() = data.toList()

        fun toList() = data.toList()

        fun changeValue(list: List<T>) = viewModel.setData(list)

        fun clear() {
            data.clear()
            runOnUIThread { changeValue(data.toList()) }
        }

        operator fun get(index: Int): T = data[index]
        operator fun set(index: Int, value: T) {
            data[index] = value
            runOnUIThread { changeValue(data.toList()) }
        }

        fun deleteElement(index: Int) {
            data.removeAt(index)
            runOnUIThread { changeValue(data) }
        }
    }
}

/**
 * Assume you have a listView and you have the position of the elements in the listView by calling [KrawlDB.getFilteredMapIndexed]
 *
 * You want to get the real index from the filtered map, then you need to call [getIndexSync]
 */
fun <T> Map<Int, T>?.getIndexSync(position: Int): Int? {
    return this?.toList()?.run {
        return@run get(position).second.let { element -> firstOrNull { it.second === element }?.first }
    }
}

/**
 * [getIndex] calls [getIndexSync] asynchronously
 */
fun <T> Map<Int, T>?.getIndex(
    coroutineScope: CoroutineScope = object : CoroutineScope {
        val job = Job()
        override val coroutineContext: CoroutineContext
            get() = job + Dispatchers.Main
    },
    position: Int,
    listener: OnTaskCompletedListener<Int?>
) {
    coroutineScope.launch {
        withContext(Dispatchers.IO) {
            runCatching<Int?> {
                return@runCatching getIndexSync(position)
            }.onFailure {
                withContext(Dispatchers.Main) { listener.onTaskFailed(it) }
            }.getOrNull()?.let { withContext(Dispatchers.Main) { listener.onTaskCompleted(it) } }
        }
    }
}

interface OnTaskCompletedListener<T> {
    fun onTaskCompleted(value: T)
    fun onTaskFailed(cause: Throwable)
}