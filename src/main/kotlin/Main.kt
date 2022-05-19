@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class, FlowPreview::class)

import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.MaterialTheme.colors
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.jetbrains.skia.svg.SVGTag
import org.jetbrains.skiko.SystemTheme
import org.jetbrains.skiko.currentSystemTheme

fun main(
    args: Array<String>
) = application {

    val initialWindowSize = DpSize(
        1000.dp,
        1920.dp
    ).div(2f)

    val scope = rememberCoroutineScope()
    val viewModel = DeviceViewModel(scope)

    Window(
        onCloseRequest = ::exitApplication,
        state = WindowState(
            size = initialWindowSize
        ),
        title = "Layout inspector",
        onKeyEvent = {
            if (it.isCtrlPressed && it.key == Key.R && it.type == KeyEventType.KeyUp) {
                viewModel.load()
            }
            true
        }
    ) {
        val selectedDevice by viewModel.selectedDevice.collectAsState(null)
        val devices by viewModel.devices.collectAsState(null)
        val content by viewModel.layoutContent.collectAsState(LayoutContentResult())

        LaunchedEffect(Unit) {
            viewModel.load()
        }

        App(
            content = content,
            devices = devices,
            selectedDevice = selectedDevice,
            onDeviceSelected = viewModel::selectDevice,
            onForceReload = {
                viewModel.load()
            }
        )
    }
}

class DeviceViewModel(
    private val coroutineScope: CoroutineScope
) {
    val devices = MutableStateFlow<Result<List<Device>>?>(null)
    val selectedDevice = MutableSharedFlow<Device?>()
    val layoutContent = selectedDevice.filterNotNull().flatMapConcat {
        combine(
            emitOnceFlow { kotlin.runCatching { screenshot() } },
            emitOnceFlow { kotlin.runCatching { getLayout() } },
            emitOnceFlow { kotlin.runCatching { getPixelsPerDp() } },
        ) { imageBitmap, viewNode, pixelsPerDp ->
            LayoutContentResult(
                screenshotBitmap = imageBitmap,
                rootNode = viewNode,
                pixelsPerDp = pixelsPerDp,
            )
        }
    }

    // Required to create this extra variable since mutable shared flow do not allow me to access the current item.
    // The reason I couldn't use a normal state flow is because that is considered cold flow and doesn't trigger if I
    // send the same device again which I want because I want it to refresh if you click already selected device
    private var previousDevice: Device? = null

    init {
        coroutineScope.launch {
            selectedDevice.collect {
                previousDevice = it
            }
        }
    }

    fun selectDevice(device: Device) {
        device.select()
        coroutineScope.launch {
            selectedDevice.emit(
                device
            )
        }
    }

    fun load() {
        coroutineScope.launch {
            devices.value = kotlin.runCatching { devices() }

            val devices = devices.value?.getOrNull().orEmpty()

            val newSelectedDevice = previousDevice.takeIf { devices.contains(previousDevice) }
                ?: devices.firstOrNull()

            newSelectedDevice?.select()

            selectedDevice.emit(newSelectedDevice)
        }
    }
}

fun <T> emitOnceFlow(block: suspend () -> T): Flow<T?> = channelFlow {
    send(null)
    launch(Dispatchers.IO) {
        send(block())
    }
}

data class LayoutContentResult(
    val screenshotBitmap: Result<ImageBitmap>? = null,
    val rootNode: Result<ViewNode>? = null,
    val pixelsPerDp: Result<Float>? = null,
)

