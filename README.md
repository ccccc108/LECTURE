# 课堂速记 ClassNote

给 iQOO Pad6 Pro（以及任何 Android 8.0+ 平板/手机）用的课堂工具：

> **上课点一下 → 实时把老师的英文授课转写成英文字幕 → 下课后自动生成中文课堂笔记。**

专为 **数学 / 物理 / 工程英语** 三类英文授课做了学科化提示词，笔记里会分好：核心知识点、术语英中对照、公式（LaTeX）、例题与解题思路、易错点、复习清单、作业待办。

---

## 0. 先说三件重要的事

**① 为什么不是"DeepSeek 插件"**
手机版 DeepSeek App 没有插件 / 扩展机制（DeepSeek 只对外提供 API，以及 Claude Code、Codex 这类 Agent 工具接入）。所以"在 DeepSeek App 里装个插件"这条路技术上不存在。要做成一键可用的工具，正确形态就是**一个独立 APK**：它在本地录音，调用语音转写 API 拿英文字幕，再调用 DeepSeek API 做总结。

**② 为什么你自己得构建一次 APK**
本工程是在一台**没有 JDK / Android SDK / Gradle，而且命令行无法联网**的机器上写的，所以这里没法直接产出编译好的 APK。本工程提供了两条几乎零门槛的构建路径：
- **方式 A（推荐，5 分钟，不用装任何开发环境）**：把工程传到 GitHub，点一下按钮，云端自动编译出 APK（`.github/workflows/build-apk.yml` 已经写好）。
- **方式 B**：任意一台装了 Android Studio 的电脑打开本工程，直接 Run / Build APK。

**③ 需要两个 API Key（都要钱，但很便宜）**
| 环节 | 默认服务 | 作用 | 价格参考 |
|---|---|---|---|
| 语音转写 | 硅基流动 SiliconFlow（`FunAudioLLM/SenseVoiceSmall`） | 把音频分片转成英文文字 | 注册有赠送额度，按量计费，通常一节课几毛钱以内；以官网为准 |
| 课后总结 | DeepSeek 官方 API（`deepseek-flash`） | 把转写稿整理成中文课堂笔记 | 一节课约 **0.05~0.2 元**（见文末估算） |

两个服务都能在国内直连。任何"OpenAI 兼容"的服务都可以替换（改 Base URL + 模型名即可）。

---

## 1. 它怎么工作

```
麦克风(16kHz 单声道)
   │  每 15 秒切成一个音频分片（可在设置里改成 8~30 秒）
   ├──► 云端语音转写 API ──► 英文分片文字 ──► 去掉重叠/去重 ──► 实时字幕（边录边显示）
   └──► 同时写入本机 audio.pcm ──► 结束后封装成 audio.wav（可回放/分享）

下课点"结束并生成总结"
   └──► 整节课转写稿 + 学科提示词 ──► DeepSeek API ──► summary.md 中文课堂笔记
```

关键点：
- **录音在"前台服务"里跑**，切到别的 App、锁屏都继续录（需要在系统里允许后台运行，见第 5 节）。
- **全程本地存储**：音频、转写稿、笔记都存放在应用私有目录；只有音频分片会发给你配置的转写服务，转写稿会发给你配置的总结服务。
- 一节课一个目录：`meta.json`（科目/时间/时长）、`transcript.txt`（英文转写稿）、`summary.md`（中文笔记）、`audio.wav`（整节课录音）。

---

## 2. 目录结构

