package ru.hdlabs.treex.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.hdlabs.treex.domain.capitalizePersonName
import ru.hdlabs.treex.domain.stableUnionId
import java.security.MessageDigest

object TreeCodec {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
        prettyPrint = true
    }

    fun encode(doc: TreeDocument): String = json.encodeToString(normalize(doc))

    fun contentHash(doc: TreeDocument): String {
        val stable = normalize(doc).copy(exportedAtMillis = 0)
        val bytes = json.encodeToString(stable).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }

    fun decode(text: String): TreeDocument {
        val clean = text.trim().removePrefix("\uFEFF")
        val doc = try {
            json.decodeFromString<TreeDocument>(clean)
        } catch (e: Exception) {
            throw IllegalArgumentException("Файл древа повреждён или это не JSON HDLabs")
        }
        require(doc.format == TreeDocument.FORMAT) {
            "Это не файл древа HDLabs"
        }
        require(doc.version in 1..TreeDocument.VERSION) {
            "Файл создан в более новой версии приложения. Обновите приложение, чтобы не потерять данные."
        }
        require(doc.people.all { it.id.isNotBlank() }) { "В файле есть человек без идентификатора" }
        require(doc.people.map { it.id }.distinct().size == doc.people.size) {
            "В файле повторяются идентификаторы людей"
        }
        return normalize(doc)
    }

    fun normalize(doc: TreeDocument): TreeDocument {
        val ids = doc.people.map { it.id }.toSet()
        val people = doc.people
            .sortedBy { it.id }
            .map { person ->
                person.copy(
                    lastName = capitalizePersonName(person.lastName),
                    birthLastName = capitalizePersonName(person.birthLastName),
                    firstName = capitalizePersonName(person.firstName),
                    middleName = capitalizePersonName(person.middleName),
                    gender = if (person.gender == "m" || person.gender == "f") person.gender else "u",
                    fatherId = person.fatherId?.takeIf { it in ids && it != person.id },
                    motherId = person.motherId?.takeIf { it in ids && it != person.id && it != person.fatherId },
                    photoFile = sanitizePhoto(person.photoFile),
                )
            }
        val unions = doc.unions
            .mapNotNull { union ->
                val a = union.partnerAId?.takeIf { it in ids }
                val b = union.partnerBId?.takeIf { it in ids }
                if (a == null && b == null) return@mapNotNull null
                if (a != null && b != null && a == b) return@mapNotNull null
                val childIds = union.childIds.filter { it in ids }.distinct().sorted()
                when {
                    a != null && b != null -> {
                        val left = minOf(a, b)
                        val right = maxOf(a, b)
                        UnionRecord(
                            id = union.id.ifBlank { stableUnionId(left, right) },
                            partnerAId = left,
                            partnerBId = right,
                            marriedOn = union.marriedOn.trim(),
                            childIds = childIds,
                            unionType = union.unionType.ifBlank { "marriage" },
                        )
                    }
                    else -> {
                        val solo = a ?: b!!
                        UnionRecord(
                            id = union.id.ifBlank { "pu-$solo" },
                            partnerAId = solo,
                            partnerBId = null,
                            marriedOn = union.marriedOn.trim(),
                            childIds = childIds,
                            unionType = union.unionType.ifBlank { "marriage" },
                        )
                    }
                }
            }
            .groupBy { it.id }
            .map { (_, same) ->
                val first = same.first()
                first.copy(
                    marriedOn = same.firstOrNull { it.marriedOn.isNotBlank() }?.marriedOn.orEmpty(),
                    childIds = same.flatMap { it.childIds }.distinct().sorted(),
                )
            }
            .sortedBy { it.id }
        val unionKeys = unions.mapNotNull { u ->
            val a = u.partnerAId ?: return@mapNotNull null
            val b = u.partnerBId ?: return@mapNotNull null
            a to b
        }.toMutableSet()
        val inferred = people.mapNotNull { person ->
            val fatherId = person.fatherId ?: return@mapNotNull null
            val motherId = person.motherId ?: return@mapNotNull null
            if (fatherId == motherId) return@mapNotNull null
            val a = minOf(fatherId, motherId)
            val b = maxOf(fatherId, motherId)
            if (a to b in unionKeys) return@mapNotNull null
            unionKeys.add(a to b)
            UnionRecord(
                id = stableUnionId(a, b),
                partnerAId = a,
                partnerBId = b,
                childIds = listOf(person.id),
            )
        }
        val allUnions = (unions + inferred).sortedBy { it.id }
        return doc.copy(
            format = TreeDocument.FORMAT,
            version = TreeDocument.VERSION,
            people = people,
            unions = allUnions,
        )
    }

    fun sanitizePhoto(path: String): String {
        val normalized = path.replace('\\', '/').removePrefix("/")
        return if (normalized.matches(Regex("photos/[A-Za-z0-9._-]{1,80}"))) normalized else ""
    }
}
