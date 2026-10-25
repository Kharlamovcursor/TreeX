package ru.hdlabs.treex.backup

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import ru.hdlabs.treex.domain.capitalizePersonName
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
                if (union.partnerAId !in ids || union.partnerBId !in ids) return@mapNotNull null
                if (union.partnerAId == union.partnerBId) return@mapNotNull null
                val a = minOf(union.partnerAId, union.partnerBId)
                val b = maxOf(union.partnerAId, union.partnerBId)
                UnionRecord(id = "u-$a-$b", partnerAId = a, partnerBId = b, marriedOn = union.marriedOn.trim())
            }
            .groupBy { it.id }
            .map { (_, same) ->
                same.first().copy(marriedOn = same.firstOrNull { it.marriedOn.isNotBlank() }?.marriedOn.orEmpty())
            }
            .sortedBy { it.id }
        return doc.copy(
            format = TreeDocument.FORMAT,
            version = TreeDocument.VERSION,
            people = people,
            unions = unions,
        )
    }

    fun sanitizePhoto(path: String): String {
        val normalized = path.replace('\\', '/').removePrefix("/")
        return if (normalized.matches(Regex("photos/[A-Za-z0-9._-]{1,80}"))) normalized else ""
    }
}
