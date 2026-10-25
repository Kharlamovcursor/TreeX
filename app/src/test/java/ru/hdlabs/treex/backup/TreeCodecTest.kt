package ru.hdlabs.treex.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity
import ru.hdlabs.treex.data.local.UnionEntity
import ru.hdlabs.treex.domain.SpouseDraft
import ru.hdlabs.treex.domain.TreeLayout
import ru.hdlabs.treex.domain.TreeRules
import ru.hdlabs.treex.domain.mergePerson
import ru.hdlabs.treex.domain.mergeSpouseDrafts
import ru.hdlabs.treex.domain.acceptsAsSpouse
import ru.hdlabs.treex.domain.addSpouseLabel
import ru.hdlabs.treex.domain.spouseNoun
import ru.hdlabs.treex.domain.suggestPatronymic
import ru.hdlabs.treex.update.ReleaseVersionParser

class TreeCodecTest {
    @Test
    fun gedcomRoundTripKeepsRussianNameContactsAndParents() {
        val document = TreeDocument(
            people = listOf(
                PersonRecord(
                    id = "p1",
                    lastName = "Иванов",
                    firstName = "Иван",
                    middleName = "Иванович",
                    gender = "m",
                    birthDate = "1950-01-02",
                    birthPlace = "Казань",
                    email = "ivan@example.com",
                    phone = "+79990001122",
                ),
                PersonRecord(
                    id = "p2",
                    lastName = "Иванова",
                    birthLastName = "Петрова",
                    firstName = "Мария",
                    gender = "f",
                ),
                PersonRecord(
                    id = "p3",
                    lastName = "Иванов",
                    firstName = "Пётр",
                    gender = "m",
                    fatherId = "p1",
                    motherId = "p2",
                ),
            ),
            unions = listOf(UnionRecord("u", "p2", "p1", marriedOn = "1970-05-01")),
        )
        val imported = GedcomCodec.import(GedcomCodec.export(document))
        val ivan = imported.people.first { it.id == "p1" }
        val maria = imported.people.first { it.id == "p2" }
        val child = imported.people.first { it.id == "p3" }
        assertEquals("Иванович", ivan.middleName)
        assertEquals("ivan@example.com", ivan.email)
        assertEquals("+79990001122", ivan.phone)
        assertEquals("1950-01-02", ivan.birthDate)
        assertEquals("Казань", ivan.birthPlace)
        assertEquals("p1", child.fatherId)
        assertEquals("p2", child.motherId)
        assertEquals("Петрова", maria.birthLastName)
        assertEquals(1, imported.unions.size)
        assertEquals("1970-05-01", imported.unions.first().marriedOn)
    }

    @Test
    fun patronymicFollowsRussianNames() {
        assertEquals("Иванович", suggestPatronymic("Иван", "m"))
        assertEquals("Ивановна", suggestPatronymic("Иван", "f"))
        assertEquals("Николаевич", suggestPatronymic("Николай", "m"))
        assertEquals("Львович", suggestPatronymic("Лев", "m"))
        assertEquals("", suggestPatronymic("Иван", "u"))
    }

    @Test
    fun newerSchemaIsRejected() {
        val error = runCatching {
            TreeCodec.decode("""{"format":"hdlabs-tree","version":99,"people":[]}""")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
        assertTrue(error?.message.orEmpty().contains("новой"))
    }

    @Test
    fun spouseWithoutParentsStandsInTheSameGeneration() {
        val bands = TreeLayout.bands(
            listOf(
                PersonEntity(id = "f", lastName = "Старший"),
                PersonEntity(id = "c", lastName = "Младший", fatherId = "f"),
                PersonEntity(id = "s", lastName = "Супруга"),
            ),
            listOf(UnionEntity("u-c-s", "c", "s")),
        )
        assertEquals(listOf("f"), bands[0].map { it.id })
        assertEquals(setOf("c", "s"), bands[1].map { it.id }.toSet())
    }

    @Test
    fun childCannotBecomeParentOfAncestor() {
        val parents = mapOf("a" to (null to null), "b" to ("a" to null))
        assertTrue(TreeRules.createsCycle(parents, "a", "b", null))
        assertTrue(!TreeRules.createsCycle(parents, "b", "a", null))
    }

    @Test
    fun versionTagMatchesAppCode() {
        assertEquals(10000, ReleaseVersionParser.parseVersionCode("v1.0.0", 0))
        assertEquals(10001, ReleaseVersionParser.parseVersionCode("v1.0.1", 0))
        assertEquals(10002, ReleaseVersionParser.parseVersionCode("v1.0.2", 0))
        assertEquals(10004, ReleaseVersionParser.parseVersionCode("v1.0.4", 0))
        assertEquals(10005, ReleaseVersionParser.parseVersionCode("v1.0.5", 0))
        assertEquals(10006, ReleaseVersionParser.parseVersionCode("v1.0.6", 0))
        assertEquals(10009, ReleaseVersionParser.parseVersionCode("v1.0.9", 0))
    }

    @Test
    fun leavingTheFormDoesNotEraseUntouchedSpouseFields() {
        val saved = PersonEntity(
            id = "wife",
            lastName = "Иванова",
            firstName = "Мария",
            phone = "+79990001122",
            email = "maria@example.com",
            gender = "f",
        )
        val blankForm = PersonEntity(id = "wife", firstName = "Мария")
        val merged = mergePerson(saved, blankForm, touched = setOf("firstName"))
        assertEquals("Иванова", merged.lastName)
        assertEquals("+79990001122", merged.phone)
        assertEquals("maria@example.com", merged.email)
        assertEquals("f", merged.gender)

        val kept = mergeSpouseDrafts(
            previous = listOf(SpouseDraft("husband", "2001-05-01")),
            incoming = emptyList(),
            replace = false,
        )
        assertEquals(listOf(SpouseDraft("husband", "2001-05-01")), kept)
    }

    @Test
    fun spouseOfferFollowsFocusGender() {
        assertEquals("Супруга", spouseNoun("m"))
        assertEquals("Супруг", spouseNoun("f"))
        assertEquals("Супруг", spouseNoun("u"))
        assertEquals("Добавить супругу", addSpouseLabel("m"))
        assertEquals("Добавить супруга", addSpouseLabel("f"))
        assertEquals("Добавить супруга", addSpouseLabel("u"))
        assertTrue(acceptsAsSpouse("m", "f"))
        assertTrue(!acceptsAsSpouse("m", "m"))
        assertTrue(!acceptsAsSpouse("m", "u"))
        assertTrue(acceptsAsSpouse("f", "m"))
        assertTrue(!acceptsAsSpouse("f", "f"))
        assertTrue(acceptsAsSpouse("u", "m"))
        assertTrue(acceptsAsSpouse("u", "f"))
        assertTrue(acceptsAsSpouse("u", "u"))
    }
}
