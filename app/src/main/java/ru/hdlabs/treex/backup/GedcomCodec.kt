package ru.hdlabs.treex.backup

object GedcomCodec {
    private val months = listOf(
        "JAN", "FEB", "MAR", "APR", "MAY", "JUN",
        "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
    )
    private val lineRegex = Regex("""^(\d+)\s+(?:@([^@]+)@\s+)?(\S+)(?:\s(.*))?$""")

    fun export(document: TreeDocument): String {
        val doc = TreeCodec.normalize(document)
        val people = doc.people.associateBy { it.id }
        val builder = StringBuilder()
        builder.appendLine("0 HEAD")
        builder.appendLine("1 GEDC")
        builder.appendLine("2 VERS 5.5.1")
        builder.appendLine("2 FORM LINEAGE-LINKED")
        builder.appendLine("1 CHAR UTF-8")
        builder.appendLine("1 SOUR HDLabs TreeX")
        doc.people.forEach { person ->
            builder.append("0 @").append(xref(person.id)).append("@ INDI").append('\n')
            val given = listOf(person.firstName, person.middleName).filter { it.isNotBlank() }.joinToString(" ")
            val name = buildString {
                if (given.isNotBlank()) append(given).append(' ')
                append('/').append(person.lastName).append('/')
            }.trim()
            appendValue(builder, 1, "NAME", name)
            if (person.firstName.isNotBlank()) appendValue(builder, 2, "GIVN", person.firstName)
            if (person.lastName.isNotBlank()) appendValue(builder, 2, "SURN", person.lastName)
            if (person.middleName.isNotBlank()) appendValue(builder, 2, "_PATR", person.middleName)
            if (person.birthLastName.isNotBlank()) appendValue(builder, 2, "_MAID", person.birthLastName)
            builder.append("1 SEX ").append(
                when (person.gender) {
                    "m" -> "M"
                    "f" -> "F"
                    else -> "U"
                },
            ).append('\n')
            if (person.birthDate.isNotBlank() || person.birthPlace.isNotBlank()) {
                builder.appendLine("1 BIRT")
                if (person.birthDate.isNotBlank()) appendValue(builder, 2, "DATE", toGedcomDate(person.birthDate))
                if (person.birthPlace.isNotBlank()) appendValue(builder, 2, "PLAC", person.birthPlace)
            }
            if (person.deathDate.isNotBlank()) {
                builder.appendLine("1 DEAT")
                appendValue(builder, 2, "DATE", toGedcomDate(person.deathDate))
            }
            if (person.email.isNotBlank()) appendValue(builder, 1, "EMAIL", person.email)
            if (person.phone.isNotBlank()) appendValue(builder, 1, "PHON", person.phone)
            if (person.notes.isNotBlank()) appendValue(builder, 1, "NOTE", person.notes)
            if (person.photoFile.isNotBlank()) {
                builder.appendLine("1 OBJE")
                appendValue(builder, 2, "FILE", person.photoFile)
            }
        }

        val families = linkedMapOf<String, Family>()
        doc.people.forEach { person ->
            if (person.fatherId == null && person.motherId == null) return@forEach
            val key = familyKey(person.fatherId, person.motherId)
            val family = families.getOrPut(key) { Family(person.fatherId, person.motherId) }
            family.children += person.id
        }
        doc.unions.forEach { union ->
            val children = union.childIds.filter { it in people }
            val partners = listOfNotNull(union.partnerAId, union.partnerBId).filter { it in people }.distinct()
            when (partners.size) {
                0 -> return@forEach
                1 -> {
                    val person = people.getValue(partners.single())
                    val (husband, wife) = singleParentRoles(person)
                    val key = familyKey(husband, wife)
                    val family = families.getOrPut(key) { Family(husband, wife) }
                    family.children += children
                    if (family.marriedOn.isBlank()) family.marriedOn = union.marriedOn
                }
                else -> {
                    val a = people.getValue(partners[0])
                    val b = people.getValue(partners[1])
                    val (husband, wife) = roles(a, b)
                    val key = familyKey(husband, wife)
                    val family = families.getOrPut(key) { Family(husband, wife) }
                    family.children += children
                    if (family.marriedOn.isBlank()) family.marriedOn = union.marriedOn
                }
            }
        }
        families.values.forEachIndexed { index, family ->
            builder.append("0 @F").append(index + 1).append("@ FAM").append('\n')
            family.husband?.let { builder.append("1 HUSB @").append(xref(it)).append("@\n") }
            family.wife?.let { builder.append("1 WIFE @").append(xref(it)).append("@\n") }
            family.children.distinct().forEach { child ->
                builder.append("1 CHIL @").append(xref(child)).append("@\n")
            }
            if (family.marriedOn.isNotBlank()) {
                builder.appendLine("1 MARR")
                appendValue(builder, 2, "DATE", toGedcomDate(family.marriedOn))
            }
        }
        builder.appendLine("0 TRLR")
        return builder.toString()
    }

