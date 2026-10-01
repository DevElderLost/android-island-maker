package com.megernolep.islandeditor.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Serializer JSON yang meniru Python `json.dumps` (separator ", " dan ": ", non-ASCII di-escape).
 * Dipakai untuk info.yml / config.yml supaya byte-nya sama dengan keluaran build_from_spec.py
 * dan aman dibaca parser YAML 1.1 (ada spasi setelah ':' dan ',').
 */
object PyJson {
    fun dumps(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when {
            v == null || v === JSONObject.NULL -> sb.append("null")
            v is Boolean -> sb.append(if (v) "true" else "false")
            v is Int || v is Long || v is Short || v is Byte -> sb.append(v.toString())
            v is Number -> sb.append(v.toString())
            v is String -> str(sb, v)
            v is JSONObject -> {
                sb.append('{')
                var first = true
                val it = v.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    if (!first) sb.append(", ")
                    first = false
                    str(sb, k); sb.append(": "); write(sb, v.opt(k))
                }
                sb.append('}')
            }
            v is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(", ")
                    first = false
                    str(sb, k.toString()); sb.append(": "); write(sb, value)
                }
                sb.append('}')
            }
            v is JSONArray -> {
                sb.append('[')
                for (i in 0 until v.length()) { if (i > 0) sb.append(", "); write(sb, v.opt(i)) }
                sb.append(']')
            }
            v is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (e in v) { if (!first) sb.append(", "); first = false; write(sb, e) }
                sb.append(']')
            }
            v is IntArray -> write(sb, v.toList())
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) {
            when {
                c == '"' -> sb.append("\\\"")
                c == '\\' -> sb.append("\\\\")
                c == '\n' -> sb.append("\\n")
                c == '\r' -> sb.append("\\r")
                c == '\t' -> sb.append("\\t")
                c == '\b' -> sb.append("\\b")
                c == '\u000c' -> sb.append("\\f")
                c.code < 0x20 || c.code > 0x7e -> sb.append(String.format("\\u%04x", c.code))
                else -> sb.append(c)
            }
        }
        sb.append('"')
    }
}
