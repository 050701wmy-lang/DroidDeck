# 中文分支维护与上游同步

上游：`https://github.com/Droid-Deck/DroidDeck.git`

Fork：`https://github.com/050701wmy-lang/DroidDeck.git`

## 分支约定

`main` 保持与上游一致，`zh-CN` 保存中文补充。首次克隆 fork 时执行：

```sh
git clone https://github.com/050701wmy-lang/DroidDeck.git
cd DroidDeck
git remote add upstream https://github.com/Droid-Deck/DroidDeck.git
git fetch origin
git switch zh-CN
```

## 跟随预览版

2026-10-21 同步至已发布预览 `main-0e78180`（`0e7818049ab5ee0cf4409af0143c12ac98e6e1a8`）。合并鼠标叠加层重绘优化、运行环境修复与手部追踪下载。保留本地帧呈现、触控滚动、详细 Steam 下载失败提示、默认中文、字体和下载加速；与上游新客户端诊断使用独立译文键，避免格式参数冲突。补齐 11 项中文资源。原生合成器从合并源码重新构建，其余载荷来自同提交官方预览 APK。

2026-10-20 完成 `main-ac25283`（`ac25283f04e94a1a2924950c5b7a2027388aa73b`）同步，版本 0.5.0（14）。保留本地帧呈现、性能 HUD、桌面存储修复和实际刷新率逻辑，纳入 DD-FG 3×/4× 更新。应用名为 `DroidDeck`，包名及签名不变。42 项本地原生/会话回归与中文、加速检查通过，Release 构建和 lintVital 通过。

2026-10-17 同步至 `main-abd89f9`（`abd89f99544593a36f02a1963ddc54d007d08799`）。合并存储管理、Quest XR 和成就页修复，补齐存储及 VR 中文资源，保留中文默认、字体、GitHub 加速、Steam 下载重试和 Albion 修复。载荷复用同提交已验证官方预览 APK。

2026-10-16 同步至 `main-0a72690`（`0a72690631b3eeabf6450f62c12699f370dd6357`）。修复 PRoot 丢失 fork 事件时的会话退出，合并触控、驱动和封面搜索更新，补齐 19 项中文。保留中文默认、字体、GitHub 加速、Steam 下载重试与 Albion 修复，全部原生载荷复用同提交官方预览 APK。

2026-10-15 同步至 `main-7fc61b4`（`7fc61b4c3d6552a5aaad35d29054d77e3466d794`）。沿用官方新中文译文，合并断点下载校验、输入和会话修复。保留默认中文、字体、GitHub 加速、Steam 下载重试和 Albion 修复，原生载荷来自同提交官方预览 APK。

2026-10-14 同步至 `main-d99d728`（`d99d728ba59173fcf17c0cf0ffe6823a470d27c1`）。合并 Epic、Amazon 商店引擎重构及过期登录令牌清理，没有新增中文资源。保留中文默认、字体、GitHub 加速、Steam 下载重试和 Albion 修复，Rust 商店原生库与其他载荷均来自同提交官方预览 APK。

2026-10-14 同步至 `main-0792617`（`079261759e7f05de3786821f9f619bc8703e4a89`）。合并会话后台任务、D-Bus 与 Steam webhelper 进程优化、zRAM 选项和存储空间检查。复用上游新增中文并移除 332 项重复补译，补齐 6 条最新提示。保留默认中文、额外字体挂载、GitHub 加速、Steam 下载重试及 Albion 修复，全部原生载荷来自同提交官方预览 APK。

2026-10-14 同步至 `main-a394dfd`（`a394dfd31b2102e20dd99c6f51b4873bbdedab05`），版本 0.4.0（13）。合并游戏详情、GOG 成就、组件下载和 VR 支持，补齐 47 项中文提示。保留 GitHub 加速、Steam 下载重试与错误提示、中文默认、字体和 Albion 修复；所有原生载荷来自此次已校验官方预览 APK，Steam 安装脚本另保留中文 fork 已提交的重试修复。

2026-10-12 同步至 `main-6b2c8ac`（`6b2c8ace0972bb17bad496ae304fb76c2c7fdc10`）。修复 KDE 中 Steam 桌面客户端启动与复用、保留大屏幕模式切换；没有新增中文资源。保留默认中文、字体和 Albion 修复，载荷复用同提交官方预览 APK。

2026-10-12 同步至 `main-4d1b842`（`4d1b8421fb94a29dd703325b2227ac858f310a2a`）。改进商店游戏页、手柄导航与下载任务重启保留，并合并图形缓存修复；补齐下载中断的中文提示。保留默认中文、字体与 Albion 修复，载荷复用同提交官方预览 APK。

2026-10-12 同步至 `main-0cafa83`（`0cafa83ea7569132d96c180dcf96487e672a56d1`）。修复 Epic JSON 清单下载，合并按游戏配置及组件、桌面设置更新，补齐中文资源。保留默认中文、字体和 Albion 修复，Rust 与原生载荷复用同提交已校验官方预览 APK。