    fun import(text: String): TreeDocument {
        val roots = parse(text.trim().removePrefix("\uFEFF"))
        val individuals = roots.filter { it.tag == "INDI" && it.pointer != null }
        require(individuals.isNotEmpty()) { "В GEDCOM нет людей" }
        val people = individuals.map { node ->
            val name = node.child("NAME")
            val rawName = name?.value.orEmpty()
            val parsed = parseName(
                raw = rawName,
                given = name?.child("GIVN")?.value,
                surname = name?.child("SURN")?.value,
                patronymic = name?.child("_PATR")?.value,
            )
            val birth = node.child("BIRT")
            val death = node.child("DEAT")
            val photo = node.child("OBJE")?.child("FILE")?.value?.let(TreeCodec::sanitizePhoto).orEmpty()
            PersonRecord(
                id = node.pointer.orEmpty(),
                lastName = parsed.surname,
                firstName = parsed.given,
                middleName = parsed.patronymic,
                birthLastName = name?.child("_MAID")?.value?.trim().orEmpty(),
                gender = when (node.child("SEX")?.value?.uppercase()) {
                    "M" -> "m"
                    "F" -> "f"
                    else -> "u"
                },
                birthDate = fromGedcomDate(birth?.child("DATE")?.value.orEmpty()),
                deathDate = fromGedcomDate(death?.child("DATE")?.value.orEmpty()),
                birthPlace = birth?.child("PLAC")?.value.orEmpty(),
                email = node.children.firstOrNull { it.tag == "EMAIL" }?.value.orEmpty(),
                phone = node.children.firstOrNull { it.tag == "PHON" }?.value.orEmpty(),
                photoFile = photo,
                notes = node.children.filter { it.tag == "NOTE" }.joinToString("\n") { it.value }.trim(),
            )
        }
        val byId = people.associateBy { it.id }.toMutableMap()
        val unions = mutableListOf<UnionRecord>()
        roots.filter { it.tag == "FAM" }.forEach { family ->
            val husband = pointerValue(family.child("HUSB")?.value)
            val wife = pointerValue(family.child("WIFE")?.value)
            if (husband != null && wife != null && husband in byId && wife in byId) {
                val a = minOf(husband, wife)
                val b = maxOf(husband, wife)
                val marriedOn = fromGedcomDate(family.child("MARR")?.child("DATE")?.value.orEmpty())
                unions += UnionRecord("u-$a-$b", a, b, marriedOn)
            }
            family.children.filter { it.tag == "CHIL" }.forEach { childNode ->
                val childId = pointerValue(childNode.value) ?: return@forEach
                val child = byId[childId] ?: return@forEach
                byId[childId] = child.copy(
                    fatherId = child.fatherId ?: husband?.takeIf { it in byId && it != childId },
                    motherId = child.motherId ?: wife?.takeIf { it in byId && it != childId && it != husband },
                )
            }
        }
        return TreeCodec.normalize(
            TreeDocument(
                exportedAtMillis = System.currentTimeMillis(),
                people = byId.values.toList(),
                unions = unions,
            ),
        )
    }

    private fun familyKey(husband: String?, wife: String?): String = "${husband.orEmpty()}|${wife.orEmpty()}"

    private fun singleParentRoles(person: PersonRecord): Pair<String?, String?> = when (person.gender) {
        "f" -> null to person.id
        "m" -> person.id to null
        else -> person.id to null
    }

    private fun roles(first: PersonRecord, second: PersonRecord): Pair<String?, String?> {
        return when {
            first.gender == "m" && second.gender != "m" -> first.id to second.id
            second.gender == "m" && first.gender != "m" -> second.id to first.id
            first.gender == "f" && second.gender != "f" -> second.id to first.id
            second.gender == "f" && first.gender != "f" -> first.id to second.id
            else -> first.id to second.id
        }
    }

