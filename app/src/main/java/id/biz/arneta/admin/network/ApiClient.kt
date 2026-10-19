package id.biz.arneta.admin.network

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class ApiResult(
    val status: Boolean,
    val message: String,
    val data: JSONObject? = null,
    val dataArray: JSONArray? = null,
    val rawData: Any? = null,
    val code: Int = 200
)

object ApiClient {
    private const val PREF_NAME = "arneta_admin_prefs_v6"
    private const val DEFAULT_BASE_URL = "https://phpnuxbill.arneta.biz.id"

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    }

    var baseUrl: String
        get() = (prefs.getString("base_url", DEFAULT_BASE_URL) ?: DEFAULT_BASE_URL).trimEnd('/')
        set(value) = prefs.edit().putString("base_url", value.trimEnd('/')).apply()

    var token: String
        get() = prefs.getString("admin_token", "") ?: ""
        set(value) = prefs.edit().putString("admin_token", value).apply()

    var adminName: String
        get() = prefs.getString("admin_name", "Administrator") ?: "Administrator"
        set(value) = prefs.edit().putString("admin_name", value).apply()

    var adminUsername: String
        get() = prefs.getString("admin_username", "admin") ?: "admin"
        set(value) = prefs.edit().putString("admin_username", value).apply()

    var adminRole: String
        get() = prefs.getString("admin_role", "SuperAdmin") ?: "SuperAdmin"
        set(value) = prefs.edit().putString("admin_role", value).apply()

    var companyName: String
        get() = prefs.getString("company_name", "Arneta.ID") ?: "Arneta.ID"
        set(value) = prefs.edit().putString("company_name", value).apply()

    var currencyCode: String
        get() = prefs.getString("currency_code", "Rp.") ?: "Rp."
        set(value) = prefs.edit().putString("currency_code", value).apply()

    var isDarkMode: Boolean
        get() = prefs.getBoolean("dark_mode", true)
        set(value) = prefs.edit().putBoolean("dark_mode", value).apply()

    // Kustomisasi Shortcut pada Tombol Tengah Navigasi Bawah
    var customQuickShortcuts: Set<String>
        get() = prefs.getStringSet(
            "quick_shortcuts",
            setOf("add_customer", "recharge_plan", "deposit_balance", "generate_voucher", "online_users", "audit_qris")
        ) ?: setOf("add_customer", "recharge_plan", "deposit_balance", "generate_voucher", "online_users", "audit_qris")
        set(value) = prefs.edit().putStringSet("quick_shortcuts", value).apply()

    fun logout() {
        prefs.edit().remove("admin_token").apply()
    }

    suspend fun request(
        action: String,
        params: Map<String, Any?> = emptyMap()
    ): ApiResult = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val endpoint = "$baseUrl/?_route=apiv1/$action"
            val url = URL(endpoint)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15000
                readTimeout = 20000
                doInput = true
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                if (token.isNotEmpty()) {
                    setRequestProperty("Authorization", "Bearer $token")
                }
            }

            val jsonBody = JSONObject()
            for ((k, v) in params) {
                if (v != null) {
                    when (v) {
                        is Map<*, *> -> jsonBody.put(k, JSONObject(v))
                        is List<*> -> jsonBody.put(k, JSONArray(v))
                        else -> jsonBody.put(k, v)
                    }
                }
            }

            OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val rawResponse = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use {
                it.readText()
            }

            val json = JSONObject(rawResponse)
            val status = json.optBoolean("status", false)
            val message = json.optString("message", "Selesai")
            val rawData = json.opt("data")

            val dataObj = rawData as? JSONObject
            val dataArr = rawData as? JSONArray

            ApiResult(
                status = status,
                message = message,
                data = dataObj,
                dataArray = dataArr,
                rawData = rawData,
                code = responseCode
            )
        } catch (e: Exception) {
            ApiResult(
                status = false,
                message = "Gangguan koneksi ke server: ${e.localizedMessage ?: "Timeout"}",
                code = 500
            )
        } finally {
            conn?.disconnect()
        }
    }
}

fun formatRupiah(amount: Double, prefix: String = ApiClient.currencyCode): String {
    val formatted = String.format("%,.0f", amount).replace(',', '.')
    return "$prefix $formatted"
}
