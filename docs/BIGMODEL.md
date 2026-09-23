# 智谱免费模型改造说明

## 配置

判断供应商新增 `bigmodel`；默认使用 `https://open.bigmodel.cn/api/paas/v4` 和 `glm-4-flash-250414`。两个文本接口共用判断密钥，只有在接口域名一致时才继承。也接受完整的 `/chat/completions` 地址，不会重复追加路径。

已有安装会先固定旧版本的隐式默认地址与模型，保留已填写的供应商、密钥、关系、OCR、知识库与历史开关。`prefs_migrated_glm` 标记只迁移一次。一键预设立即保存两路智谱地址和模型，清除其他供应商的密钥，关闭云端视觉；再次应用智谱预设会保留已配置的智谱判断密钥。

新调试包名是 `com.jev.probe.glm`，与原版隔离，首次安装不会读取原版的数据。设备测试通过隔离的 SharedPreferences 文件验证升级迁移，不覆盖实际用户设置。

## 发布构建

release 包名为 `com.jev.probe.glm.release`，应用名「ChatHelp 智谱助手」，当前版本 `1.4.1-glm`（versionCode 6），关闭调试。沿用 1.4-glm 发布包签名，可覆盖升级；与原版、调试版分别保存设置。支持 Android 11+、ARM64。

设置 JDK 17 和 Android SDK 35 后执行：

```powershell
python scripts/prepare_release_signing.py
.\gradlew.bat :app:assembleRelease
```

首次运行脚本会在 `.signing/` 下生成 RSA 4096 位的 PKCS12 发布密钥和 `keystore.properties`；已有完整配置时不会覆盖。此目录和分发目录 `outputs/releases/` 已被 Git 忽略。请安全备份整个 `.signing/`，不要转发其中的文件。后续升级使用同一签名并递增 `versionCode`，避免测试者必须卸载应用。

也可通过 `JEV_KEYSTORE_PROPS` 指向外部属性文件，支持 `storeFile`、`storePassword`、`keyAlias`、`keyPassword` 四项。相对 `storeFile` 按属性文件所在目录解析。签名缺失时 release 构建会直接失败，避免误发未签名 APK。

构建产物：`app/build/outputs/apk/release/app-release.apk`；分发文件名 `chathelp-android-1.4.1-glm-release.apk`，在本分支 GitHub Release 下载。密钥由各自在应用内填写。完整许可与隐私说明随 APK 提供。

现有设备测试也可直接针对实际 release 运行：

```powershell
.\gradlew.bat -PtestBuildType=release :app:assembleRelease :app:assembleReleaseAndroidTest
.\gradlew.bat -PtestBuildType=release :app:connectedReleaseAndroidTest
```

## 请求与结果

- 判断及排序将原有问题定义放入 system 提示词，将聊天、联系人背景、知识库和历史作为 user 数据；HTTP 请求使用 `model/messages/response_format`，不是 Jev 专用协议。
- GLM 输出的 `answers` 必须覆盖全部问题；选择项必须属于已有枚举，概率必须有限且在 0–1 范围内，排序必须覆盖三条候选。只对总和接近 1 的舍入误差做归一化。
- 原题目的风险评分实际有十档，编号为 0–9；保持这个定义，防止出现一档偏移。分数与把握度均为模型估计。
- 回复必须是三条不同的非空字符串，每条最多 40 个 Unicode 码点；支持 JSON 对象、旧兼容供应商的数组和完整 Markdown JSON 代码块。不从散文中截取候选，不用固定文字伪装成功。
- 请求串行覆盖智谱判断、回复、排序、摘要及设置页测试。HTTP 429、5xx、网络故障最多尝试三次；采用退避并尊重有上限的 Retry-After，401/403、其他请求错误与重定向直接报错。
- 结果使用会话代次校验，离开、切换或刷新会话后旧结果失效。JSON 错误和 HTTP 错误不回显聊天内容或密钥。

## OCR

聊天采集继续只用本机 ML Kit，不切换为云端视觉。免费 bundled 中文模型随 APK 安装。截图限频、失败退避、悬浮窗避让与手动入口沿用；增加异步结果过期检查。关闭本机 OCR 可能导致飞书等自绘界面无法获得正文。

云端图片理解默认关闭，开关与本机 OCR 分离；即便其他代码直接调用 VisionClient，也会在请求前检查开关。当前云端功能只用于设置页测试，不声称已实现聊天截图的云端采集。

## 验证方式与边界

`testDebugUnitTest` 验证 JSON、枚举、概率、请求协议、本地 HTTP 重试/超时、串行与会话过期；`connectedDebugAndroidTest` 验证配置迁移、密钥隔离、视觉关闭和真实 ML Kit 中文识别。OCR 使用 Canvas 生成的虚构中文图片，测试不接触实际联系人和聊天。

设置页「测试完整流程」用虚构对话请求真实服务，依次验证判断、回复和排序；需要有效的个人智谱通用 API Key。无需在源码、命令或聊天中提供密钥。

微信、QQ 的完整读取和回填效果取决于设备上的目标应用版本、无障碍权限及其节点结构。通过自动化的解析测试不等于已经在这些应用的真实会话中验收；设备和现场验收情况以本次交付报告为准。