2026-10-11 同步至 `main-ec24b3a`（`ec24b3a47224c7669fa2d83c9a590a0176cfcba9`）。合并 DualShock 4、DualSense 内置手柄与触摸板支持、屏幕方向与展开控件、桌面启动及组件安装改进，补齐 12 项中文。保留默认中文、字体和 Albion 修复，使用同提交官方预览载荷。

2026-10-11 同步至 `main-d414241`（`d41424170a756339a591adf6bf4ddf47ec007c35`）。合并控制器编辑器拖动对齐、桌面卸载与旧 LXQt 清理、Amazon 商店修复；补齐 6 项中文提示。保留中文默认、字体和 Albion 修复，载荷复用同提交官方预览 APK。

2026-10-10 同步至 `main-8ac5556`（`8ac555664ad1b079019d00af3348125d0de0c7c1`）。修正 Access 2000 运行库包装器分类与组件安装提示，并修复构建载荷校验的任务顺序。保留中文默认、字体与 Albion 修复，原生载荷复用同提交官方预览 APK。

2026-10-10 同步至 `main-846b20f`（`846b20f78aa9e5a037c8fd4a989777ff097e90f5`）。合并 Steam 触控、游戏识别与封面、商店凭据保护及桌面改进，补齐 9 项中文提示；保留中文默认、字体和 Albion 修复，载荷复用同提交官方预览 APK。

2026-10-10 同步至 `main-8e1a676`（`8e1a676f1957d1fbd3e3491e74b6e77269821aab`）。合并 GOG、Epic、Amazon 商店、下载管理、云存档、KDE 桌面及 Windows EXE 组件解包支持，补齐商店与组件中文资源。保留中文默认、字体和 Albion 修复；原生载荷含 Rust 商店引擎，复用同提交已校验的官方预览 APK。

2026-10-09 同步至 `main-0a96767`（`0a9676765728ffc9de4d408578359cb2dfba99d4`）。修复桌面无法绑定大核时会话退出，并加入折叠屏分辨率跟随；补齐相应中文提示。保留中文默认、字体与 Albion 修复，使用同提交官方预览的原生载荷。

2026-10-09 同步至 `main-c101a45`（`c101a451e3824ddebdc62d2d2f2d743aa96c3a81`），版本 0.3.2（12）。合并 Steam SD 卡文件操作性能优化、Flatpak 本地文件安装及运行环境清理改进；补齐 2 项中文提示，保留默认中文、额外字体挂载和 Albion ICU 修复。原生载荷来自此提交已发布且校验通过的官方预览 APK。

2026-10-08 同步至 `main-679948e`（`679948e792ee51514d0c2944b575541d6288b71b`），保留默认简体中文、上游字体修复及额外字体目录挂载、Albion ICU 修复。补齐 4 项后台下载与暂停提示。新增 Steam 库、快捷方式和共享字体脚本在 Linux 中验证；同步仍以官方已发布预览提交为准。

2026-10-07 同步至 `main-ef38c97`（`ef38c975b994bb131eb327454c836c0ea84efd20`）。语言入口改为复用上游 `core.AppLanguage`，未选择语言时默认简体中文，用户仍可通过设置切换语言；Steam 跟随应用语言。上游已提供系统字体挂载及 fontconfig 配置，复用该修复并保留 OEM、下载字体目录挂载。保留 Albion ICU 修复，并合并新的 Windows 组件和 SSBS 启动逻辑。

后续中文分支以 `Droid-Deck/DroidDeck-CI` 的 `catalog.json` 中已发布的 `preview.commit` 为同步目标，不跟随尚未发布的 main 提交或 PR 测试构建。2026-10-05 已同步至 `main-eaa4a7a`（`eaa4a7a0472abc211364c3286f78dd9c48c463e1`），补齐 92 项新增中文资源，并迁移简体中文目录到 `values-zh-rCN`。

中文版升级保持包名 `com.droiddeck.launcher.zh`、现有签名及显示名称 `DroidDeck`，保留默认中文及 Linux 字体挂载。官方预览 APK 仅作为对应提交的原生载荷来源，不直接替代中文版；复用前校验官方 SHA-256、签名及脚本一致性，原生文件必须与此次预览提交匹配。历史预览 `main-eaa4a7a` 原始 APK 的 SHA-256 为 `2a6980a2c9a379feadc75d367252f7cbab6625bd77e3d597562222caa229ca38`。

## 手动更新上游

先提交或暂存本地工作，确保工作区干净，再执行：

```sh
git fetch upstream
git switch zh-CN
git merge <catalog-preview-commit>
python -m unittest tools.tests.test_localization
git push origin zh-CN
```

`--ff-only` 在分支已分叉时会停止，避免覆盖已有提交。请检查意外进入 `main` 的改动，再决定如何保留。中文分支合并有冲突时，手动解决冲突，执行 `git add` 和 `git commit`，检查翻译、构建并在设备上验证后推送。无需强制推送。

不要直接合并 `upstream/main` 或使用 GitHub 的 **Sync fork** 作为预览版同步目标；它们可能包含尚未发布的提交。需要更新 fork 的 `main` 时，仅快进到已经核验的 `catalog-preview-commit`。

## 翻译约定与检查

