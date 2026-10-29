package ru.hdlabs.treex.domain

data class DeletePersonImpact(
    val personId: String,
    val personLabel: String,
    val becomesVacantSlot: Boolean,
    val childrenStayInTree: List<String>,
    val spousesStayInTree: List<String>,
    val parentLinksRemoved: List<String>,
    val summary: String,
)
