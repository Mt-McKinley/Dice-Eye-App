package com.example.dice_eye_app.ml

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ClassMappingTest {

    @Test
    fun mapping_isExpected() {
        val expected = intArrayOf(4, 3, 0, 5, 2, 1)
        assertArrayEquals(expected, ClassMapping.mapping)
    }

    @Test
    fun mapping_isValidAndBijective() {
        assertEquals(true, ClassMapping.isValid())
    }

    @Test
    fun map_bounds() {
        assertEquals(-1, ClassMapping.map(-1))
        assertEquals(4, ClassMapping.map(0))
        assertEquals(1, ClassMapping.map(5))
        assertEquals(-1, ClassMapping.map(6))
    }
}

