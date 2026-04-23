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
 *
 * 原理：Android 不把 RCS 广播到第三方 App（SMS_RECEIVED 只对运营商短信），
 * 但 Messages App 会给 RCS 消息弹通知。我们读通知的 title 作为发件人、
 * text (优先 BIG_TEXT) 作为正文，喂进和 SMS 一样的处理管线。
 *
 * 权限：BIND_NOTIFICATION_LISTENER_SERVICE 是 signature-level，用户必须在
 * 系统设置"通知访问权限"里手动打勾，不能通过 runtime 弹窗拿。
 *
 * 隐私：虽然权限允许读所有 App 的通知，本服务只处理 MESSAGING_PACKAGES 白名单
 * 里的包，其它通知直接 return，不读、不存、不转发。
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
        // 展开通知优先 (BIG_TEXT)，降级到普通 text
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = (bigText ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString())
            ?.trim()
            .orEmpty()

        if (title.isEmpty() || text.isEmpty()) return

        // 过滤掉通知摘要（比如 "2 new messages from John"）
        // 这些没有实际消息体，不转发；真正的消息通知在 EXTRA_TEXT 里是具体内容
        if (looksLikeSummary(text)) {
            Logger.log(applicationContext, "SKIP notif from=$title reason=summary")
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
     * 简单判定：通知文本非常短 + 包含数字 + 关键词
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

    companion object {
        /** 已知消息 App 包名白名单 — 只监听这些 */
        private val MESSAGING_PACKAGES = setOf(
            "com.samsung.android.messaging",     // Samsung Messages (Flip 7 默认)
            "com.google.android.apps.messaging"  // Google Messages
        )
    }
}
