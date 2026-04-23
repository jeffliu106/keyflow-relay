package com.keyflow.relay

data class FilterResult(
    val shouldForward: Boolean,
    val sourceChannel: String,   // "ai_hotline" | "colleague" | "repeat_customer" | "unknown"
    val reason: String            // 用于日志
)

object Filter {
    fun decide(
        sender: String,
        body: String,
        whitelist: List<WhitelistEntry>,
        blacklist: List<String>
    ): FilterResult {
        val normalizedSender = Settings.normalizePhone(sender)

        // 1. 白名单命中立即放行
        for (entry in whitelist) {
            if (matchesPattern(normalizedSender, sender, entry.pattern)) {
                return FilterResult(
                    shouldForward = true,
                    sourceChannel = entry.sourceChannel,
                    reason = "whitelist:${entry.pattern}"
                )
            }
        }

        // 2. 黑名单命中丢弃
        val haystack = "$sender $body".lowercase()
        for (keyword in blacklist) {
            if (haystack.contains(keyword)) {
                return FilterResult(
                    shouldForward = false,
                    sourceChannel = "unknown",
                    reason = "blacklist:$keyword"
                )
            }
        }

        // 3. 纯数字短号（3-6 位，商家推广常见格式）默认丢
        if (sender.isNotEmpty() && sender.length in 3..6 && sender.all { it.isDigit() }) {
            return FilterResult(
                shouldForward = false,
                sourceChannel = "unknown",
                reason = "short_code"
            )
        }

        // 4. 其它"放行但标 unknown"
        return FilterResult(
            shouldForward = true,
            sourceChannel = "unknown",
            reason = "default_allow"
        )
    }

    private fun matchesPattern(normalizedSender: String, rawSender: String, pattern: String): Boolean {
        if (pattern.isEmpty()) return false
        // 纯数字 pattern: 按数字末尾/完全匹配比（宽松匹配前缀 / 后缀）
        if (pattern.all { it.isDigit() }) {
            if (normalizedSender.isEmpty()) return false
            return normalizedSender == pattern ||
                normalizedSender.endsWith(pattern) ||
                pattern.endsWith(normalizedSender)
        }
        // 非数字 pattern（比如 "AMAZON"）: 和原始发件人不区分大小写相等
        return rawSender.equals(pattern, ignoreCase = true)
    }
}
