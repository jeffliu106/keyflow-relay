package com.keyflow.relay

import android.content.Context
import java.util.Date

/**
 * 接收 SMS 和 notification 两条入口共用的消息处理管线。
 *
 * 流程：serviceEnabled 门 → 去重 → Filter → Forwarder
 */
object MessageHandler {

    /**
     * @param source 日志里区分来源的短标签: "sms" | "notif"
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

        // 去重（30 秒窗）
        val key = Dedupe.keyOf(sender, body, receivedAt.time)
        if (!Dedupe.claim(key)) {
            Logger.log(context, "SKIP $source from=$sender reason=dup")
            return
        }

        val decision = Filter.decide(
            sender = sender,
            body = body,
            whitelist = settings.parseWhitelist(),
            blacklist = settings.parseBlacklist()
        )

        if (!decision.shouldForward) {
            Logger.log(context, "SKIP $source from=$sender reason=${decision.reason}")
            return
        }

        val result = Forwarder.postLead(
            serverUrl = settings.serverUrl,
            sender = sender,
            body = body,
            sourceChannel = decision.sourceChannel,
            receivedAt = receivedAt
        )
        val tag = if (result.success) "OK" else "FAIL"
        val err = result.errorMessage ?: "-"
        Logger.log(
            context,
            "$tag $source from=$sender channel=${decision.sourceChannel} " +
                "status=${result.statusCode} err=$err"
        )
    }
}