- 保留上游已有的 `values-zh-rCN/strings.xml`。新增遗漏提示先从 Kotlin 代码提取为英文默认资源，再在 `values-zh-rCN/strings_localization.xml` 添加中文译文。
- Compose 中使用 `stringResource`，回调、服务等非 Compose 上下文使用 `Context.getString`。不要在代码中按语言判断或硬编码中文。
- 本 fork 复用 `core.AppLanguage`，通过 `chosen` 的默认值设为简体中文；用户显式选择其他语言或系统默认后尊重其选择。Application/Service 使用 `wrap`，Activity 按上游方式使用 `applyTo`。新增入口应遵循上游语言配置，避免重复包装上下文。
- 保留格式参数（如 `%1$s`、`%2$d`）、换行和文件路径；动态数量和文件名通过参数传入。
- 上游若新增或删除资源，运行检查并补齐中文；上游若已提取同一文本，优先复用其资源，移除本分支重复项。

```sh
python -m unittest tools.tests.test_localization
```

该检查验证资源覆盖、重复名称、数组长度和格式参数类型。它不能代替 Android 资源编译和设备验证。使用上游构建流程构建 APK，在中文和英文系统语言下确认应用仍显示中文，检查文件操作、删除确认、无线配对通知及手柄编辑器。对于新增加的其他语言，可使用默认英文回退，随后再补译。

## 发布与更新器

Steam 大屏幕模式的中文由 Linux 的 fontconfig/CEF 渲染，与 Android 应用资源语言无关。上游 `LinuxRuntime.bindSystemFonts` 将系统字体挂载到 `/usr/local/share/fonts/android`，并通过会话 fontconfig 配置启用；本分支还在 `LinuxRuntime.binds` 中将可读的额外字体目录挂载到 `/usr/share/fonts/android-*`，让 Linux 使用手机已有的中文字体作为回退，不替换运行环境的原有字体。此修复在每次启动会话时生效，已安装的运行环境无需重装；更新 APK 后完整退出 Steam 会话并重新启动。设备验证时可用 `fc-list :lang=zh-cn` 检查中文字体，再检查 Steam 设置与游戏库中的汉字。

Fork 不会自动继承上游的 Release、签名密钥或 Actions secrets。首次启用 Actions 前，应核对 `.github/workflows/` 中的构建、发布和定时任务；本次适配没有启用定时任务。上游更新器继续使用上游来源，安装上游 APK 可能覆盖中文分支新增内容；维护 fork 发布渠道时需要另行配置和验证签名、版本及更新索引。

## GitHub 下载加速

2026-10-12 中文分支加入统一 GitHub 下载加速。入口：设置 → 启动器 → 下载。默认启用第三方 `https://ghfast.top/`，可更换 HTTPS 地址前缀或留空关闭。地址按“前缀 + 原始 GitHub URL”拼接，更改对新请求生效。

只代理 `raw.githubusercontent.com` 公开资源和 `github.com/<owner>/<repo>/releases/download/...` 文件，包括运行环境、桌面、组件、驱动和应用更新文件。GitHub API 查询仍直连，Steam、Epic、GOG 等商店、登录和游戏 CDN 不改写。第三方无法连接、返回非成功 HTTP 状态或 HTML 页面时回退官方地址；传输中断仍遵循原下载器重试逻辑。保留所有原有摘要和签名校验，校验失败仍拒绝安装。

后续同步须保留 `core.GitHubDownloads`、Application 中的初始化、独立下载器的接入，以及 `GitHubDownloadSettings` 和英文/中文资源。测试：`python -m unittest tools.tests.test_github_downloads tools.tests.test_localization`；测试用本机 JDK 编译真实 Java 路由，覆盖地址白名单、HTTPS 校验、关闭状态、Range 请求头和网络/HTTP/HTML 回退。加速服务属于第三方，国内可用性和速度不能保证，可在设置中更换地址。

## 显示名称

2026-10-17 按用户要求，应用显示名称统一为 `DroidDeck`，不再添加“中文版”。使用上游的 `app_name`，本地构建不再加入 `.gradle/install/chinese-res` 名称覆盖。包名仍为 `com.droiddeck.launcher.zh`，签名、默认简体中文、GitHub 加速及其他修复不变。后续同步和构建须保留此名称要求。

## 包含本地原生修改的发布构建

ac25283 的基础载荷来自 SHA-256 为 `5fe45888db172b3b92fa4f494feeb612daf543184bcffa8a24a5e1fdbb40efb0` 的官方预览 APK。使用 WSL NDK 25c 从合并后的源码重建 `libdroiddeckwayland.so`（LSFG 和 DD-FG 均保留）；其余运行载荷沿用同提交官方产物。会话脚本使用当前合并源码的 LF 文件，保留本地下载和触控修复。来源及所有文件摘要记录在本机 `.gradle/install/ac25283-payload-verification.json`，构建前执行校验，构建后逐项比对 APK；不再使用旧实验构建的“跳过 attestation”流程。

