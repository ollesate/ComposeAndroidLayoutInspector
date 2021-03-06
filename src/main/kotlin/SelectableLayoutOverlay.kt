import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import androidx.compose.ui.text.font.FontWeight
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

        scale(
            scale = scale,
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
        val node = primarySelection
        if (node != null && secondarySelection == null) {
            // Draw labels for primary box to show how big it is
            listOf(
                node.bounds.bottomCenter to node.bounds.width,
                node.bounds.centerLeft to node.bounds.height,
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

            val metadataPosition = node.bounds.topCenter.times(scale)
            Column(
                Modifier.centeredAndAbove(metadataPosition).background(Color.LightGray)
            ) {
                Text(
                    text = node.humanReadableClassName,
                    fontSize = 18.times(scale).coerceAtLeast(14f).sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                node.humanReadableResourceId.takeUnless { it.isBlank() }?.let {
                    Text(
                        text = it,
                        fontSize = 18.times(scale).coerceAtLeast(12f).sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
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

private fun Modifier.centeredAndAbove(
    position: Offset
) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints.copy(maxWidth = Int.MAX_VALUE))

    layout(constraints.maxWidth, constraints.maxHeight) {
        val centerX = (position.x - placeable.measuredWidth / 2)
        val aboveY = (position.y - placeable.measuredHeight)
        placeable.place(centerX.toInt(), aboveY.toInt())
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

private fun DrawScope.drawVerticalMeasureLines(
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

private fun DrawScope.drawHorizontalMeasureLines(
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

private fun DrawScope.drawMeasureLine(from: Offset, to: Offset) {
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
        return@let it.inflate(2f) // Make the box a bit bigger, so we filter out some lines or else we can end up with 3 measure lines cluttering
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

fun DrawScope.drawGuideline(from: Offset, to: Offset) = drawLine(
    Color.Red.copy(alpha = 0.45f),
    from,
    to,
    strokeWidth = 6f,
    pathEffect = PathEffect.dashPathEffect(floatArrayOf(40f, 20f))
)
