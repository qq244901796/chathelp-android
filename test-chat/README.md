# ChatHelp 测试聊天

独立的离线安卓聊天模拟软件，无需注册、登录或连接服务器。用于验证 ChatHelp 的文字采集、本机 OCR、来信触发、会话切换以及回复填入。

## 安装与使用

1. 安装 `chathelp-test-chat-1.0.0-release.apk` 和最新的 ChatHelp APK（当前 `chathelp-android-1.4.3-glm-release.apk`）。最低需要 ChatHelp 1.4.2-glm 才能识别这个测试应用，1.4.3-glm 修复了智谱输出格式兼容问题；测试软件独立安装，不覆盖任何聊天软件。
2. 在 ChatHelp 中开启无障碍、悬浮窗和助手开关。使用智谱分析时，在设置中填自己的 API Key 并保存。
3. 打开「ChatHelp 测试聊天」，默认显示小明的两条虚构对话。点 ChatHelp 悬浮球 →「分析当前对话」。
4. 输入文字后点「模拟对方来信」，追加左侧消息；输入框为空时自动使用一句示例文字。ChatHelp 开启自动分析时，新来信会触发分析。
5. 点「发送我的消息」追加右侧消息；单纯编辑输入框不是发送。选择 ChatHelp 候选回复的「填入」后，应只更新输入框，消息数不变；确认后自行点击发送。
6. 「切换会话」在小明和小红之间切换。「重置示例」恢复当前会话的两条样例，「清空」用于验证空会话反馈。

## OCR 测试

打开「OCR 测试模式」后，气泡正文通过 Canvas 绘制，不在无障碍节点中暴露文字。ChatHelp 使用真实截图及 bundled ML Kit 识别每个气泡，并保留左右说话方。

- ChatHelp 的「读不到文字时使用本机 OCR」需开启。
- OCR 自动分析默认关闭。自动触发需要同时打开「自动分析」和 OCR 自动分析；也可以手动点「分析当前对话」。
- 本机识别不需要模型 Key；后续判断、生成回复、排序需要有效 Key 和网络。
- 关闭本机 OCR 后点击分析，应出现明确提示。清空会话后不应把标题或工具按钮识别成消息。
- 长按悬浮球的「截屏识别一次」是另一条通用整屏识别路径，不能可靠区分说话方；验证双方区分请使用适配器的气泡 OCR。

## 数据与限制

测试软件没有网络权限，不会发送真实消息，也不读取真实联系人。两组对话保留在运行状态中，旋转屏幕时恢复；退出任务后重新打开会恢复示例。每组最多保留 100 条消息，单条输入最多 1000 字。

ChatHelp 按自己的配置读取此页面；开启分析会把采集的文字发给配置的模型服务商，开启聊天历史时也会按既有规则存入 ChatHelp 本机记录。请使用虚构消息测试。

本模拟环境不代表已经验证微信、QQ、飞书等真实软件的所有版本兼容性。

## 开发与许可

包名：`io.github.qq244901796.chathelp.testchat`；Android 11+；版本 `1.0.0`。没有新的运行时依赖，使用 Kotlin 标准库及 JetBrains annotations。

```powershell
.\gradlew.bat :test-chat:exportRuntimeArtifacts
python scripts/collect_test_chat_licenses.py
.\gradlew.bat :app:assembleRelease :test-chat:assembleRelease
```

签名沿用仓库的本机发布配置，不提交签名材料。设备集成测试需要先安装测试聊天 APK，再执行 ChatHelp 的 `TestChatInstrumentedTest`；测试通过 UiAutomation 读取这个模拟页面，不启用真实聊天采集或修改用户密钥。

Copyright (c) 2026 ChatHelp contributors。自有代码按仓库 MIT 许可提供；Kotlin 和 JetBrains annotations 按 Apache-2.0 提供。完整通知在 `THIRD_PARTY_NOTICES.txt`，也可从软件底部「测试说明与许可」离线查看。