构建命令使用 `.gradle/install/build-chinese.gradle`、`build/frame-pacing-tools/local-dependencies.gradle` 及 `.gradle/install/preview-opencv.gradle`。后者只路由已按 Maven 官方摘要校验的本地 OpenCV 4.13.0 缓存，不改变源码依赖版本。新版 APK 完成签名、包名、版本、195 项载荷、Android 原生依赖和商店 JNI 接口验证。未执行新版设备游戏测试，不能仅凭构建通过宣称改善 Kingdom Rush 兼容性。

## 当前包名要求（2026-10-22）

按用户最新要求，后续使用原版包名 com.droiddeck.launcher 和显示名 DroidDeck。此前 .zh 包名仅为历史记录。本地构建不再覆盖 applicationId，保留现有签名、默认中文、字体、GitHub 加速及本地修复。包名变化创建独立数据目录，保留旧 .zh 应用和数据；遇到官方签名包不卸载、不清除、不覆盖。

## 已发布预览 main-923ffde（2026-10-22）

同步官方 catalog 中的 `923ffde71574130e3871a2c8424c9e080a473217`，官方 APK SHA-256 为 `131848f2ce80233c0f48954cb1a561aa6f98401eb44f998b1c0ac13c5d67b80e`。新增组件 Stable/Nightly 切换、GOG/Epic 安装语言选择、Decky 更新通道和 VR 控制器选项，补齐 29 项缺失中文资源。

HUD 合并使用上游 `TICK_MS` 常量及帧时间图，保留本地实际呈现统计；默认中文、字体挂载、ICU、GitHub 下载加速和触控修复继续保留。原生合成器由当前合并源码构建，其余原生库来自本次已验证的官方 APK；177 项载荷及源码摘要记录在 `.gradle/install/923ffde-payload-verification.json`。中文资源与加速检查通过；运行脚本及原生检查共 134 项通过、1 项跳过。

用户再次确认保持 `com.droiddeck.launcher` 和名称 `DroidDeck`，现有签名不变；定时维护提示也已按此更新。安装与 APK 验证详情记录在本机 `.gradle/install/preview-state.json`，不将本地签名文件或设备日志提交到仓库。

### 首次安装组件包修复

本地仅复用官方 APK 内的 assets 和原生库，不会自动保留该 APK 的 `BuildConfig.PACK_*` 常量。缺少 `app/build/packs/*.tar.zst` 时，本地构建会生成空的组件包摘要；新设备或更换包名后没有已安装副本，运行环境安装会在 proot 阶段失败。2026-10-22 设备日志确认此问题，存储空间充足。

此预览使用官方提交对应的 Build APK run `39747414228` 的 `droiddeck-packs` 产物，五个包分别与 DroidDeck-Components 的内容寻址发布摘要核验后放入 `app/build/packs`，由 Gradle 生成下载摘要与长度。来源及摘要记录在 `.gradle/install/923ffde-addon-packs.json`；本地载荷验证同时检查这五个包。后续预览必须重新取得对应构建产物，不能沿用旧包。

新增 `verifyReleaseAddonPacks`，发布打包前要求五个组件包都有摘要；调试构建仍可使用上游的设备副本复用机制。用缺包输入确认检查拒绝发布，再恢复完整输入构建。设备的原加速地址返回 403，已将该设备下载设置改为实测可获取官方索引的 `https://gh-proxy.org/`，摘要校验保持启用。

### Steam 首次安装的官方 CDN 回退

同日设备在 Steam 安装清单阶段报 `STEAM_DOWNLOAD_FAILED manifest 35`；三个 `client-update.*.steamstatic.com` 地址均遇到 TLS EOF、连接重置或超时。手机 Linux 诊断会话实测 `https://media.steampowered.com/client` 可获取相同版本清单，真实组件的 SHA-256 与清单一致。Steam 安装脚本优先使用此官方分发地址，原三个 CDN 仍作为回退；不经过 GitHub 代理，不关闭 TLS 或组件摘要校验。

`tools.tests.test_steam_install` 的 10 项检查通过，覆盖官方地址之间的回退、真实 curl 的临时服务错误重试、失败标记、组件校验与修复安装。后续同步须保留此地址及相应测试，或以设备验证过的上游修复替代。

## 已发布预览 main-ffa40a1（2026-10-23）

同步 catalog 发布的 `ffa40a15d5655fb7fb2aa22cdf51d13f7ba95af1`，版本 `0.6.0 (15)`，官方 APK SHA-256 为 `e25e9e82dd8a31582e6cfff2ab2e450e6280e8e88907f41ad58c08dff04f6c79`。更新包括 Wine ARM64 上下文切换与写监控优化、PipeWire 启动修复、CJK 字体注册和可自定义渐变主题；补齐两项控件中文，移除上游已删除的三项 zRAM 选项翻译。保留默认中文、额外字体挂载、GitHub 加速、Steam 官方媒体 CDN、发布缺包检查及本地原生呈现修复。

上游此次重写了历史。旧预览 `923ffde71574130e3871a2c8424c9e080a473217` 与新历史的 `aaa2e40defb1a777811ee20e9d5b068200983cc2` 的源码树均为 `8419d3c89d2b6a5c71d1146e74061ec548e25b8e`。以此相同源码快照为三方合并基线，生成无冲突的合并树，最终合并保留原 fork HEAD 和此次真实预览提交为父提交；未重写 fork 历史、未强制推送。后续正常同步沿新预览提交继续。

