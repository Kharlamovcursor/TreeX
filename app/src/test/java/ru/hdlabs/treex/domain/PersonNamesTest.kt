package ru.hdlabs.treex.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonNamesTest {
    @Test
    fun capitalizePersonNameHandlesRussianParts() {
        assertEquals("Иван", capitalizePersonName("иван"))
        assertEquals("Иванов", capitalizePersonName("  иванов "))
        assertEquals("Мария-Анна", capitalizePersonName("мария-анна"))
        assertEquals("Пётр Сергеевич", capitalizePersonName("пётр   сергеевич"))
        assertEquals("", capitalizePersonName("   "))
    }
}
