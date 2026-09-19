package com.locode.company119

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object Company119Api {

    const val BASE = "https://ride.setla.co.kr"
    private const val UA = "Mozilla/5.0 (Linux; Android 13; 119COMPANY-Stats) AppleWebKit/537.36"
    private const val PREFS = "company119_secure"

    private lateinit var prefs: android.content.SharedPreferences
    private val cookies = mutableListOf<Cookie>()
    private lateinit var client: OkHttpClient

    fun init(ctx: Context) {
        if (::client.isInitialized) return
        val masterKey = MasterKey.Builder(ctx)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        prefs = EncryptedSharedPreferences.create(
            ctx, PREFS, masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
        UdpLogger.enabled = prefs.getBoolean("debug_enabled", true)
        UdpLogger.log("init", "Company119Api init, debug=${UdpLogger.enabled}")
        loadCookies()
        client = OkHttpClient.Builder()
            .cookieJar(object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, list: List<Cookie>) {
                    synchronized(cookies) {
                        for (c in list) {
                            cookies.removeAll { it.name == c.name && it.domain == c.domain }
                            cookies.add(c)
                        }
                        saveCookies()
                    }
                }
                override fun loadForRequest(url: HttpUrl): List<Cookie> = synchronized(cookies) {
                    cookies.filter { it.matches(url) }
                }
            })
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun loadCookies() {
        val raw = prefs.getString("cookies", null) ?: return
        for (line in raw.split("\n")) {
            if (line.isBlank()) continue
            val parts = line.split("|", limit = 5)
            if (parts.size < 5) continue
            val cookie = Cookie.Builder()
                .name(parts[0])
                .value(parts[1])
                .domain(parts[2])
                .path(parts[3])
                .expiresAt(parts[4].toLongOrNull() ?: Long.MAX_VALUE)
                .build()
            cookies.add(cookie)
        }
    }

    private fun saveCookies() {
        val s = cookies.joinToString("\n") {
            "${it.name}|${it.value}|${it.domain}|${it.path}|${it.expiresAt}"
        }
        prefs.edit().putString("cookies", s).apply()
    }

    fun clearCookies() {
        synchronized(cookies) {
            cookies.clear()
            prefs.edit().remove("cookies").apply()
        }
    }

    fun hasRememberToken(): Boolean = synchronized(cookies) {
        cookies.any { it.name == "remember_token" && it.expiresAt > System.currentTimeMillis() }
    }

    data class LoginResult(val ok: Boolean, val name: String?, val store: String?, val error: String?)
    data class Stats(
        val completed: Int,
        val rejected: Int,
        val dispatch: Int,
        val delivery: Int,
        val rejectLeft: Int?,
        val weekCompleted: Int?,
        val weekOffslot: Int?,
        val status: String?,
        val store: String?,
        val timestamp: String?,
        val doneRank: Int?,
        val weekRank: Int?
    )

    sealed class StatsResult {
        data class Ok(val stats: Stats) : StatsResult()
        object Unauthorized : StatsResult()
        object Error : StatsResult()
    }

    private fun req(path: String): Request.Builder =
        Request.Builder()
            .url("$BASE$path")
            .header("User-Agent", UA)
            .header("Accept", "application/json,text/html;q=0.9")
            .header("Accept-Language", "ko-KR,ko;q=0.9")
            .header("Referer", "$BASE/rider")

    fun login(phone: String, password: String): LoginResult {
        val body = JSONObject().apply {
            put("phone", phone)
            put("password", password)
        }.toString().toRequestBody("application/json".toMediaType())
        val request = req("/api/auth/login")
            .header("Origin", BASE)
            .header("Referer", "$BASE/rider/login")
            .post(body)
            .build()
        return try {
            client.newCall(request).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                if (!resp.isSuccessful) return LoginResult(false, null, null, "HTTP ${resp.code}")
                val json = JSONObject(text)
                if (json.optBoolean("success")) {
                    val name = json.optString("name")
                    val store = json.optString("store")
                    // /me 응답에는 store가 없어 로그인 시 확보한 매장명을 캐시(자동로그인 표시용)
                    prefs.edit().putString("store_name", store).apply()
                    LoginResult(true, name, store, null)
                } else {
                    LoginResult(false, null, null, json.optString("error", "로그인 실패"))
                }
            }
        } catch (e: Exception) {
            LoginResult(false, null, null, "서버 연결 실패")
        }
    }

    fun fetchStats(): StatsResult {
        UdpLogger.log("fetchStats", "GET /api/me")
        val request = req("/api/me").get().build()
        return try {
            client.newCall(request).execute().use { resp ->
                UdpLogger.log("fetchStats", "code=${resp.code}")
                if (resp.code == 401) return StatsResult.Unauthorized
                if (!resp.isSuccessful) return StatsResult.Error
                val body = resp.body?.string() ?: return StatsResult.Error
                UdpLogger.log("fetchStats", "len=${body.length}")
                val json = JSONObject(body)
                val week = fetchWeekTotals()
                StatsResult.Ok(
                    Stats(
                        completed = json.optInt("completed"),
                        rejected = json.optInt("rejected"),
                        dispatch = json.optInt("dispatch_cancel"),
                        delivery = json.optInt("delivery_cancel"),
                        rejectLeft = week.remaining,
                        weekCompleted = week.completed,
                        weekOffslot = week.offslot,
                        status = json.optString("status"),
                        store = prefs.getString("store_name", null),
                        timestamp = json.optString("timestamp"),
                        doneRank = if (json.has("done_rank")) json.optInt("done_rank") else null,
                        weekRank = week.rank
                    )
                )
            }
        } catch (e: Exception) {
            UdpLogger.log("fetchStats", "EXCEPTION: ${e.javaClass.simpleName} ${e.message}")
            StatsResult.Error
        }
    }

    // 잔여거절권=total.remaining, 주간총완료=total.completed, 주간심야=total.offslot(시간외/SLA밖), 주간순위=week_rank(top-level)
    private data class WeekTotals(val remaining: Int?, val completed: Int?, val offslot: Int?, val rank: Int?)

    // /api/record?week=0 한 번 호출
    private fun fetchWeekTotals(): WeekTotals {
        val empty = WeekTotals(null, null, null, null)
        val request = req("/api/record?week=0").get().build()
        return try {
            client.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return empty
                val body = resp.body?.string() ?: return empty
                val root = JSONObject(body)
                val rank = if (root.has("week_rank")) root.optInt("week_rank") else null
                val total = root.optJSONObject("total") ?: return WeekTotals(null, null, null, rank)
                val remaining = if (total.has("remaining")) total.optInt("remaining") else null
                val completed = if (total.has("completed")) total.optInt("completed") else null
                val offslot = total.optInt("offslot", 0)
                WeekTotals(remaining, completed, offslot, rank)
            }
        } catch (e: Exception) {
            empty
        }
    }

    fun setDebugEnabled(v: Boolean) {
        prefs.edit().putBoolean("debug_enabled", v).apply()
        UdpLogger.enabled = v
        UdpLogger.log("debug", "enabled=$v")
    }

    fun isDebugEnabled(): Boolean = prefs.getBoolean("debug_enabled", false)

    fun setWeekGoal(v: Int) {
        prefs.edit().putInt("week_goal", v).apply()
        UdpLogger.log("weekGoal", "goal=$v")
    }

    fun getWeekGoal(): Int = prefs.getInt("week_goal", 200)
}