@Composable
@Preview
fun App(
    content: LayoutContentResult,
    devices: Result<List<Device>>?,
    selectedDevice: Device?,
    onDeviceSelected: (Device) -> Unit,
    onForceReload: () -> Unit,
) {
    // rootNode.prettyPrint()

    MaterialTheme (
        content = {
            ImageContainerView(
                bitmap = content.screenshotBitmap?.getOrNull(),
            ) {
                if (devices?.isFailure == true) {
                    // When devices fail to load it's usually som adb problems, probably using different adb's and thus
                    // they force each other to kill the adb server.
                    Column(Modifier.align(Alignment.TopCenter).padding(124.dp)) {
                        Text(
                            "Failed to load devices: ${devices.exceptionOrNull()?.message}",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.clickable {

                            }
                        )

                        if (devices.exceptionOrNull()?.message.orEmpty().contains("doesn't match this client")) {
                            Text(
                                """
                                Looks like there are conflicting adb's causing an issue. This program tries to run:
                                ${adb.takeUnless { it == "adb" } ?: "which adb".execute()}
                                This might be different from what you would normally use? Try run 'which adb' in a terminal.
                                Make sure your adb's don't differ. 
                                You can change the adb you'd like this program to use in text field below
                                """.trimIndent(),
                                color = Color.Red,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )

                            Row(Modifier.align(Alignment.CenterHorizontally).padding(vertical = 8.dp)) {
                                var textFieldValue by remember { mutableStateOf("") }
                                OutlinedTextField(
                                    value = textFieldValue,
                                    singleLine = true,
                                    modifier = Modifier.defaultMinSize(minWidth = 200.dp, minHeight = 1.dp),
                                    onValueChange = {
                                        textFieldValue = it
                                    },
                                    textStyle = TextStyle.Default,
                                    colors = TextFieldDefaults.outlinedTextFieldColors(
                                        cursorColor = Color.White,
                                        textColor = Color.White,
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.5f),
                                        focusedBorderColor = Color.White
                                    )
                                )
                                OutlinedButton(
                                    onClick = {
                                        adbOverride = textFieldValue.trim()
                                        onForceReload()
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterVertically),
                                ) {
                                    Text("Set")
                                }
                            }
                        }

                        Button(
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            onClick = {
                                onForceReload()
                            }
                        ) {
                            Text("Reload")
                        }
                    }
                    return@ImageContainerView
                }

                val rootNode = content.rootNode?.getOrNull()
                val screenshotBitmap = content.screenshotBitmap?.getOrNull()
                val pixelsPerDp = content.pixelsPerDp?.getOrNull()

                if (rootNode != null && screenshotBitmap != null && pixelsPerDp != null) {
                    SelectableLayoutOverlay(
                        realImageSize = IntSize(screenshotBitmap.width, screenshotBitmap.height),
                        rootNode = rootNode,
                        pixelsPerDp = pixelsPerDp
                    )
                } else {
                    loadingView(content)
                }

                val devices = devices?.getOrNull()?.takeIf { it.isNotEmpty() } ?: return@ImageContainerView

                Row(
                    Modifier.align(Alignment.TopCenter).padding(top = 8.dp).height(IntrinsicSize.Min)
                ) {
                    devices.forEachIndexed { index, device ->
                        if (index > 0) {
                            Spacer(Modifier.width(1.dp).fillMaxHeight().background(Color.White))
                        }

                        Text(
                            text = device.name,
                            modifier = Modifier
                                .clip(
                                    if (devices.size == 1) {
                                        RoundedCornerShape(16.dp)
                                    } else {
                                        when (index) {
                                            0 -> RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                                            devices.lastIndex -> RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                                            else -> RoundedCornerShape(0.dp)
                                        }
                                    }
                                )
                                .background(Color.Blue.takeIf { selectedDevice == device } ?: Color.Black)
                                .padding(6.dp)
                                .clickable {
                                    onDeviceSelected(device)
                                },
                            color = Color.White,
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun BoxScope.loadingView(content: LayoutContentResult) {
    Box(Modifier.matchParentSize().background(Color.White.copy(alpha = 0.3f))) {
        Column(
            Modifier
                .align(Alignment.Center)
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(16.dp)
        ) {
            listOf(
                content.screenshotBitmap to "Screenshot",
                content.rootNode to "Layout",
                content.pixelsPerDp to "Device metadata",
            ).forEach { (result, description) ->
                Spacer(Modifier.height(16.dp))

                Row(Modifier.height(24.dp)) {
                    Text(
                        "$description: ",
                        color = Color.White
                    )
                    if (result == null) {
                        CircularProgressIndicator(
                            Modifier.width(20.dp),
                            color = Color.Yellow.copy(alpha = 0.8f)
                        )
                    } else if (result.isFailure) {
                        Icon(Icons.Outlined.Close, null, tint = Color.Red)
                    } else {
                        Icon(Icons.Outlined.Check, null, tint = Color.Green)
                    }
                }

                if (result?.isFailure == true) {
                    Text(
                        modifier = Modifier.padding(start = 16.dp, top = 8.dp),
                        text = result.exceptionOrNull()?.message.orEmpty(),
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@LayoutScopeMarker
@Immutable
interface ImageContainerScope: BoxScope {
    val contentSize: IntSize?
    val totalSize: IntSize?
}

class ImageContainerScopeInstance(
    override val contentSize: IntSize?,
    override val totalSize: IntSize?,
    private val boxScope: BoxScope
) : ImageContainerScope, BoxScope by boxScope

@Composable
fun ImageContainerView(
    bitmap: ImageBitmap?,
    content: @Composable ImageContainerScope.() -> Unit
) {
    var contentSize: IntSize? by remember { mutableStateOf(null) }
    var totalSize: IntSize? by remember { mutableStateOf(null) }

    Box(
        Modifier.fillMaxSize().background(Color.Black).onSizeChanged { totalSize = it }
    ) {

        Box(
            Modifier.align(Alignment.Center).onSizeChanged { contentSize = it }
        ) {
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                )
            }

            Box(
                modifier = Modifier.matchParentSize().takeUnless { bitmap == null } ?: Modifier.fillMaxSize()
            ) {
                ImageContainerScopeInstance(
                    contentSize = contentSize,
                    totalSize = totalSize,
                    boxScope = this
                ).content()
            }
        }
    }
}
