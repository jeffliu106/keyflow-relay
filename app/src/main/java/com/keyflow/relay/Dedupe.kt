package com.keyflow.relay

/**
 * 内存 LRU 去重：防止 SMS_RECEIVED 广播和 NotificationListener 同一条消息被处理两次。
 *
 * Key = sender + body 前 80 字符 + 30 秒时段桶
 * 30 秒桶意味着：短时间内同发件人 + 同内容开头 最多转一次。
 */
object Dedupe {
    private const val MAX_ENTRIES = 200
    private const val TIME_BUCKET_MS = 30_000L   // 30 秒

    // access-order LRU; 最近用过的保留，最老的被驱逐
    private val map = object : LinkedHashMap<String, Long>(256, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<String, Long>?): Boolean {
            return size > MAX_ENTRIES
        }
    }
    private val lock = Any()

    fun keyOf(sender: String, body: String, timeMillis: Long): String {
        val bodyPrefix = body.take(80).replace(Regex("\\s+"), " ").trim()
        val bucket = timeMillis / TIME_BUCKET_MS
        return "$sender|$bodyPrefix|$bucket"
    }

    /** true=新消息（调用方继续）；false=重复，跳过 */
    fun claim(key: String): Boolean {
        synchronized(lock) {
            if (map.containsKey(key)) {
                // 触达一次以更新 LRU 顺序
                map[key]
                return false
            }
            map[key] = System.currentTimeMillis()
            return true
        }
    }
}
