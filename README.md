[![](https://jitpack.io/v/Tanay360/KrawlDB.svg)](https://jitpack.io/#Tanay360/KrawlDB)
# KrawlDB

> KrawlDB is a nosql json database library which has a livedata prebuilt containing the elements from the database which you can observe on the main thread

Add the dependency for KrawlDB:

In your root gradle file add:
```groovy
allprojects {
    repositories {
        maven { url 'https://jitpack.io' }
    }
}
```

In your app module add:
```groovy
dependencies {
    // Replace $krawldb_version with the version in the release
    implementation "com.github.Tanay360:KrawlDB:$krawldb_version"
}
```

## To get started follow the steps below

### 1. Initialize Database

> You must initialize the database in a coroutine scope. The factory method takes in the context, lifecycleowner of fragment, activity or a composable, and the KClass object of the entity you will be using.

```kotlin
val db = KrawlDB.getDB<YourEntity>(context, lifecycleOwner, YourEntity::class)
```

> If you are using compose, add the following dependencies to your app/build.gradle file

```groovy
implementation 'androidx.compose.runtime:runtime-livedata:1.2.0-alpha01'
implementation "androidx.lifecycle:lifecycle-viewmodel-compose:2.4.0"
```

### 2. Observe data

> To observe data in an activity or fragment:

```kotlin
db.data.observe(lifecycleOwner) {
    // Your code here
}
```

> To observe data in a composable:

```kotlin
db.data.observeAsState()
```

### 3. Add elements to database

```kotlin
db.addToDatabase(
    elements = arrayOf(/* Your elements here */),
    listener = object: KrawlDB.OnTaskDoneListener {
        override fun onCompleted() {
            // Task is successful
        }

        override fun onError(cause: Throwable) {
            // Error occurred in executing task
        }

    }
)
```

### 4. Get a single element by index from database

```kotlin
db.getElement(
    index = indexOfElement,
    listener = object : OnTaskCompletedListener<YourEntity> {
        override fun onTaskCompleted(value: YourEntity) {
            // Task Completed Successfully
        }
        override fun onTaskFailed(cause: Throwable) {
            // Task failed to execute
        }
    })
```

### 5. Update an element in database

```kotlin
db.updateElement(
    index = indexOfElement,
    newElement = objectOfYourEntity,
    listener = object: KrawlDB.OnTaskDoneListener {
        override fun onCompleted() {
            // Task is successful
        }

        override fun onError(cause: Throwable) {
            // Error occurred in executing task
        }

    }
)
```

### 6. Delete an element from database

```kotlin
db.deleteElement(
    index = indexOfElement,
    listener = object : KrawlDB.OnTaskDoneListener {
        override fun onCompleted() {
            // Task Completed Successfully!
        }

        override fun onError(cause: Throwable) {
            // Task failed to execute!
        }

    }
)
```

### 7. Delete the whole database

```kotlin
db.deleteDatabase(object : KrawlDB.OnTaskDoneListener {
        override fun onCompleted() {
            // Task Completed Successfully!
        }
        override fun onError(cause: Throwable) {
            // Task failed to execute!
        }
    }
)
```

> Please note that all functions given above are called only after one is executed! To call them before all tasks are done, just add the word sync after every function name

You can check out an example as well in the app directory of this repository although it is not documented.