官方组件包来自同提交 Build APK run `39910137366`，产物 ZIP SHA-256 为 `9cbc86b2cb2dd9bab198967210acce1461df7d8e697c44e905bd9a46f3e91684`；五个包及 178 项载荷的来源、摘要在本机 `.gradle/install/ffa40a1-addon-packs.json`、`ffa40a1-payload-verification.json`。原生合成器由此次合并源码构建，其余原生库使用本次官方 APK。中文与 GitHub 加速检查通过；相关运行脚本、快速路径、Wine、PipeWire 和原生检查 170 项通过、2 项跳过。APK、设备安装与待安装状态以 `.gradle/install/preview-state.json` 为准。


## 已发布预览 main-476642b（2026-10-24）

同步 catalog 发布的 `476642be7168a1c9e872b8644231c68b52b1d100`，版本仍为 `0.6.0 (15)`，官方 APK SHA-256 为 `8c64a3c6361f2719a12e1a3d1126cc2623c2fb2313880def81d140429131b3b5`。更新包括 Steam 账户识别、安装下载的断点续传与 MB 进度、无 ARM64 测试版时的稳定通道回退、下载监测性能、Epic 启动参数和图形兼容性修复；补齐七项新增中文设置。

Steam 安装脚本冲突使用上游新的持久下载缓存、通道回退和校验实现，保留手机已验证的官方 `media.steampowered.com/client` 优先地址及本地中文错误识别标记。测试的耗尽重试夹具从三个 CDN 对齐到四个，并补充清单、组件失败标记检查；26 项 Steam 安装检查通过。默认中文、额外字体挂载、GitHub 加速、发布缺包检查和本地呈现修复保持。

全部组件包取自同提交 Build APK run `40067301496`；产物 ZIP SHA-256 为 `83a37153b5e4e0c7b9e0433190d11af2c217a3d9c08302fbaf37c6c45394c39b`。原生合成器从当前合并源码重新编译，其余原生载荷来自本次已验证的官方 APK；180 项载荷和源码摘要记录在本机 `.gradle/install/476642b-payload-verification.json`，五个组件包来源记录在 `476642b-addon-packs.json`。

中文和下载加速检查通过；相关 Steam、组件、图形与原生检查共 330 项通过、5 项跳过。首次检查中的临时目录缺少工作流及 Git 索引已补齐，仅重跑相应来源验证；所有失败项已复验通过。设备安装与待安装状态以 `.gradle/install/preview-state.json` 为准。


## 已发布预览 main-0790cb4（2026-10-24）

同步 catalog 发布的 `0790cb4390627ea79fb7c9aef3d1acc98b941bdb`，版本仍为 `0.6.0 (15)`。官方 APK SHA-256 为 `2474b0f2336a76a927fd37abc7d97bbb3758120d47026c4b5b6cc987625ae903`。此版本删除 Proton 后清理它保存的组件原件和选择记录，但文件夹没有完全删掉时保留原件；同时关闭 Ubisoft Connect 的游戏内覆盖层，写入设置时保留注释、空行及文件权限，使用唯一临时文件名。

默认中文、额外字体挂载、GitHub 加速、Steam 官方媒体 CDN 与断点续传、本地原生呈现修复和发布缺包检查保持。无需新增翻译；中文覆盖与下载加速检查通过，相关游戏环境、组件及 Steam 安装脚本 105 项检查通过。ProtonExtrasTest 的同一份编译生产类和测试类在 WSL 的 POSIX Java 环境中运行，10 项通过；Windows 的文件替换语义与 Android 不同，因此未为测试宿主改动 Android 的原子替换实现。

全部组件包来自同提交 Build APK run `40093629289`，产物 ZIP SHA-256 为 `5042c5fe3ff07bceee2bff8f9a4317d9eb051f69c6a4934ee03ad9658863522a`。原生合成器使用当前合并源码构建，其余原生载荷来自本次官方 APK；180 项载荷和五个组件包的来源及摘要分别记录在本机 `.gradle/install/0790cb4-payload-verification.json` 和 `0790cb4-addon-packs.json`。APK 与设备安装状态以 `.gradle/install/preview-state.json` 为准。


## 已发布预览 main-1f42f51（2026-10-25）

同步 catalog 发布的 `1f42f512e2594e741abbefb621f5fa38ec2bc709`，版本仍为 `0.6.0 (15)`。官方 APK SHA-256 为 `e3d2b95e1c4f15f8c6104aa43f5f4dbf718f1b2ef5b39757e59c13eb1d588bad`。更新包括 Xbox 云游戏原生播放器、arm64 与 x86-64 Steam 客户端共享游戏、Proton 自动更新的会话保护、itch.io 筛选与分页、Steam 文件对话框及 GOG 登录 User-Agent 修复。上游已提供对应中文，无需补译。

