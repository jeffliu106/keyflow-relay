package com.keyflow.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Date

class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val settings = Settings(context)
        if (!settings.serviceEnabled) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // 多段短信按发件人合并为一条
        val grouped = messages.groupBy { it.originatingAddress ?: "" }

        for ((sender, parts) in grouped) {
            if (sender.isBlank()) continue
            val body = parts.joinToString("") { it.messageBody ?: "" }
            val receivedAt = Date(parts.first().timestampMillis)
            handleOne(context, settings, sender, body, receivedAt)
        }
    }

    private fun handleOne(
        context: Context,
        settings: Settings,
        sender: String,
        body: String,
        receivedAt: Date
    ) {
        val whitelist = settings.parseWhitelist()
        val blacklist = settings.parseBlacklist()
        val decision = Filter.decide(sender, body, whitelist, blacklist)

        if (!decision.shouldForward) {
            Logger.log(context, "SKIP from=$sender reason=${decision.reason}")
            return
        }

        // 异步转发，不阻塞广播
        scope.launch {
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
                "$tag from=$sender channel=${decision.sourceChannel} status=${result.statusCode} err=$err"
            )
        }
    }
}
