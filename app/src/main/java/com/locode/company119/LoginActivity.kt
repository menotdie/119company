package com.locode.company119

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlin.concurrent.thread

class LoginActivity : AppCompatActivity() {

    private lateinit var inpPhone: EditText
    private lateinit var inpPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var errorView: TextView
    private var a11yPromptShown = false

    private var updateChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        Company119Api.init(applicationContext)   // UdpLogger 활성화가 여기서 되므로 업데이트 확인보다 먼저
        UpdateChecker.check(this) {
            updateChecked = true
            startLogin()
            resumeLogin()
        }
    }

    /** 업데이트 확인을 통과한 뒤에만 실행 — 새 버전이 있으면 여기로 오지 않아 앱 사용이 막힌다. */
    private fun startLogin() {
        inpPhone = findViewById(R.id.inp_phone)
        inpPassword = findViewById(R.id.inp_password)
        btnLogin = findViewById(R.id.btn_login)
        errorView = findViewById(R.id.error)

        inpPhone.addTextChangedListener(PhoneFormatter(inpPhone))
        btnLogin.setOnClickListener { doLogin() }

        if (Company119Api.hasRememberToken()) {
            btnLogin.isEnabled = false
            btnLogin.text = getString(R.string.btn_loading)
            thread {
                val r = Company119Api.fetchStats()
                runOnUiThread {
                    btnLogin.isEnabled = true
                    btnLogin.text = getString(R.string.btn_login)
                    when (r) {
                        is Company119Api.StatsResult.Ok -> onLoggedIn()
                        is Company119Api.StatsResult.Unauthorized -> Company119Api.clearCookies()
                        is Company119Api.StatsResult.Error -> {}
                    }
                }
            }
        }
    }

    private fun doLogin() {
        val phone = inpPhone.text.toString().filter { it.isDigit() }
        val password = inpPassword.text.toString().trim()
        if (phone.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.err_empty))
            return
        }
        errorView.visibility = View.GONE
        btnLogin.isEnabled = false
        btnLogin.text = getString(R.string.btn_loading)
        thread {
            val r = Company119Api.login(phone, password)
            runOnUiThread {
                btnLogin.isEnabled = true
                btnLogin.text = getString(R.string.btn_login)
                if (r.ok) onLoggedIn()
                else showError(r.error ?: getString(R.string.err_login))
            }
        }
    }

    private fun showError(msg: String) {
        errorView.text = msg
        errorView.visibility = View.VISIBLE
    }

    private fun onLoggedIn() {
        if (Company119AccessibilityService.isEnabled(this)) {
            Company119AccessibilityService.instance?.showOverlay()
            finish()
            return
        }
        suggestAccessibility()
    }

    /**
     * 통화 중에는 시스템이 '다른 앱 위에 표시' 권한을 일시 차단한다.
     * 접근성 오버레이는 그 검사를 타지 않으므로 이쪽을 먼저 권한다.
     */
    private fun suggestAccessibility() {
        AlertDialog.Builder(this)
            .setTitle(R.string.a11y_prompt_title)
            .setMessage(R.string.a11y_prompt_msg)
            .setPositiveButton(R.string.a11y_prompt_open) { _, _ ->
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .setNegativeButton(R.string.a11y_prompt_skip) { _, _ -> startNormalOverlay() }
            .setCancelable(false)
            .show()
    }

    private fun startNormalOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(this)) {
            Toast.makeText(this, R.string.need_overlay_permission, Toast.LENGTH_LONG).show()
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        startService(Intent(this, OverlayService::class.java))
        finish()
    }

    override fun onResume() {
        super.onResume()
        if (updateChecked) resumeLogin()
    }

    private fun resumeLogin() {
        if (!Company119Api.hasRememberToken()) return
        if (Company119AccessibilityService.isEnabled(this)) {
            Company119AccessibilityService.instance?.showOverlay()
            finish()
            return
        }
        // 접근성이 꺼져 있으면 조용히 일반 모드로 가지 않고 한 번은 안내한다.
        if (!a11yPromptShown) {
            a11yPromptShown = true
            suggestAccessibility()
        }
    }


    private class PhoneFormatter(private val edit: EditText) : TextWatcher {
        private var busy = false
        override fun afterTextChanged(s: Editable) {
            if (busy) return
            busy = true
            val digits = s.toString().filter { it.isDigit() }.take(11)
            val out = when {
                digits.length <= 3 -> digits
                digits.length <= 7 -> digits.substring(0, 3) + "-" + digits.substring(3)
                else -> digits.substring(0, 3) + "-" + digits.substring(3, 7) + "-" + digits.substring(7)
            }
            edit.setText(out)
            edit.setSelection(out.length)
            busy = false
        }
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    }
}
