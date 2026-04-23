package com.keyflow.relay

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.keyflow.relay.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var settings: Settings
    private lateinit var logAdapter: LogAdapter

    private val requestPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val sms = grants[Manifest.permission.RECEIVE_SMS] == true &&
            grants[Manifest.permission.READ_SMS] == true
        if (!sms) {
            toast(getString(R.string.msg_perm_required))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        settings = Settings(this)

        // 初始化表单
        binding.etServerUrl.setText(settings.serverUrl)
        binding.etWhitelist.setText(settings.whitelistText)
        binding.etBlacklist.setText(settings.blacklistText)
        binding.swService.isChecked = settings.serviceEnabled

        // 日志列表
        logAdapter = LogAdapter(emptyList())
        binding.rvLogs.layoutManager = LinearLayoutManager(this)
        binding.rvLogs.adapter = logAdapter
        refreshLogs()

        // 按钮绑定
        binding.btnSave.setOnClickListener { onSave() }
        binding.btnTest.setOnClickListener { onTestConnection() }
        binding.btnClearLogs.setOnClickListener {
            Logger.clear(this)
            refreshLogs()
        }
        binding.btnBatterySettings.setOnClickListener { openBatteryOptimizationSettings() }
        binding.btnNotifListener.setOnClickListener { openNotificationAccessSettings() }

        binding.swService.setOnCheckedChangeListener { _, checked ->
            settings.serviceEnabled = checked
            if (checked) {
                ensurePermissionsAndStart()
            } else {
                ForwardingService.stop(this)
                toast("服务已关闭")
            }
        }

        requestPermissionsIfNeeded()
        updateNotifListenerUi()
    }

    override fun onResume() {
        super.onResume()
        refreshLogs()
        updateNotifListenerUi()
    }

    // ---- Actions ----

    private fun onSave() {
        settings.serverUrl = binding.etServerUrl.text?.toString()?.trim().orEmpty()
        settings.whitelistText = binding.etWhitelist.text?.toString().orEmpty()
        settings.blacklistText = binding.etBlacklist.text?.toString().orEmpty()
        toast(getString(R.string.msg_saved))
    }

    private fun onTestConnection() {
        val url = binding.etServerUrl.text?.toString()?.trim().orEmpty()
        if (url.isEmpty()) {
            toast("请先填写服务器地址")
            return
        }
        toast(getString(R.string.msg_testing))
        lifecycleScope.launch {
            val result = Forwarder.testConnection(url)
            if (result.success) {
                toast(getString(R.string.msg_test_ok))
            } else {
                val detail = result.errorMessage ?: "unknown"
                toast(getString(R.string.msg_test_fail_prefix) + detail)
            }
        }
    }

    private fun ensurePermissionsAndStart() {
        if (!hasSmsPermissions()) {
            requestPermissionsIfNeeded()
            toast(getString(R.string.msg_perm_required))
            return
        }
        ForwardingService.start(this)
        toast("服务已启动")
    }

    private fun requestPermissionsIfNeeded() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.RECEIVE_SMS
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)
            != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.READ_SMS
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) needed += Manifest.permission.POST_NOTIFICATIONS
        }
        if (needed.isNotEmpty()) requestPermissions.launch(needed.toTypedArray())
    }

    private fun hasSmsPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED
    }

    /**
     * 检查 MessageListenerService 是否被系统 enable（通知访问权限开了）。
     * 原理：enabled_notification_listeners 里是冒号分隔的 "pkg/class" 列表。
     */
    private fun isNotificationListenerEnabled(): Boolean {
        val flat = AndroidSettings.Secure.getString(
            contentResolver, "enabled_notification_listeners"
        ) ?: return false
        val me = ComponentName(this, MessageListenerService::class.java).flattenToString()
        val meShort = ComponentName(this, MessageListenerService::class.java).flattenToShortString()
        return flat.split(':').any { it == me || it == meShort }
    }

    private fun updateNotifListenerUi() {
        val enabled = isNotificationListenerEnabled()
        binding.tvNotifListenerStatus.setText(
            if (enabled) R.string.label_notif_listener_on
            else R.string.label_notif_listener_off
        )
        binding.btnNotifListener.setText(
            if (enabled) R.string.btn_notif_listener_revoke
            else R.string.btn_notif_listener_grant
        )
    }

    private fun openNotificationAccessSettings() {
        try {
            startActivity(Intent(AndroidSettings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            toast("无法打开通知访问设置，请手动：设置 → 通知 → 通知访问权限")
        }
    }

    /**
     * 跳系统设置让用户把本 App 从电池优化名单里移出去。
     * 三星 One UI 另有 "睡眠应用" / "深度休眠应用" 列表，需要用户自己再进
     * 设置 → 应用 → KeyFlow Relay → 电池 设置。
     */
    private fun openBatteryOptimizationSettings() {
        try {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (pm.isIgnoringBatteryOptimizations(packageName)) {
                toast("已在白名单")
                return
            }
            val intent = Intent(
                AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
            ).apply { data = Uri.parse("package:$packageName") }
            startActivity(intent)
        } catch (_: Exception) {
            try {
                startActivity(
                    Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.parse("package:$packageName"))
                )
            } catch (_: Exception) {
                toast("无法打开系统设置")
            }
        }
    }

    // ---- Helpers ----

    private fun refreshLogs() {
        val lines = Logger.recent(this, 50)
        logAdapter.submit(lines)
        binding.tvNoLogs.visibility =
            if (lines.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
