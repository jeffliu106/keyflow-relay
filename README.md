# KeyFlow Relay

Minimal Android SMS forwarder for the KeyFlow locksmith management system.

## 功能

- 后台监听收到的短信
- 按白名单直通 + 黑名单过滤
- 命中规则的短信 POST 到 KeyFlow `/api/leads`
- 极简前台服务（静默图标）保活
- 开机自启

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
5. 填白名单（一行一个）：
   ```
   +16045550101 ai_hotline
   6049876543 colleague
   ```
6. 确认黑名单（已预填常见 OTP / 物流 / 运营商关键词）
7. 打开"转发服务开关"
8. 点"去打开'电池未受限'"按系统引导把 App 加到白名单

### 4. 三星 Flip 7 额外一步（关键！）

Samsung One UI 有独立的"睡眠应用"机制，不让 App 在后台待太久。需要：

1. **设置 → 应用 → KeyFlow Relay → 电池** → 选 **"不受限制"**
2. **设置 → 电池和设备维护 → 电池 → 后台使用限制 → 休眠应用** → 确保 KeyFlow Relay **不在**这个列表里
3. **设置 → 电池和设备维护 → 电池 → 后台使用限制 → 深度休眠应用** → 同上

（上面的菜单路径在不同 One UI 版本会略有差异，核心是找到"电池优化 / 睡眠应用 / 休眠应用"三个名单，把 KeyFlow Relay 排除）

## 验收

- 通知栏有个灰色小图标（KeyFlow Relay Running）
- 从另一部手机给白名单里的号码发测试短信，`http://192.168.86.42:3001/inbox` 几秒内多一条
- 发一条带 "verification code" 的短信：不进 Inbox（查 App 日志看到 SKIP 记录）
- 重启手机：图标和服务自动回来

## 代码结构

```
app/src/main/
├── AndroidManifest.xml
├── java/com/keyflow/relay/
│   ├── RelayApp.kt            Application 入口
│   ├── MainActivity.kt         唯一的 UI 页面（设置+日志）
│   ├── SmsReceiver.kt          短信广播接收器
│   ├── ForwardingService.kt    前台保活服务
│   ├── BootReceiver.kt         开机自启
│   ├── Filter.kt               白/黑名单判定
│   ├── Forwarder.kt            HTTP POST 到 KeyFlow
│   ├── Settings.kt             SharedPreferences 封装
│   ├── Logger.kt               本地滚动日志
│   └── LogAdapter.kt           RecyclerView 适配器
└── res/
    ├── layout/
    ├── values/
    ├── xml/
    ├── drawable/
    └── mipmap-anydpi-v26/
```
