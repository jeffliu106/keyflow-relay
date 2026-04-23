package com.keyflow.relay

import android.app.Application
import android.content.Context
import androidx.core.content.edit

class RelayApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // 一次性白名单迁移（v1.2.0 起）：把已知的 AI 热线号码追加进用户的白名单
        runMigrations()

        // 若用户之前已开启过服务，冷启动时自动恢复
        val settings = Settings(this)
        if (settings.serviceEnabled) {
            ForwardingService.start(this)
        }
    }

    /**
     * 每个 migration key 跑一次就打标，下次启动直接跳过。
     * 卸载重装会清 SharedPreferences 所以会重跑——正确行为（新装需要同样的默认值）。
     */
    private fun runMigrations() {
        val prefs = getSharedPreferences("keyflow_relay_migrations", Context.MODE_PRIVATE)

        if (!prefs.getBoolean(MIGRATION_V1_2_WHITELIST, false)) {
            addAiHotlineToWhitelist()
            prefs.edit { putBoolean(MIGRATION_V1_2_WHITELIST, true) }
        }
    }

    private fun addAiHotlineToWhitelist() {
        val settings = Settings(this)
        val current = settings.whitelistText
        // 已经包含该号码就不重复追加
        if (current.contains("17789070744")) return
        val addition = "+17789070744 ai_hotline"
        settings.whitelistText = if (current.isBlank()) {
            addition
        } else {
            "${current.trimEnd()}\n$addition"
        }
    }

    companion object {
        private const val MIGRATION_V1_2_WHITELIST = "mig_v1_2_ai_hotline_whitelist"
    }
}
