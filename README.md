# KeyFlow Relay

Minimal Android SMS forwarder for the KeyFlow locksmith management system.

## 功能

- 后台监听收到的短信（SMS + RCS/Chat）
- 按白名单直通 + 黑名单过滤
- 命中规则的消息 POST 到 KeyFlow `/api/leads`
- 极简前台服务（静默图标）保活
- 开机自启

### 两条入口

| 入口 | 捕获 | 触发机制 |
|---|---|---|
| `SmsReceiver` | 运营商 SMS | `SMS_RECEIVED` 广播 |
| `MessageListenerService` | **RCS / Chat features / 任何 Messages 通知** | Notification Listener |

两个入口都走同一个 `MessageHandler` 管线（去重 → 白/黑名单 → HTTP POST）。
`Dedupe` 用 sender + body 前缀 + 30 秒窗去重，防止 SMS 和通知同时触发导致重复转发。

**本批次不做**：AI 解析、重试队列、App 锁、历史回溯。批次 ③ 再加。

## 构建

1. 用 Android Studio 打开本目录
2. 等 Gradle sync 完成（首次可能 5-10 分钟下载依赖）
3. `Build → Build Bundle(s) / APK(s) → Build APK(s)`
4. 产物在 `app/build/outputs/apk/debug/app-debug.apk`

## 安装到手机（三星 Galaxy Z Flip 7）

### 1. APK 传到手机

无线方式任选：
- Quick Share / AirDrop
- 邮件附件发给自己
- U 盘 / Google Drive

### 2. 装

点开 APK，Android 会弹"来自未知来源"警告，点"允许"或"仍然安装"。

### 3. 配置

1. 打开 **KeyFlow Relay** App
2. 授予 SMS 和通知权限
3. 确认服务器地址（默认 `http://192.168.86.42:3001` 是开发环境；正式用改成 `:3000`）
4. 点"测试连接"，应返回 `✅ HTTP 200`
5. 填白名单（一行一个，最后一个 token 若是 `ai_hotline / colleague / repeat_customer / manual / unknown` 会被识别为来源标签）：
   ```
   +16045550101 ai_hotline
   6049876543 colleague
   Liu Zheng ai_hotline
   ```
   - 数字条目会做归一化（去掉 +/空格/括号），后缀匹配
   - 名字条目（通讯录联系人名）精确匹配通知 title，不区分大小写
6. 确认黑名单（已预填常见 OTP / 物流 / 运营商关键词）
7. 打开"转发服务开关"
8. 点"去打开'电池未受限'"按系统引导把 App 加到白名单
9. **捕获 RCS 消息必做**：点"通知监听（RCS）"那一行的"去授权"按钮 → 在系统"通知访问权限"里找到 KeyFlow Relay 打勾 → 回到 App，状态变为 "✅ 已启用"

### 4. 三星 Flip 7 额外一步（关键！）

Samsung One UI 有独立的"睡眠应用"机制，不让 App 在后台待太久。需要：

1. **设置 → 应用 → KeyFlow Relay → 电池** → 选 **"不受限制"**
2. **设置 → 电池和设备维护 → 电池 → 后台使用限制 → 休眠应用** → 确保 KeyFlow Relay **不在**这个列表里
3. **设置 → 电池和设备维护 → 电池 → 后台使用限制 → 深度休眠应用** → 同上

（上面的菜单路径在不同 One UI 版本会略有差异，核心是找到"电池优化 / 睡眠应用 / 休眠应用"三个名单，把 KeyFlow Relay 排除）

## 验收

- 通知栏有个灰色小图标（KeyFlow Relay Running）
- 主页面"通知监听（RCS）"显示 ✅ 已启用
- 从另一部手机给白名单里的号码发一条**运营商 SMS**（关掉发件人的 RCS/Chat）→ 日志出 `OK sms from=...` → Inbox 出新 Lead
- 再发一条 **RCS 消息**（对方开着 Chat features）→ 日志出 `OK notif from=...` → Inbox 出新 Lead
- 发一条带 "verification code" 的消息 → 日志 `SKIP ... reason=blacklist:verification code`，不进 Inbox
- 同一条消息不会出现两条日志（SMS + 通知被 Dedupe 折叠为一次转发）
- 重启手机：图标和服务自动回来；通知监听权限保持

## 代码结构

```
app/src/main/
├── AndroidManifest.xml
├── java/com/keyflow/relay/
│   ├── RelayApp.kt                  Application 入口
│   ├── MainActivity.kt              唯一的 UI 页面（设置+日志）
│   ├── SmsReceiver.kt               运营商 SMS 广播接收器
│   ├── MessageListenerService.kt    通知监听（RCS/Chat）
│   ├── MessageHandler.kt            两条入口共用的处理管线
│   ├── Dedupe.kt                    LRU 去重（防 SMS+通知双触发）
│   ├── ForwardingService.kt         前台保活服务
│   ├── BootReceiver.kt              开机自启
│   ├── Filter.kt                    白/黑名单判定
│   ├── Forwarder.kt                 HTTP POST 到 KeyFlow
│   ├── Settings.kt                  SharedPreferences 封装
│   ├── Logger.kt                    本地滚动日志
│   └── LogAdapter.kt                RecyclerView 适配器
└── res/
    ├── layout/
    ├── values/
    ├── xml/
    ├── drawable/
    └── mipmap-anydpi-v26/
```

## 隐私说明

`MessageListenerService` 通过 `BIND_NOTIFICATION_LISTENER_SERVICE` 权限获得了理论上能读所有 App 通知的能力——这是 Android 该权限的粒度，无法更细。

代码层面做了硬约束：
- `MESSAGING_PACKAGES` 白名单只包含 `com.samsung.android.messaging` 和
  `com.google.android.apps.messaging`；其他 App 的通知直接 `return`，不读 extras、不打日志、不转发
- 消息体只在转发时通过 HTTPS/HTTP POST 发到 `DATABASE_URL` 对应的 KeyFlow 服务器，本地不落磁盘（日志只记 sender + 状态，不记正文）
