package com.keyflow.relay

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 简单的滚动文件日志。写入 app-files 目录下的 relay.log。
 * 超过 MAX_LINES 就按尾部保留方式截掉前面的老记录。
 */
object Logger {
    private const val FILE = "relay.log"
    private const val MAX_LINES = 500
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun log(context: Context, line: String) {
        try {
            val file = File(context.filesDir, FILE)
            val entry = "${fmt.format(Date())} $line\n"
            file.appendText(entry, Charsets.UTF_8)

            // Trim if too long.
            val lines = file.readLines(Charsets.UTF_8)
            if (lines.size > MAX_LINES) {
                val keep = lines.takeLast(MAX_LINES)
                file.writeText(keep.joinToString("\n") + "\n", Charsets.UTF_8)
            }
        } catch (_: Exception) {
            // Swallow — logger must never crash the app.
        }
    }

    /** 最近 n 条（最新在前） */
    fun recent(context: Context, n: Int = 20): List<String> {
        return try {
            val file = File(context.filesDir, FILE)
            if (!file.exists()) return emptyList()
            file.readLines(Charsets.UTF_8).takeLast(n).asReversed()
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE).delete()
        } catch (_: Exception) {
        }
    }
}