```
classnote/
├─ settings.gradle / build.gradle / gradle.properties   # 构建配置（AGP 8.5.2 + Gradle 8.7 + JDK 17）
├─ .github/workflows/build-apk.yml                      # 【方式 A】GitHub 云端一键打包
├─ tools/bootstrap-gradle.ps1                           # 【方式 B】生成 gradle wrapper（给 Android Studio 用）
├─ tools/build-apk.ps1                                  # 【方式 B】一条命令直接出 APK
└─ app/
   ├─ build.gradle
   └─ src/main/
      ├─ AndroidManifest.xml
      ├─ java/com/classnote/app/
      │  ├─ MainActivity.java        首页：科目选择 / 开始录音 / 实时字幕
      │  ├─ RecordService.java       前台服务：采集→分片转写→保存→总结
      │  ├─ AudioCapture.java        麦克风采集（AudioRecord，带录音源回退与分片重叠）
      │  ├─ AsrClient.java           转写 API 客户端（multipart 上传 WAV）
      │  ├─ LlmClient.java           DeepSeek/兼容接口客户端
      │  ├─ Summarizer.java          总结流程（超长转写稿自动分段提炼再合并）
      │  ├─ Subjects.java            【要改提示词就改这里】数学/物理/工程英语
      │  ├─ Http.java / Json.java    无第三方依赖的 HTTP + JSON 工具
      │  ├─ WavUtil.java             PCM→WAV 封装
      │  ├─ TextMerge.java           分片重叠去重、时长/词数格式化
      │  ├─ SessionStore.java        本地存储（每节课一个目录）
      │  ├─ SessionHub.java          服务↔界面的进程内状态总线
      │  ├─ SettingsActivity.java    设置（两个服务的 Key/地址/模型/分片时长）
      │  ├─ HistoryActivity.java     历史记录列表
      │  └─ DetailActivity.java      笔记详情：看原文、复制、分享、分享录音、重新总结
      └─ res/  layout / values（含 colors/themes/strings） / drawable / mipmap-anydpi-v26 / xml
```

依赖只有 `androidx.appcompat`，没有第三方网络库、没有 Kotlin、没有 Compose —— 目的是**最大化编译成功率**、缩小安装包。

---

## 3. 方式 A：GitHub Actions 云端打包（推荐）

> 全程只需要浏览器，不需要电脑装任何开发环境。首次约 5 分钟。

1. **注册 / 登录 GitHub**（github.com）。
2. 右上角 **+ → New repository**，名字随意（如 `classnote`），可见性选 **Private** 或 Public 都行，点 **Create repository**。
3. 进入新仓库 → **Add file → Upload files** → 在文件选择框里打开本工程的 `classnote` 文件夹 → **Ctrl+A 全选**（注意要包含 `.github` 文件夹）→ 拖进网页 → **Commit changes**。
   - GitHub 网页上传有单文件 25MB 限制，本工程所有文件都远小于这个值。
   - 如果 `.github` 没上传成功（有些系统会隐藏点开头的文件夹）：仓库页 **Add file → Create new file**，文件名填
     `.github/workflows/build-apk.yml`，把本工程里同名文件的内容整段粘进去，Commit。
4. 仓库顶部 **Actions** 标签 → 左侧选择 **Build APK** → 右侧 **Run workflow → Run workflow**。
5. 等 3~6 分钟，运行记录里出现绿色对勾后，打开该次运行，页面底部 **Artifacts** 下载 `ClassNote-debug-apk.zip`。
6. 解压得到 **`app-debug.apk`**，传到平板安装（微信文件传输助手 / 数据线 / U 盘都行）。

> 每次改了代码，推上去会自动重新编译（`push` 触发已配置好）。
> 如果编译报错：把 Actions 页面里红字部分（`gradle assembleDebug` 那一步的日志）发给我，我按报错改代码。

---

## 4. 方式 B：本机 Android Studio / 命令行

**最省事的一条命令**（电脑装过 Android Studio、SDK 下好之后）：

```powershell
powershell -ExecutionPolicy Bypass -File tools\build-apk.ps1
```

它会自动找到 SDK、写 `local.properties`、下载 Gradle 8.7、编译，并告诉你 `app-debug.apk` 在哪。

手动做法：

