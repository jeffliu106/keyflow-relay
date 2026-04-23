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
     * 白名单解析规则：每行一个条目，格式 `<pattern> [sourceChannel]`。
     * - 若最后一个 token 是已知的 sourceChannel（ai_hotline/colleague/...），
     *   则前面所有 token 拼起来作为 pattern（支持 "Liu Zheng ai_hotline" 这种多词联系人名）
     * - 否则整行作为 pattern，channel 取默认 (repeat_customer)
     * - 纯数字 pattern 会被归一化（只保留数字），用于号码比对
     * - 非数字 pattern（联系人名 / "AMAZON" 这种字母发件人）保留原文，精确匹配
     * - 空行、# 开头行忽略
     */
    fun parseWhitelist(): List<WhitelistEntry> {
        return whitelistText.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val tokens = line.split(Regex("\\s+"))
                if (tokens.isEmpty()) return@mapNotNull null

                val lastToken = tokens.last()
                val (rawPattern, channel) =
                    if (tokens.size >= 2 && lastToken in KNOWN_CHANNELS) {
                        tokens.dropLast(1).joinToString(" ") to lastToken
                    } else {
                        line to DEFAULT_WHITELIST_CHANNEL
                    }

                if (rawPattern.isEmpty()) return@mapNotNull null

                // 含数字 → 归一化为纯数字；不含数字 → 保留原文
                val normalized = if (rawPattern.any { it.isDigit() }) {
                    normalizePhone(rawPattern)
                } else {
                    rawPattern
                }

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

        val KNOWN_CHANNELS = setOf(
            "ai_hotline",
            "colleague",
            "repeat_customer",
            "manual",
            "unknown"
        )

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

        /** 只保留数字 */
        fun normalizePhone(raw: String): String {
            return raw.replace(Regex("[^\\d]"), "")
        }
    }
}
