package com.itonkung.meowtoranger.modules.routine

import android.content.Context
import android.graphics.PixelFormat
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.hypot

/** What the Accessibility service provides to teach mode and replay. Input only reaches LINE Rangers. */
interface RoutineHost {
    fun later(ms: Long, block: () -> Unit)
    /** Downscaled frame (height [Looks.FRAME_H]); null on failure or when LINE Rangers is not in front. */
    fun grab(onFrame: (Img?) -> Unit)
    fun tap(nx: Float, ny: Float, onDone: (Boolean) -> Unit)
    fun swipe(nx: Float, ny: Float, nx2: Float, ny2: Float, ms: Long, onDone: (Boolean) -> Unit)
    fun back()
    fun status(message: String)
}

/**
 * Teach mode: a transparent full-screen layer catches each touch, remembers
 * how the screen looked under the finger, then passes the touch on to the
 * game. The floating control bar must be added after [start] so it stays on top.
 */
class RoutineRecorder(
    private val context: Context,
    private val wm: WindowManager,
    private val host: RoutineHost,
    val routineId: String,
) {
    private class Touch(val x: Float, val y: Float, val x2: Float, val y2: Float,
                        val moved: Boolean, val ms: Long, val downAt: Long, val upAt: Long)

    private val raw = mutableListOf<Raw>()
    private val queue = ArrayDeque<Touch>()
    private var busy = false
    private var closed = false
    private var frameW = 0
    private var frameH = 0
    private var lastUpAt = 0L
    private var catcher: View? = null
    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    }

    val count get() = raw.size

    fun start(): Boolean {
        val view = View(context)
        var downX = 0f; var downY = 0f; var downAt = 0L
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; downAt = SystemClock.uptimeMillis() }
                MotionEvent.ACTION_UP -> {
                    val b = wm.currentWindowMetrics.bounds
                    val w = b.width().toFloat(); val h = b.height().toFloat()
                    val moved = hypot(e.rawX - downX, e.rawY - downY) > h * 0.035f
                    val now = SystemClock.uptimeMillis()
                    queue.addLast(Touch(downX / w, downY / h, e.rawX / w, e.rawY / h, moved,
                        (now - downAt).coerceIn(150L, 1500L), downAt, now))
                    pump()
                }
            }
            true
        }
        return try { wm.addView(view, lp); catcher = view; true } catch (_: Exception) { false }
    }

    /** Groups what was taught into steps and saves it. Returns the number of steps saved. */
    fun finish(): Int {
        close()
        if (frameW == 0 || frameH == 0) return 0
        val steps = Grouping.toSteps(raw, frameW.toFloat() / frameH)
        if (steps.isEmpty()) return 0
        RoutineStore.save(context, Routine(routineId, RoutineStore.repeats(routineId), frameW, frameH, steps))
        return steps.size
    }

    fun close() {
        closed = true
        queue.clear()
        catcher?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        catcher = null
    }

    private fun pump() {
        if (busy || closed) return
        val t = queue.removeFirstOrNull() ?: return
        busy = true
        // Capture BEFORE passing the touch on, so the look is the button, not its result.
        host.grab { frame ->
            if (closed) return@grab
            if (frame != null) { frameW = frame.w; frameH = frame.h }
            val wait = if (lastUpAt == 0L) 0L else (t.downAt - lastUpAt).coerceAtLeast(0L)
            lastUpAt = t.upAt
            if (t.moved) raw += Raw.Swipe(Step.Swipe(t.x, t.y, t.x2, t.y2, t.ms))
            else raw += Raw.Tap(t.downAt, frame?.let { Looks.cut(it, t.x, t.y) }, wait)
            host.status(if (frame == null) "สอนแล้ว ${raw.size} · จับภาพไม่ทัน แตะช้าลงนิด"
                else "กำลังสอน · ${raw.size} ครั้ง")
            passThrough(t)
        }
    }

    private fun passThrough(t: Touch) {
        val view = catcher ?: return
        lp.flags = lp.flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        try { wm.updateViewLayout(view, lp) } catch (_: Exception) { }
        host.later(40) {
            val done: (Boolean) -> Unit = {
                host.later(60) {
                    lp.flags = lp.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE.inv()
                    catcher?.let { v -> try { wm.updateViewLayout(v, lp) } catch (_: Exception) { } }
                    busy = false
                    pump()
                }
            }
            if (t.moved) host.swipe(t.x, t.y, t.x2, t.y2, t.ms, done) else host.tap(t.x, t.y, done)
        }
    }
}
