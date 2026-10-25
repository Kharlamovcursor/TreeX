package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hdlabs.treex.data.local.PersonEntity

class PersonDisplayTest {
    @Test
    fun formatCardDateUsesRussianPattern() {
        assertEquals("15.03.1988", formatCardDate("1988-03-15"))
        assertEquals("16.04.1987", formatCardDate("1987-04-16"))
        assertEquals("16.04.1987", formatCardDate("16.04.1987"))
        assertEquals("01.01.1985", formatCardDate("1985"))
    }

    @Test
    fun yearsLabelOnCardWithoutBirthPrefix() {
        val person = PersonEntity(id = "p", birthDate = "1987-04-16")
        assertEquals("16.04.1987", person.yearsLabel())
    }

    @Test
    fun maidenNameShownOnlyForWomen() {
        val man = PersonEntity(id = "m", gender = "m", lastName = "Иванов", birthLastName = "Петров", firstName = "Иван")
        val woman = PersonEntity(id = "f", gender = "f", lastName = "Иванова", birthLastName = "Петрова", firstName = "Мария")
        assertEquals("Иванов Иван", man.fullName())
        assertEquals("Иванова (Петрова) Мария", woman.fullName())
    }

    @Test
    fun suggestedParentFamilyNameUsesMaidenForWomen() {
        val woman = PersonEntity(
            id = "w",
            gender = "f",
            lastName = "Харламова",
            birthLastName = "Лымарь",
            firstName = "Елизавета",
        )
        val man = PersonEntity(id = "m", gender = "m", lastName = "Харламов", firstName = "Яков")
        assertEquals("Лымарь", suggestedParentFamilyName(woman))
        assertEquals("Харламов", suggestedParentFamilyName(man))
    }

    @Test
    fun acceptsAsParentByGender() {
        val man = PersonEntity(id = "m", gender = "m", firstName = "Иван")
        val woman = PersonEntity(id = "f", gender = "f", firstName = "Мария")
        assertTrue(acceptsAsParent("father", man))
        assertFalse(acceptsAsParent("father", woman))
        assertTrue(acceptsAsParent("mother", woman))
        assertFalse(acceptsAsParent("mother", man))
    }
}
