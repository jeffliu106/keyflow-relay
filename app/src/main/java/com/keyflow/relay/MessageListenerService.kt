package com.keyflow.relay

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

/**
 * 监听 Samsung Messages / Google Messages 的通知，用于捕获 RCS 消息。
 * （Android 不把 RCS 广播给第三方 App，但 Messages 会发通知）
 *
 * 隐私：虽然权限允许读所有 App 通知，本服务只处理 MESSAGING_PACKAGES 白名单内的包，
 *      其它通知直接 return，不读 extras、不打日志、不转发。
 */
class MessageListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName ?: return
        if (pkg !in MESSAGING_PACKAGES) return

        val settings = Settings(applicationContext)
        if (!settings.serviceEnabled) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = (bigText ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            ?.trim()
            .orEmpty()

        if (title.isEmpty() || text.isEmpty()) return

        // 摘要通知（"2 new messages from John"）→ 丢
        if (looksLikeSummary(text)) {
            Logger.log(applicationContext, "SKIP notif from=$title reason=summary")
            return
        }

        // 结构化长消息被截断（如 AI 热线 lead 通知），让 SMS 路径兜底
        if (isLikelyTruncatedStructured(text)) {
            Logger.log(applicationContext, "SKIP notif from=$title reason=truncated")
            return
        }

        scope.launch {
            MessageHandler.handleIncoming(
                context = applicationContext,
                sender = title,
                body = text,
                receivedAt = Date(sbn.postTime),
                source = "notif"
            )
        }
    }

    /**
     * "2 new messages" / "N unread" / "未读 N 条" 等摘要格式。
     * 短文本 + 含数字 + 关键词即视为摘要。
     */
    private fun looksLikeSummary(text: String): Boolean {
        if (text.length > 60) return false
        val lower = text.lowercase()
        val hasSummaryWord = listOf(
            "new message", "unread", "new messages",
            "条新消息", "条未读", "未读消息"
        ).any { lower.contains(it) || text.contains(it) }
        val hasDigit = text.any { it.isDigit() }
        return hasSummaryWord && hasDigit
    }

    /**
     * 启发式：正文含结构化标记（Summary/Details/Conversation Summary）
     * AND 结尾不是正常句末标点 → 很可能被通知渲染截断。
     *
     * 这类消息几乎总有对应的 SMS 广播（AI 热线走运营商发），
     * 主动放弃这条通知 path 让 SMS 拿到完整正文。
     *
     * 副作用：如果真的只有 RCS 没有 SMS（罕见），会丢这条。
     *        权衡后认为可接受——结构化模板消息几乎都走 SMS。
     */
    private fun isLikelyTruncatedStructured(text: String): Boolean {
        val trimmed = text.trimEnd()
        if (trimmed.isEmpty()) return false
        val last = trimmed.last()
        val properEnd = last in PROPER_ENDINGS
        if (properEnd) return false

        val hasStructureMarker = STRUCTURE_MARKERS.any {
            text.contains(it, ignoreCase = true)
        }
        return hasStructureMarker
    }

    companion object {
        /** 已知消息 App 包名白名单 */
        private val MESSAGING_PACKAGES = setOf(
            "com.samsung.android.messaging",     // Samsung Messages (Flip 7 默认)
            "com.google.android.apps.messaging"  // Google Messages
        )

        private val PROPER_ENDINGS = setOf(
            '.', '!', '?', ')', '"', '\'', '。', '！', '？', '…', ']', '】'
        )

        private val STRUCTURE_MARKERS = listOf(
            "Summary:", "Details:", "Conversation Summary",
            "对话摘要", "详情："
        )
    }
}
