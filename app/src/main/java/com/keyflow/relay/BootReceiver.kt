package com.keyflow.relay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 开机自启：若用户之前开启了服务，重启手机后也跟着起来。
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED
        ) return

        val settings = Settings(context)
        if (settings.serviceEnabled) {
            ForwardingService.start(context)
            Logger.log(context, "BOOT serviceEnabled=true, service started")
        }
    }
}
