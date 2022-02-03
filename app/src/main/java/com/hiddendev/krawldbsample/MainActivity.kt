package com.hiddendev.krawldbsample

import android.annotation.SuppressLint
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hiddendev.krawldbsample.ui.theme.KrawlDBTheme
import com.tanay.krawldb.KrawlDB
import com.tanay.krawldb.OnTaskCompletedListener
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KrawlDBTheme {
                Surface(color = MaterialTheme.colors.background) {
                    MainApp()
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalAnimationApi::class)
@SuppressLint("CoroutineCreationDuringComposition")
@Composable
fun MainApp() {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var showAddorEditWordEntityDialog by remember {
        mutableStateOf(false to -1)
    }
    var showElementDetailsDialog by remember {
        mutableStateOf(false to -1)
    }
    var showDeleteElementDialog by remember {
        mutableStateOf(false to -1)
    }
    var db by remember {
        mutableStateOf(null as KrawlDB<WordEntity>?)
    }
    coroutineScope.launch {
        db = KrawlDB.getDB(context, lifecycleOwner, WordEntity::class)
    }
    Scaffold(
        topBar = {
            TopAppBar(title = {
                Text("KrawlDB Sample")
            },
                actions = {
                    IconButton(onClick = {
                        db?.deleteDatabase(object : KrawlDB.OnTaskDoneListener {
                            override fun onCompleted() {
                                Toast.makeText(
                                    context,
                                    "Deleted database successfully!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                            override fun onError(cause: Throwable) {
                                cause.printStackTrace()
                                Toast.makeText(
                                    context,
                                    "Failed to delete database!",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }

                        }) ?: Toast.makeText(
                            context,
                            "Failed to delete database!",
                            Toast.LENGTH_SHORT
                        ).show()
                    }) {
                        Icon(Icons.Default.Delete, "Delete Database")
                    }
                })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = {
                showAddorEditWordEntityDialog = true to -1
            }) {
                Icon(Icons.Default.Add, "Add Item")
            }
        }
    ) {
        val wordsList = db?.data?.observeAsState()
        LazyColumn {
            wordsList?.value?.let { words ->
                items(words.size) { index ->
                    val wordEntity = words[index]
                    val interactionSource = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                interactionSource = interactionSource,
                                indication = rememberRipple(),
                                role = Role.Button,
                                onClick = {
                                    showElementDetailsDialog = true to index
                                },
                                onLongClick = {
                                    showDeleteElementDialog = true to index
                                }
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Word: ${wordEntity.word}",
                            modifier = Modifier.padding(bottom = 4.dp),
                            textAlign = TextAlign.Center
                        )
                        Text(text = "Meaning: ${wordEntity.meaning}", textAlign = TextAlign.Center)
                    }
                    Divider()
                }
            }
        }
        if (showAddorEditWordEntityDialog.first) {
            var word by remember {
                mutableStateOf("")
            }
            var meaning by remember {
                mutableStateOf("")
            }
            val task = if (showAddorEditWordEntityDialog.second < 0) "Add" else "Edit"
            AlertDialog(
                onDismissRequest = { showAddorEditWordEntityDialog = false to -1 },
                title = {
                    Text("$task Word and Meaning to Dictionary")
                },
                text = {
                    Column {
                        TextField(
                            label = {
                                Text("Word")
                            },
                            value = word,
                            onValueChange = { word = it },
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        TextField(label = {
                            Text("Meaning")
                        }, value = meaning, onValueChange = { meaning = it })
                    }
                },
                buttons = {
                    Row(
                        modifier = Modifier.padding(all = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Spacer(modifier = Modifier.fillMaxWidth(fraction = 0.4f))
                        Button(
                            onClick = { showAddorEditWordEntityDialog = false to -1 }
                        ) {
                            Text("Dismiss")
                        }
                        Button(
                            onClick = {
                                val listener = object : KrawlDB.OnTaskDoneListener {
                                    override fun onCompleted() {
                                        showAddorEditWordEntityDialog = false to -1
                                        Toast.makeText(
                                            context,
                                            "${task}ed word and meaning successfully!",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }

                                    override fun onError(cause: Throwable) {
                                        showAddorEditWordEntityDialog = false to -1
                                        Toast.makeText(
                                            context,
                                            "Failed to $task word and meaning to dictionary",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        cause.printStackTrace()
                                    }

                                }
                                if (task == "Add") {
                                    db?.addToDatabase(
                                        arrayOf(
                                            WordEntity(
                                                word = word,
                                                meaning = meaning
                                            )
                                        ),
                                        listener
                                    ) ?: run {
                                        Toast.makeText(
                                            context,
                                            "Failed to add word and meaning to dictionary",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        showAddorEditWordEntityDialog = false to -1
                                    }
                                } else {
                                    db?.updateElement(
                                        index = showAddorEditWordEntityDialog.second,
                                        newElement = WordEntity(word = word, meaning = meaning),
                                        listener = listener
                                    ) ?: run {
                                        Toast.makeText(
                                            context,
                                            "Failed to edit word and meaning to dictionary",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        showAddorEditWordEntityDialog = false to -1
                                    }
                                }
                            },
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            Text("OK")
                        }
                    }
                }
            )
        }
        if (showElementDetailsDialog.first) {
            Box(modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            showElementDetailsDialog = false to -1
                        }
                    )
                }) {
                @Suppress("DEPRECATION")
                AnimatedVisibility(
                    modifier = Modifier
                        .fillMaxHeight(0.4f)
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = {})
                        },
                    visible = true,
                    initiallyVisible = true,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                            .background(Color.LightGray)
                    ) {
                        val wordEntity = wordsList?.value?.get(showElementDetailsDialog.second)
                        Divider()
                        wordEntity?.let {
                            SelectionContainer(Modifier.fillMaxWidth()) {
                                Column(
                                    Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        "Word: ${wordEntity.word}",
                                        fontSize = 14.sp,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        "Meaning: ${wordEntity.meaning}",
                                        fontSize = 16.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            Button(
                                modifier = Modifier.align(Alignment.CenterHorizontally),
                                onClick = {
                                    showAddorEditWordEntityDialog = showElementDetailsDialog
                                    showElementDetailsDialog = false to -1
                                }) {
                                Text("Edit word and meaning")
                            }
                        } ?: run {
                            Toast.makeText(
                                context,
                                "Could not load details of word!",
                                Toast.LENGTH_SHORT
                            ).show()
                            showElementDetailsDialog = false to -1
                        }
                    }
                }
            }
        }
        if (showDeleteElementDialog.first) {
            AlertDialog(
                onDismissRequest = { showDeleteElementDialog = false to -1 },
                title = {
                    Text("Do you really want to delete ${showDeleteElementDialog.second + 1} word and meaning?")
                },
                buttons = {
                    Row(
                        modifier = Modifier.padding(all = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Spacer(modifier = Modifier.fillMaxWidth(fraction = 0.4f))
                        Button(
                            onClick = { showDeleteElementDialog = false to -1 }
                        ) {
                            Text("Dismiss")
                        }
                        Button(
                            onClick = {
                                db?.deleteElement(
                                    index = showDeleteElementDialog.second,
                                    listener = object : KrawlDB.OnTaskDoneListener {
                                        override fun onCompleted() {
                                            Toast.makeText(
                                                context,
                                                "Deleted ${showDeleteElementDialog.second + 1} element from dictionary!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                            showDeleteElementDialog = false to -1
                                        }

                                        override fun onError(cause: Throwable) {
                                            cause.printStackTrace()
                                            showDeleteElementDialog = false to -1
                                            Toast.makeText(
                                                context,
                                                "Failed to delete element from dictionary!",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }

                                    }
                                ) ?: run {
                                    Toast.makeText(
                                        context,
                                        "Failed to delete element from dictionary!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                    showDeleteElementDialog = false to -1
                                }
                            },
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            Text("OK")
                        }
                    }
                }
            )
        }
    }
}

data class WordEntity(
    val word: String,
    val meaning: String
)