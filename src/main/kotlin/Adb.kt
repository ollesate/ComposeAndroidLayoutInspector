import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import java.io.File

data class LayoutDump(
    val screenshotPath: String,
    val layoutPath: String,
    val pixelsPerDp: Float
)

const val SCREENSHOT_PATH = "/tmp/screencap.png"
const val LAYOUT_DUMP_PATH = "/tmp/window_dump.xml"
val outputFiles = arrayOf("/tmp/screencap.png", "/tmp/window_dump.xml")

val adb : String
    get() = System.getenv("ADB") ?: "adb" // Try get adb defined in env or else just hope plain adb will work

fun screenDump(): LayoutDump {
    val (screenshotPath, layoutPath) = outputFiles

    "$adb devices".execute()

    println("Taking screenshot")
    "$adb shell screencap -p > $screenshotPath".execute()

    println("Dump layout")
    "$adb shell uiautomator dump && adb pull /sdcard/window_dump.xml $layoutPath".execute()


    val density = "[0-9]+".toRegex().find(
        "$adb shell wm density".execute()
    )?.value ?: error("No density found")

    println("Density" + density)


    return LayoutDump(
        screenshotPath = screenshotPath,
        layoutPath = layoutPath,
        pixelsPerDp = density.toInt() / 160f
    )
}

suspend fun screenshot(): ImageBitmap {
    println("Taking screenshot")
    withContext(Dispatchers.IO) {
        "$adb shell screencap -p > $SCREENSHOT_PATH".execute()
    }
    return File(SCREENSHOT_PATH).toBitmap().also {
        File(SCREENSHOT_PATH).delete()
    }
}

suspend fun getLayout(): ViewNode {
    println("Dump layout")
    withContext(Dispatchers.IO) {
        "$adb shell uiautomator dump && adb pull /sdcard/window_dump.xml $LAYOUT_DUMP_PATH".execute()
    }
    return createRootNode(LAYOUT_DUMP_PATH).also {
        File(LAYOUT_DUMP_PATH).delete()
    }
}

suspend fun getPixelsPerDp(): Float {
    val adbOutput = withContext(Dispatchers.IO) {
        "$adb shell wm density".execute()
    }
    return "[0-9]+".toRegex().findAll(adbOutput).last().value.toInt().div(160f)
}


data class Devices(
    val name: String
)

fun devices() = "adb devices".execute()
    .substringAfter("List of devices attached")
    .trim()
    .split("\n")
    .map { it.split("\t") }
    .map { (name, _) ->
        Devices(name = name)
    }