// Copyright (c) 2026 Vern McGeorge. All rights reserved.
package com.twodo2go.app

import org.json.JSONArray
import org.json.JSONObject

/**
 * Priority is two independent continuous axes (Eisenhower matrix: how important, how urgent),
 * each 0f..1f. Referred items get these from MicroTasking (the exact touch point on its matrix
 * widget); ad-hoc/local items get them from 2do2go's own matrix widget. Values are stored raw and
 * unweighted - see [priorityScore], which applies the user's importance-weight setting at
 * read/sort time rather than baking a fixed formula into the stored data.
 */
data class ToDoItem(
    val id: String,
    val description: String,
    val list: String,
    val link: String = "",
    val importance: Float = 0f,
    val urgency: Float = 0f,
    val progress: Int = 0,
    val done: Boolean = false,
    val addedAtEpochMs: Long = System.currentTimeMillis(),
    val doneAtEpochMs: Long? = null
)

/** Default importance weight (matches the old fixed important*2 + urgent formula's ratio). */
const val DEFAULT_IMPORTANCE_WEIGHT = 2f

/**
 * Sort key, computed on read rather than stored: `importance * importanceWeight + urgency`.
 * [importanceWeight] is a user setting (see Settings screen), not a constant - this is a
 * deliberately tunable ranking, not a fixed rule.
 */
fun ToDoItem.priorityScore(importanceWeight: Float): Float = importance * importanceWeight + urgency

enum class Quadrant(val label: String) {
    DO_FIRST("Do First"),
    SCHEDULE("Schedule"),
    DELEGATE("Delegate"),
    ELIMINATE("Eliminate")
}

/** Coarse quadrant for badge display/coloring only - the real priority value stays continuous. */
fun ToDoItem.quadrant(): Quadrant = when {
    importance >= 0.5f && urgency >= 0.5f -> Quadrant.DO_FIRST
    importance >= 0.5f -> Quadrant.SCHEDULE
    urgency >= 0.5f -> Quadrant.DELEGATE
    else -> Quadrant.ELIMINATE
}

/** Open items highest-priority-first (by [importanceWeight]), ties broken by whichever was added first. */
fun sortedForDisplay(items: List<ToDoItem>, importanceWeight: Float): List<ToDoItem> =
    items.sortedWith(
        compareByDescending<ToDoItem> { it.priorityScore(importanceWeight) }.thenBy { it.addedAtEpochMs }
    )

private fun JSONObject.optNullableLong(key: String): Long? =
    if (has(key) && !isNull(key)) getLong(key) else null

private fun itemToJson(item: ToDoItem): JSONObject = JSONObject().apply {
    put("id", item.id)
    put("description", item.description)
    put("list", item.list)
    put("link", item.link)
    put("importance", item.importance.toDouble())
    put("urgency", item.urgency.toDouble())
    put("progress", item.progress)
    put("done", item.done)
    put("addedAtEpochMs", item.addedAtEpochMs)
    put("doneAtEpochMs", item.doneAtEpochMs ?: JSONObject.NULL)
}

private fun itemFromJson(json: JSONObject): ToDoItem = ToDoItem(
    id = json.getString("id"),
    description = json.getString("description"),
    list = json.getString("list"),
    link = json.optString("link", ""),
    importance = json.optDouble("importance", 0.0).toFloat(),
    urgency = json.optDouble("urgency", 0.0).toFloat(),
    progress = json.optInt("progress", 0),
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
 * One sheet row's description/link, as read from the plain CSV export (columns A-C only -
 * importance/urgency never appear there, see [SheetPriority]/[fetchTabPriorities]).
 */
data class SheetRow(val description: String, val link: String, val checked: Boolean)

/**
 * Parses one sheet tab's CSV rows (columns A-C only). Column A is the enabled checkbox (a Google
 * Sheets checkbox exports "TRUE"/"FALSE"; a tab with no checkboxes at all treats every row as
 * checked). Description/link columns are matched by header text so column order/extra columns
 * don't break parsing.
 */
fun parseToDoCsvRows(csvText: String): List<SheetRow> {
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
        SheetRow(description = description, link = row.getOrNull(linkIndex).orEmpty().trim(), checked = checked)
    }
}

/**
 * Gated ingestion: builds to-do items for [listName] from this tab's plain CSV rows plus the
 * importance/urgency values already fetched for that tab via the Apps Script endpoint (see
 * [fetchTabPriorities]). Only checked rows that MicroTasking has actually referred (present in
 * [priorities], keyed by description) become items - a row just checked in the sheet directly,
 * never referred, stays MicroTasking's task and never shows up here. See SPEC.md "Items".
 */
fun toDoItemsFromReferredRows(
    csvText: String,
    listName: String,
    priorities: Map<String, SheetPriority>
): List<ToDoItem> = parseToDoCsvRows(csvText)
    .filter { it.checked }
    .mapNotNull { row ->
        val priority = priorities[row.description] ?: return@mapNotNull null
        ToDoItem(
            // Deterministic so a re-sync recognizes the same row instead of duplicating it.
            // Editing a description in the sheet therefore reads as a new item, same convention
            // MicroTasking's task-pool import uses.
            id = "sheet-$listName-${row.description}",
            description = row.description,
            list = listName,
            link = row.link,
            importance = priority.importance,
            urgency = priority.urgency
        )
    }

/**
 * Folds freshly-imported sheet items into the existing set. Only adds items not already present
 * (matched by id) - never removes or overwrites an existing item just because its sheet row
 * disappeared or the re-import ran again. An item you're already treating as a live to-do
 * (progress, done state) is yours until you deal with it in the app; the sheet is a source of new
 * items, not a mirror to sync down to. See SPEC.md.
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
