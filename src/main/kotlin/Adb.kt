import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.*
import java.io.File

const val SCREENSHOT_PATH = "/tmp/screencap.png"
const val LAYOUT_DUMP_PATH = "/tmp/window_dump.xml"

val configFile = File(System.getProperty("user.home") + File.separator + ".ComposeAndroidLayoutInspector")

// For me, I have different adb when I run `which adb` and `sudo which adb`. Since the program runs in sudo if it is
// opened by the window manager (not terminal) this override will help users to change the adb this program uses.
var adbOverride: String? by configFile

val adb : String
    get() = adbOverride?.trim()
        ?: System.getenv("ADB") // This seems to never work which is unfortunate...
        ?: "adb" // Try get adb defined in env or else just hope plain adb will work

data class LayoutContentResult(
    val screenshotBitmap: Result<ImageBitmap>? = null,
    val rootNode: Result<ViewNode>? = null,
    val pixelsPerDp: Result<Float>? = null,
)

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
    File(LAYOUT_DUMP_PATH).delete()
    withContext(Dispatchers.IO) {
        try {
            "$adb shell uiautomator dump && $adb pull /sdcard/window_dump.xml $LAYOUT_DUMP_PATH".execute()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    if (!File(LAYOUT_DUMP_PATH).exists()) {
        error("Failed to create layout")
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


data class Device(
    val name: String
)

suspend fun devices() = "$adb devices".execute()
    .substringAfter("List of devices attached")
    .trim()
    .split("\n")
    .filterNot { it.isEmpty() }
    .map { it.split("\t") }
    .map { (name, _) ->
        Device(name = name)
    }

fun Device.select() = overrideEnvironment.put("ANDROID_SERIAL", name)