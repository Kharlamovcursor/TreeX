package ru.hdlabs.treex.domain.graph

/**
 * Family hub: partners and shared children. Either partner may be null (incomplete union / OpenLink slot).
 */
data class Family(
    val id: String,
    val partnerAId: String? = null,
    val partnerBId: String? = null,
    val childrenIds: List<String> = emptyList(),
)