两个原生冲突已结合处理：云串流先进入独立渲染路径，普通 Wayland 场景继续设置本地帧缓存的源帧编号；Vulkan 只启用驱动提供的扩展，AHardwareBuffer 按上游条件启用，同时即便没有 AHardwareBuffer 支持也保留 DMA-BUF 需要的 FOREIGN 队列所有权扩展，且不重复注册。默认中文、字体挂载、GitHub 加速、Steam 官方 CDN、发布缺包检查及本地呈现统计保持。

组件包来自同提交 Build APK run `40160697326`，产物 ZIP SHA-256 为 `e233013b675cfebe42841e8ae7b851f4b9d3784d911aea67b8f6446226b437bb`。本地合成器从当前合并源码构建，其他原生库取自此次官方 APK；184 项载荷和五个组件包摘要分别记录在 `.gradle/install/1f42f51-payload-verification.json`、`1f42f51-addon-packs.json`。新增 WebRTC 依赖为上游固定的 `io.github.webrtc-sdk:android:125.6422.07`，使用官方 Maven Central；本机 `preview-webrtc.gradle` 仅将该模块路由到该仓库。

中文和 GitHub 加速检查通过。相关脚本、共享库、输入、文件对话框及原生回归检查 201 项通过、1 项跳过；文件对话框 JavaScript 用现有 Windows Node 复验，未因 WSL 缺少 Node 跳过。Xbox 云游戏设置与筛选、itch 浏览的 15 项针对性 JUnit 检查通过，发布构建与 lint 通过。APK 与设备安装状态以本机 `.gradle/install/preview-state.json` 为准，构建通过不代表已做云游戏服务或硬件串流测试。


## 已发布预览 main-9fc4851（2026-10-26）

同步 catalog 发布的 `9fc48515c13937578817645a08d9264a630d151d`，版本仍为 `0.6.0 (15)`，官方 APK SHA-256 为 `f52010bcc2c80939d8d0bb95be90edebf675c1d769d8e9aaaa8fa9ff59adb4a1`。更新包括统一低延迟模式、未开启长按时保持直接触屏按住、性能与 RAM/VRAM 设置、默认安装位置和库筛选顺序、DD-FG 流层内存优化及 GOG 原生安装器回退；补齐 39 项新增中文和格式参数。

会话脚本冲突采用上游同时处理宽、高的嵌套输出尺寸和拉伸逻辑，保留现有仅在显式游戏帧率限制时传入 gamescope -r 的策略。默认中文、字体挂载、GitHub 加速、Steam 官方 CDN 与续传、AHardwareBuffer 支持、本地原生呈现统计和发布缺包检查保持。

组件包来自同提交 Build APK run `40320808356`，产物 ZIP SHA-256 为 `09e9eb379cc3b436ee780c75bf4d71c560be84ed128d8076bbc59b3da707c3e9`。当前合并源码的合成器及 DD-FG 已重新编译，其余原生载荷取自此次官方 APK；195 项载荷及源码摘要保存在 `.gradle/install/9fc4851-payload-verification.json`，五个组件包来源保存在 `9fc4851-addon-packs.json`。

中文与 GitHub 加速检查通过。相关脚本和原生回归检查执行 206 项；DD-FG 另 6 项在设置现有 NDK Vulkan 头及原生构建同用的 vk_layer.h 兼容头后复验通过，未为测试改动生产逻辑。触屏按住、会话手势路由与性能模式的 21 项针对性 JUnit 检查通过，发布构建与 lint 通过。实际 APK 及设备安装状态记录在本机 `.gradle/install/preview-state.json`，尚未在设备验证触屏或性能效果。


## 只读更新日志（2026-10-27）

按用户要求，侧栏和页面标题改为“更新日志”，保留原 updates 导航键以兼容已保存的页面选择。页面读取官方 catalog 的 preview 与 recentPreviews，按提交去重并按发布时间展示标题、摘要、时间及当前版本；支持缓存、刷新和错误提示，自签构建也可查看。

UpdatesMenu 只读取与刷新目录。页面的通道选择、APK 下载/安装动作、未知来源权限弹窗和安装状态接收器已移除，侧栏更新角标也已移除。AppUpdates 和 SelfInstaller 的后端校验保留，后续同步不得恢复官方 APK 的应用内下载或安装入口；这一入口应继续作为只读日志。

此界面修改沿用已验证的 main-9fc4851 原生载荷和五个组件包、原版包名 com.droiddeck.launcher、DroidDeck 名称及既有签名。APK 与设备验证结果记录在本机 preview-state.json。

日志正文也必须跟随应用语言。ChangelogNotes 按完整提交号引用 Android 字符串资源；英文原文与简体中文标题、摘要分别保存在 values 和 values-zh-rCN 的 strings_changelog_entries.xml。每次同步应补齐官方 preview/recentPreviews 的对应译文，并保留已收录的旧日志。简体中文下尚未收录的提交显示中文待翻译提示，不直接回退上游英文正文；英文显示原文。语言选择继续由 AppLanguage 和 Android 资源系统处理。


## 已发布预览 main-43a0ce9（2026-10-27）

本次仅合并官方 catalog 的已发布预览提交 `43a0ce93f9fec1ea14e7017fe0d95a7d65ba33a6`。官方 APK SHA-256 为 `47084efb2d6a6612f6f57eebf2ceb4b0ce14a3a98f89eb8058b2f9e191708723`，包名仍为 `com.droiddeck.launcher`，版本为 `0.6.0 (15)`。包含竖屏布局、Steam 集成、arm64 Steam 远程畅玩解码、帧率同步、战网安装及 Proton 组件选择修复。

