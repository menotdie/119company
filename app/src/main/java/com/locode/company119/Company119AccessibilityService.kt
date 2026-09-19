package com.locode.company119

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent

/**
 * 통화 중 SYSTEM_ALERT_WINDOW appop이 시스템에 의해 deny 되는 것을 우회하기 위한 오버레이 호스트.
 * TYPE_ACCESSIBILITY_OVERLAY 는 해당 appop 검사를 타지 않는다.
 */
class Company119AccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: Company119AccessibilityService? = null

        /** 설정에서 이 접근성 서비스가 켜져 있는지. */
        fun isEnabled(ctx: Context): Boolean {
            val enabled = Settings.Secure.getString(
                ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val me = "${ctx.packageName}/${Company119AccessibilityService::class.java.name}"
            return enabled.split(':').any { it == me }
        }
    }

    private var overlay: OverlayManager? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Company119Api.init(applicationContext)
        UdpLogger.log("A11y", "onServiceConnected")
        if (Company119Api.hasRememberToken()) showOverlay()
    }

    fun showOverlay() {
        if (overlay != null) return
        overlay = OverlayManager(this, accessibilityOverlay = true).also { it.attach() }
        UdpLogger.log("A11y", "overlay attached")
    }

    fun hideOverlay() {
        overlay?.detach()
        overlay = null
        UdpLogger.log("A11y", "overlay detached")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        hideOverlay()
        instance = null
        super.onDestroy()
    }
}
