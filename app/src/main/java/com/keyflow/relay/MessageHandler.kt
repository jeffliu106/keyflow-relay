package com.keyflow.relay

import android.content.Context
import java.util.Date

/**
 * SMS 和 Notification 两条入口共用的处理管线。
 *
 * 流程：
 *   1) serviceEnabled 门
 *   2) 联系人名 → 号码解析（v1.2.0+，需 READ_CONTACTS）
 *   3) 去重（Dedupe 双键策略）
 *   4) Filter 白/黑名单
 *   5) Forwarder HTTP POST
 */
object MessageHandler {

    /**
     * @param source 日志区分：'sms' | 'notif'
     */
    suspend fun handleIncoming(
        context: Context,
        sender: String,
        body: String,
        receivedAt: Date,
        source: String
    ) {
        val settings = Settings(context)
        if (!settings.serviceEnabled) return

        // sender 无数字 → 尝试按联系人名解析为号码（让 SMS 和通知路径收敛到同一 sender）
        val resolved = maybeResolveContactName(context, sender)
        val effectiveSender = resolved ?: sender
        if (resolved != null && resolved != sender) {
            Logger.log(context, "RESOLVE \"$sender\" -> $resolved")
        }

        // 去重
        val keys = Dedupe.keysOf(effectiveSender, body, receivedAt.time)
        if (!Dedupe.claim(keys)) {
            Logger.log(context, "SKIP $source from=$effectiveSender reason=dup")
            return
        }

        // Filter
        val decision = Filter.decide(
            sender = effectiveSender,
            body = body,
            whitelist = settings.parseWhitelist(),
            blacklist = settings.parseBlacklist()
        )
        if (!decision.shouldForward) {
            Logger.log(context, "SKIP $source from=$effectiveSender reason=${decision.reason}")
            return
        }

        // Forward
        val result = Forwarder.postLead(
            serverUrl = settings.serverUrl,
            sender = effectiveSender,
            body = body,
            sourceChannel = decision.sourceChannel,
            receivedAt = receivedAt
        )
        val tag = if (result.success) "OK" else "FAIL"
        val err = result.errorMessage ?: "-"
        Logger.log(
            context,
            "$tag $source from=$effectiveSender channel=${decision.sourceChannel} " +
                "status=${result.statusCode} err=$err"
        )
    }

    /** sender 包含数字视为号码，不查通讯录；否则尝试联系人解析 */
    private fun maybeResolveContactName(context: Context, sender: String): String? {
        if (sender.any { it.isDigit() }) return null
        return Contacts.resolvePhoneByDisplayName(context, sender)
    }
}
