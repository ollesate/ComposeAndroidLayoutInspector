import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import java.awt.SystemColor

data class ViewNode(
    val className: String,
    val resourceId: String,
    val bounds: Rect,
    val text: String? = null,
    val children: List<ViewNode> = emptyList()
) {

}

fun ViewNode.prettyPrint() {
    toStringOnNode { level ->
        val padLeft = (0 until level).joinToString(separator = "") { " " }
        padLeft + "<$level $resourceId class=${className}, bounds=${bounds} ${SystemColor.text}>"
    }
}


fun ViewNode.toStringOnNode(
    level: Int = 0,
    block: ViewNode.(level: Int) -> String
) {
    println(
        block(level)
    )
    children.forEach { child ->
        child.apply {
            toStringOnNode(level + 1, block)
        }
    }
}

fun ViewNode.findNodes(predicate: (ViewNode) -> Boolean): List<ViewNode> {
    return listOf(this) + children.filter(predicate).map { it.findNodes(predicate) }.flatten()
}

fun ViewNode.allNodes() = findNodes { true }

fun ViewNode.select(position: Offset): ViewNode {
    return findNodes { it.bounds.contains(position) }
        .minByOrNull { it.bounds.size.area } ?: error("Found no node on position $position")
}