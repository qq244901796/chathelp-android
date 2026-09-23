# 许可范围与再分发

项目自有代码沿用 MIT：保留上游 LICENSE 与 NOTICE 原文。本分支新增的适配、测试、文档和原创图标同样按 MIT 提供，Copyright (c) 2026 ChatHelp contributors。复制或再分发时保留上述版权声明与完整许可。

`test-chat` 是独立的原创模拟聊天应用，同样按 MIT 提供；其 APK 仅包含 Kotlin 标准库和 JetBrains annotations，完整第三方许可与依赖清单位于 `test-chat/` 并内置于该应用的测试说明页。测试 APK 不包含 ChatHelp 的 ML Kit SDK。

MIT 不会改变依赖的许可。AndroidX、Material、Kotlin 等组件的许可与版本列于 THIRD_PARTY_NOTICES.txt 和 dependencies.json，随 APK 在“关于、开源许可与隐私”中可离线查看。ML Kit 及部分 Google SDK 适用 Google 发布的 SDK/服务条款，不能宣称整个 APK 都是 MIT 或所有 SDK 都开源。

ML Kit 条款：https://developers.google.com/ml-kit/terms

Android SDK 条款：https://developer.android.com/studio/terms

模型服务不包含在 MIT 授权内，API Key 由使用者自己申请。原项目名称仅用于来源说明，不授予其商标或官方身份。分支发布的安装包、源码、SHA256 校验和与构建说明位于：

https://github.com/qq244901796/chathelp-android/releases

保留上游 Git 历史；上游旧版本文档和测试结论属于历史资料。本分支的实际验证范围见 TESTING.md。许可文本及来源资料便于接收者核验，不构成对所有部署场景的法律保证。
