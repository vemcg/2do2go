// Copyright (c) 2026 Vern McGeorge. All rights reserved.
package com.twodo2go.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToDoDataTest {

    @Test
    fun parseToDoCsvRows_parsesCheckedStateAndFields() {
        val csv = """
            checked,description,link
            TRUE,Buy milk,
            FALSE,Skip this one,
            TRUE,Call the plumber,https://example.com
        """.trimIndent()
        val rows = parseToDoCsvRows(csv)
        assertEquals(3, rows.size)
        assertEquals(setOf("Buy milk", "Skip this one", "Call the plumber"), rows.map { it.description }.toSet())
        assertFalse(rows.first { it.description == "Skip this one" }.checked)
        assertEquals("https://example.com", rows.first { it.description == "Call the plumber" }.link)
    }

    @Test
    fun parseToDoCsvRows_treatsEveryRowAsChecked_whenTabHasNoCheckboxes() {
        val csv = """
            checked,description
            ,Legacy row one
            ,Legacy row two
        """.trimIndent()
        val rows = parseToDoCsvRows(csv)
        assertEquals(2, rows.size)
        assertTrue(rows.all { it.checked })
    }

    @Test
    fun parseToDoCsvRows_skipsBlankDescriptions() {
        val csv = """
            checked,description
            TRUE,
            TRUE,Real task
        """.trimIndent()
        val rows = parseToDoCsvRows(csv)
        assertEquals(1, rows.size)
        assertEquals("Real task", rows.single().description)
    }

    @Test
    fun toDoItemsFromReferredRows_importsOnlyCheckedRowsWithPrioritySet() {
        val csv = """
            checked,description,link
            TRUE,Buy milk,
            TRUE,Not referred yet,
            FALSE,Referred but unchecked,
        """.trimIndent()
        val priorities = mapOf(
            "Buy milk" to SheetPriority(importance = 0.8f, urgency = 0.2f),
            "Referred but unchecked" to SheetPriority(importance = 0.9f, urgency = 0.9f)
        )
        val items = toDoItemsFromReferredRows(csv, "Errands", priorities)
        // "Not referred yet" is checked but has no priority entry - stays MicroTasking's task.
        // "Referred but unchecked" has a priority entry but isn't checked - excluded too.
        assertEquals(1, items.size)
        val item = items.single()
        assertEquals("Buy milk", item.description)
        assertEquals("sheet-Errands-Buy milk", item.id)
        assertEquals(0.8f, item.importance)
        assertEquals(0.2f, item.urgency)
    }

    @Test
    fun mergeImportedToDoItems_addsOnlyNewIds() {
        val existing = listOf(
            ToDoItem(id = "sheet-List-A", description = "A", list = "List", importance = 1f, urgency = 1f, done = true)
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
        assertEquals(1f, a.importance)
        assertEquals(1f, a.urgency)
    }

    @Test
    fun quadrant_mapsContinuousValuesToLabelsAndScore() {
        assertEquals(Quadrant.DO_FIRST, ToDoItem(id = "1", description = "", list = "", importance = 1f, urgency = 1f).quadrant())
        assertEquals(Quadrant.SCHEDULE, ToDoItem(id = "2", description = "", list = "", importance = 1f, urgency = 0f).quadrant())
        assertEquals(Quadrant.DELEGATE, ToDoItem(id = "3", description = "", list = "", importance = 0f, urgency = 1f).quadrant())
        assertEquals(Quadrant.ELIMINATE, ToDoItem(id = "4", description = "", list = "", importance = 0f, urgency = 0f).quadrant())

        assertEquals(3f, ToDoItem(id = "1", description = "", list = "", importance = 1f, urgency = 1f).priorityScore(DEFAULT_IMPORTANCE_WEIGHT))
        assertEquals(0f, ToDoItem(id = "4", description = "", list = "", importance = 0f, urgency = 0f).priorityScore(DEFAULT_IMPORTANCE_WEIGHT))
        // The weight is a setting, not a constant - a higher weight favors importance further.
        assertEquals(4f, ToDoItem(id = "5", description = "", list = "", importance = 1f, urgency = 0f).priorityScore(4f))
    }

    @Test
    fun sortedForDisplay_ordersByScoreThenAddedTime() {
        val eliminate = ToDoItem(id = "e", description = "", list = "", addedAtEpochMs = 1)
        val doFirstOlder = ToDoItem(id = "d1", description = "", list = "", importance = 1f, urgency = 1f, addedAtEpochMs = 2)
        val doFirstNewer = ToDoItem(id = "d2", description = "", list = "", importance = 1f, urgency = 1f, addedAtEpochMs = 3)
        val schedule = ToDoItem(id = "s", description = "", list = "", importance = 1f, addedAtEpochMs = 4)

        val sorted = sortedForDisplay(listOf(eliminate, schedule, doFirstNewer, doFirstOlder), DEFAULT_IMPORTANCE_WEIGHT)
        assertEquals(listOf("d1", "d2", "s", "e"), sorted.map { it.id })
    }

    @Test
    fun readWriteToDoItems_roundTrips() {
        val items = listOf(
            ToDoItem(
                id = "sheet-List-A", description = "A", list = "List", link = "https://x",
                importance = 0.75f, urgency = 0.25f, progress = 40, done = true, addedAtEpochMs = 100, doneAtEpochMs = 200
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