导航冲突采用上游共用的横竖屏导航组件，同时保留更新日志图标并移除更新角标；活动销毁时保留新启动流程的取消操作，不恢复更新安装接收器。合成器采用上游 HUD 声明迁移并保留本地内容序列与呈现统计。会话脚本采用上游新的刷新率跟随协议：gamescope 的 `-r` 表示初始屏幕刷新率，同时设置 `GAMESCOPE_NESTED_REFRESH_FOLLOWS_HOST=1`，游戏帧率限制由 Steam 管理；旧版仅在显式帧率限制时传入 `-r` 的兼容逻辑和相关测试随之调整。CompositorHost 帧率参数由调用方显式传入，不引用上游已删除的 SessionState 字段。

补齐 45 项中文界面文案与 10 条近期日志的中英资源，保留已收录的 20 条日志译文、默认中文、字体挂载、GitHub 加速、Steam 官方 CDN 与续传及只读更新日志。

本次载荷来自新的官方 APK，并完整重编译合并后的本地合成器及依赖，共 196 项载荷和源码摘要记录在 `.gradle/install/43a0ce9-payload-verification.json`。五个组件包取自同提交官方构建 run `40478519944` 的产物 `12721654983`，产物 ZIP SHA-256 为 `7333d48dfecd9af52421d51845922c07abb19743de977f748c03f41675a4340a`，记录在 `43a0ce9-addon-packs.json`。实际 APK、检查和设备安装状态以本机 `preview-state.json` 为准。

发布构建与 lint 检查通过；310 项脚本/原生回归检查在处理旧帧率协议测试及 WSL 缺少 Node 的环境问题后通过，中文与 GitHub 下载检查另有 3 项通过。19 个相关 JUnit 测试类合计 152 项通过，已修正 fork 旧语言测试的 Robolectric API 与资源限定符顺序；组件重复发布的 4 项使用相同已编译测试在 Linux JVM 复核，以覆盖 Android 的原子重命名语义，未为 Windows 文件替换差异改动生产代码。


## 已发布预览 main-8c4f97a（2026-10-28）

仅合并官方 catalog 已发布的 `8c4f97a1705d34f042d79a8f1d31c401f0872d4e`，官方 APK SHA-256 为 `7fe8048f49bb55c3556c43a25edd3b648409f3ff00a3eac8c555c9b22614f9e2`，版本仍为 `0.6.0 (15)`。包含新游戏帧到达即绘制、gamescope 单次循环处理完成帧、独立 vsync 线程、Steam D3D 驱动查询、手动暂停、音量键同步、CPU 0 拓扑、Turnip 兼容性提示、可选游戏调试日志和受限日志分享等变更。

合成器保留上游 dirty 等待时间与节拍延迟计数，同时保留本地内容序列、重复回调过滤和基于原始回调时间戳的刷新间隔，避免将事件分发抖动当作屏幕刷新率。CompositorHost 采用上游独立显示优先级线程与回调代际保护，同时保留暂停状态检查、显式帧率参数和无效刷新率过滤。SessionActivity 同时保留下载续传重试诊断与上游新增的非法指令、Vulkan 启动失败提示。

更新日志页面冲突保留 fork 的完整只读页面，不恢复通道选择或官方 APK 安装入口；新增 11 项缺失中文界面资源、10 条已发布日志的中英资源，保留历史译文，共收录 40 条。原版包名、DroidDeck 名称、原签名、默认中文、字体挂载、GitHub 加速和 Steam 官方 CDN 与续传保持。

原生输入采用本次官方 APK，覆盖合并后的脚本，并完整清理重编译当前合成器及依赖。196 项载荷和对应源码摘要记录在 `.gradle/install/8c4f97a-payload-verification.json`。五个组件包来自同提交官方构建 run `40663526778` 的产物 `12806056571`，ZIP SHA-256 为 `68534ece40d303aa042cc5072266bad1e0ea64a9face6f17da4d1cc3b52c410c`，记录在 `8c4f97a-addon-packs.json`。

发布构建、lint、原生依赖闭包及最终 APK 包名、名称、版本、签名、全部载荷和 DEX 中的五个组件摘要校验通过。108 项相关脚本/原生检查通过（现有计时夹具补齐上游统计字段并按实际有符号类型复核），另有 3 项中文/GitHub 检查、7 项 Node 同步检查通过。18 个测试类的 189 项 JUnit 在 Windows 通过；日志分享、压缩包和保留的下载诊断共 32 项使用相同已编译测试在 Linux JVM 通过，合计 221 项。实际 APK 与设备安装状态记录在本机 `preview-state.json`。


## 更新日志离线与旧缓存修复（2026-10-28）

