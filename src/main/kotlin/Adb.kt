
val hardcoded = arrayOf(
    "/home/olof/projects/ComposeAndroidLayoutInspector/output/screenshot.png",
    "/home/olof/projects/ComposeAndroidLayoutInspector/output/window_dump.xml"
)

data class LayoutDump(
    val screenshotPath: String,
    val layoutPath: String,
    val pixelsPerDp: Float
)

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
        screenshotPath = "/tmp/screencap.png",
        layoutPath = "/tmp/window_dump.xml",
        pixelsPerDp = density.toInt() / 160f
    )
}