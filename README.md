# Drawer-Android

一个简易的名称抽取程序（Android版本）。

如需使用Windows的版本，请前往[Drawer](https://github.com/YuXiang187/Drawer)库。

如需使用Linux版本，请前往[Drawer-Linux](https://github.com/YuXiang187/Drawer-Linux)库。

## 功能

程序启动进入主页可直接点击“抽取”按钮进行应用内抽取。

打开“悬浮按钮”开关后会在屏幕顶部显示一个绿色的悬浮按钮，点击即可弹出窗体抽取名称，可跨应用抽取。

“编辑”页面的文本语法为：

```
名称1,名称2,名称3,名称4,名称5,...
```

注意：分割符为**英文逗号**，不是中文逗号。

## 抽取算法

自v2.2版本起，抽取名称功能的 Gaussian（高斯分布）模型参考了 [rpick](https://github.com/bowlofeggs/rpick) 的实现。

该算法会根据抽签历史动态调整概率：名单中越久没有被抽中的项目，概率越高；最近被抽中的项目移动到列表末尾，概率降低。默认标准差缩放因子为 3.0 。

本项目与 rpick 均采用 GPL-3.0 开源许可证。

## 说明

软件说明：

- 最低版本：Android 5.0（API 21）
- 目标版本：Android 15（API 35）
- UI 框架：Material Design 3
- 构建工具：Gradle（Kotlin DSL）
- 依赖：AndroidX AppCompat 1.7.0、Material 1.12.0

权限说明：

- 悬浮窗权限（SYSTEM_ALERT_WINDOW）：用于显示悬浮按钮和悬浮结果窗口
- 接收开机广播权限（RECEIVE_BOOT_COMPLETED）：用于开机自启

多语言支持：

- 英语
- 简体中文
- 繁体中文
- 日语