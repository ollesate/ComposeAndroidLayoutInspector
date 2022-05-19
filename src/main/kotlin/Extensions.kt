@file:OptIn(ExperimentalCoroutinesApi::class)

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.reflect.KProperty

fun String.findMatches(regex: String) = regex.toRegex()
    .findAll(this)
    .map { it.value }
    .toList()

val Size.area: Float
    get() = width * height

val overrideEnvironment = mutableMapOf<String, String>()

fun String.execute(): String {
    println(this)
    val process = ProcessBuilder("sh", "-c", this).apply {
        environment().putAll(overrideEnvironment)
    }.start()
    val errorText = process.errorStream.reader().readText().trim().also(::println)
    if (errorText.isNotEmpty()) {
        error(errorText)
    }
    return process.inputStream.reader().readText().trim().also(::println)
}

fun String.execute2(): String {
    println(this)
    val cmd = this
    val process = Runtime.getRuntime().exec(cmd)
    process.waitFor()
    return BufferedReader(InputStreamReader(process.inputStream)).readText().also(::println)
}

fun File.toBitmap() = org.jetbrains.skia.Image.makeFromEncoded(readBytes()).toComposeImageBitmap()

fun <T, B>guardNonNull(
    value1: T?,
    value2: B?,
    block: (Pair<T, B>) -> Unit
) = value1?.let { value2?.let { block(value1 to value2) } }

operator fun File.setValue(nothing: Nothing?, property: KProperty<*>, value: String?) {
    createNewFile()
    writeText(value.orEmpty())
}

operator fun File.getValue(nothing: Nothing?, property: KProperty<*>): String? {
    if (!exists()) {
        return null
    }
    return readText().takeUnless { it.isEmpty() }
}

fun <T> emitOnceFlow(block: suspend () -> T): Flow<T?> = channelFlow {
    send(null)
    launch(Dispatchers.IO) {
        send(block())
    }
}