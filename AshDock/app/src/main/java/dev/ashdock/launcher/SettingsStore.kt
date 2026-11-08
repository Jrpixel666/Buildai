package dev.ashdock.launcher

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

data class LinuxShortcut(val label: String, val command: String, val graphical: Boolean = false)

class SettingsStore(ctx: Context) {
    private val prefs: SharedPreferences = ctx.getSharedPreferences("ashdock", Context.MODE_PRIVATE)
    var theme: String
        get() = prefs.getString("theme", "EMBER") ?: "EMBER"
        set(v) = prefs.edit().putString("theme", v).apply()
    var gridColumns: Int
        get() = prefs.getInt("grid", 4).coerceIn(3, 6)
        set(v) = prefs.edit().putInt("grid", v.coerceIn(3, 6)).apply()
    var textScale: Int
        get() = prefs.getInt("scale", 100).coerceIn(80, 130)
        set(v) = prefs.edit().putInt("scale", v.coerceIn(80, 130)).apply()
    var showClock: Boolean
        get() = prefs.getBoolean("clock", true)
        set(v) = prefs.edit().putBoolean("clock", v).apply()
    var compactApps: Boolean
        get() = prefs.getBoolean("compact", false)
        set(v) = prefs.edit().putBoolean("compact", v).apply()
    var use24Hour: Boolean
        get() = prefs.getBoolean("24hour", true)
        set(v) = prefs.edit().putBoolean("24hour", v).apply()
    var showLinuxHome: Boolean
        get() = prefs.getBoolean("linux_home", true)
        set(v) = prefs.edit().putBoolean("linux_home", v).apply()
    var leftHanded: Boolean
        get() = prefs.getBoolean("left", false)
        set(v) = prefs.edit().putBoolean("left", v).apply()
    var showStatusBar: Boolean
        get() = prefs.getBoolean("status", true)
        set(v) = prefs.edit().putBoolean("status", v).apply()
    var showHints: Boolean
        get() = prefs.getBoolean("hints", true)
        set(v) = prefs.edit().putBoolean("hints", v).apply()
    var accentHex: String
        get() = prefs.getString("custom_accent", "#FF8B2B") ?: "#FF8B2B"
        set(v) = prefs.edit().putString("custom_accent", v).apply()

    fun pinned(): MutableSet<String> = prefs.getStringSet("pinned", mutableSetOf())!!.toMutableSet()
    fun togglePinned(key: String) {
        val set = pinned()
        if (!set.add(key)) set.remove(key)
        prefs.edit().putStringSet("pinned", set).apply()
    }
    fun hidden(): MutableSet<String> = prefs.getStringSet("hidden", mutableSetOf())!!.toMutableSet()
    fun toggleHidden(key: String) {
        val set = hidden()
        if (!set.add(key)) set.remove(key)
        prefs.edit().putStringSet("hidden", set).apply()
    }
    fun shortcuts(): MutableList<LinuxShortcut> {
        val result = mutableListOf<LinuxShortcut>()
        val arr = try { JSONArray(prefs.getString("linux_shortcuts", "[]")) } catch (_: Exception) { JSONArray() }
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val label = o.optString("label").trim()
            val command = o.optString("command").trim()
            if (label.isNotEmpty() && command.isNotEmpty()) result.add(LinuxShortcut(label, command, o.optBoolean("gui")))
        }
        return result
    }
    fun setShortcuts(items: List<LinuxShortcut>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("label", it.label).put("command", it.command).put("gui", it.graphical)) }
        prefs.edit().putString("linux_shortcuts", arr.toString()).apply()
    }
    fun addShortcut(item: LinuxShortcut) {
        val all = shortcuts().filterNot { it.label == item.label }.toMutableList()
        all.add(item)
        setShortcuts(all)
    }
    fun appendConsole(message: String) {
        val previous = prefs.getString("console", "") ?: ""
        prefs.edit().putString("console", (previous + message).takeLast(35000)).apply()
    }
    fun console() = prefs.getString("console", "") ?: ""
    fun clearConsole() = prefs.edit().remove("console").apply()
    fun exportJson(): String {
        val obj = JSONObject()
        obj.put("schema", 1)
        obj.put("theme", theme)
        obj.put("custom_accent", accentHex)
        obj.put("grid", gridColumns)
        obj.put("scale", textScale)
        obj.put("clock", showClock)
        obj.put("compact", compactApps)
        obj.put("24hour", use24Hour)
        obj.put("linux_home", showLinuxHome)
        obj.put("left", leftHanded)
        obj.put("status", showStatusBar)
        obj.put("hints", showHints)
        obj.put("pinned", JSONArray(pinned().toList()))
        obj.put("hidden", JSONArray(hidden().toList()))
        obj.put("linux_shortcuts", JSONArray().also { a ->
            shortcuts().forEach { a.put(JSONObject().put("label", it.label).put("command", it.command).put("gui", it.graphical)) }
        })
        return obj.toString(2)
    }
    fun importJson(raw: String) {
        val obj = JSONObject(raw)
        require(obj.getInt("schema") == 1) { "Unknown backup schema" }
        val edits = prefs.edit()
        listOf("theme", "custom_accent").forEach { if (obj.has(it)) edits.putString(it, obj.getString(it)) }
        listOf("grid", "scale").forEach { if (obj.has(it)) edits.putInt(it, obj.getInt(it)) }
        listOf("clock", "compact", "24hour", "linux_home", "left", "status", "hints").forEach {
            if (obj.has(it)) edits.putBoolean(it, obj.getBoolean(it))
        }
        listOf("pinned", "hidden").forEach { key ->
            val arr = obj.optJSONArray(key) ?: JSONArray()
            val values = mutableSetOf<String>()
            for (i in 0 until arr.length()) values.add(arr.optString(i))
            edits.putStringSet(key, values)
        }
        // Limit imported shortcuts and command lengths to keep preference storage bounded.
        val arr = obj.optJSONArray("linux_shortcuts") ?: JSONArray()
        val clean = JSONArray()
        for (i in 0 until minOf(arr.length(), 150)) {
            val item = arr.optJSONObject(i) ?: continue
            if (item.optString("label").length in 1..80 && item.optString("command").length in 1..2048) clean.put(item)
        }
        edits.putString("linux_shortcuts", clean.toString()).apply()
    }
}
