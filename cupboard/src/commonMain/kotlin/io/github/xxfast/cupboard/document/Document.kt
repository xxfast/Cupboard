@file:OptIn(ExperimentalUuidApi::class)

package io.github.xxfast.cupboard.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

fun newId(): String = Uuid.random().toString()

/**
 * All geometry is in document units: points at 1x on the fixed 944x531 slide.
 * Colors are packed ARGB (0xAARRGGBB).
 */
@Serializable
data class Document(
    val id: String = newId(),
    val name: String = "Untitled",
    val slideWidth: Float = SLIDE_WIDTH,
    val slideHeight: Float = SLIDE_HEIGHT,
    val nodes: List<SlideNode> = emptyList(),
) {
    companion object {
        const val SLIDE_WIDTH: Float = 944f
        const val SLIDE_HEIGHT: Float = 531f
    }
}

/** Navigator tree: a node is either a slide or a collapsible group of nodes. */
@Serializable
sealed interface SlideNode {
    val id: String
    val title: String
}

@Serializable
@SerialName("slide")
data class Slide(
    override val id: String = newId(),
    override val title: String = "Untitled slide",
    val elements: List<Element> = emptyList(),
    val builds: List<Build> = emptyList(),
    val notes: String = "",
) : SlideNode

@Serializable
@SerialName("group")
data class SlideGroup(
    override val id: String = newId(),
    override val title: String = "Untitled group",
    val children: List<SlideNode> = emptyList(),
    val collapsed: Boolean = false,
) : SlideNode

/** Depth-first slides in presentation order. */
fun Document.allSlides(): List<Slide> {
    fun walk(nodes: List<SlideNode>): List<Slide> = nodes.flatMap { node ->
        when (node) {
            is Slide -> listOf(node)
            is SlideGroup -> walk(node.children)
        }
    }
    return walk(nodes)
}

fun Document.updateSlide(updated: Slide): Document {
    fun walk(nodes: List<SlideNode>): List<SlideNode> = nodes.map { node ->
        when (node) {
            is Slide -> if (node.id == updated.id) updated else node
            is SlideGroup -> node.copy(children = walk(node.children))
        }
    }
    return copy(nodes = walk(nodes))
}
