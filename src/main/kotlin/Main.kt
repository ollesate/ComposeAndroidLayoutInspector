@file:OptIn(ExperimentalComposeUiApi::class, ExperimentalCoroutinesApi::class)

import androidx.compose.material.MaterialTheme
import androidx.compose.desktop.ui.tooling.preview.Preview
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

fun main(
    args: Array<String>
) = application {

//    return@application Window(
//        onCloseRequest = ::exitApplication,
//        state = WindowState(),
//        undecorated = true,
//    ) {
//        Row {
//            Text(
//                text = "",
//                modifier = Modifier.fillMaxHeight().width(200.dp).background(Color.Green)
//            )
//
//            Box(
//                Modifier
//                    .width(250.dp)
//                    .height(100.dp)
//                    .background(Color.Red)
//            ) {
//                Text(
//                    "16 hello dp",
//                    fontSize = 32.sp,
//                    color = Color.White,
//                    modifier = Modifier
//                        .layout { measurable, constraints ->
//                            println(measurable.parentData)
//                            val placeable = measurable.measure(constraints.copy(maxWidth = Int.MAX_VALUE))
//                            println("placeable measured width ${placeable.measuredWidth}, ${placeable.measuredHeight}")
//                            println("constraints $constraints")
//                            val xOffset = ((constraints.maxWidth - placeable.width) / 2).coerceAtLeast(0)
//                            println("xOffset $xOffset")
//                            layout(constraints.maxWidth, constraints.maxHeight) {
//                                println("layout")
//                                placeable.place(xOffset, 0)
//                            }
//                        }
//                        .background(Color.Black)
//
//                )
//            }
//        }
//    }

//    val (screenshotPath, xmlDumpPath, pixelsPerDp) = screenDump()

//    val bitmap = File(screenshotPath).toBitmap()

    val initialWindowSize = DpSize(
        1080.dp,
        1920.dp
    ).div(2f)

    Window(
        onCloseRequest = ::exitApplication,
        state = WindowState(
            size = initialWindowSize
        ),
        undecorated = true,
    ) {

        val content: LayoutContentResult by produceState(LayoutContentResult()) {

            println("combine collect")
            combine(
                suspendFlow { kotlin.runCatching { screenshot() } },
                suspendFlow { kotlin.runCatching { getLayout() } },
                suspendFlow { kotlin.runCatching { getPixelsPerDp() } },
            ) { imageBitmap, viewNode, pixelsPerDp ->
                println("Result $imageBitmap, $pixelsPerDp, $viewNode")
                value = LayoutContentResult(
                    screenshotBitmap = imageBitmap,
                    rootNode = viewNode,
                    pixelsPerDp = pixelsPerDp,
                )
            }.collect()
        }

        App(
            content = content,
            onReloadPressed = {
                println("No content yet")
//                load()
            }
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
    onReloadPressed: () -> Unit
) {
    // rootNode.prettyPrint()

    val rootNode = content.rootNode?.getOrNull()
    val screenshotBitmap = content.screenshotBitmap?.getOrNull()
    val pixelsPerDp = content.pixelsPerDp?.getOrNull()

    MaterialTheme(
        content = {
            if (rootNode != null && screenshotBitmap != null && pixelsPerDp != null) {
                LayoutInspectorView(
                    screenshotBitmap,
                    rootNode,
                    pixelsPerDp
                )
            } else {
                Box(
                    Modifier.fillMaxSize()
                ) {
                    Column(
                        Modifier.align(Alignment.Center)
                    ) {
                        if (content.screenshotBitmap == null || content.rootNode == null || content.pixelsPerDp == null) {
                            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
                        }
                        if (content.screenshotBitmap == null) {
                            Text("Fetching bitmap...")
                        } else if (content.screenshotBitmap.isFailure) {
                            Text("Failed to get bitmap")
                        }
                        if (content.rootNode == null) {
                            Text("Fetching layout...")
                        } else if (content.rootNode.isFailure) {
                            Text("Failed to get layout")
                        }
                        if (content.pixelsPerDp == null) {
                            Text("Fetching device density...")
                        } else if (content.pixelsPerDp.isFailure) {
                            Text("Failed to get density")
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun LayoutInspectorView(
    screenshotBitmap: ImageBitmap,
    rootNode: ViewNode,
    pixelsPerDp: Float
) {
    var primarySelection: ViewNode? by remember { mutableStateOf(null) }
    var secondarySelection: ViewNode? by remember { mutableStateOf(null) }
    var scale: Float by remember { mutableStateOf(0.5f) }
    var measureLines: List<Line> by remember { mutableStateOf(emptyList()) }
    var boxSize: IntSize by remember { mutableStateOf(IntSize(0, 0)) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .onSizeChanged { size ->
                // To keep aspect ratio consistent we'll first get the amount of scaling required to fill the width
                // If we use that scaling to scale height we'll check if that makes the view higher than it can be and
                // if that's the case we'll use the scaling for height instead.
                val widthScale = size.width.toFloat() / screenshotBitmap.width
                boxSize = size
                scale = widthScale
                    .takeUnless { screenshotBitmap.height * widthScale > size.height }
                    ?: (size.height.toFloat() / screenshotBitmap.height)
            }
    ) {
        Image(
            bitmap = screenshotBitmap,
            contentDescription = null,
            modifier = Modifier
                .width((screenshotBitmap.width * scale).dp)
                .height((screenshotBitmap.height * scale).dp)
                .align(Alignment.Center)
        )
        Canvas(
            modifier = Modifier
                .width((screenshotBitmap.width * scale).dp)
                .height((screenshotBitmap.height * scale).dp)
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { position ->
                            val tappedNode = rootNode.select(position.div(scale))
                            if (primarySelection == null) {
                                primarySelection = tappedNode
                            } else if (tappedNode == primarySelection) {
                                primarySelection = null
                                secondarySelection = null
                            } else {
                                secondarySelection = tappedNode
                            }
                        },
                        onLongPress = { position ->
                            primarySelection = rootNode.select(position.div(scale))
                            secondarySelection = null
                        }
                    )
                }
        ) {
            val realSize = size.div(scale)

            scaledScope(
                scale,
                pivot = Offset.Zero
            ) {
                // Draw debug outlines
                rootNode.allNodes().forEach {
                    // drawNodeOutline(it, Color.Red, strokeWidth = 1f)
                }

                primarySelection?.let {
                    drawNodeOutline(it, Color.Green, strokeWidth = 6f)
                }

                secondarySelection?.let {
                    drawNodeOutline(it, Color.Blue, strokeWidth = 6f)
                }

                if (secondarySelection == null || primarySelection == null) {
                    measureLines = emptyList()
                }

                guardNonNull(
                    primarySelection?.bounds,
                    secondarySelection?.bounds
                ) { (primaryBounds, secondaryBounds) ->

                    println(
                        primaryBounds.bottom
                    )
                    println(
                        secondaryBounds.top
                    )

                    // Measurements above and top
                    val drawnVerticalLines = drawVerticalMeasureLines(primaryBounds, secondaryBounds)
                    drawnVerticalLines.forEach { (fromPoint, toPoint) ->
                        val x = fromPoint.x
                        if (x !in primaryBounds.left..primaryBounds.right) {
                            val (fromX, toX) = if (x < primaryBounds.left) {
                                0f to primaryBounds.left
                            } else {
                                realSize.width to primaryBounds.right
                            }
                            drawGuideline(
                                from = Offset(fromX, fromPoint.y),
                                to = Offset(toX, fromPoint.y)
                            )
                        } else if (x !in secondaryBounds.left..secondaryBounds.right) {
                            val (fromX, toX) = if (x < secondaryBounds.left) {
                                0f to secondaryBounds.left
                            } else {
                                realSize.width to secondaryBounds.right
                            }
                            drawGuideline(
                                from = Offset(fromX, toPoint.y),
                                to = Offset(toX, toPoint.y)
                            )
                        }
                    }

                    val drawnHorizontalLines = drawHorizontalMeasureLines(primaryBounds, secondaryBounds)

                    println("Horizontal lines ")
                    println(drawnHorizontalLines)

                    measureLines = drawnHorizontalLines + drawnVerticalLines


                    drawnHorizontalLines.forEach { (fromPoint, toPoint) ->
                        val y = fromPoint.y
                        if (y !in primaryBounds.top..primaryBounds.bottom) {
                            val (fromY, toY) = if (y < primaryBounds.top) {
                                0f to primaryBounds.top
                            } else {
                                primaryBounds.bottom to realSize.height
                            }
                            drawGuideline(
                                from = Offset(fromPoint.x, fromY),
                                to = Offset(fromPoint.x, toY)
                            )
                        } else if (y !in secondaryBounds.top..secondaryBounds.bottom) {
                            val (fromY, toY) = if (y < secondaryBounds.top) {
                                0f to primaryBounds.top
                            } else {
                                primaryBounds.bottom to realSize.height
                            }
                            drawGuideline(
                                from = Offset(fromPoint.x, fromY),
                                to = Offset(fromPoint.x, toY)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .width((screenshotBitmap.width * scale).dp)
                .height((screenshotBitmap.height * scale).dp)
                .align(Alignment.Center)
        ) {
            measureLines.forEach { line ->
                val center = line.center()
                val offset = boxSize.toSize() - Size(screenshotBitmap.width * scale, screenshotBitmap.height * scale)

                val tag = "%.1f".format(line.distance().div(pixelsPerDp))
                    .replace(".0", "")
                    .plus("dp")

                Text(
                    text = tag,
                    fontSize = 32.times(scale).sp,
                    modifier = Modifier
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(maxWidth = Int.MAX_VALUE))

                            layout(constraints.maxWidth, constraints.maxHeight) {
                                val x = (line.center().x.times(scale) - placeable.measuredWidth / 2)
                                    .coerceAtLeast(-offset.width)
                                    .coerceAtMost(boxSize.width - placeable.measuredWidth.toFloat())
                                val y = (line.center().y.times(scale) - placeable.measuredHeight / 2)
                                    .coerceAtLeast(-offset.height)
                                    .coerceAtMost(boxSize.height - placeable.measuredHeight.toFloat())

                                placeable.place(x.toInt(), y.toInt())
                            }
                        }
                        .background(Color.LightGray),
                )
            }
        }
    }
}

private operator fun Size.minus(size: Size): Size {
    return Size(width - size.width, height - size.height)
}

typealias Line = Pair<Offset, Offset>

private fun ScaledDrawScope.drawVerticalMeasureLines(
    primaryBounds: Rect,
    secondaryBounds: Rect
) : List<Line> {
    val lines = mutableSetOf<Line>()

    // List all combination of lines from one box to other box
    listOf(
        primaryBounds.topCenter to secondaryBounds.topCenter, // Boxes are inside each other
        primaryBounds.topCenter to secondaryBounds.bottomCenter, // Primary box is below
        primaryBounds.bottomCenter to secondaryBounds.topCenter, // Secondary box is below
        primaryBounds.bottomCenter to secondaryBounds.bottomCenter, // Boxes are inside each other
    ).also(::println).forEach { currentLine: Line ->
        val otherIntersectingLine = lines.firstOrNull { currentLine.intersects(it) }
        if (otherIntersectingLine == null) {
            // Found no collision, add it to the list.
            lines.add(currentLine)
        } else {
            // Found collision, check what line has the smallest vertical length
            if (currentLine.verticalDistance() < otherIntersectingLine.verticalDistance()) {
                lines.remove(otherIntersectingLine)
                lines.add(currentLine)
            }
        }
    }
//
//    lines.forEach { (from, to) ->
//        val x = if (primaryBounds.center.x in secondaryBounds.left..secondaryBounds.right) {
//            primaryBounds.center.x
//        } else secondaryBounds.center.x
//        drawLine(Color.Blue, from.copy(x = x), to.copy(x = x))
//    }

    return lines
        .map { (from, to) ->
            val chosenX = primaryBounds.center.x.takeIf { // Chose center x of primary bounds if it's inside left/right of s.box
                primaryBounds.center.x in secondaryBounds.left..secondaryBounds.right
            } ?: secondaryBounds.center.x

            from.copy(x = chosenX) to to.copy(x = chosenX)
        }
        .filterNot { it.verticalDistance() == 0f }
        .onEach { (from, to) ->
            drawMeasureLine(from, to)
        }
}

private fun ScaledDrawScope.drawHorizontalMeasureLines(
    primaryBounds: Rect,
    secondaryBounds: Rect
) : List<Line> {
    val lines = mutableSetOf<Line>()

    // List all combination of lines from one box to other box
    listOf(
        primaryBounds.centerLeft to secondaryBounds.centerLeft, // Boxes are inside each other
        primaryBounds.centerLeft to secondaryBounds.centerRight, // Primary box is to right
        primaryBounds.centerRight to secondaryBounds.centerLeft, // Secondary box is to right
        primaryBounds.centerRight to secondaryBounds.centerRight, // Boxes are inside each other
    ).also(::println).forEachIndexed { index, currentLine: Line ->
        val otherIntersectingLine = lines.firstOrNull { currentLine.intersects(it) }
        if (otherIntersectingLine == null) {
            // Found no collision, add it to the list.
            lines.add(currentLine)
        } else {
            // Found collision, check what line has the smallest vertical length
            if (currentLine.horizontalDistance() < otherIntersectingLine.horizontalDistance()) {
                lines.remove(otherIntersectingLine)
                lines.add(currentLine)
            }
        }
    }

    return lines
        .map { (from, to) ->
            val chosenY = primaryBounds.center.y.takeIf { // Chose center x of primary bounds if it's inside left/right of s.box
                primaryBounds.center.y in secondaryBounds.top..secondaryBounds.bottom
            } ?: secondaryBounds.center.y

            from.copy(y = chosenY) to to.copy(y = chosenY)
        }
        .filterNot { it.horizontalDistance() == 0f }
        .onEach { (from, to) ->
            drawMeasureLine(from, to)
        }
}

private fun ScaledDrawScope.drawMeasureLine(from: Offset, to: Offset) {
    drawLine(Color.Blue, from, to, strokeWidth = 3f)
}

private fun Line.verticalDistance() : Float {
    val (pointA, pointB) = this
    return abs(pointA.y - pointB.y)
}

private fun Line.horizontalDistance() : Float {
    val (pointA, pointB) = this
    return abs(pointA.x - pointB.x)
}

private val Line.startX : Float
    get() = min(first.x, second.x)

private val Line.startY : Float
    get() = min(first.y, second.y)

private fun Line.intersects(line2: Line) : Boolean {
    return this.toRect().overlaps(line2.toRect())
}

private fun Line.center() : Offset {
    val (pointA, pointB) = this
    return Offset((pointA.x + pointB.x) / 2, (pointA.y + pointB.y) / 2)
}

private fun Line.distance(): Float {
    val (pointA, pointB) = this
    return (pointB - pointA).getDistance()
}

private fun Line.toRect(): Rect {
    val topLeft = Offset(min(first.x, second.x), min(first.y, second.y))
    val bottomRight = Offset(max(first.x, second.x), max(first.y, second.y))
    return Rect(
        topLeft = topLeft,
        bottomRight = bottomRight
    ).let {
        // Here we check that neither width nor height is 0 or else they're not going to intersect anything
        val delta = 2f
//        if (it.width == 0f) {
//            return@let it.copy(left = it.left - delta, right = it.right + delta)
//        }
//        if (it.height == 0f) {
//            return@let it.copy(top = it.top - delta, bottom = it.bottom + delta)
//        }
        return@let it.inflate(delta) // Make the box a bit bigger, so we filter out some lines or else we can end up with 3 measure lines cluttering
    }
}

private infix fun Float.between(other: Pair<Float, Float>): Boolean {
    return this > other.first && this < other.second
}

private fun ScaledDrawScope.drawVerticalMeasureLine(
    from: Offset,
    to: Offset
) {

}


private fun ScaledDrawScope.drawMeasureLineUp(
    primaryBounds: Rect,
    secondaryBounds: Rect
) {
    val from = primaryBounds.topCenter
    val to = listOf(
        secondaryBounds.bottomCenter, // Pick the bottom if its above
        secondaryBounds.topCenter // Else check if top is above
    ).filter { it.y < from.y }.minByOrNull { abs(from.y - it.y) } ?: return
    if (primaryBounds.center.x.toInt() in secondaryBounds.left.toInt() until secondaryBounds.right.toInt()) {
        drawLine(Color.Blue, from, to.copy(x = from.x))
    } else {
        drawLine(Color.Blue, from.copy(x = to.x), to)
    }
}

private fun ScaledDrawScope.drawMeasureLineDown(
    primaryBounds: Rect,
    secondaryBounds: Rect
) {
    val from = primaryBounds.bottomCenter
    val to = listOf(
        secondaryBounds.bottomCenter,
        secondaryBounds.topCenter
    ).filter { it.y > from.y }.minByOrNull { abs(from.y - it.y) } ?: return
    if (primaryBounds.center.x.toInt() in secondaryBounds.left.toInt() until secondaryBounds.right.toInt()) {
        drawLine(Color.Blue, from, to.copy(x = from.x))
    } else {
        drawLine(Color.Blue, from.copy(x = to.x), to)
    }
}

private fun ScaledDrawScope.drawMeasureLineLeft(
    primaryBounds: Rect,
    secondaryBounds: Rect
) {
    val from = primaryBounds.centerLeft
    val to = listOf(
        secondaryBounds.centerLeft,
        secondaryBounds.centerRight
    ).filter { it.x < from.x }.minByOrNull { abs(from.x - it.x) } ?: return
    if (primaryBounds.center.y.toInt() in secondaryBounds.top.toInt() until secondaryBounds.bottom.toInt()) {
        drawLine(Color.Blue, from, to.copy(y = from.y))
    } else {
        drawLine(Color.Blue, from.copy(y = to.y), to)
    }
}

private fun ScaledDrawScope.drawMeasureLineRight(
    primaryBounds: Rect,
    secondaryBounds: Rect
) {
    val from = primaryBounds.centerRight
    val to = listOf(
        secondaryBounds.centerLeft,
        secondaryBounds.centerRight
    ).filter { it.x > from.x }.minByOrNull { abs(from.x - it.x) } ?: return
    if (primaryBounds.center.y.toInt() in secondaryBounds.top.toInt() until secondaryBounds.bottom.toInt()) {
        drawLine(Color.Blue, from, to.copy(y = from.y))
    } else {
        drawLine(Color.Blue, from.copy(y = to.y), to)
    }
}

fun DrawScope.drawNodeOutline(
    node: ViewNode,
    outlineColor: Color = Color.Red,
    strokeWidth: Float = 1f
) {
    drawRect(
        outlineColor,
        topLeft = node.bounds.topLeft,
        size = node.bounds.size,
        style = Stroke(width = strokeWidth)
    )
}

fun ScaledDrawScope.drawGuideline(from: Offset, to: Offset) = drawLine(
    Color.Red.copy(alpha = 0.45f),
    from,
    to,
    strokeWidth = 6f,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 20f))
)

fun DrawScope.scaledScope(
    scale: Float,
    pivot: Offset = Offset.Zero,
    block: ScaledDrawScope.() -> Unit
) = scale(scale, pivot) {
    ScaledDrawScope(scale, this).also(block)
}

class ScaledDrawScope(
    private val scale: Float,
    drawScope: DrawScope
) : DrawScope by drawScope {
    val realSize: Size
        get() = size.div(scale)
}

