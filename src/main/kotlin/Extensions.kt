import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

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
fun <T>guardNonNull(
    vararg values: T?,
    block: (List<T>) -> Unit
) {
    values.toList().filterNotNull().takeIf { it.size == values.size }?.also(block)
}