1. 安装 [Android Studio](https://developer.android.com/studio)（自带 JDK 17），第一次启动让它自动下载 SDK。
2. 本工程缺少 `gradle-wrapper.jar`（二进制文件无法用纯文本生成），两种做法任选：
   - **自动**：在工程根目录运行
     `powershell -ExecutionPolicy Bypass -File tools\bootstrap-gradle.ps1`
     它会下载 Gradle 8.7 并生成 wrapper。
   - **手动**：装了 Gradle 的话，在工程根目录执行
     `gradle wrapper --gradle-version 8.7 --distribution-type bin`
3. Android Studio → **Open** → 选择 `classnote` 目录 → 等 Gradle 同步完成。
4. 菜单 **Build → Build Bundle(s)/APK(s) → Build APK(s)**；
   或连上平板（打开 USB 调试）后直接 **Run ▶** 装到设备上。
5. 产物位置：`app/build/outputs/apk/debug/app-debug.apk`。

命令行等价操作（已生成 wrapper 后）：

```bash
./gradlew assembleDebug
# Windows: gradlew.bat assembleDebug
```

---

## 5. 平板上的安装与设置（iQOO Pad6 Pro / OriginOS）

本应用要求 **Android 8.0（API 26）及以上**，iQOO Pad6 Pro 完全满足。

**① 允许安装**
把 `app-debug.apk` 传到平板 → 文件管理里点开 → 系统提示"禁止安装未知应用"时，去 **设置 → 应用与权限 → 特殊应用权限 → 安装未知应用**，给对应的文件管理器/浏览器打开开关，再回去点安装。

**② 首次打开要给的权限**
- **麦克风**：必须（否则无法录音）
- **通知**：建议给（录音常驻通知、总结完成提醒）

**③ 让它别被系统杀掉（很重要）**
OriginOS 对后台管控比较严，想锁屏录一整节课，建议做这三件事：
1. **设置 → 电池 → 后台耗电管理** → 找到"课堂速记" → 选 **允许后台高耗电 / 不限制**。
2. **设置 → 应用与权限 → 权限管理 / 自启动** → 允许"课堂速记"自启动。
3. 最近任务里把"课堂速记"**加锁**（下拉卡片点锁图标），避免一键清理时被清掉。

**④ 录音效果建议**
- 坐在**前排**，平板平放、麦克风朝讲台；教室大/人多时建议配一个几十块的**领夹麦或 USB-C 小蜜蜂**（平板直接插上，应用会自动用外接麦克风）。
- 老师板书多、说话少的课，转写内容会稀疏，这是语音转写的固有特性。

---

## 6. 使用流程

**课前（1 分钟）**
1. 打开 App → 右上角 **设置**，填两个 Key：
   - 转写：`Base URL = https://api.siliconflow.cn/v1`，Key = 硅基流动的 API 密钥，模型 `FunAudioLLM/SenseVoiceSmall`
   - 总结：`Base URL = https://api.deepseek.com`，Key = DeepSeek 的 API Key，模型 `deepseek-flash`
   - 点 **保存设置**，再点 **测试总结服务**，弹出"连接成功"就说明总结链路通了。
2. 首页选择科目（数学 / 物理 / 工程英语 / 通用）。

**课中**
3. 点 **开始录音（实时转写）**，把平板放在桌上，可以锁屏、可以切去记笔记/看 PPT。
   - 顶部状态会显示"已录 00:12:34"，下面是滚动出现的英文字幕。
   - 每 15 秒一段，网络正常时字幕大约延迟 5~15 秒出现。
4. 下课点 **结束并生成总结**（也可以点通知栏里的"结束并生成总结"）。

**课后**
5. 等 10~60 秒，会弹出"课后总结已生成"→ 点 **查看**。
6. 详情页可以：
   - 在 **中文笔记 / 英文转写原文** 之间切换
   - **复制当前内容** → 粘到 Notion / 备忘录 / 微信
   - **分享文本** → 发给自己或同学
   - **分享录音** → 把整节课 WAV 发出去（可配合电脑端再精修）
   - **重新生成总结**（换了提示词、或当时网络失败时用）
7. 所有课程都在右上角 **历史** 里，按时间倒序排列，带 ✅ 的是已生成笔记的。

---

## 7. 生成的笔记长什么样

摘要结构（`Subjects.java` 里定义，可自行改）：

```
# 课程笔记：物理（英文授课）
## 一、一句话概览
## 二、核心知识点
## 三、关键术语（英中对照）      ← 表格：英文 / 中文 / 说明
## 四、公式 / 定理 / 定律        ← 工程英语课这一节换成「重要表达与句型」
## 五、例题与解题思路
## 六、易错点与老师强调
## 七、课后复习清单
## 八、作业与待办
```

提示词里的硬性约束（防止 AI 瞎编）：
- 只依据转写稿整理，**不得虚构**结论/公式/例题，信息不足标注"（转写不清，待补）"
- 结合专业上下文**纠正语音识别错误**（把听错的术语恢复成正确写法）
- 中文行文、专业术语保留英文、公式用 LaTeX 行内写法
- 直接输出 Markdown，不回显提示词

**想改教学风格？** 打开
`app/src/main/java/com/classnote/app/Subjects.java`：
- `subjectFocus(...)`：各学科"重点看什么"
- `sectionFour(...)`：第四节标题
- `systemPrompt(...)`：整体结构和输出格式

改完重新打包即可。

---

## 8. 费用估算

**总结（DeepSeek `deepseek-flash`，价格以官网为准）**
- 90 分钟英文课转写稿约 1.2~1.8 万英文词 ≈ 2~2.5 万 tokens
- 单价（高峰时段）：输入约 2 元/百万 tokens（未命中缓存）、输出约 8 元/百万 tokens
- 一次总结 ≈ 0.05~0.15 元；一天 6 节课 ≈ 1 元以内

**转写（硅基流动 SenseVoice）**
- 按音频时长计费，通常一小时音频几毛钱；新注册常有赠送额度
- 想更省：把分片时长调大（减少请求次数不影响音频总时长计费，只影响"实时感"和请求开销）

**省钱建议**
- 上课只开录音+转写，笔记留到晚上一起看（DeepSeek 空闲时段价格是高峰的一半：北京时间周一至周五 9-12、14-18 点为高峰）

---

## 9. 隐私与安全

- 两个 API Key 存在**本机应用私有目录**的 SharedPreferences 里，App 没有网络上传自己数据的逻辑。
- 录音文件、转写稿、笔记只写在本机 `files/sessions/` 下，卸载 App 即删除。
- 会离开设备的数据：**音频分片**（发给转写服务）、**转写稿**（发给总结服务）。如果课堂内容敏感，请自行评估是否使用云端服务，或改成局域网/本地部署的兼容接口（改 Base URL 即可）。
- 应用默认**只允许 https**（`usesCleartextTraffic="false"`）。要用局域网 http 接口（如 Ollama 网关、自建 ASR），需要给服务加 https 证书（caddy/nginx 都有一行命令），或把 `AndroidManifest.xml` 里的 `usesCleartextTraffic` 改成 `true` 后重新打包。
- `app-debug.apk` 用 Android 默认调试证书签名，仅适合自己安装使用；要分发给别人请自行生成 release keystore。

---

## 10. 已知限制（请务必知道）

1. **需要网络**。断网时该分片转写失败（会自动重试 1 次），但录音不受影响，音频文件是完整的；网络恢复后可以继续录，只是失败的那几段文字丢了。
2. **区分不了说话人**（不区分老师/学生）。人多嘈杂时，转写会混入闲聊内容。
3. **同音词、口音、专业缩写**仍可能出错；课堂板书、公式口述（比如"x squared over two"）转写质量取决于模型，提示词会尽力纠正。
4. **锁屏长时间录音**依赖系统后台策略，务必按第 5 节设好白名单，否则可能被 OriginOS 杀掉。
5. 一节课一个进程，**不能同时录两门课**；上一节的总结没生成完时，开始按钮会暂时不可用。
6. 应用按英文授课优化（`en`）。中文/中英混讲的课也能转，但效果取决于转写模型。
7. **存储占用**：16kHz 单声道录音约 **115MB/小时**，结束时会临时再生成一份同体积 WAV（约 170MB/90 分钟），转换完成后自动删掉 PCM。平板空间紧张时先清一清。
8. **网络慢的时候**：转写队列堆到 40 段后会自动跳过新分片并在状态栏提示"网络较慢已跳过 N 段"，录音本身不受影响；等网络恢复就能继续正常转写。

---

## 11. 排错

| 现象 | 原因 / 处理 |
|---|---|
| 点开始录音，提示"请先填写转写服务的 API Key" | 去 设置 → 转写服务 填 Key |
| 字幕一直不出，状态显示"第 N 段转写失败：鉴权失败（401）" | Key 错/过期，或模型名没权限；到设置里点"测试总结服务"只能测总结，转写请检查硅基流动控制台的模型名 |
| "接口地址不存在（404）" | Base URL 少了 `/v1`，或多了 `/audio/transcriptions` |
| "请求过于频繁或额度不足（429）" | 账户余额不足或限流，去对应平台充值/降频 |
| 录音没有声音 / 麦克风错误 | 检查系统是否给了麦克风权限、是否被其他 App（会议软件）占用；外接麦克风拔插后建议重开 App |
| 锁屏后录音断了 | 按第 5 节设置后台耗电白名单 + 自启动 |
| 总结很慢 | `deepseek-flash` 正常 10~60 秒；网络差会更久。可在设置里确认 Base URL 是 `https://api.deepseek.com` |
| 总结内容空 / 只返回空字符串 | 模型名不被支持或内容被过滤；换 `deepseek-v4-pro` 或换服务商再试 |
| 提示"模型不存在 / model not found（400）" | 模型名以 DeepSeek 官方文档为准：默认 `deepseek-flash`，另一个是 `deepseek-v4-pro`；如果是别家服务，模型名要去它自己的控制台查 |
| 状态栏提示"网络较慢，已跳过 N 段转写" | 教室网络跟不上，录音仍完整；可把分片时长调到 20~30 秒降低请求频率，或换个网络 |
| 提示"可能连到了需要登录的 WiFi 门户页" | 校园网需要先打开浏览器登录认证，认证后再回 App 点开始 |
| 填了 `http://` 的自建接口，请求全部失败 | 应用只允许 https；给服务加证书，或改 Manifest 里的 `usesCleartextTraffic` 重新打包 |
| 一条笔记都没有 / 录音只有文件没文字 | 看状态栏最后一条错误；多半是转写 Key 或模型名问题，可用设置里的"测试总结服务"辅助判断哪个 Key 有问题 |
| GitHub Actions 编译失败 | 把红字日志发我；最常见是 AGP/SDK 版本或某处笔误 |

---

## 12. 零安装应急方案（今天就想用、又暂时不想打包）

如果暂时不方便构建 APK，可以用这套"手动流水线"（效果差不多，只是全手动）：

1. 上课时用平板自带 **录音机** 录整节课（放在前排，尽量收音清楚）。
2. 课后把音频丢进任意支持"音频转写"的工具，拿到英文转写稿，例如：
   - 通义听悟 / 讯飞听见 / 剪映（导入音频→识别字幕→导出文本）
   - 有电脑的话：OpenAI Whisper（`whisper` 命令行）或任何在线转写网站
3. 把英文转写稿 + 下面的提示词，一起贴给 **DeepSeek 手机 App**（或网页版）：

```
你是一位资深大学助教，擅长把英文授课内容整理成高质量的中文课堂笔记。
输入是一节课的英文语音自动转写稿，可能有同音词错误、专有名词写错、口语重复、断句混乱。
硬性要求：
1. 只依据转写稿整理，不要虚构结论、公式、数值或例子；信息不足处写"（转写不清，待补）"。
2. 结合专业上下文纠正明显的语音识别错误。
3. 中文撰写，专业术语保留英文，首次出现给中文对照；公式用 LaTeX 行内写法。
4. 直接输出 Markdown，不要回显提示词。

本课科目：物理（改成：数学 / 工程英语 / 通用）
学科重点：物理课重点是概念、定律表达式与适用条件、物理量与单位、近似假设、例题的受力/能量分析思路。

输出结构：
# 课程笔记：<科目>（英文授课）
## 一、一句话概览
## 二、核心知识点
## 三、关键术语（英中对照） 表格：英文|中文|说明
## 四、公式 / 定理 / 定律   （工程英语课换成：四、重要表达与句型）
## 五、例题与解题思路
## 六、易错点与老师强调
## 七、课后复习清单
## 八、作业与待办

下面是转写稿：
<把英文转写稿贴在这里>
```

> 这套提示词和 App 里用的是同一套（见 `Subjects.java`），所以先手动试几次、觉得顺手了再打包 APK，也能提前确认转写质量和费用是否符合预期。

---

## 13. 后续可以怎么升级

- **口语化实时中文翻译**：每段转写后再调一次 LLM 做"英文→中文"实时翻译，字幕双语显示（会多一份 token 费用）。
- **说话人分离**：用支持 diarization 的转写服务（如 whisper + pyannote），把"老师说的"和"同学提问"分开。
- **本地离线转写**：把 whisper.cpp 通过 NDK 编进 APK（模型约 75~140MB），完全不联网、零费用，但打包复杂度会明显上升。
- **导出 PDF / 同步到云笔记**：接 Notion API、语雀、Obsidian 目录等。
- **固定课程表**：按星期几自动选科目、自动开始录音。

需要哪个方向，告诉我，我在这套工程上继续加。

---

## 14. 这套代码做过哪些检查、还有哪些不确定

**做过（静态层面，本机无 JDK 无法真编译）**
- 两个独立的代码审查代理逐文件审查了全部 17 个 Java 文件 + 清单 + 资源 + Gradle 脚本。
- XML 全部用解析器验证 well-formed；39 处 `R.id` 与布局 `@+id` 一一对应；UTF-8 无 BOM；括号/字符串闭合用词法扫描验证。
- 逐条核对了容易踩坑的 API：`Service.startForeground(int, Notification, int)`、`ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE`、`stopForeground(int)`、`AudioRecord` 五参构造、`NotificationCompat.addAction(int, CharSequence, PendingIntent)`、`PendingIntent.FLAG_IMMUTABLE`、org.json 各方法、AGP 8.5.2 / Gradle 8.7 DSL。
- 结论：**没有会导致 javac/aapt2 编译失败的问题**。

**根据审查意见已经修掉的运行时隐患**
1. 授权回调被 1.5 秒防连点逻辑吞掉 → 点了"开始录音"却没反应。
2. 两个对话框在 Activity 销毁时不 dismiss → 窗口泄漏。
3. 用 `startForegroundService` 发"结束"指令，服务可能没进前台就退出 → Android 12+ 前台服务超时崩溃。
4. 转写任务队列无上限 → 网络慢时可涨到上百 MB 导致 OOM；现在超过 40 段就跳过并提示。
5. 收尾任务提交到已关闭的线程池 → 抛异常导致"处理中"永久卡死；现在有兜底直接就地收尾。
6. 失败重试 × 超时会把"结束"拖成几分钟；现在收尾阶段只试一次、读超时降到 60s。
7. 分片之间原本没有音频重叠，导致分片边界上的单词被切断；现在每片尾部多带 1.5 秒音频，配合去重逻辑对齐。
8. 部分平板/外接麦克风不支持 `VOICE_RECOGNITION` 录音源 → 整节课录不了；现在按 `VOICE_RECOGNITION → MIC → DEFAULT` 自动回退。
9. 收尾时在主线程 `join` 录音线程 → ANR 风险；`onDestroy` 里改到子线程。
10. WAV 转换、HTTP 响应体、JSON 转义等小坑（句柄泄漏、超大响应、Locale）。

**还不确定 / 需要你第一次构建时确认**
- 本机无法真编译：如果 `assembleDebug` 报错，把 Actions 日志里的红字发我，我按报错改（大概率是版本或笔误，不是设计问题）。
- 你账号下的转写模型名（硅基流动控制台里能看到确切名字）和 DeepSeek 模型名，需要在设置页里对一下。
- iQOO/OriginOS 的后台策略：锁屏录整节课一定要按第 5 节设置白名单，否则系统可能杀掉进程（此时音频文件会丢，因为进程被杀时还没封装 WAV）。
