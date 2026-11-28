package com.novabox.tv.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogosTest {
    @Test fun namesNormalizeLikeTheBuilder() {
        assertEquals("ARYDIGITAL", Logos.normalize("PK| ARY Digital HD"))
        assertEquals("SKYSPORTSMAINEVENT", Logos.normalize("UK: Sky Sports Main Event FHD"))
        assertEquals("BOLENTERTAINMENT", Logos.normalize("BOL Entertainment (1080p)"))
        assertEquals("STARPLUS", Logos.normalize("Star+"))
        assertEquals("GB", Logos.countryHint("UK: BBC One"))
        assertNull(Logos.countryHint("BBC One"))
    }

    @Test fun ambiguousNamesFollowTheirCategory() {
        fun ch(name: String, genre: String) = Channel(name, name, 1, "", "", genre)
        val index = mapOf(
            "ARYDIGITAL" to listOf("PK" to "ary"),
            "HUMTV" to listOf("PK" to "hum"),
            "APLUS" to listOf("FR" to "aplus-fr", "PK" to "aplus-pk"),
            "NEWS" to listOf("FR" to "n-fr", "MX" to "n-mx"),
        )
        val out = Logos.resolve(
            listOf(ch("ARY Digital", "urdu"), ch("Hum TV", "urdu"), ch("A Plus", "urdu"), ch("News", "urdu")),
            emptyList(), index,
        )
        assertEquals("ary", out["ARY Digital"])
        assertEquals("aplus-pk", out["A Plus"])
        assertNull(out["News"])  // no country fits: initials rather than a wrong logo
    }
}
