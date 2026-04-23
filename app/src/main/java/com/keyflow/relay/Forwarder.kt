package com.keyflow.relay

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Forwarder {
    data class Result(val success: Boolean, val statusCode: Int, val errorMessage: String?)

    /**
     * POST 到 KeyFlow /api/leads
     */
    suspend fun postLead(
        serverUrl: String,
        sender: String,
        body: String,
        sourceChannel: String,
        receivedAt: Date
    ): Result = withContext(Dispatchers.IO) {
        val endpoint = "${serverUrl.trimEnd('/')}/api/leads"
        val payload = JSONObject().apply {
            put("sourceChannel", sourceChannel)
            put("sourceSender", sender)
            put("sourceRaw", body)
            put("receivedAt", iso8601(receivedAt))
        }
        postJson(endpoint, payload)
    }

    /**
     * 用 GET /api/settings 做连通性探测。选它是因为：
     * - 接口必然存在
     * - 无副作用（不会在 Inbox 产生测试数据）
     * - 返回 200 即证明 KeyFlow 后端在跑
     */
    suspend fun testConnection(serverUrl: String): Result = withContext(Dispatchers.IO) {
        val endpoint = "${serverUrl.trimEnd('/')}/api/settings"
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            val code = connection.responseCode
            if (code in 200..299) Result(true, code, null)
            else Result(false, code, "HTTP $code")
        } catch (e: Exception) {
            Result(false, -1, describe(e))
        } finally {
            connection?.disconnect()
        }
    }

    private suspend fun postJson(endpoint: String, payload: JSONObject): Result = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("User-Agent", USER_AGENT)
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code in 200..299) Result(true, code, null)
            else Result(false, code, "HTTP $code")
        } catch (e: Exception) {
            Result(false, -1, describe(e))
        } finally {
            connection?.disconnect()
        }
    }

    private fun describe(e: Exception): String {
        val kind = e.javaClass.simpleName
        val msg = e.message ?: "(no message)"
        return "$kind: $msg"
    }

    private fun iso8601(date: Date): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(date)
    }

    private const val USER_AGENT = "KeyFlowRelay/1.0"
}
