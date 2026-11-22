package com.itonkung.meowtoranger.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.itonkung.meowtoranger.R
import kotlin.math.abs

/**
 * In-game menu: a draggable mascot bubble. Tap it to open the menu; it closes
 * by itself when a task starts so it never covers the game.
 */
class FloatingMenu(
    private val context: Context,
    private val wm: WindowManager,
    private val prefs: SharedPreferences,
    private val actions: Actions,
) {
    interface Actions {
        fun friend()
        fun daily()
        fun farm()
        fun all()
        fun teach(routineId: String)
        fun saveTeach()
        fun cancelTeach()
        fun stop()
    }

    enum class Mode { IDLE, BUSY, TEACHING }

    private val ui = Ui(context)
    private var root: LinearLayout? = null
    private lateinit var bubble: ImageView
    private lateinit var chip: TextView
    private lateinit var stopButton: View
    private lateinit var menu: View
    private lateinit var teachChoice: View
    private lateinit var teachBar: View
    private lateinit var teachCount: TextView
    private var expanded = false
    private var choosingTeach = false
    private var mode = Mode.IDLE
    private val lp = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = prefs.getInt("bubble_x", ui.dp(16))
        y = prefs.getInt("bubble_y", ui.dp(72))
    }

    val isShown get() = root != null

    fun show(): Boolean {
        if (root != null) return true
        val view = build()
        return try { wm.addView(view, lp); root = view; refresh(); true } catch (_: Exception) { false }
    }

    fun hide() {
        root?.let { try { wm.removeView(it) } catch (_: Exception) { } }
        root = null
    }

    /** Hidden for a moment while teach mode remembers how a button looks. */
    fun setCaptureHidden(hidden: Boolean) {
        root?.visibility = if (hidden) View.INVISIBLE else View.VISIBLE
    }

    fun render(mode: Mode, status: String, count: Int) {
        if (mode != this.mode && mode != Mode.IDLE) { expanded = false; choosingTeach = false }
        this.mode = mode
        if (root == null) return
        chip.text = status
        teachCount.text = "REC $count"
        refresh()
    }

    private fun refresh() {
        if (root == null) return
        fun View.on(show: Boolean) { visibility = if (show) View.VISIBLE else View.GONE }
        chip.on(mode == Mode.BUSY || (mode == Mode.IDLE && expanded))
        stopButton.on(mode == Mode.BUSY)
        menu.on(mode == Mode.IDLE && expanded && !choosingTeach)
        teachChoice.on(mode == Mode.IDLE && expanded && choosingTeach)
        teachBar.on(mode == Mode.TEACHING)
        bubble.background = ui.oval(Palette.tile, if (mode == Mode.BUSY) Palette.good else Palette.accent, 2)
    }

    private fun build(): LinearLayout {
        val col = ui.column()

        val head = ui.row()
        bubble = ImageView(context).apply {
            setImageResource(R.drawable.mascot)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setPadding(ui.dp(3), ui.dp(3), ui.dp(3), ui.dp(3))
            clipToOutline = true
            contentDescription = "เมนู Easylineranger"
            elevation = ui.dp(4).toFloat()
        }
        attachDrag(bubble)
        head.addView(bubble, ui.lp(ui.dp(50), ui.dp(50)))
        chip = ui.text("พร้อม", 12f, Palette.text, bold = true).apply {
            setPadding(ui.dp(10), ui.dp(7), ui.dp(10), ui.dp(7))
            background = ui.round(Palette.bg, 99, Palette.line)
            maxWidth = ui.dp(230)
            maxLines = 2
        }
        head.addView(chip, ui.lp(-2, -2, left = 8))
        stopButton = ui.button(R.drawable.ic_stop, "หยุด", Palette.badBg, Palette.bad, 12f) { actions.stop() }
        head.addView(stopButton, ui.lp(-2, -2, left = 6))
        col.addView(head)

        menu = card().apply {
            addView(ui.button(R.drawable.ic_play_all, "ทำทั้งหมด", Palette.accent, Palette.onAccent, 15f) {
                collapse(); actions.all()
            })
            val tiles = ui.row()
            tiles.addView(ui.tile(R.drawable.ic_heart, "ส่งคะแนน") { collapse(); actions.friend() }, ui.lp(0, -2, 1f))
            tiles.addView(ui.tile(R.drawable.ic_list, "ภารกิจ") { collapse(); actions.daily() }, ui.lp(0, -2, 1f, left = 6))
            tiles.addView(ui.tile(R.drawable.ic_repeat, "ฟาร์มด่าน") { collapse(); actions.farm() }, ui.lp(0, -2, 1f, left = 6))
            addView(tiles, ui.lp(-1, -2, top = 8))
            addView(ui.button(R.drawable.ic_school, "สอนบอท", Palette.tile, Palette.text, 13f) {
                choosingTeach = true; refresh()
            }, ui.lp(-1, -2, top = 8))
        }
        col.addView(menu, ui.lp(ui.dp(236), -2, top = 8))

        teachChoice = card().apply {
            addView(ui.text("จะสอนอะไร", 13f, Palette.text, bold = true))
            addView(ui.text("เริ่มจากหน้าหลัก เล่นตามปกติ แล้วกลับมาหน้าหลัก", 11f, Palette.muted).apply {
                setPadding(0, ui.dp(4), 0, 0)
            })
            val tiles = ui.row()
            tiles.addView(ui.tile(R.drawable.ic_list, "ภารกิจ") { collapse(); actions.teach("daily") }, ui.lp(0, -2, 1f))
            tiles.addView(ui.tile(R.drawable.ic_repeat, "ฟาร์มด่าน") { collapse(); actions.teach("farm") }, ui.lp(0, -2, 1f, left = 6))
            tiles.addView(ui.tile(R.drawable.ic_close, "ยกเลิก") { choosingTeach = false; refresh() }, ui.lp(0, -2, 1f, left = 6))
            addView(tiles, ui.lp(-1, -2, top = 8))
        }
        col.addView(teachChoice, ui.lp(ui.dp(236), -2, top = 8))

        teachBar = card().apply {
            val bar = ui.row()
            teachCount = ui.text("REC 0", 12f, Palette.bad, bold = true).apply {
                setCompoundDrawablesRelativeWithIntrinsicBounds(R.drawable.ic_rec, 0, 0, 0)
                compoundDrawableTintList = android.content.res.ColorStateList.valueOf(Palette.bad)
                compoundDrawablePadding = ui.dp(4)
            }
            bar.addView(teachCount, ui.lp(0, -2, 1f))
            bar.addView(ui.button(R.drawable.ic_check, "บันทึก", Palette.goodBg, Palette.good, 12f) { actions.saveTeach() })
            bar.addView(ui.button(R.drawable.ic_close, "ยกเลิก", Palette.tile, Palette.text, 12f) { actions.cancelTeach() },
                ui.lp(-2, -2, left = 6))
            addView(bar)
            addView(ui.text("แตะช้าๆ ทีละปุ่ม · ห้ามแตะปุ่มที่ใช้ Ruby", 11f, Palette.muted).apply {
                setPadding(0, ui.dp(6), 0, 0)
            })
        }
        col.addView(teachBar, ui.lp(ui.dp(236), -2, top = 8))
        return col
    }

    private fun card() = ui.column().apply {
        setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
        background = ui.round(Palette.bg, 16, Palette.line)
        elevation = ui.dp(4).toFloat()
    }

    private fun collapse() { expanded = false; choosingTeach = false; refresh() }

    /** Drag anywhere; a short touch counts as a tap that opens or closes the menu. */
    private fun attachDrag(view: View) {
        var downX = 0f; var downY = 0f; var startX = 0; var startY = 0; var dragging = false
        view.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { downX = e.rawX; downY = e.rawY; startX = lp.x; startY = lp.y; dragging = false }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX; val dy = e.rawY - downY
                    if (!dragging && (abs(dx) > ui.dp(8) || abs(dy) > ui.dp(8))) dragging = true
                    if (dragging) {
                        lp.x = (startX + dx).toInt().coerceAtLeast(0)
                        lp.y = (startY + dy).toInt().coerceAtLeast(0)
                        root?.let { try { wm.updateViewLayout(it, lp) } catch (_: Exception) { } }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) prefs.edit().putInt("bubble_x", lp.x).putInt("bubble_y", lp.y).apply()
                    else if (mode == Mode.IDLE) { expanded = !expanded; choosingTeach = false; refresh() }
                    else if (mode == Mode.BUSY) { expanded = false; refresh() }
                }
            }
            true
        }
    }
}
