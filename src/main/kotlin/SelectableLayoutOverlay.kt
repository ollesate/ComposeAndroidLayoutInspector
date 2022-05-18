import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toSize
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
fun ImageContainerScope.SelectableLayoutOverlay(
    realImageSize: IntSize,
    rootNode: ViewNode,
    pixelsPerDp: Float?
) {
    var primarySelection: ViewNode? by remember { mutableStateOf(null) }
    var secondarySelection: ViewNode? by remember { mutableStateOf(null) }
    var measureLines: List<Line> by remember { mutableStateOf(emptyList()) }

    val totalSize = totalSize ?: return
    val imageSize = contentSize ?: return
    val scale = realImageSize.scaleToFitIn(imageSize)

    Canvas(
        modifier = Modifier
            .matchParentSize()
            .pointerInput(scale) {
                detectTapGestures(
                    onTap = { position ->
                        val tappedNode = rootNode.select(position.div(scale))
                        println("Selected node ${Offset(tappedNode.bounds.top, tappedNode.bounds.left)}")
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

    pixelsPerDp ?: return


    Box(
        modifier = Modifier.matchParentSize()
    ) {
        // Draw labels for primary box to show how big it is
        val _primarySelection = primarySelection
        if (_primarySelection != null && secondarySelection == null) {
            listOf(
                _primarySelection.bounds.topCenter to _primarySelection.bounds.width,
                _primarySelection.bounds.centerLeft to _primarySelection.bounds.height,
            ).forEach { (labelPosition, measurementSize) ->
                val sizeText = "%.1f".format(measurementSize.div(pixelsPerDp))
                    .replace(".0", "")
                    .plus("dp")

                Text(
                    text = sizeText,
                    fontSize = 32.times(scale).coerceAtLeast(12f).sp,
                    modifier = Modifier
                        .coerceInside(totalSize, labelPosition.times(scale))
                        .background(Color.LightGray),
                )
            }
        }

        // Draw labels for lines to show distance
        measureLines.forEach { line ->
            val position = line.center().times(scale)

            val tag = "%.1f".format(line.distance().div(pixelsPerDp))
                .replace(".0", "")
                .plus("dp")

            Text(
                text = tag,
                fontSize = 32.times(scale).coerceAtLeast(12f).sp,
                modifier = Modifier
                    .coerceInside(totalSize, position)
                    .background(Color.LightGray),
            )
        }
    }
}

// Assuming the inner box is centered inside outer box
private fun Modifier.coerceInside(
    outerBound: IntSize,
    position: Offset
): Modifier {
    return layout { measurable, constraints ->
        val padding = (outerBound.toSize() - Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())) / 2f

        val placeable = measurable.measure(constraints.copy(maxWidth = Int.MAX_VALUE))

        layout(constraints.maxWidth, constraints.maxHeight) {
            val x = (position.x - placeable.measuredWidth / 2)
                .coerceAtLeast(-padding.width)
                .coerceAtMost(outerBound.width - placeable.measuredWidth.toFloat())
            val y = (position.y - placeable.measuredHeight / 2)
                .coerceAtLeast(-padding.height)
                .coerceAtMost(outerBound.height - placeable.measuredHeight.toFloat())

            placeable.place(x.toInt(), y.toInt())
        }
    }
}

private fun IntSize.scaleToFitIn(totalSize: IntSize): Float {
    val contentSize = this
    val widthScale = totalSize.width.toFloat() / contentSize.width
    return widthScale
        .takeUnless { contentSize.height * widthScale > totalSize.height }
        ?: (totalSize.height.toFloat() / contentSize.height)
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
    ).forEach { currentLine: Line ->
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
    ).forEachIndexed { index, currentLine: Line ->
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