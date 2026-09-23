package com.locode.company119

import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * Floating overlay: collapsed circular FAB <-> expanded 4-cell panel.
 * Drag to move. Refresh button has 30s cooldown.
 */
class OverlayManager(
    private val ctx: Context,
    private val accessibilityOverlay: Boolean = false
) {

    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var rootView: View
    private lateinit var collapsedView: View
    private lateinit var expandedView: View

    private lateinit var tvCompleted: TextView
    private lateinit var tvRejected: TextView
    private lateinit var tvDispatch: TextView
    private lateinit var tvDelivery: TextView
    private lateinit var tvRejectLeft: TextView
    private lateinit var tvWeekCompleted: TextView
    private lateinit var tvRank: TextView
    private lateinit var tvStatus: TextView
    private lateinit var btnRefresh: TextView
    private lateinit var btnCollapse: TextView
    private lateinit var statsGrid: View
    private lateinit var buttonRow: View
    private lateinit var tvMessage: TextView

    private val params = WindowManager.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
        if (accessibilityOverlay)
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 40
        y = 200
    }

    private var lastRefreshAt = 0L
    private var countdownRunnable: Runnable? = null
    private var weekAnim: ValueAnimator? = null

    fun attach() {
        buildView()
        wm.addView(rootView, params)
        refresh()
    }

    fun detach() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        weekAnim?.cancel()
        weekAnim = null
        if (::rootView.isInitialized) {
            try { wm.removeView(rootView) } catch (_: Exception) {}
        }
    }

    private fun dp(v: Int): Int =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, v.toFloat(),
            ctx.resources.displayMetrics
        ).toInt()

    private fun buildView() {
        val container = FrameLayout(ctx)

        collapsedView = buildCollapsed().apply {
            setOnTouchListener(DragTouchListener(onClick = ::onFabClick))
        }
        expandedView = buildExpanded().apply {
            visibility = View.GONE
        }

        container.addView(collapsedView)
        container.addView(expandedView)
        rootView = container
    }

    private fun buildCollapsed(): View {
        val size = dp(56)
        val tv = TextView(ctx).apply {
            text = "119"
            textSize = 18f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.primary))
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_fab)
            layoutParams = FrameLayout.LayoutParams(size, size)
        }
        return tv
    }

    private fun buildExpanded(): View {
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_card)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = FrameLayout.LayoutParams(dp(350), ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        // 헤더: 좌측 상태 / 우측 로그아웃
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        tvStatus = TextView(ctx).apply {
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.subtitle))
            text = "-"
        }
        val tvSettings = TextView(ctx).apply {
            text = ctx.getString(R.string.btn_settings)
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.subtitle))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            isClickable = true
            isFocusable = true
            setOnClickListener { onSettingsClick() }
        }
        val tvExit = TextView(ctx).apply {
            text = ctx.getString(R.string.btn_exit)
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.subtitle))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            isClickable = true
            isFocusable = true
            setOnClickListener { onExitClick() }
        }
        val tvLogout = TextView(ctx).apply {
            text = ctx.getString(R.string.btn_logout)
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.c_red))
            setPadding(dp(8), dp(4), dp(4), dp(4))
            isClickable = true
            isFocusable = true
            setOnClickListener { onLogoutClick() }
        }
        header.addView(tvStatus, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ))
        header.addView(tvSettings, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        header.addView(tvExit, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        header.addView(tvLogout, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        card.addView(header, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(8) })

        val grid = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 5f
        }

        tvCompleted = buildStatCell(grid, R.string.stat_completed, R.color.c_green)
        tvRejected = buildStatCell(grid, R.string.stat_rejected, R.color.c_red)
        tvDispatch = buildStatCell(grid, R.string.stat_dispatch, R.color.c_yellow)
        tvDelivery = buildStatCell(grid, R.string.stat_delivery, R.color.text)
        tvRejectLeft = buildStatCell(grid, R.string.stat_reject_left, R.color.text)

        statsGrid = grid
        card.addView(grid, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // 완료/거절/취소 칸 밑 주간 총 완료 (흰색)
        tvWeekCompleted = TextView(ctx).apply {
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text))
            gravity = Gravity.START
            setPadding(dp(8), 0, 0, 0)
        }
        card.addView(tvWeekCompleted, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })

        // 주간 총 완료 바로 밑 순위 (같은 스타일)
        tvRank = TextView(ctx).apply {
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(ContextCompat.getColor(ctx, R.color.text))
            gravity = Gravity.START
            setPadding(dp(8), 0, 0, 0)
        }
        card.addView(tvRank, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(6) })

        // 로그아웃 안내 메시지 (401 시에만 표시)
        tvMessage = TextView(ctx).apply {
            text = ctx.getString(R.string.msg_logged_out)
            textSize = 14f
            setTextColor(ContextCompat.getColor(ctx, R.color.c_red))
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(28), dp(20), dp(28))
            visibility = View.GONE
        }
        card.addView(tvMessage, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        // 버튼 행
        val btnRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            weightSum = 2f
        }
        btnRefresh = pillButton(ctx.getString(R.string.btn_refresh))
        btnRefresh.setOnClickListener { onRefreshClick() }
        btnCollapse = pillButton(ctx.getString(R.string.btn_collapse))
        btnCollapse.setOnClickListener { collapse() }

        btnRow.addView(btnRefresh, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { setMargins(0, dp(8), dp(4), 0) })
        btnRow.addView(btnCollapse, LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f
        ).apply { setMargins(dp(4), dp(8), 0, 0) })

        buttonRow = btnRow
        card.addView(btnRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))

        card.setOnTouchListener(DragTouchListener(onClick = {}))
        return card
    }

    private fun buildStatCell(parent: LinearLayout, labelRes: Int, numColorRes: Int): TextView {
        val cell = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_stat_cell)
            setPadding(dp(4), dp(10), dp(4), dp(10))
        }
        val num = TextView(ctx).apply {
            text = "0"
            textSize = 22f
            setTextColor(ContextCompat.getColor(ctx, numColorRes))
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val label = TextView(ctx).apply {
            setText(labelRes)
            textSize = 11f
            setTextColor(ContextCompat.getColor(ctx, R.color.label))
            gravity = Gravity.CENTER
        }
        cell.addView(num)
        cell.addView(label, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(2) })

        val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(4)
        }
        parent.addView(cell, lp)
        return num
    }

    private fun pillButton(text: String): TextView {
        return TextView(ctx).apply {
            this.text = text
            textSize = 13f
            setTextColor(ContextCompat.getColor(ctx, R.color.text))
            gravity = Gravity.CENTER
            background = ContextCompat.getDrawable(ctx, R.drawable.bg_pill)
            setPadding(dp(8), dp(10), dp(8), dp(10))
        }
    }

    private fun onFabClick() {
        expand()
    }

    private fun onLogoutClick() {
        Company119Api.clearCookies()
        ctx.startActivity(Intent(ctx, LoginActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
        dismiss()
    }

    private fun onExitClick() {
        dismiss()
    }

    /** 오버레이 소유자(접근성 서비스 / 포그라운드 서비스)에 맞게 내린다. */
    private fun dismiss() {
        if (accessibilityOverlay) {
            Company119AccessibilityService.instance?.hideOverlay() ?: detach()
        } else {
            ctx.stopService(Intent(ctx, OverlayService::class.java))
        }
    }

    private fun onSettingsClick() {
        ctx.startActivity(Intent(ctx, SettingsActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    private fun expand() {
        collapsedView.visibility = View.GONE
        expandedView.visibility = View.VISIBLE
        startCountdown()
    }

    private fun collapse() {
        expandedView.visibility = View.GONE
        collapsedView.visibility = View.VISIBLE
        countdownRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun onRefreshClick() {
        val elapsed = System.currentTimeMillis() - lastRefreshAt
        if (elapsed < COOLDOWN_MS) return
        refresh()
    }

    private fun refresh() {
        UdpLogger.log("Overlay", "refresh() called")
        lastRefreshAt = System.currentTimeMillis()
        btnRefresh.isEnabled = false
        btnRefresh.alpha = 0.5f
        thread {
            val r = Company119Api.fetchStats()
            UdpLogger.log("Overlay", "fetchStats result: ${r.javaClass.simpleName}")
            handler.post {
                when (r) {
                    is Company119Api.StatsResult.Ok -> {
                        showStats()
                        applyStats(r.stats)
                    }
                    is Company119Api.StatsResult.Unauthorized -> showLoggedOut()
                    is Company119Api.StatsResult.Error -> {}
                }
                startCountdown()
            }
        }
    }

    private fun showStats() {
        statsGrid.visibility = View.VISIBLE
        tvWeekCompleted.visibility = View.VISIBLE
        buttonRow.visibility = View.VISIBLE
        tvMessage.visibility = View.GONE
    }

    private fun showLoggedOut() {
        statsGrid.visibility = View.GONE
        tvWeekCompleted.visibility = View.GONE
        buttonRow.visibility = View.GONE
        tvMessage.visibility = View.VISIBLE
        tvStatus.text = "-"
    }

    private fun applyStats(s: Company119Api.Stats) {
        tvCompleted.text = s.completed.toString()
        tvRejected.text = s.rejected.toString()
        tvDispatch.text = s.dispatch.toString()
        tvDelivery.text = s.delivery.toString()
        weekAnim?.cancel()
        weekAnim = null
        // 당일 순위(/api/me)와 주간 순위(/api/record)는 출처가 달라 각각 없을 수 있다 — 있는 쪽만 한 줄에 붙인다
        tvRank.text = listOfNotNull(
            s.doneRank?.let { "당일 순위 $it" },
            s.weekRank?.let { "주간 순위 $it" }
        ).joinToString(" · ")
        tvWeekCompleted.text = if (s.weekCompleted == null) "" else {
            val diff = s.weekCompleted - Company119Api.getWeekGoal()
            val num = String.format("%+d", diff)
            val offslot = s.weekOffslot ?: 0
            val sb = SpannableStringBuilder("${ctx.getString(R.string.stat_week_completed)} ${s.weekCompleted}")
            // 앞 = 심야 포함 총 완료, 괄호(심야>0일 때만) = 심야 건수(연회색)
            if (offslot > 0) {
                val grayStart = sb.length
                sb.append("($offslot)")
                sb.setSpan(
                    ForegroundColorSpan(Color.parseColor("#AAAAAA")),
                    grayStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            // stat_week_diff ends with %+d -> label = formatted minus the number tail
            sb.append(ctx.getString(R.string.stat_week_diff, diff).dropLast(num.length))
            if (diff >= 0) {
                val rainbow = intArrayOf(
                    Color.parseColor("#FF0000"), Color.parseColor("#FF7F00"), Color.parseColor("#FFFF00"),
                    Color.parseColor("#00FF00"), Color.parseColor("#0000FF"), Color.parseColor("#4B0082"),
                    Color.parseColor("#8B00FF")
                )
                val base = sb.length
                val text = "☆☆축, 달성!!☆☆"
                sb.append(text)
                var last = -1
                // 무한 ValueAnimator. 단계값 하나로 무지개 오프셋 순환 + 별 깜빡임
                weekAnim = ValueAnimator.ofInt(0, 14).apply {
                    duration = 2100L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = android.view.animation.LinearInterpolator()
                    addUpdateListener {
                        val step = (it.animatedValue as Int) % 14
                        if (step == last) return@addUpdateListener
                        last = step
                        sb.getSpans(base, sb.length, ForegroundColorSpan::class.java).forEach(sb::removeSpan)
                        val starColor = if (step % 2 == 0) Color.parseColor("#FFD700") else Color.parseColor("#555555")
                        text.forEachIndexed { i, _ ->
                            val c = if (i < 2 || i >= text.length - 2) starColor
                                    else rainbow[((i - 2) + rainbow.size - step % rainbow.size) % rainbow.size]
                            sb.setSpan(ForegroundColorSpan(c), base + i, base + i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                        tvWeekCompleted.text = sb
                    }
                    start()
                }
            } else {
                val start = sb.length
                sb.append(num)
                sb.setSpan(
                    ForegroundColorSpan(Color.parseColor(if (diff > 0) "#4CAF50" else "#FF5252")),
                    start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            sb
        }
        UdpLogger.log("applyStats", "rejectLeft=${s.rejectLeft}")
        if (s.rejectLeft == null) {
            tvRejectLeft.text = "-"
            tvRejectLeft.setTextColor(ContextCompat.getColor(ctx, R.color.text))
        } else {
            val v = s.rejectLeft
            tvRejectLeft.text = v.toString()
            val color = if (v < 0) R.color.c_red else if (v > 0) R.color.c_green else R.color.text
            tvRejectLeft.setTextColor(ContextCompat.getColor(ctx, color))
        }
        val parts = mutableListOf<String>()
        if (!s.store.isNullOrEmpty()) parts += s.store
        if (!s.status.isNullOrEmpty()) parts += s.status
        if (!s.timestamp.isNullOrEmpty()) parts += s.timestamp
        tvStatus.text = parts.joinToString(" · ")
    }

    private fun startCountdown() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        val tick = object : Runnable {
            override fun run() {
                val left = COOLDOWN_MS - (System.currentTimeMillis() - lastRefreshAt)
                if (left <= 0) {
                    btnRefresh.isEnabled = true
                    btnRefresh.alpha = 1f
                    btnRefresh.text = ctx.getString(R.string.btn_refresh)
                    return
                }
                btnRefresh.isEnabled = false
                btnRefresh.alpha = 0.5f
                val sec = (left / 1000) + 1
                btnRefresh.text = "${sec}초"
                handler.postDelayed(this, 250)
            }
        }
        countdownRunnable = tick
        handler.post(tick)
    }

    /** 손가락 드래그로 윈도우 이동, threshold 이하 이동은 클릭으로 인식. */
    private inner class DragTouchListener(private val onClick: () -> Unit) : View.OnTouchListener {
        private var startX = 0; private var startY = 0
        private var touchX = 0f; private var touchY = 0f
        private var moved = false
        private val slop = dp(8)

        override fun onTouch(v: View, ev: MotionEvent): Boolean {
            when (ev.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x; startY = params.y
                    touchX = ev.rawX; touchY = ev.rawY
                    moved = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (ev.rawX - touchX).toInt()
                    val dy = (ev.rawY - touchY).toInt()
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        params.x = startX + dx
                        params.y = startY + dy
                        try { wm.updateViewLayout(rootView, params) } catch (_: Exception) {}
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onClick()
                }
            }
            return true
        }
    }

    companion object {
        private const val COOLDOWN_MS = 30_000L
    }
}