此前更新了日志译文，但列表仅从网络 catalog 缓存生成；设备刷新失败时，即使新 APK 已有译文，也可能继续只显示旧记录。ChangelogNotes 现在同时保留每个完整提交号的标题资源、摘要资源和已核验发布时间；列表合并官方 preview、recentPreviews 与随包发布记录，按提交号去重、发布时间倒序，网络记录优先。空缓存或旧缓存不能隐藏内置的新发布记录，网络比内置更新时仍正常显示新记录。

每次同步必须连同译文补齐相应发布时间，保留历史发布记录，不能只增加资源而遗漏日志条目。页面明确说明内置日志可离线查看，并把缓存时间标为“上次联网刷新”，不伪造联网成功时间。中英文继续由 Android 资源随 AppLanguage 解析；只读页面不增加安装入口。

本次内置 40 条已核验发布记录，最新为 main-8c4f97a。空缓存、旧缓存去重及新网络记录优先级的 3 项回归，与 6 项语言检查合计 9 项 JUnit 通过，中文资源检查、发布构建及 lint 通过。最终 APK 确认包含新的中文联网时间标签，并验证原包名、名称、签名、196 项载荷和五个组件摘要不变。设备与安装状态以本机 preview-state.json 为准。


## 已发布预览 main-27bdc68（2026-10-29 同步）

仅合并 catalog 已发布预览 `27bdc68867309221ede2e2ab44fe0ada9d1631f5`，官方 APK SHA-256 为 `0823f2de23024ed2e515ac377966e10cee22345c3ddeac4f613ceffceadebfd8`，版本仍为 `0.6.0 (15)`。包含 glibc 系统调用中转与缓存加速、无帧率上限时 OpenGL 等待刷新修复、首次桌面/Proton 下载取消、itch.io 已安装游戏更新检测与安全暂存合并，以及构建流水线改进。

CompositorHost 冲突保留上游新增的提交即绘制开关，同时调用 fork 的有效刷新率过滤与显式帧率处理。保留源帧序列、呈现缓存、重复 vsync 去重及独立线程代际保护。计时隔离夹具补齐新绘制路径依赖，新提交绘制测试补齐 fork 的原始回调时间戳状态，两类行为都保留回归覆盖。只读日志继续合并随包与网络记录，新增六条中英译文与已核验发布时间，共 46 条内置日志，旧缓存不能隐藏本次预览。

原生载荷取自新的官方 APK，合成器及依赖完整清理重编译，覆盖合并后的脚本。196 项载荷与源码摘要见 `.gradle/install/27bdc68-payload-verification.json`。五个组件取自同提交成功构建 run `40716811463` 的产物 `12829583911`，ZIP SHA-256 为 `d5c754871ddd630e0d6c25f0cf2f82e9129b6f757551b8a837b1e3e4530e929e`，见 `27bdc68-addon-packs.json`。不使用 deploy_fast 将旧版本原生文件混入本次 APK。

原版包名、DroidDeck 名称与既有签名、默认中文、字体挂载、GitHub 下载加速、Steam 官方 CDN 与续传及无应用内更新安装入口保持。实际 APK、检查结果及设备安装状态以本机 preview-state.json 为准。

发布构建、lint 和原生依赖闭包检查通过。165 项相关脚本/原生回归、3 项中文/GitHub 加速检查，以及 12 个测试类的 64 项 JUnit 全部通过，包括下载续传与取消、itch 安全暂存/更新检测、PRoot、离线日志和语言及会话输入检查。最终 APK 校验原包名、名称、版本与既有签名，并逐一核验 196 项载荷及实际 DEX 中的五个精确组件摘要。


## main-d1bd23d：采用上游 HUD（2026-10-29）

用户明确选择“使用上游”：采用 FusionHUD 及其 MangoHud 数据口径，接受上游删除 PerfHud，不迁移旧 HUD 的提交/显示/锁存标签。底层呈现缓存、帧序列与 vsync 修复继续保留，后续同步不得因旧 HUD 已删除而回退此选择。

同步官方已发布的 d1bd23df161de27520e05601f0ad71ab45f78a2c，官方 APK SHA-256 为 38ab8d6ccb8062db65329f0eca2a60d1bd90fafa17732196bd1f20fe054ef26e。新增 FusionHUD、封面卡片/详情样式与显示菜单、USB 2.0 游戏库速度提醒及 Xbox 后端更新。补齐 34 项中文文案和 10 条日志译文与时间，共 56 条内置日志；字面百分比 HUD 标签设置 formatted=false，避免被当作格式参数。

原生输入取自此次官方 APK，当前合成器完整重编译。五个组件采用同提交官方 run 40816976536、产物 12873153395，ZIP SHA-256 为 c6d0d39e02f1f569e01be8f5065f5010f225791b0a2c1cc709d2f47120867165。包名、显示名称、签名及中文默认、字体挂载、下载加速和只读离线日志保持。APK、验证与安装状态见本机 preview-state.json。

发布构建和 lint 通过；3 项中文/下载加速检查、6 项 HUD 帧通道及底层呈现回归通过。相关 JUnit 合计 32 项通过，其中 USB 的 7 项在 Linux JVM 复核，以支持 sysfs 中含冒号的路径；其余 25 项在 Windows 通过。最终 APK 校验原包名、名称、版本、签名、199 项精确载荷与五个组件摘要。
