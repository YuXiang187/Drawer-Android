# Drawer-Android

一个简易的名称抽取程序（Android版本）。

如需使用Windows的版本，请前往[Drawer](https://github.com/YuXiang187/Drawer)库。

如需使用Linux版本，请前往[Drawer-Linux](https://github.com/YuXiang187/Drawer-Linux)库。

“编辑”页面的文本语法为：

```
名称1,名称2,名称3,名称4,名称5,...
```

注意：分割符为**英文逗号**，不是中文逗号。

---

自v2.2版本起，抽取名称功能的 Gaussian（高斯分布）模型参考了 [rpick](https://github.com/bowlofeggs/rpick) 的实现

该算法会根据抽签历史动态调整概率：名单中越久没有被抽中的项目，概率越高；最近被抽中的项目移动到列表末尾，概率降低。默认标准差缩放因子为 3.0

本项目与 rpick 均采用 GPL-3.0 开源许可证

---

软件需要的权限为：

* 悬浮窗权限（SYSTEM_ALERT_WINDOW）：用于显示悬浮按钮和悬浮窗体
* 接收开机广播权限（SYSTEM_ALERT_WINDOW）：用于开机自启