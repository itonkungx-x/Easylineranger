package com.itonkung.meowtoranger.modules.routine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RoutineModelTest {
    /** Random noisy "game screen" with a distinct button drawn at (bx, by). */
    private fun screen(w: Int, h: Int, bx: Int, by: Int, buttonColor: Int, seed: Int = 1): Img {
        val rnd = Random(seed)
        val px = IntArray(w * h) { (0xFF shl 24) or rnd.nextInt(0x1000000) }
        for (y in by - 20..by + 20) for (x in bx - 24..bx + 24)
            if (x in 0 until w && y in 0 until h)
                px[y * w + x] = if ((x - bx + y - by + 70) % 7 == 0) 0xFFFFFFFF.toInt() else buttonColor
        return Img(w, h, px)
    }

    @Test fun findsButtonAfterItMoved() {
        val green = 0xFF2EBF4A.toInt()
        val taught = screen(640, 360, 300, 200, green)
        val target = Looks.cut(taught, 300f / 640, 200f / 360)
        // Same button, list scrolled 17 px left and 9 px down, new background.
        val later = screen(640, 360, 283, 209, green, seed = 2)
        val m = Matcher.find(later, target, 0.12f, Matcher.STRICT)
        assertNotNull(m)
        assertEquals(283, m!!.x)
        assertEquals(209, m.y)
    }

    @Test fun rejectsDifferentButton() {
        val taught = screen(640, 360, 300, 200, 0xFF2EBF4A.toInt())
        val target = Looks.cut(taught, 300f / 640, 200f / 360)
        // A red (e.g. Ruby) button in the same place must never match.
        val other = screen(640, 360, 300, 200, 0xFFD03030.toInt(), seed = 3)
        assertNull(Matcher.find(other, target, 0.12f, Matcher.LOOSE))
    }

    @Test fun rejectsButtonOutsideSearchRadius() {
        val green = 0xFF2EBF4A.toInt()
        val target = Looks.cut(screen(640, 360, 300, 200, green), 300f / 640, 200f / 360)
        assertNull(Matcher.find(screen(640, 360, 560, 60, green, seed = 4), target, 0.12f, Matcher.STRICT))
    }

    @Test fun edgeTapKeepsOffset() {
        val green = 0xFF2EBF4A.toInt()
        val taught = screen(640, 360, 5, 5, green)
        val target = Looks.cut(taught, 5f / 640, 5f / 360)
        assertEquals(5, target.ox)
        val m = Matcher.find(taught, target, 0.05f, Matcher.STRICT)!!
        assertEquals(5, m.x); assertEquals(5, m.y)
    }

    @Test fun groupsRapidTapsIntoSpamAndKeepsSlowTaps() {
        val img = screen(640, 360, 100, 100, 0xFF2EBF4A.toInt())
        fun tap(at: Long, x: Float, y: Float) = Raw.Tap(at, Looks.cut(img, x, y), 0)
        val raw = listOf(
            tap(0, .5f, .5f),                       // slow tap
            tap(3000, .2f, .9f), tap(3300, .3f, .9f), tap(3600, .2f, .9f), tap(3900, .3f, .9f), // battle spam, 2 spots
            tap(8000, .5f, .7f),                    // victory OK
            Raw.Swipe(Step.Swipe(.8f, .5f, .2f, .5f, 400)),
            Raw.Tap(9000, null, 0),                 // look not captured -> dropped
        )
        val steps = Grouping.toSteps(raw, 640f / 360)
        assertEquals(4, steps.size)
        assertTrue(steps[0] is Step.Tap)
        assertEquals(2, (steps[1] as Step.Spam).targets.size)
        assertTrue(steps[2] is Step.Tap)
        assertTrue(steps[3] is Step.Swipe)
    }

    @Test fun twoQuickTapsStayTaps() {
        val img = screen(640, 360, 100, 100, 0xFF2EBF4A.toInt())
        val raw = listOf(Raw.Tap(0, Looks.cut(img, .1f, .1f), 0), Raw.Tap(400, Looks.cut(img, .9f, .9f), 0))
        assertTrue(Grouping.toSteps(raw, 16f / 9).all { it is Step.Tap })
    }
}
