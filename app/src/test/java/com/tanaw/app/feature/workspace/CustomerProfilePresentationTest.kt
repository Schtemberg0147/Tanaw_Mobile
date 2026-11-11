package com.tanaw.app.feature.workspace

import org.junit.Assert.assertEquals
import org.junit.Test

class CustomerProfilePresentationTest {

    @Test
    fun initialsExtractsUpToTwoInitialsUppercase() {
        assertEquals("MS", CustomerProfilePresentation.initials("Maria Santos"))
        assertEquals("J", CustomerProfilePresentation.initials("John"))
        assertEquals("JD", CustomerProfilePresentation.initials("john doe"))
        assertEquals("AB", CustomerProfilePresentation.initials("Alice Bob Charlie"))
        assertEquals("C", CustomerProfilePresentation.initials(""))
        assertEquals("C", CustomerProfilePresentation.initials("   "))
    }

    @Test
    fun memberSinceFormatsDateCorrectly() {
        assertEquals("MEMBER SINCE JANUARY 2025", CustomerProfilePresentation.memberSince(null))
        assertEquals("MEMBER SINCE JANUARY 2025", CustomerProfilePresentation.memberSince(""))
        assertEquals("MEMBER SINCE OCTOBER 2024", CustomerProfilePresentation.memberSince("2024-10-15T08:00:00Z"))
        assertEquals("MEMBER SINCE MARCH 2023", CustomerProfilePresentation.memberSince("2023-03-01"))
        assertEquals("MEMBER SINCE JANUARY 2025", CustomerProfilePresentation.memberSince("invalid-date"))
    }
}
