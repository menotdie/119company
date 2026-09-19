package com.locode.company119

import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var goalPicker: NumberPicker

    private fun dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Company119Api.init(applicationContext)

        val root = ScrollView(this).apply {
            setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, R.color.bg))
            isFillViewport = true
        }
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }

        val goalRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        goalRow.addView(TextView(this).apply {
            text = getString(R.string.settings_week_goal)
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.subtitle))
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        goalPicker = NumberPicker(this).apply {
            minValue = 0
            maxValue = STEP_COUNT - 1
            displayedValues = Array(STEP_COUNT) { (GOAL_MIN + it * GOAL_STEP).toString() }
            value = goalToIndex(Company119Api.getWeekGoal())
        }
        goalRow.addView(goalPicker, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        outer.addView(goalRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(16) })

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        btnRow.addView(Button(this).apply {
            text = getString(R.string.settings_cancel)
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginEnd = dp(8)
        })
        btnRow.addView(Button(this).apply {
            text = getString(R.string.settings_save)
            setOnClickListener { onSaveClick() }
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        outer.addView(btnRow, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(16) })

        root.addView(outer)
        setContentView(root)
    }

    private fun onSaveClick() {
        Company119Api.setWeekGoal(GOAL_MIN + goalPicker.value * GOAL_STEP)
        Toast.makeText(this, R.string.settings_save, Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        private const val GOAL_MIN = 200
        private const val GOAL_STEP = 50
        private const val STEP_COUNT = 17   // 200..1000

        /** 저장값이 범위 밖이거나 50 배수가 아니면 가장 가까운 단계로 스냅. */
        fun goalToIndex(goal: Int): Int =
            Math.round((goal - GOAL_MIN) / GOAL_STEP.toFloat()).coerceIn(0, STEP_COUNT - 1)
    }
}
