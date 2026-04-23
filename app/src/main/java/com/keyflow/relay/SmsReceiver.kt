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

/**
 * 运营商普通 SMS 入口。
 * RCS / Chat features 走不到这里（见 MessageListenerService）。
 */
class SmsReceiver : BroadcastReceiver() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // 多段短信按发件人合并
        val grouped = messages.groupBy { it.originatingAddress ?: "" }

        for ((sender, parts) in grouped) {
            if (sender.isBlank()) continue
            val body = parts.joinToString("") { it.messageBody ?: "" }
            val receivedAt = Date(parts.first().timestampMillis)

            scope.launch {
                MessageHandler.handleIncoming(
                    context = context.applicationContext,
                    sender = sender,
                    body = body,
                    receivedAt = receivedAt,
                    source = "sms"
                )
            }
        }
    }
}
