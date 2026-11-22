package com.itonkung.meowtoranger.modules.routine

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Teach mode core. Pure Kotlin (no Android types) so it can be unit tested.
 *
 * Every device teaches its own routine, so button looks and positions are
 * always from that exact screen. Replay only taps where the taught look is
 * found again; it never taps a blind coordinate.
 */

/** ARGB pixels, row-major. */
class Img(val w: Int, val h: Int, val px: IntArray) {
    fun crop(x0: Int, y0: Int, cw: Int, ch: Int): Img {
        val out = IntArray(cw * ch)
        for (y in 0 until ch) System.arraycopy(px, (y0 + y) * w + x0, out, y * cw, cw)
        return Img(cw, ch, out)
    }
}

/** A taught tap: normalized screen position + how the button looked there. (ox, oy) = tap point inside [look]. */
class Target(val x: Float, val y: Float, val look: Img, val ox: Int, val oy: Int)

sealed class Step {
    /** [waitMs]: how long the user waited before this tap while teaching (loading screens). */
    class Tap(val target: Target, val waitMs: Long) : Step()
    /** Rapid repeated taps (e.g. deploying rangers in battle): keep tapping until the next step shows. */
    class Spam(val targets: List<Target>, val waitMs: Long) : Step()
    class Swipe(val x: Float, val y: Float, val x2: Float, val y2: Float, val ms: Long) : Step()
}

class Routine(val id: String, val repeat: Boolean, val frameW: Int, val frameH: Int, val steps: List<Step>)

object Looks {
    /** Frames are downscaled to this height before matching. */
    const val FRAME_H = 360
    /** Template edge in downscaled pixels (~9% of screen height). */
    const val SIZE = 32

    fun cut(frame: Img, nx: Float, ny: Float): Target {
        val cx = (nx * frame.w).toInt().coerceIn(0, frame.w - 1)
        val cy = (ny * frame.h).toInt().coerceIn(0, frame.h - 1)
        val x0 = (cx - SIZE / 2).coerceIn(0, frame.w - SIZE)
        val y0 = (cy - SIZE / 2).coerceIn(0, frame.h - SIZE)
        return Target(nx, ny, frame.crop(x0, y0, SIZE, SIZE), cx - x0, cy - y0)
    }
}

/** Tap point in frame pixels; [score] = mean absolute RGB difference (0 = identical). */
class Match(val x: Int, val y: Int, val score: Int)

object Matcher {
    /** Normal steps: the button must look almost the same as when taught. */
    const val STRICT = 22
    /** Spam steps: tolerate dimmed cooldown icons, still reject covered/other buttons. */
    const val LOOSE = 40

    fun diff(f: Img, t: Img, x0: Int, y0: Int, limit: Int): Int {
        val n = t.w * t.h * 3
        val cap = limit.toLong() * n
        var sum = 0L
        for (y in 0 until t.h) {
            var fi = (y0 + y) * f.w + x0
            var ti = y * t.w
            repeat(t.w) {
                val a = f.px[fi++]
                val b = t.px[ti++]
                sum += abs((a shr 16 and 255) - (b shr 16 and 255)) +
                    abs((a shr 8 and 255) - (b shr 8 and 255)) +
                    abs((a and 255) - (b and 255))
            }
            if (sum > cap) return Int.MAX_VALUE
        }
        return (sum / n).toInt()
    }

    /** Best match of [target] within [radius] (fraction of frame height) of where it was taught. */
    fun find(f: Img, target: Target, radius: Float, limit: Int): Match? {
        val t = target.look
        if (f.w < t.w || f.h < t.h) return null
        val r = (radius * f.h).toInt()
        val bx = (target.x * f.w).toInt() - target.ox
        val by = (target.y * f.h).toInt() - target.oy
        val x0 = max(0, bx - r); val x1 = min(f.w - t.w, bx + r)
        val y0 = max(0, by - r); val y1 = min(f.h - t.h, by + r)
        if (x0 > x1 || y0 > y1) return null
        // Coarse pass every 2 px with a relaxed cap, then refine around the best spot.
        // Equal scores (identical buttons in a list) go to the one nearest the taught position.
        var best = limit * 2 + 10
        var bestX = -1; var bestY = -1
        var bestDist = Int.MAX_VALUE
        fun consider(x: Int, y: Int) {
            val d = diff(f, t, x, y, best)
            if (d > best) return
            val dist = abs(x - bx) + abs(y - by)
            if (d < best || dist < bestDist) { best = d; bestX = x; bestY = y; bestDist = dist }
        }
        for (y in y0..y1 step 2) for (x in x0..x1 step 2) consider(x, y)
        if (bestX < 0) return null
        val cx = bestX; val cy = bestY
        for (y in max(y0, cy - 1)..min(y1, cy + 1)) for (x in max(x0, cx - 1)..min(x1, cx + 1)) consider(x, y)
        return if (best <= limit) Match(bestX + target.ox, bestY + target.oy, best) else null
    }
}

/** What the recorder saw, before grouping into steps. */
sealed class Raw {
    class Tap(val atMs: Long, val target: Target?, val waitMs: Long) : Raw()
    class Swipe(val step: Step.Swipe) : Raw()
}

object Grouping {
    const val BURST_GAP_MS = 1000L
    const val BURST_MIN = 3
    /** Two taps closer than this (in screen heights) are the same spot. */
    const val SAME_SPOT = 0.05f

    /** [aspect] = frame width / height, so distances are measured in screen heights on both axes. */
    fun toSteps(raw: List<Raw>, aspect: Float): List<Step> {
        val steps = mutableListOf<Step>()
        val burst = mutableListOf<Raw.Tap>()
        fun flush() {
            if (burst.size >= BURST_MIN) {
                val spots = mutableListOf<Target>()
                for (tap in burst) {
                    val t = tap.target ?: continue
                    if (spots.none { hypot((it.x - t.x) * aspect, it.y - t.y) < SAME_SPOT }) spots += t
                }
                if (spots.isNotEmpty()) steps += Step.Spam(spots, burst.first().waitMs)
            } else {
                // A tap whose look could not be captured can never be verified: drop it.
                for (tap in burst) tap.target?.let { steps += Step.Tap(it, tap.waitMs) }
            }
            burst.clear()
        }
        for (r in raw) when (r) {
            is Raw.Swipe -> { flush(); steps += r.step }
            is Raw.Tap -> {
                val last = burst.lastOrNull()
                if (last != null && r.atMs - last.atMs >= BURST_GAP_MS) flush()
                burst += r
            }
        }
        flush()
        return steps
    }
}
