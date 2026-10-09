package com.ferma.game

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.pow

class Item(val emoji: String, val name: String, val baseCost: Double, val cps: Double)

val ITEMS = listOf(
    Item("🌱", "Грядка", 15.0, 0.2),
    Item("💧", "Колодец", 100.0, 1.0),
    Item("🏡", "Теплица", 500.0, 5.0),
    Item("🥕", "Огород", 3000.0, 25.0),
    Item("🚜", "Трактор", 15000.0, 120.0),
    Item("🌽", "Кукурузное поле", 100000.0, 700.0),
    Item("🏠", "Амбар", 600000.0, 4000.0),
    Item("🏭", "Фабрика", 5000000.0, 25000.0),
    Item("🚚", "Грузовик", 40000000.0, 160000.0)
)

private val THRESHOLDS = listOf(10, 25, 50, 100, 200)

fun fmt(v: Double): String {
    if (v < 1000.0) {
        return if (v < 10.0 && v != Math.floor(v)) String.format("%.1f", v) else String.format("%.0f", v)
    }
    val units = listOf("K", "M", "B", "T", "Qa", "Qi")
    var x = v
    var i = -1
    while (x >= 1000.0 && i < units.size - 1) {
        x /= 1000.0
        i++
    }
    return String.format("%.2f", x) + units[i]
}

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var coins = 0.0
    private var tapLevel = 0
    private val owned = IntArray(ITEMS.size)
    private var lastTick = 0L
    private var lastSave = 0L
    private var lastSeen = 0L

    private lateinit var coinsView: TextView
    private lateinit var cpsView: TextView
    private lateinit var feedback: TextView
    private lateinit var list: LinearLayout
    private val rows = ArrayList<TextView>()

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun mult(i: Int): Double = 2.0.pow(THRESHOLDS.count { owned[i] >= it }.toDouble())
    private fun itemCps(i: Int): Double = ITEMS[i].cps * owned[i] * mult(i)
    private fun cps(): Double {
        var s = 0.0
        for (i in ITEMS.indices) s += itemCps(i)
        return s
    }
    private fun cost(i: Int): Double = ITEMS[i].baseCost * 1.15.pow(owned[i].toDouble())
    private fun tapCost(): Double = 20.0 * 1.7.pow(tapLevel.toDouble())
    private fun tapValue(): Double = 1.0 + tapLevel + cps() * 0.01

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            val dt = (now - lastTick).coerceAtMost(1000L) / 1000.0
            lastTick = now
            coins += cps() * dt
            refresh()
            if (now - lastSave > 5000) save()
            handler.postDelayed(this, 100)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load()
        buildUi()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        offline()
        lastTick = System.currentTimeMillis()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        save()
    }

    private fun offline() {
        val now = System.currentTimeMillis()
        val secs = ((now - lastSeen) / 1000).coerceAtMost(8 * 3600L)
        val gain = cps() * secs * 0.5
        if (secs > 30 && gain > 0.0) {
            coins += gain
            AlertDialog.Builder(this)
                .setTitle("С возвращением!")
                .setMessage("Пока тебя не было, ферма заработала 💰 " + fmt(gain))
                .setPositiveButton("Забрать", null)
                .show()
        }
        lastSeen = now
    }

    private fun save() {
        lastSave = System.currentTimeMillis()
        lastSeen = lastSave
        getSharedPreferences("ferma", Context.MODE_PRIVATE).edit()
            .putString("coins", coins.toString())
            .putInt("tap", tapLevel)
            .putString("owned", owned.joinToString(","))
            .putLong("seen", lastSeen)
            .apply()
    }

    private fun load() {
        val p = getSharedPreferences("ferma", Context.MODE_PRIVATE)
        coins = p.getString("coins", "0")?.toDoubleOrNull() ?: 0.0
        tapLevel = p.getInt("tap", 0)
        val o = p.getString("owned", "")?.split(",") ?: emptyList()
        for (i in owned.indices) owned[i] = o.getOrNull(i)?.toIntOrNull() ?: 0
        lastSeen = p.getLong("seen", System.currentTimeMillis())
    }

    private fun reset() {
        coins = 0.0
        tapLevel = 0
        owned.fill(0)
        save()
        refresh()
    }

    private fun buyTap() {
        val c = tapCost()
        if (coins >= c) {
            coins -= c
            tapLevel++
        }
    }

    private fun buy(i: Int) {
        val c = cost(i)
        if (coins >= c) {
            coins -= c
            owned[i]++
        }
    }

    private fun onTap(v: View) {
        val g = tapValue()
        coins += g
        feedback.text = "+" + fmt(g)
        v.animate().cancel()
        v.scaleX = 0.92f
        v.scaleY = 0.92f
        v.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
        refresh()
    }

    private fun addRow(onClick: () -> Unit): TextView {
        val t = TextView(this).apply {
            textSize = 15f
            setTextColor(Color.parseColor("#2D3A1F"))
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#CDD8B8"))
            }
            setOnClickListener {
                onClick()
                refresh()
            }
        }
        list.addView(
            t,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8) }
        )
        return t
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#F4F1DE"))
            setPadding(dp(14), dp(36), dp(14), dp(10))
        }

        coinsView = TextView(this).apply {
            textSize = 34f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#3D5A27"))
        }
        cpsView = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#6B705C"))
        }
        root.addView(coinsView)
        root.addView(cpsView)

        val tapBtn = TextView(this).apply {
            text = "🌾"
            textSize = 72f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#A7C957"))
                setStroke(dp(4), Color.parseColor("#6A994E"))
            }
            setOnClickListener { onTap(it) }
        }
        root.addView(
            tapBtn,
            LinearLayout.LayoutParams(dp(180), dp(180)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(12)
            }
        )

        feedback = TextView(this).apply {
            textSize = 16f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#386641"))
            setPadding(0, dp(6), 0, 0)
        }
        root.addView(feedback)

        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rows.clear()
        rows.add(addRow { buyTap() })
        for (i in ITEMS.indices) rows.add(addRow { buy(i) })

        list.addView(TextView(this).apply {
            text = "Начать заново"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(Color.GRAY)
            setPadding(0, dp(12), 0, dp(12))
            setOnClickListener {
                AlertDialog.Builder(this@MainActivity)
                    .setMessage("Сбросить весь прогресс?")
                    .setPositiveButton("Да") { _, _ -> reset() }
                    .setNegativeButton("Нет", null)
                    .show()
            }
        })

        root.addView(
            ScrollView(this).apply { addView(list) },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f).apply {
                topMargin = dp(10)
            }
        )
        setContentView(root)
    }

    private fun refresh() {
        coinsView.text = "💰 " + fmt(coins)
        cpsView.text = fmt(cps()) + " в секунду"

        rows[0].text = "🧤 Перчатки · ур. $tapLevel\n+1 к сбору за клик · цена " + fmt(tapCost())
        rows[0].alpha = if (coins >= tapCost()) 1f else 0.5f

        for (i in ITEMS.indices) {
            val item = ITEMS[i]
            rows[i + 1].text = item.emoji + " " + item.name + " · ×" + owned[i] +
                "\nДаёт " + fmt(item.cps * mult(i)) + "/с каждая · цена " + fmt(cost(i))
            rows[i + 1].alpha = if (coins >= cost(i)) 1f else 0.5f
        }
    }
}
