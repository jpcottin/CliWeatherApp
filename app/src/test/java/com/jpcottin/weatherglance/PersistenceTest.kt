package com.jpcottin.weatherglance

import org.junit.Assert.assertEquals
import org.junit.Test

/** Saved coordinates come back as numbers, or as the default when the store holds nothing usable. */
class PersistenceTest {

    @Test
    fun aSavedCoordinateIsParsedBack() {
        assertEquals(48.8566, coordinateOrDefault("48.8566", 0.0), 1e-9)
        assertEquals(-122.084, coordinateOrDefault("-122.084", 0.0), 1e-9)
    }

    @Test
    fun nothingSavedGivesTheDefault() {
        assertEquals(37.422, coordinateOrDefault(null, 37.422), 1e-9)
    }

    @Test
    fun garbageInTheStoreGivesTheDefaultInsteadOfCrashing() {
        assertEquals(37.422, coordinateOrDefault("abc", 37.422), 1e-9)
        assertEquals(37.422, coordinateOrDefault("", 37.422), 1e-9)
        assertEquals(37.422, coordinateOrDefault("NaN", 37.422), 1e-9)
        assertEquals(37.422, coordinateOrDefault("1e300", 37.422, limit = 90.0), 1e-9)   // finite, impossible
        assertEquals(37.422, coordinateOrDefault("-95", 37.422, limit = 90.0), 1e-9)
    }
}
