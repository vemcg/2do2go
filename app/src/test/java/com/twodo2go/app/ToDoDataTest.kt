// Copyright (c) 2026 Vern McGeorge. All rights reserved.
package com.twodo2go.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToDoDataTest {

    @Test
    fun parseToDoCsv_importsOnlyCheckedRows() {
        val csv = """
            checked,description,link
            TRUE,Buy milk,
            FALSE,Skip this one,
            TRUE,Call the plumber,https://example.com
        """.trimIndent()
        val items = parseToDoCsv(csv, "Errands")
        assertEquals(2, items.size)
        assertEquals(setOf("Buy milk", "Call the plumber"), items.map { it.description }.toSet())
        assertEquals("https://example.com", items.first { it.description == "Call the plumber" }.link)
        assertTrue(items.all { it.list == "Errands" })
        // Freshly imported items start untriaged (Eliminate quadrant).
        assertTrue(items.all { !it.important && !it.urgent })
    }

    @Test
    fun parseToDoCsv_importsEverything_whenTabHasNoCheckboxes() {
        val csv = """
            checked,description
            ,Legacy row one
            ,Legacy row two
        """.trimIndent()
        val items = parseToDoCsv(csv, "Legacy")
        assertEquals(2, items.size)
    }

    @Test
    fun parseToDoCsv_skipsBlankDescriptions() {
        val csv = """
            checked,description
            TRUE,
            TRUE,Real task
        """.trimIndent()
        val items = parseToDoCsv(csv, "List")
        assertEquals(1, items.size)
        assertEquals("Real task", items.single().description)
    }

    @Test
    fun mergeImportedToDoItems_addsOnlyNewIds() {
        val existing = listOf(
            ToDoItem(id = "sheet-List-A", description = "A", list = "List", important = true, urgent = true, done = true)
        )
        val imported = listOf(
            ToDoItem(id = "sheet-List-A", description = "A", list = "List"),
            ToDoItem(id = "sheet-List-B", description = "B", list = "List")
        )
        val merged = mergeImportedToDoItems(imported, existing)
        assertEquals(2, merged.size)
        // The existing item's triage/done state survives untouched - the sheet copy is ignored.
        val a = merged.first { it.id == "sheet-List-A" }
        assertTrue(a.done)
        assertTrue(a.important && a.urgent)
    }

    @Test
    fun quadrant_mapsBooleansToLabelsAndScore() {
        assertEquals(Quadrant.DO_FIRST, ToDoItem(id = "1", description = "", list = "", important = true, urgent = true).quadrant())
        assertEquals(Quadrant.SCHEDULE, ToDoItem(id = "2", description = "", list = "", important = true, urgent = false).quadrant())
        assertEquals(Quadrant.DELEGATE, ToDoItem(id = "3", description = "", list = "", important = false, urgent = true).quadrant())
        assertEquals(Quadrant.ELIMINATE, ToDoItem(id = "4", description = "", list = "", important = false, urgent = false).quadrant())

        assertEquals(3, ToDoItem(id = "1", description = "", list = "", important = true, urgent = true).priorityScore())
        assertEquals(0, ToDoItem(id = "4", description = "", list = "", important = false, urgent = false).priorityScore())
    }

    @Test
    fun sortedForDisplay_ordersByScoreThenAddedTime() {
        val eliminate = ToDoItem(id = "e", description = "", list = "", addedAtEpochMs = 1)
        val doFirstOlder = ToDoItem(id = "d1", description = "", list = "", important = true, urgent = true, addedAtEpochMs = 2)
        val doFirstNewer = ToDoItem(id = "d2", description = "", list = "", important = true, urgent = true, addedAtEpochMs = 3)
        val schedule = ToDoItem(id = "s", description = "", list = "", important = true, addedAtEpochMs = 4)

        val sorted = sortedForDisplay(listOf(eliminate, schedule, doFirstNewer, doFirstOlder))
        assertEquals(listOf("d1", "d2", "s", "e"), sorted.map { it.id })
    }

    @Test
    fun readWriteToDoItems_roundTrips() {
        val items = listOf(
            ToDoItem(
                id = "sheet-List-A", description = "A", list = "List", link = "https://x",
                important = true, urgent = false, done = true, addedAtEpochMs = 100, doneAtEpochMs = 200
            )
        )
        val roundTripped = readToDoItems(writeToDoItems(items))
        assertEquals(items, roundTripped)
    }

    @Test
    fun readToDoItems_survivesGarbageJson() {
        assertTrue(readToDoItems("not json").isEmpty())
        assertFalse(readToDoItems("[]").isNotEmpty())
    }
}
