// Copyright (c) 2026 Vern McGeorge. All rights reserved.
package com.twodo2go.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Priority isn't stored as a single field - it's two independent axes (Eisenhower matrix: is this
 * important, is this urgent), set by which quadrant the user taps. [priorityScore] turns that
 * into the sort key.
 */
data class ToDoItem(
    val id: String,
    val description: String,
    val list: String,
    val link: String = "",
    val important: Boolean = false,
    val urgent: Boolean = false,
    val done: Boolean = false,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
    val doneAtEpochMs: Long? = null
)

/** Do First = 3, Schedule = 2, Delegate = 1, Eliminate = 0. */
fun ToDoItem.priorityScore(): Int = (if (important) 2 else 0) + (if (urgent) 1 else 0)

enum class Quadrant(val label: String) {
    DO_FIRST("Do First"),
    SCHEDULE("Schedule"),
    DELEGATE("Delegate"),
    ELIMINATE("Eliminate")
}

fun ToDoItem.quadrant(): Quadrant = when {
    important && urgent -> Quadrant.DO_FIRST
    important && !urgent -> Quadrant.SCHEDULE
    !important && urgent -> Quadrant.DELEGATE
    else -> Quadrant.ELIMINATE
}

/** Open items highest-priority-first, ties broken by whichever was added first. */
fun sortedForDisplay(items: List<ToDoItem>): List<ToDoItem> =
    items.sortedWith(compareByDescending<ToDoItem> { it.priorityScore() }.thenBy { it.addedAtEpochMs })

private fun JSONObject.optNullableLong(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null

private fun itemToJson(item: ToDoItem): JSONObject = JSONObject().apply {
    put("id", item.id)
    put("description", item.description)
    put("list", item.list)
    put("link", item.link)
    put("important", item.important)
    put("urgent", item.urgent)
    put("done", item.done)
    put("addedAtEpochMs", item.addedAtEpochMs)
    put("doneAtEpochMs", item.doneAtEpochMs ?: JSONObject.NULL)
}

private fun itemFromJson(json: JSONObject): ToDoItem = ToDoItem(
    id = json.getString("id"),
    description = json.getString("description"),
    list = json.getString("list"),
    link = json.optString("link", ""),
    important = json.optBoolean("important", false),
    urgent = json.optBoolean("urgent", false),
    done = json.optBoolean("done", false),
    addedAtEpochMs = json.optLong("addedAtEpochMs", System.currentTimeMillis()),
    doneAtEpochMs = json.optNullableLong("doneAtEpochMs")
)

fun readToDoItems(json: String): List<ToDoItem> = runCatching {
    val values = JSONArray(json)
    List(values.length()) { index -> itemFromJson(values.getJSONObject(index)) }
}.getOrDefault(emptyList())

fun writeToDoItems(items: List<ToDoItem>): String = JSONArray().apply {
    items.forEach { put(itemToJson(it)) }
}.toString()

/**
 * Parses one sheet tab's CSV rows into to-do items for [listName]. Column A is the enabled
 * checkbox (a Google Sheets checkbox exports "TRUE"/"FALSE"; a tab with no checkboxes at all
 * imports everything). Description/link columns are matched by header text so column
 * order/extra columns don't break import. New items always start untriaged (Eliminate quadrant,
 * i.e. bottom of the sort) - triage happens in the app, not the sheet.
 */
fun parseToDoCsv(csvText: String, listName: String): List<ToDoItem> {
    if (csvText.isBlank()) return emptyList()

    val rows = csvText.lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map { splitCsvLine(it) }
        .toList()
    if (rows.isEmpty()) return emptyList()

    val header = rows.first().map { it.lowercase() }
    val descriptionIndex = header.indexOfFirst { it.contains("description") }
    val linkIndex = header.indexOfFirst { it.contains("link") || it.contains("url") }
    if (descriptionIndex == -1) return emptyList()

    val dataRows = rows.drop(1)
    val columnA = dataRows.map { it.firstOrNull()?.lowercase().orEmpty() }
    val tabUsesCheckboxes = columnA.any { it == "true" || it == "false" }

    return dataRows.mapIndexedNotNull { index, row ->
        if (row.size <= descriptionIndex) return@mapIndexedNotNull null
        val description = row[descriptionIndex].trim()
        if (description.isEmpty()) return@mapIndexedNotNull null
        val checked = if (tabUsesCheckboxes) columnA[index] == "true" else true
        if (!checked) return@mapIndexedNotNull null
        ToDoItem(
            // Deterministic so a re-sync recognizes the same row instead of duplicating it.
            // Editing a description in the sheet therefore reads as a new item, same convention
            // MicroTasking's task-pool import uses.
            id = "sheet-$listName-$description",
            description = description,
            list = listName,
            link = row.getOrNull(linkIndex).orEmpty().trim()
        )
    }
}

/**
 * Folds freshly-imported sheet items into the existing set. Only adds items not already present
 * (matched by id) - never removes or overwrites an existing item just because its sheet row
 * disappeared, got unchecked, or the re-import ran again. An item you're already treating as a
 * live to-do (priority triage, done state) is yours until you deal with it in the app; the sheet
 * is a source of new items, not a mirror to sync down to. See SPEC.md.
 */
fun mergeImportedToDoItems(imported: List<ToDoItem>, existing: List<ToDoItem>): List<ToDoItem> {
    val existingIds = existing.mapTo(mutableSetOf()) { it.id }
    return existing + imported.filter { it.id !in existingIds }
}

fun readStringList(json: String): List<String> = runCatching {
    val values = JSONArray(json)
    List(values.length()) { values.getString(it) }
}.getOrDefault(emptyList())

fun writeStringList(values: List<String>): String = JSONArray().apply {
    values.forEach { put(it) }
}.toString()
