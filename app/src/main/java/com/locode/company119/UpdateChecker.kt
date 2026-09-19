package com.locode.company119

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.pm.PackageInfoCompat
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * GitHub 최신 릴리스와 현재 versionCode 비교.
 * 태그 규칙: v<versionName>-c<versionCode> (app/build.gradle.kts 의 publishGithubRelease 와 맞춘다)
 */
object UpdateChecker {
    private const val TAG = "update"
    private const val LATEST_URL = "https://api.github.com/repos/menotdie/119company/releases/latest"
    private val TAG_CODE = Regex("""-c(\d+)$""")

    private val client = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()

    private class Release(val tag: String, val code: Long, val apkUrl: String, val apkName: String)

    /** 새 버전이 없거나 조회 실패 시 onNoUpdate 호출. 새 버전이면 닫을 수 없는 창을 띄우고 onNoUpdate 는 부르지 않는다. */
    fun check(activity: Activity, onNoUpdate: () -> Unit) {
        val current = try {
            PackageInfoCompat.getLongVersionCode(
                activity.packageManager.getPackageInfo(activity.packageName, 0)
            )
        } catch (e: Exception) {
            UdpLogger.log(TAG, "현재 versionCode 조회 실패: $e")
            onNoUpdate(); return
        }
        thread {
            val latest = fetchLatest()
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                if (latest == null || latest.code <= current) {
                    UdpLogger.log(TAG, "업데이트 없음 current=$current latest=${latest?.code}")
                    onNoUpdate()
                } else {
                    UdpLogger.log(TAG, "새 버전 ${latest.tag} (current=$current)")
                    showBlockingDialog(activity, latest)
                }
            }
        }
    }

    private fun fetchLatest(): Release? = try {
        val req = Request.Builder().url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) {
                UdpLogger.log(TAG, "릴리스 조회 실패 HTTP ${resp.code}")
                null
            } else {
                val json = JSONObject(resp.body!!.string())
                val tag = json.getString("tag_name")
                val code = TAG_CODE.find(tag)?.groupValues?.get(1)?.toLong()
                val assets = json.getJSONArray("assets")
                val apk = (0 until assets.length()).map { assets.getJSONObject(it) }
                    .firstOrNull { it.getString("name").endsWith(".apk") }
                if (code == null || apk == null) {
                    UdpLogger.log(TAG, "릴리스 형식 불일치 tag=$tag apk=${apk != null}")
                    null
                } else Release(tag, code, apk.getString("browser_download_url"), apk.getString("name"))
            }
        }
    } catch (e: Exception) {
        UdpLogger.log(TAG, "릴리스 조회 예외: $e")
        null
    }

    private fun showBlockingDialog(activity: Activity, r: Release) {
        val dialog = AlertDialog.Builder(activity)
            .setTitle("새 버전 ${r.tag}")
            .setMessage("다운로드 폴더에서 설치하세요")
            .setCancelable(false)
            .setPositiveButton("다운로드", null)
            .create()
        dialog.setCanceledOnTouchOutside(false)
        dialog.setOnShowListener {
            // 기본 동작(버튼 누르면 닫힘)을 막아 창을 유지한다
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { download(activity, r) }
        }
        dialog.show()
    }

    private fun download(ctx: Context, r: Release) {
        try {
            val req = DownloadManager.Request(Uri.parse(r.apkUrl))
                .setTitle(r.apkName)
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, r.apkName)
            val id = (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(req)
            UdpLogger.log(TAG, "다운로드 시작 id=$id ${r.apkUrl}")
            Toast.makeText(ctx, "다운로드를 시작했습니다", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            UdpLogger.log(TAG, "다운로드 실패: $e")
            Toast.makeText(ctx, "다운로드 실패: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
