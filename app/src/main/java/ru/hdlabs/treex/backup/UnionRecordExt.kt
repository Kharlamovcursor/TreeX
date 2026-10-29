package ru.hdlabs.treex.backup

fun UnionRecord.partnerIds(): List<String> = listOfNotNull(partnerAId, partnerBId)

fun UnionRecord.hasBothPartners(): Boolean = partnerAId != null && partnerBId != null
