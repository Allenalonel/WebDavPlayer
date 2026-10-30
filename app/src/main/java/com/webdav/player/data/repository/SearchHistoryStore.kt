package com.webdav.player.data.repository

import android.content.Context
import org.json.JSONArray

interface SearchHistoryStore {
    fun getSearchHistory(): List<String>
    fun addQuery(query: String): List<String>
    fun removeQuery(query: String): List<String>
    fun clear()
}

class SharedPreferencesSearchHistoryStore(
    context: Context,
    prefsName: String = "webdav_search_history",
) : SearchHistoryStore {
    private val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)

    override fun getSearchHistory(): List<String> {
        val raw = prefs.getString("history_json", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { arr.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun addQuery(query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return getSearchHistory()
        val current = getSearchHistory().filter { it != trimmed }
        val updated = (listOf(trimmed) + current).take(20)
        val arr = JSONArray(updated)
        prefs.edit().putString("history_json", arr.toString()).apply()
        return updated
    }

    override fun removeQuery(query: String): List<String> {
        val updated = getSearchHistory().filter { it != query }
        val arr = JSONArray(updated)
        prefs.edit().putString("history_json", arr.toString()).apply()
        return updated
    }

    override fun clear() {
        prefs.edit().remove("history_json").apply()
    }
}
