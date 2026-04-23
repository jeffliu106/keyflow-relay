package com.keyflow.relay

import android.content.Context
import androidx.core.content.edit

data class WhitelistEntry(val pattern: String, val sourceChannel: String)

class Settings(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL) ?: DEFAULT_SERVER_URL
        set(value) = prefs.edit { putString(KEY_SERVER_URL, value.trim()) }

    var whitelistText: String
        get() = prefs.getString(KEY_WHITELIST, "") ?: ""
        set(value) = prefs.edit { putString(KEY_WHITELIST, value) }

    var blacklistText: String
        get() = prefs.getString(KEY_BLACKLIST, DEFAULT_BLACKLIST) ?: DEFAULT_BLACKLIST
        set(value) = prefs.edit { putString(KEY_BLACKLIST, value) }

    var serviceEnabled: Boolean
        get() = prefs.getBoolean(KEY_SERVICE_ENABLED, false)
        set(value) = prefs.edit { putBoolean(KEY_SERVICE_ENABLED, value) }

    /**
     * 每行一条规则，格式: `<号码或发件人> [sourceChannel]`
     * 空白行、# 开头的注释行忽略。
     */
    fun parseWhitelist(): List<WhitelistEntry> {
        return whitelistText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                val rawPattern = parts.getOrNull(0) ?: return@mapNotNull null
                val channel = parts.getOrNull(1)?.trim()?.ifEmpty { null } ?: DEFAULT_WHITELIST_CHANNEL
                // 号码用归一化; 非数字发件人（如 "AMAZON"）保留原文
                val normalized = if (rawPattern.any { it.isDigit() }) normalizePhone(rawPattern) else rawPattern
                WhitelistEntry(normalized, channel)
            }
            .toList()
    }

    fun parseBlacklist(): List<String> {
        return blacklistText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.lowercase() }
            .toList()
    }

    companion object {
        private const val PREFS_NAME = "keyflow_relay"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_WHITELIST = "whitelist"
        private const val KEY_BLACKLIST = "blacklist"
        private const val KEY_SERVICE_ENABLED = "service_enabled"

        const val DEFAULT_SERVER_URL = "http://192.168.86.42:3001"
        const val DEFAULT_WHITELIST_CHANNEL = "repeat_customer"

        val DEFAULT_BLACKLIST = """
            # 默认黑名单 — 发件人或内容命中任一条即不转发（不区分大小写）
            verification code
            verify
            OTP
            passcode
            Amazon
            FedEx
            UPS
            Canada Post
            Rogers
            Telus
            Bell
            Presto
            Compass
            【
        """.trimIndent()

        /** 只保留数字，用于号码比对 */
        fun normalizePhone(raw: String): String {
            return raw.replace(Regex("[^\\d]"), "")
        }
    }
}