    private fun parseName(raw: String, given: String?, surname: String?, patronymic: String?): ParsedName {
        if (!given.isNullOrBlank() || !surname.isNullOrBlank() || !patronymic.isNullOrBlank()) {
            val first = given?.trim().orEmpty().substringBefore(' ').trim()
            val middleFromGiven = given?.trim().orEmpty().substringAfter(' ', "").trim()
            return ParsedName(
                surname = surname?.trim().orEmpty(),
                given = first,
                patronymic = patronymic?.trim().orEmpty().ifBlank { middleFromGiven },
            )
        }
        if (raw.contains(',') && !raw.contains('/')) {
            val last = raw.substringBefore(',').trim()
            val parts = raw.substringAfter(',').trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            return ParsedName(last, parts.firstOrNull().orEmpty(), parts.drop(1).joinToString(" "))
        }
        val last = Regex("""/([^/]*)/""").find(raw)?.groupValues?.get(1).orEmpty().trim()
        val rest = raw.replace(Regex("""/[^/]*"""), "").replace("/", "").trim()
        val parts = rest.split(Regex("\\s+")).filter { it.isNotBlank() }
        return ParsedName(last, parts.firstOrNull().orEmpty(), parts.drop(1).joinToString(" "))
    }

    private fun parse(text: String): List<GedNode> {
        val roots = mutableListOf<GedNode>()
        val stack = ArrayDeque<GedNode>()
        text.lines().forEach { rawLine ->
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) return@forEach
            val match = lineRegex.matchEntire(line) ?: return@forEach
            val level = match.groupValues[1].toInt()
            val pointer = match.groupValues[2].ifBlank { null }
            val tag = match.groupValues[3]
            val value = match.groupValues[4]
            if (tag == "CONC" && stack.isNotEmpty()) {
                stack.last().value += value
                return@forEach
            }
            if (tag == "CONT" && stack.isNotEmpty()) {
                stack.last().value = stack.last().value + "\n" + value
                return@forEach
            }
            val node = GedNode(level, tag, pointer, value)
            while (stack.isNotEmpty() && stack.last().level >= level) stack.removeLast()
            if (stack.isEmpty()) roots += node else stack.last().children += node
            stack.addLast(node)
        }
        roots.forEach { unescape(it) }
        return roots
    }

    private fun unescape(node: GedNode) {
        node.value = node.value.replace("@@", "@")
        node.children.forEach { unescape(it) }
    }

    private fun appendValue(builder: StringBuilder, level: Int, tag: String, value: String) {
        val escaped = value.replace("@", "@@").replace("\r\n", "\n")
        val lines = escaped.split('\n')
        lines.forEachIndexed { index, line ->
            val chunks = line.chunked(200).ifEmpty { listOf("") }
            if (index == 0) {
                builder.append(level).append(' ').append(tag).append(' ').append(chunks.first()).append('\n')
                chunks.drop(1).forEach { chunk ->
                    builder.append(level + 1).append(" CONC ").append(chunk).append('\n')
                }
            } else {
                builder.append(level + 1).append(" CONT ").append(chunks.first()).append('\n')
                chunks.drop(1).forEach { chunk ->
                    builder.append(level + 2).append(" CONC ").append(chunk).append('\n')
                }
            }
        }
    }

    private fun pointerValue(value: String?): String? =
        value?.trim()?.trim('@')?.takeIf { it.isNotBlank() }

    private fun xref(id: String): String = id.replace("@", "").replace(" ", "")

    fun toGedcomDate(value: String): String {
        val match = Regex("""^(\d{4})-(\d{2})-(\d{2})$""").matchEntire(value.trim()) ?: return value.trim()
        val month = months.getOrNull(match.groupValues[2].toInt() - 1) ?: return value.trim()
        return "${match.groupValues[3]} $month ${match.groupValues[1]}"
    }

    fun fromGedcomDate(value: String): String {
        val match = Regex("""^(\d{1,2})\s+([A-Za-z]{3})\s+(\d{4})$""").matchEntire(value.trim()) ?: return value.trim()
        val month = months.indexOf(match.groupValues[2].uppercase())
        if (month < 0) return value.trim()
        return "%s-%02d-%02d".format(match.groupValues[3], month + 1, match.groupValues[1].toInt())
    }

    private data class ParsedName(val surname: String, val given: String, val patronymic: String)
    private data class Family(
        val husband: String?,
        val wife: String?,
        val children: MutableList<String> = mutableListOf(),
        var marriedOn: String = "",
    )

    private class GedNode(
        val level: Int,
        val tag: String,
        val pointer: String?,
        var value: String,
        val children: MutableList<GedNode> = mutableListOf(),
    ) {
        fun child(tag: String): GedNode? = children.firstOrNull { it.tag == tag }
    }
}
