package com.itonkung.meowtoranger.ui

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Shared look for the app screen and the in-game menu: warm dark theme matching the mascot's orange hair. */
object Palette {
    val bg = Color.rgb(23, 18, 15)
    val card = Color.rgb(36, 28, 23)
    val tile = Color.rgb(58, 44, 34)
    val line = Color.rgb(74, 58, 46)
    val accent = Color.rgb(255, 157, 60)
    val onAccent = Color.rgb(42, 22, 6)
    val accentSoft = Color.rgb(255, 180, 106)
    val text = Color.rgb(246, 239, 232)
    val muted = Color.rgb(185, 168, 154)
    val good = Color.rgb(139, 224, 166)
    val goodBg = Color.rgb(31, 58, 42)
    val bad = Color.rgb(255, 120, 112)
    val badBg = Color.rgb(90, 31, 31)
}

class Ui(private val context: Context) {
    fun dp(n: Int): Int = (n * context.resources.displayMetrics.density + .5f).toInt()

    fun round(color: Int, radiusDp: Int, stroke: Int? = null, strokeDp: Int = 1) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (stroke != null) setStroke(dp(strokeDp), stroke)
    }

    fun oval(color: Int, stroke: Int, strokeDp: Int) = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(strokeDp), stroke)
    }

    fun text(value: String, sizeSp: Float, color: Int = Palette.text, bold: Boolean = false) = TextView(context).apply {
        text = value
        textSize = sizeSp
        setTextColor(color)
        includeFontPadding = false
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun icon(res: Int, color: Int, sizeDp: Int) = ImageView(context).apply {
        setImageResource(res)
        imageTintList = ColorStateList.valueOf(color)
        layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
    }

    fun row(gravity: Int = Gravity.CENTER_VERTICAL) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        this.gravity = gravity
    }

    fun column() = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

    /** Icon + label button. */
    fun button(iconRes: Int, label: String, bg: Int, fg: Int, sizeSp: Float = 14f, click: () -> Unit) = row(Gravity.CENTER).apply {
        setPadding(dp(12), dp(10), dp(12), dp(10))
        background = round(bg, 12)
        isClickable = true
        contentDescription = label
        setOnClickListener { click() }
        addView(icon(iconRes, fg, 18))
        addView(text(label, sizeSp, fg, bold = true).apply { setPadding(dp(8), 0, 0, 0) })
    }

    /** Square-ish tile: icon on top, small label under it. */
    fun tile(iconRes: Int, label: String, click: () -> Unit) = column().apply {
        gravity = Gravity.CENTER
        setPadding(dp(6), dp(9), dp(6), dp(8))
        background = round(Palette.tile, 12)
        isClickable = true
        contentDescription = label
        setOnClickListener { click() }
        addView(icon(iconRes, Palette.accentSoft, 20))
        addView(text(label, 11f, Palette.text).apply { setPadding(0, dp(5), 0, 0); gravity = Gravity.CENTER })
    }

    fun pill(value: String, bg: Int, fg: Int) = text(value, 11f, fg, bold = true).apply {
        setPadding(dp(8), dp(3), dp(8), dp(3))
        background = round(bg, 99)
    }

    fun lp(w: Int, h: Int, weight: Float = 0f, left: Int = 0, top: Int = 0, right: Int = 0, bottom: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply {
            leftMargin = dp(left); topMargin = dp(top); rightMargin = dp(right); bottomMargin = dp(bottom)
        }
}
