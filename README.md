# 重启防线 V3 Alpha

这是「重启」的 Android 原生执行层第一版。

## V3 Alpha 做什么

- 用户手动授予「使用情况访问」权限
- 用户手动授予「显示在其他应用上层」权限
- 设置高风险时间段
- 从手机已安装的桌面 App 中选择高风险 App
- 启动一个常驻前景服务
- 高风险时段打开指定 App 时，弹出全屏干预层
- 第一次至少等待 30 秒
- 连续选择“暂时继续”会增加下一次等待时间
- 可以直接离开风险 App
- 可以直接跳转到现有「重启 V2.1」PWA

## 为什么没有用 AccessibilityService

V3 Alpha 使用 Android 官方的 UsageStats + Application Overlay 路线。
它不读取屏幕文字、输入内容或网页内容。

## 云端生成 APK（不需要 Android Studio）

1. 新建一个 GitHub 仓库，例如 `reboot-guard`.
2. 把本项目所有文件/目录上传进去，包括 `.github` 文件夹。
3. 提交后打开仓库顶部 `Actions`.
4. 等待 `Build Reboot Guard APK` 完成。
5. 打开成功的 Workflow run.
6. 在 Artifacts 下载 `reboot-guard-v3-alpha`.
7. 解压得到 `app-debug.apk`.
8. 把 APK 传到 Android 手机并安装。

## 第一次安装后的顺序

1. 打开「重启防线」
2. 授予「使用情况访问」
3. 授予「显示在其他应用上层」
4. 允许常驻通知
5. 设置高风险时段
6. 勾选高风险 App
7. 保存设置
8. 点击「测试一次全屏干预」
9. 测试成功后点击「启动防线」

## 当前边界

这是 Alpha：
- 不屏蔽网站
- 不自动开机启动
- 不防止用户手动停止/卸载
- 不读取其他 App 的具体内容
- OriginOS 可能会限制后台服务，后续版本再做电池/自启动适配

下一阶段：
- V3 Beta：网站本地过滤 + 更强严格模式
- V3.1：开机恢复、防线日志、PWA 与 Android 数据联动
