package com.itonkung.meowtoranger.modules.routine

import android.os.SystemClock
import kotlin.math.abs

/**
 * Replays a taught routine. Safety rules:
 * - A tap happens only where the taught button look is found, in two frames in a row.
 * - A step that does not show up in time is skipped; 3 skips in a row stops the run
 *   (e.g. an unexpected popup such as "buy energy with Ruby" is never tapped).
 * - Repeating routines end when their first step no longer shows up.
 */
class RoutinePlayer(
    private val host: RoutineHost,
    private val routine: Routine,
    /** reason, finished = the routine actually got done (not stopped/failed). */
    private val onEnd: (String, Boolean) -> Unit,
) {
    companion object {
        private const val SEARCH = 0.12f        // how far a button may have moved, in screen heights
        private const val SPAM_SEARCH = 0.03f
        private const val SPAM_IDLE_MS = 12_000L
        private const val SPAM_MAX_MS = 10 * 60_000L
        private const val MAX_SKIPS = 3
        private const val MAX_ROUNDS = 100
        private const val MAX_BACK = 4
        fun timeoutFor(waitMs: Long) = (waitMs * 2 + 8_000L).coerceIn(10_000L, 90_000L)
    }

    private var active = false
    private var index = 0
    private var round = 0
    private var stepStarted = 0L
    private var lastHit = 0L
    private var hits = 0
    private var spamCursor = 0
    private var skipsInRow = 0
    private var backPresses = 0
    private var pending: Match? = null

    val label get() = "${RoutineStore.title(routine.id)} ${index + 1}/${routine.steps.size}" +
        if (routine.repeat) " · รอบ ${round + 1}" else ""

    fun start() {
        active = true
        beginStep()
        tick(300)
    }

    fun stop(reason: String, finished: Boolean = false) {
        if (!active) return
        active = false
        onEnd(reason, finished)
    }

    private fun tick(ms: Long) = host.later(ms) {
        if (active) host.grab { f -> if (active) onFrame(f) }
    }

    private fun beginStep() {
        stepStarted = SystemClock.elapsedRealtime()
        hits = 0
        spamCursor = 0
        pending = null
        host.status(label)
    }

    private fun onFrame(f: Img?) {
        if (f == null) { tick(800); return }
        if (f.w != routine.frameW || f.h != routine.frameH) {
            stop("จอไม่ตรงกับตอนสอน · ถือแนวนอนแบบเดิมหรือสอนใหม่"); return
        }
        val now = SystemClock.elapsedRealtime()
        when (val step = routine.steps[index]) {
            is Step.Tap -> onTap(f, step, now)
            is Step.Spam -> onSpam(f, step, now)
            is Step.Swipe -> host.swipe(step.x, step.y, step.x2, step.y2, step.ms) { ok ->
                if (ok) advance(skipped = false, delay = 900) else tick(700)
            }
        }
    }

    private fun onTap(f: Img, step: Step.Tap, now: Long) {
        val m = Matcher.find(f, step.target, SEARCH, Matcher.STRICT)
        if (m != null) {
            val p = pending
            if (p != null && abs(p.x - m.x) <= 2 && abs(p.y - m.y) <= 2) {
                pending = null
                host.tap(m.x / f.w.toFloat(), m.y / f.h.toFloat()) { ok ->
                    if (ok) advance(skipped = false, delay = 700) else tick(600)
                }
            } else { pending = m; tick(250) } // wait for the screen to stop moving
            return
        }
        pending = null
        // Run start only: Back closes popups / returns toward the screen the routine was taught on.
        if (index == 0 && round == 0 && backPresses < MAX_BACK && now - stepStarted > 3_000L * (backPresses + 1)) {
            backPresses++
            host.status("หาจุดเริ่ม · กด Back ($backPresses/$MAX_BACK)")
            host.back()
            tick(1_500)
            return
        }
        if (now - stepStarted > timeoutFor(step.waitMs)) advance(skipped = true, delay = 300) else tick(400)
    }

    private fun onSpam(f: Img, step: Step.Spam, now: Long) {
        if (now - stepStarted > SPAM_MAX_MS) { stop("ขั้นแตะรัวนานเกิน 10 นาที · หยุด"); return }
        val next = routine.steps.getOrNull(index + 1) as? Step.Tap
        if (next != null && Matcher.find(f, next.target, SEARCH, Matcher.STRICT) != null) {
            advance(skipped = false, delay = 0); return
        }
        val n = step.targets.size
        for (k in 0 until n) {
            val i = (spamCursor + k) % n
            val m = Matcher.find(f, step.targets[i], SPAM_SEARCH, Matcher.LOOSE) ?: continue
            spamCursor = (i + 1) % n
            hits++
            lastHit = now
            host.tap(m.x / f.w.toFloat(), m.y / f.h.toFloat()) { tick(200) }
            return
        }
        val idleFor = if (hits == 0) now - stepStarted else now - lastHit
        val limit = if (hits == 0) timeoutFor(step.waitMs) else SPAM_IDLE_MS
        when {
            idleFor <= limit -> tick(300)
            hits == 0 -> advance(skipped = true, delay = 300)
            else -> advance(skipped = false, delay = 300)
        }
    }

    private fun advance(skipped: Boolean, delay: Long) {
        if (skipped) {
            skipsInRow++
            if (round > 0 && index == 0) { stop("ฟาร์มเสร็จ $round รอบ", finished = true); return }
            if (skipsInRow >= MAX_SKIPS) {
                if (round > 0) stop("ฟาร์มเสร็จ $round รอบ", finished = true)
                else stop("ไม่พบปุ่มขั้นที่ ${index + 1} · หยุดเพื่อความปลอดภัย")
                return
            }
        } else skipsInRow = 0
        index++
        if (index >= routine.steps.size) {
            round++
            if (!routine.repeat) { stop("${RoutineStore.title(routine.id)} เสร็จแล้ว", finished = true); return }
            if (round >= MAX_ROUNDS) { stop("ฟาร์มครบ $MAX_ROUNDS รอบ", finished = true); return }
            index = 0
        }
        beginStep()
        tick(delay)
    }
}
