import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import java.io.File

val hardcoded = arrayOf(
    "/home/olof/projects/ComposeAndroidLayoutInspector/output/screenshot.png",
    "/home/olof/projects/ComposeAndroidLayoutInspector/output/window_dump.xml"
)

data class LayoutDump(
    val screenshotPath: String,
    val layoutPath: String,
    val pixelsPerDp: Float
)

val screenshotPath = "/tmp/screencap.png"
val layoutDumpPath = "/tmp/window_dump.xml"
val outputFiles = arrayOf("/tmp/screencap.png", "/tmp/window_dump.xml")

const val adb = "/home/olof/Android/Sdk/platform-tools/adb"

fun main() {
    screenDump()
}

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
        "$adb shell screencap -p > $screenshotPath".execute()
    }
    return File(screenshotPath).toBitmap().also {
        File(screenshotPath).delete()
    }
}

suspend fun getLayout(): ViewNode {
    println("Dump layout")
    withContext(Dispatchers.IO) {
        "$adb shell uiautomator dump && adb pull /sdcard/window_dump.xml $layoutDumpPath".execute()
    }
    return createRootNode(layoutDumpPath).also {
        File(layoutDumpPath).delete()
    }
}

suspend fun getPixelsPerDp(): Float {
    val adbOutput = withContext(Dispatchers.IO) {
        "$adb shell wm density".execute()
    }
    return "[0-9]+".toRegex().findAll(adbOutput).last().value.toInt().div(160f)
}