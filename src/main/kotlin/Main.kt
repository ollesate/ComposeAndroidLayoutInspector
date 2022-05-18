@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)

import androidx.compose.material.MaterialTheme
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

fun main(
    args: Array<String>
) = application {

    val initialWindowSize = DpSize(
        1080.dp,
        1920.dp
    ).div(2f)

    val onRefreshSignal = Signal<Unit>()

    Window(
        onCloseRequest = ::exitApplication,
        state = WindowState(
            size = initialWindowSize
        ),
        title = "Layout inspector",
        onKeyEvent = {
            if (it.isCtrlPressed && it.key == Key.R && it.type == KeyEventType.KeyUp) {
                onRefreshSignal(Unit)
            }
            true
        }
    ) {
        var content: LayoutContentResult by remember {
            mutableStateOf(LayoutContentResult())
        }
        val scope = rememberCoroutineScope()

        fun load() = scope.launch {
            combine(
                suspendFlow { kotlin.runCatching { screenshot() } },
                suspendFlow { kotlin.runCatching { getLayout() } },
                suspendFlow { kotlin.runCatching { getPixelsPerDp() } },
            ) { imageBitmap, viewNode, pixelsPerDp ->
                println(imageBitmap)
                content = LayoutContentResult(
                    screenshotBitmap = imageBitmap,
                    rootNode = viewNode,
                    pixelsPerDp = pixelsPerDp,
                )
            }.collect()
        }

        LaunchedEffect(Unit) {
            onRefreshSignal {
                load()
            }
            load()
        }

        App(
            content = content
        )
    }
}


fun <T>CoroutineScope.suspendFlow(block: suspend () -> T): Flow<T?> = MutableStateFlow<T?>(null).apply {
    launch(Dispatchers.IO) {
        value = block()
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
) {
    // rootNode.prettyPrint()

    MaterialTheme (
        content = {
            ImageContainerView(
                bitmap = content.screenshotBitmap?.getOrNull(),
            ) {

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
