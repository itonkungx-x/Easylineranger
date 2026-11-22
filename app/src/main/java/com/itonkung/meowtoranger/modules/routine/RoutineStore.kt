package com.itonkung.meowtoranger.modules.routine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64

/** One JSON file per taught routine in app-private storage. Button looks are stored as base64 RGB. */
object RoutineStore {
    const val DAILY = "daily"
    const val FARM = "farm"

    fun title(id: String) = if (id == DAILY) "ภารกิจประจำวัน" else "ฟาร์มด่าน"

    /** Game day: LINE Rangers resets at 00:00 GMT+9. */
    fun gameDay(now: Long = System.currentTimeMillis()) = (now + 9 * 3_600_000L) / 86_400_000L

    fun markDone(context: Context, id: String) = context.getSharedPreferences("auto", Context.MODE_PRIVATE)
        .edit().putLong("done_day_$id", gameDay()).apply()

    fun doneToday(context: Context, id: String) =
        context.getSharedPreferences("auto", Context.MODE_PRIVATE).getLong("done_day_$id", -1L) == gameDay()
    fun repeats(id: String) = id == FARM

    private fun file(context: Context, id: String) = File(context.filesDir, "routine_$id.json")

    fun delete(context: Context, id: String) { file(context, id).delete() }

    fun stepCount(context: Context, id: String): Int =
        try { JSONObject(file(context, id).readText()).getJSONArray("steps").length() } catch (_: Exception) { 0 }

    fun save(context: Context, r: Routine) {
        val steps = JSONArray()
        for (s in r.steps) steps.put(when (s) {
            is Step.Tap -> JSONObject().put("type", "tap").put("wait", s.waitMs).put("t", target(s.target))
            is Step.Spam -> JSONObject().put("type", "spam").put("wait", s.waitMs)
                .put("ts", JSONArray().apply { s.targets.forEach { put(target(it)) } })
            is Step.Swipe -> JSONObject().put("type", "swipe").put("x", s.x.toDouble()).put("y", s.y.toDouble())
                .put("x2", s.x2.toDouble()).put("y2", s.y2.toDouble()).put("ms", s.ms)
        })
        val root = JSONObject().put("schema", 1).put("id", r.id).put("repeat", r.repeat)
            .put("w", r.frameW).put("h", r.frameH).put("steps", steps)
        val f = file(context, r.id)
        val tmp = File(f.path + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    fun load(context: Context, id: String): Routine? = try {
        val root = JSONObject(file(context, id).readText())
        val arr = root.getJSONArray("steps")
        val steps = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            when (o.getString("type")) {
                "tap" -> Step.Tap(target(o.getJSONObject("t")), o.getLong("wait"))
                "spam" -> {
                    val ts = o.getJSONArray("ts")
                    Step.Spam((0 until ts.length()).map { target(ts.getJSONObject(it)) }, o.getLong("wait"))
                }
                else -> Step.Swipe(o.getDouble("x").toFloat(), o.getDouble("y").toFloat(),
                    o.getDouble("x2").toFloat(), o.getDouble("y2").toFloat(), o.getLong("ms"))
            }
        }
        if (root.optInt("schema") != 1 || steps.isEmpty()) null
        else Routine(id, root.getBoolean("repeat"), root.getInt("w"), root.getInt("h"), steps)
    } catch (_: Exception) { null }

    private fun target(t: Target): JSONObject {
        val bytes = ByteArray(t.look.px.size * 3)
        t.look.px.forEachIndexed { i, c ->
            bytes[i * 3] = (c shr 16).toByte(); bytes[i * 3 + 1] = (c shr 8).toByte(); bytes[i * 3 + 2] = c.toByte()
        }
        return JSONObject().put("x", t.x.toDouble()).put("y", t.y.toDouble()).put("ox", t.ox).put("oy", t.oy)
            .put("lw", t.look.w).put("lh", t.look.h).put("px", Base64.getEncoder().encodeToString(bytes))
    }

    private fun target(o: JSONObject): Target {
        val bytes = Base64.getDecoder().decode(o.getString("px"))
        val w = o.getInt("lw"); val h = o.getInt("lh")
        require(bytes.size == w * h * 3)
        val px = IntArray(w * h) { i ->
            (0xFF shl 24) or ((bytes[i * 3].toInt() and 255) shl 16) or
                ((bytes[i * 3 + 1].toInt() and 255) shl 8) or (bytes[i * 3 + 2].toInt() and 255)
        }
        return Target(o.getDouble("x").toFloat(), o.getDouble("y").toFloat(), Img(w, h, px), o.getInt("ox"), o.getInt("oy"))
    }
}
