package ru.hdlabs.treex.relatives

import kotlinx.serialization.Serializable

/** JSON shape returned by relatives-tree calcTree (MIT). */
@Serializable
data class RelativesTreeData(
    val canvas: RelativesCanvas,
    val nodes: List<RelativesLayoutNode>,
    val connectors: List<RelativesConnector>,
)

@Serializable
data class RelativesCanvas(val width: Int, val height: Int)

@Serializable
data class RelativesLayoutNode(
    val id: String,
    val gender: String,
    val left: Int,
    val top: Int,
    val hasSubTree: Boolean = false,
)

@Serializable
data class RelativesRelation(val id: String, val type: String)

/** Input node for relatives-tree. */
@Serializable
data class RelativesGraphNode(
    val id: String,
    val gender: String,
    val parents: List<RelativesRelation> = emptyList(),
    val children: List<RelativesRelation> = emptyList(),
    val siblings: List<RelativesRelation> = emptyList(),
    val spouses: List<RelativesRelation> = emptyList(),
)

typealias RelativesConnector = List<Double>
