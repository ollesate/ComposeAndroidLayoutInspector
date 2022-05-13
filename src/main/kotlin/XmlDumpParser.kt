import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

fun createRootNode(xmlDumpPath: String): ViewNode {
    val factory = DocumentBuilderFactory.newInstance()
    val builder = factory.newDocumentBuilder()
    val document = builder.parse(File(xmlDumpPath))

    val hierarchy = document.documentElement
    val rootNode = hierarchy.childNodes.item(0)

    return rootNode.createViewNodeRecursive()
}

private fun Node.attr(key: String) = attributes.getNamedItem(key).nodeValue

private fun Node.createViewNodeRecursive(): ViewNode {
    return ViewNode(
        className = attr("class"),
        resourceId = attr("resource-id"),
        text = attr("text"),
        children = children().map {
            it.createViewNodeRecursive()
        },
        bounds = attr("bounds")
            .findMatches("[0-9]+")  // bounds="[0,0][1080,66]"
            .let { (x1, y1, x2, y2) ->
                Rect(
                    offset = Offset(x1.toFloat(), y1.toFloat()),
                    size = Size(x2.toFloat() - x1.toFloat(), y2.toFloat() - y1.toFloat())
                )
            }
    )
}

private fun Node.children() = (0 until childNodes.length).map {
    childNodes.item(it)
}