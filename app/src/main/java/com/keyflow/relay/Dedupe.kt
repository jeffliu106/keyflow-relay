package com.keyflow.relay

/**
 * 双键 LRU 去重：防止 SMS_RECEIVED 广播和 NotificationListener 同一条消息被处理两次。
 *
 * 主键 primary  = sender(归一化) + body 前 80 字符 + 30 秒时段
 *   - 严格：同一 sender 同一内容的重复发送才命中
 *   - 不同 sender 表示（号码 vs 联系人名）→ 不同 key → 走辅键
 *
 * 辅键 secondary = "*"(通配 sender) + body 前 80 字符 + 30 秒时段
 *   - 只在正文足够长（≥ 50 字符）时启用
 *   - 用途：SMS 记 "+17789070744" / 通知记 "AI助理"，但 body 相同 → 辅键命中
 *   - 短消息（"ok"、"hi"）不启用辅键，避免误把两个不同发件人的同文本当成一条
 */
object Dedupe {
    private const val MAX_ENTRIES = 300
    private const val TIME_BUCKET_MS = 30_000L
    private const val BODY_ONLY_MIN_LEN = 50

    data class Keys(val primary: String, val secondary: String?)

    private val map = object : LinkedHashMap<String, Long>(512, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Long>?): Boolean {
            return size > MAX_ENTRIES
        }
    }
    private val lock = Any()

    fun keysOf(sender: String, body: String, timeMillis: Long): Keys {
        val bodyPrefix = body.take(80).replace(Regex("\\s+"), " ").trim()
        val bucket = timeMillis / TIME_BUCKET_MS
        val normSender = normalizeSender(sender)
        val primary = "$normSender|$bodyPrefix|$bucket"
        val secondary = if (bodyPrefix.length >= BODY_ONLY_MIN_LEN) {
            "*|$bodyPrefix|$bucket"
        } else null
        return Keys(primary, secondary)
    }

    /** true = 新消息，调用方继续处理；false = 重复，跳过。 */
    fun claim(keys: Keys): Boolean {
        synchronized(lock) {
            val hit = map.containsKey(keys.primary) ||
                (keys.secondary != null && map.containsKey(keys.secondary))
            if (hit) {
                // Touch for LRU
                map[keys.primary]
                keys.secondary?.let { map[it] }
                return false
            }
            val now = System.currentTimeMillis()
            map[keys.primary] = now
            keys.secondary?.let { map[it] = now }
            return true
        }
    }

    /** 有数字 → 只留数字；无数字 → lowercase+trim */
    private fun normalizeSender(sender: String): String {
        val s = sender.trim()
        return if (s.any { it.isDigit() }) {
            s.replace(Regex("[^\\d]"), "")
        } else {
            s.lowercase()
        }
    }
}
