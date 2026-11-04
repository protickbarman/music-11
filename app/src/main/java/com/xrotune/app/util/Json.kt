package com.xrotune.app.util

import org.json.JSONArray
import org.json.JSONObject

/**
 * Helpers for reading InnerTube responses. The renderer tree changes between
 * server releases, so lookups walk the tree instead of hard-coding deep paths.
 */
object Json {

    fun walk(node: Any?, visit: (JSONObject) -> Unit) {
        when (node) {
            is JSONObject -> {
                visit(node)
                for (key in node.keys()) walk(node.opt(key), visit)
            }
            is JSONArray -> {
                for (i in 0 until node.length()) walk(node.opt(i), visit)
            }
        }
    }

    fun findAll(node: Any?, predicate: (JSONObject) -> Boolean): List<JSONObject> {
        val out = ArrayList<JSONObject>()
        walk(node) { if (predicate(it)) out.add(it) }
        return out
    }

    fun first(node: Any?, predicate: (JSONObject) -> Boolean): JSONObject? {
        var found: JSONObject? = null
        walk(node) {
            if (found == null && predicate(it)) found = it
        }
        return found
    }

    /** Reads `{"runs":[{"text":"a"},...]}` or `{"simpleText":"a"}` into one string. */
    fun runsText(node: JSONObject?): String? {
        if (node == null) return null
        (node.opt("simpleText") as? String)?.takeIf { it.isNotBlank() }?.let { return it }
        val runs = node.optJSONArray("runs") ?: return null
        val sb = StringBuilder()
        for (i in 0 until runs.length()) {
            sb.append(runs.optJSONObject(i)?.opt("text") as? String ?: "")
        }
        return sb.toString().ifBlank { null }
    }

    /** Follows a dotted path, returning null at the first missing segment. */
    fun at(root: Any?, vararg path: String): JSONObject? {
        var cur: Any? = root
        for (segment in path) {
            cur = (cur as? JSONObject)?.opt(segment) ?: return null
        }
        return cur as? JSONObject
    }

    fun stringAt(root: Any?, vararg path: String): String? = at(root, *path)?.let { runsText(it) }
}
