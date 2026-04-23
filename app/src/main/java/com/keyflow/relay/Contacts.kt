package com.keyflow.relay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * 联系人查询帮助类。
 * 用途：通知路径收到的 sender 是联系人显示名（如 "AI助理"）而不是号码时，
 *      把它解析回手机里保存的电话号码，以便：
 *        1) 让 Dedup 认出是同一条消息（SMS path 的 sender 是号码）
 *        2) 白名单写号码就够了，不必同时写名字
 *        3) 上报给 KeyFlow 的 sourceSender 是真实号码，客户去重更准
 *
 * 权限：READ_CONTACTS（v1.2.0 起）。未授权时所有查询返回 null，不影响主流程。
 */
object Contacts {

    fun hasPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * 按联系人显示名精确查询（区分大小写）首个电话号码。
     * 未授权 / 未找到 / 查询异常 均返回 null。
     * 返回的号码保留通讯录里原始格式（可能带空格、括号、+1 等）；下游需要归一化的自行 normalizePhone。
     */
    fun resolvePhoneByDisplayName(context: Context, displayName: String): String? {
        val name = displayName.trim()
        if (name.isEmpty()) return null
        if (!hasPermission(context)) return null
        return try {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} = ?",
                arrayOf(name),
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)?.takeIf { it.isNotBlank() }
                } else null
            }
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }
}
