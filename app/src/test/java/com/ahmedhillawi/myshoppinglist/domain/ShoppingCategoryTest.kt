package com.ahmedhillawi.myshoppinglist.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ShoppingCategoryTest {

    @Test
    fun `fromString resolves every enum name back to itself`() {
        for (category in ShoppingCategory.entries) {
            assertEquals(category, ShoppingCategory.fromString(category.name))
        }
    }

    @Test
    fun `fromString falls back to GENERAL for an unknown value`() {
        assertEquals(ShoppingCategory.GENERAL, ShoppingCategory.fromString("NOT_A_REAL_CATEGORY"))
    }

    @Test
    fun `fromString falls back to GENERAL for null`() {
        assertEquals(ShoppingCategory.GENERAL, ShoppingCategory.fromString(null))
    }

    @Test
    fun `GENERAL sorts last`() {
        val maxOrder = ShoppingCategory.entries.maxOf { it.order }
        assertEquals(ShoppingCategory.GENERAL.order, maxOrder)
    }
}
