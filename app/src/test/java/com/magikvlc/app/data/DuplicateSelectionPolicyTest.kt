package com.magikvlc.app.data

import kotlin.test.Test
import kotlin.test.assertEquals

class DuplicateSelectionPolicyTest {
    @Test
    fun choosingAnotherKeepItemReplacesSelectionOnlyInsideThatGroup() {
        val current = setOf("a", "b", "other")
        val selected = DuplicateSelectionPolicy.replaceGroupSelection(
            currentUris = current,
            groupUris = setOf("a", "b"),
            nextSelectedUris = setOf("a"),
        )

        assertEquals(setOf("a", "other"), selected)
    }
}
