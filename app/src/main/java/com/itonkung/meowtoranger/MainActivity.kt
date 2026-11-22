package com.itonkung.meowtoranger

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Toast
import com.itonkung.meowtoranger.core.config.RemoteConfig
import com.itonkung.meowtoranger.core.update.AppUpdater
import com.itonkung.meowtoranger.modules.routine.RoutineStore
import com.itonkung.meowtoranger.ui.Palette
import com.itonkung.meowtoranger.ui.Ui

/** Home screen: setup checklist, today's status per module, one big "open game" button. */
class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("auto", MODE_PRIVATE) }
    private val ui by lazy { Ui(this) }
    private val ticker = Handler(Looper.getMainLooper())
    private lateinit var content: LinearLayout
    private var shownState = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Palette.bg
        window.navigationBarColor = Palette.bg
        content = ui.column().apply { setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(28)) }
        setContentView(ScrollView(this).apply {
            setBackgroundColor(Palette.bg)
            isFillViewport = true
            addView(content)
        })
        RemoteConfig(this).refreshInBackground()
    }

    override fun onResume() { super.onResume(); shownState = ""; ticker.post(refresh) }
    override fun onPause() { ticker.removeCallbacks(refresh); super.onPause() }

    /** Rebuild only when something visible changed, so the screen never flickers. */
    private val refresh = object : Runnable {
        override fun run() {
            val state = listOf(serviceOn(), consent(), friendToday(), prefs.getString("last_status", ""),
                RoutineStore.stepCount(this@MainActivity, RoutineStore.DAILY), RoutineStore.stepCount(this@MainActivity, RoutineStore.FARM),
                RoutineStore.doneToday(this@MainActivity, RoutineStore.DAILY), RoutineStore.doneToday(this@MainActivity, RoutineStore.FARM),
                RoutineStore.doneToday(this@MainActivity, "friend")).joinToString("|")
            if (state != shownState) { shownState = state; build() }
            ticker.postDelayed(this, 1200)
        }
    }

    private fun serviceOn(): Boolean {
        val manager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
            it.resolveInfo?.serviceInfo?.packageName == packageName &&
                it.resolveInfo?.serviceInfo?.name?.endsWith("FriendAccessibilityService") == true
        }
    }
    private fun consent() = prefs.getBoolean("consent", false)
    private fun steps(id: String) = RoutineStore.stepCount(this, id)
    /** Friend points sent during the current game day, or -1 if none recorded today. */
    private fun friendToday(): Int {
        val updated = prefs.getLong("last_updated", 0L)
        return if (updated > 0 && RoutineStore.gameDay(updated) == RoutineStore.gameDay())
            prefs.getInt("current_count", -1) else -1
    }

    private fun build() {
        content.removeAllViews()
        val on = serviceOn()
        val friendMax = prefs.getInt("current_max", 50).coerceAtLeast(1)
        val friendDone = RoutineStore.doneToday(this, "friend")
        val dailyDone = RoutineStore.doneToday(this, RoutineStore.DAILY)
        val farmDone = RoutineStore.doneToday(this, RoutineStore.FARM)
        val doneCount = listOf(friendDone, dailyDone, farmDone).count { it }

        // Header
        val head = ui.row()
        head.addView(ImageView(this).apply {
            setImageResource(R.drawable.mascot)
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = ui.round(Palette.tile, 18)
            clipToOutline = true
            contentDescription = "มาสคอต Easylineranger"
        }, ui.lp(ui.dp(60), ui.dp(60)))
        val titles = ui.column().apply { setPadding(ui.dp(14), 0, 0, 0) }
        titles.addView(ui.text("Easylineranger", 22f, bold = true))
        titles.addView(ui.text(if (doneCount == 3) "วันนี้ทำครบแล้ว พักได้เลย" else "วันนี้ทำแล้ว $doneCount/3",
            13f, Palette.muted).apply { setPadding(0, ui.dp(5), 0, 0) })
        head.addView(titles)
        content.addView(head)

        // Setup checklist, hidden once everything is done
        val setup = listOf(
            Triple("เปิดสิทธิ์ช่วยเหลือ (Accessibility)", prefs.getBoolean("acc_seen", false), { openAccessibility() }),
            Triple("อนุญาตส่งคะแนนมิตรภาพ", consent(), { askConsent() }),
            Triple("สอนบอทอย่างน้อย 1 อย่าง", steps(RoutineStore.DAILY) + steps(RoutineStore.FARM) > 0, { teachGuide(RoutineStore.DAILY) }),
        )
        val setupDone = setup.count { it.second }
        if (setupDone < setup.size) {
            val card = card(Palette.accent)
            card.addView(ui.text("เริ่มต้นใช้งาน · $setupDone/${setup.size}", 15f, bold = true))
            setup.forEach { (label, done, action) ->
                val row = ui.row().apply {
                    setPadding(0, ui.dp(10), 0, 0)
                    if (!done) { isClickable = true; setOnClickListener { action() } }
                }
                row.addView(ui.icon(if (done) R.drawable.ic_check_circle else R.drawable.ic_circle,
                    if (done) Palette.good else Palette.accentSoft, 20))
                row.addView(ui.text(label, 14f, if (done) Palette.muted else Palette.text), ui.lp(0, -2, 1f, left = 10))
                if (!done) row.addView(ui.icon(R.drawable.ic_chevron, Palette.accentSoft, 20))
                card.addView(row)
            }
            content.addView(card, ui.lp(-1, -2, top = 18))
        }

        // Accessibility strip
        val acc = card(if (on) Palette.goodBg else Palette.line)
        val accRow = ui.row()
        accRow.addView(ui.icon(R.drawable.ic_accessibility, if (on) Palette.good else Palette.muted, 22))
        val accText = ui.column()
        accText.addView(ui.text(if (on) "สิทธิ์ช่วยเหลือเปิดอยู่" else "สิทธิ์ช่วยเหลือปิดอยู่", 14f, bold = true))
        accText.addView(ui.text(if (on) "ออกจากเกมแล้วจะปิดเอง" else "เปิดก่อนเข้าเกมทุกครั้ง", 12f, Palette.muted)
            .apply { setPadding(0, ui.dp(3), 0, 0) })
        accRow.addView(accText, ui.lp(0, -2, 1f, left = 12))
        accRow.addView(if (on) ui.button(R.drawable.ic_shield, "ปิดตอนนี้", Palette.tile, Palette.text, 12f) {
            val ok = FriendAccessibilityService.disableForBanking()
            Toast.makeText(this, if (ok) "ปิดสิทธิ์แล้ว เปิด K PLUS ได้" else "สิทธิ์ปิดอยู่แล้ว", Toast.LENGTH_SHORT).show()
        } else ui.button(R.drawable.ic_accessibility, "เปิดสิทธิ์", Palette.accent, Palette.onAccent, 12f) { openAccessibility() })
        acc.addView(accRow)
        content.addView(acc, ui.lp(-1, -2, top = 12))

        // Today
        content.addView(ui.text("วันนี้", 13f, Palette.muted, bold = true), ui.lp(-1, -2, top = 22))
        val sent = friendToday()
        content.addView(module(R.drawable.ic_heart, "คะแนนมิตรภาพ",
            when {
                !consent() -> "ต้องอนุญาตก่อน"
                sent >= 0 -> "ส่งแล้ว $sent/$friendMax"
                else -> "ยังไม่ได้ส่งวันนี้"
            },
            when {
                friendDone -> ui.pill("ครบ", Palette.goodBg, Palette.good)
                !consent() -> ui.pill("อนุญาต", Palette.tile, Palette.accentSoft)
                else -> ui.pill("พร้อม", Palette.tile, Palette.muted)
            },
            progress = if (sent >= 0) sent to friendMax else null,
        ) { if (consent()) friendOptions() else askConsent() }, ui.lp(-1, -2, top = 8))
        for ((id, iconRes) in listOf(RoutineStore.DAILY to R.drawable.ic_list, RoutineStore.FARM to R.drawable.ic_repeat)) {
            val n = steps(id)
            val done = RoutineStore.doneToday(this, id)
            content.addView(module(iconRes, RoutineStore.title(id),
                if (n == 0) "ยังไม่ได้สอน" else "สอนแล้ว $n ขั้น" + if (done) " · ทำแล้ววันนี้" else "",
                when {
                    done -> ui.pill("เสร็จ", Palette.goodBg, Palette.good)
                    n == 0 -> ui.pill("สอน", Palette.tile, Palette.accentSoft)
                    else -> ui.pill("พร้อม", Palette.tile, Palette.muted)
                },
            ) { if (n == 0) teachGuide(id) else routineOptions(id) }, ui.lp(-1, -2, top = 8))
        }

        // Main action
        content.addView(ui.button(R.drawable.ic_play, "เปิด LINE Rangers", Palette.accent, Palette.onAccent, 17f) {
            openGame()
        }.apply { setPadding(ui.dp(14), ui.dp(16), ui.dp(14), ui.dp(16)) }, ui.lp(-1, -2, top = 20))
        content.addView(ui.text("ในเกม แตะน้องแมวเพื่อเปิดเมนู · ลากไปวางตรงไหนก็ได้", 12f, Palette.muted).apply {
            gravity = Gravity.CENTER
        }, ui.lp(-1, -2, top = 10))
        prefs.getString("last_status", null)?.takeIf { it.isNotBlank() }?.let {
            content.addView(ui.text("ล่าสุด: $it", 12f, Palette.muted).apply { gravity = Gravity.CENTER }, ui.lp(-1, -2, top = 6))
        }

        // Footer
        val version = packageManager.getPackageInfo(packageName, 0).versionName ?: "?"
        val foot = ui.row(Gravity.CENTER)
        foot.addView(ui.button(R.drawable.ic_refresh, "ตรวจสอบอัปเดต", Palette.card, Palette.muted, 12f) {
            AppUpdater(this).checkForUpdates()
        })
        foot.addView(ui.text("v$version", 12f, Palette.muted), ui.lp(-2, -2, left = 12))
        content.addView(foot, ui.lp(-1, -2, top = 26))
    }

    private fun card(border: Int) = ui.column().apply {
        setPadding(ui.dp(16), ui.dp(14), ui.dp(16), ui.dp(14))
        background = ui.round(Palette.card, 16, border)
    }

    private fun module(iconRes: Int, title: String, subtitle: String, pill: View,
                       progress: Pair<Int, Int>? = null, click: () -> Unit): View {
        val c = card(Palette.card).apply { isClickable = true; setOnClickListener { click() } }
        val row = ui.row()
        val tile = ui.row(Gravity.CENTER).apply { background = ui.round(Palette.tile, 12) }
        tile.addView(ui.icon(iconRes, Palette.accentSoft, 22))
        row.addView(tile, ui.lp(ui.dp(44), ui.dp(44)))
        val texts = ui.column()
        texts.addView(ui.text(title, 15f, bold = true))
        texts.addView(ui.text(subtitle, 12f, Palette.muted).apply { setPadding(0, ui.dp(4), 0, 0) })
        row.addView(texts, ui.lp(0, -2, 1f, left = 12))
        row.addView(pill)
        c.addView(row)
        if (progress != null) {
            c.addView(ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = progress.second
                this.progress = progress.first.coerceAtLeast(0)
                progressTintList = ColorStateList.valueOf(if (progress.first >= progress.second) Palette.good else Palette.accent)
                progressBackgroundTintList = ColorStateList.valueOf(Palette.tile)
            }, ui.lp(-1, ui.dp(6), top = 10))
        }
        return c
    }

    private fun openAccessibility() = startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    private fun openGame() {
        if (!serviceOn()) {
            AlertDialog.Builder(this)
                .setTitle("ยังไม่ได้เปิดสิทธิ์ช่วยเหลือ")
                .setMessage("ต้องเปิดสิทธิ์ก่อน น้องแมวถึงจะขึ้นในเกม Android ไม่ให้แอปเปิดสิทธิ์เอง")
                .setPositiveButton("เปิดสิทธิ์") { _, _ -> openAccessibility() }
                .setNegativeButton("เข้าเกมเลย") { _, _ -> launchGame() }
                .show()
        } else launchGame()
    }

    private fun launchGame() {
        val intent = packageManager.getLaunchIntentForPackage(FriendAccessibilityService.GAME_PACKAGE)
        if (intent != null) startActivity(intent) else Toast.makeText(this, "ไม่พบ LINE Rangers ในเครื่อง", Toast.LENGTH_LONG).show()
    }

    private fun askConsent() {
        AlertDialog.Builder(this)
            .setTitle("ส่งคะแนนมิตรภาพอัตโนมัติ")
            .setMessage("เมื่อกด ส่งคะแนน หรือ ทำทั้งหมด ในเกม แอปจะกดช่วยเหลือเพื่อน ซึ่งส่งข้อความ LINE ถึงเพื่อน จนเต็มโควต้าประจำวัน กดหยุดได้ตลอด")
            .setPositiveButton("อนุญาต") { _, _ -> prefs.edit().putBoolean("consent", true).apply(); shownState = "" }
            .setNegativeButton("ยกเลิก", null)
            .show()
    }

    private fun friendOptions() {
        AlertDialog.Builder(this)
            .setTitle("คะแนนมิตรภาพ")
            .setMessage("ในเกม แตะน้องแมว แล้วกด ส่งคะแนน หรือ ทำทั้งหมด")
            .setPositiveButton("ปิด", null)
            .setNegativeButton("ยกเลิกการอนุญาต") { _, _ -> prefs.edit().putBoolean("consent", false).apply(); shownState = "" }
            .show()
    }

    private fun teachGuide(id: String) {
        AlertDialog.Builder(this)
            .setTitle("สอนบอท: ${RoutineStore.title(id)}")
            .setMessage("1. เข้าเกม แล้วแตะน้องแมว > สอนบอท > ${RoutineStore.title(id)}\n" +
                "2. เริ่มจากหน้าหลัก เล่นตามปกติหนึ่งรอบ แล้วกลับมาหน้าหลัก\n" +
                "3. แตะช้าๆ ทีละปุ่ม ตอนสู้แตะรัวๆ ที่ช่องเรนเจอร์ได้\n" +
                "4. กด บันทึก\n\nห้ามแตะปุ่มที่ใช้ Ruby ตอนสอน บอทจะแตะเฉพาะปุ่มที่หน้าตาเหมือนตอนสอน")
            .setPositiveButton("เปิดเกม") { _, _ -> openGame() }
            .setNegativeButton("ปิด", null)
            .show()
    }

    private fun routineOptions(id: String) {
        AlertDialog.Builder(this)
            .setTitle(RoutineStore.title(id))
            .setMessage("สอนแล้ว ${steps(id)} ขั้น · ในเกม แตะน้องแมว แล้วกด ${if (id == RoutineStore.DAILY) "ภารกิจ" else "ฟาร์มด่าน"}")
            .setPositiveButton("สอนใหม่") { _, _ -> teachGuide(id) }
            .setNeutralButton("ลบที่สอนไว้") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage("ลบสิ่งที่สอนไว้สำหรับ ${RoutineStore.title(id)}? ต้องสอนใหม่ถึงจะใช้ได้")
                    .setPositiveButton("ลบ") { _, _ -> RoutineStore.delete(this, id); shownState = "" }
                    .setNegativeButton("ยกเลิก", null)
                    .show()
            }
            .setNegativeButton("ปิด", null)
            .show()
    }
}
