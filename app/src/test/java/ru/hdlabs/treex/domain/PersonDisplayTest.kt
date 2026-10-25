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
