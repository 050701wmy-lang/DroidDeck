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

2026-10-10 同步至 `main-8e1a676`（`8e1a676f1957d1fbd3e3491e74b6e77269821aab`）。合并 GOG、Epic、Amazon 商店、下载管理、云存档、KDE 桌面及 Windows EXE 组件解包支持，补齐商店与组件中文资源。保留中文默认、字体和 Albion 修复；原生载荷含 Rust 商店引擎，复用同提交已校验的官方预览 APK。

2026-10-09 同步至 `main-0a96767`（`0a9676765728ffc9de4d408578359cb2dfba99d4`）。修复桌面无法绑定大核时会话退出，并加入折叠屏分辨率跟随；补齐相应中文提示。保留中文默认、字体与 Albion 修复，使用同提交官方预览的原生载荷。

2026-10-09 同步至 `main-c101a45`（`c101a451e3824ddebdc62d2d2f2d743aa96c3a81`），版本 0.3.2（12）。合并 Steam SD 卡文件操作性能优化、Flatpak 本地文件安装及运行环境清理改进；补齐 2 项中文提示，保留默认中文、额外字体挂载和 Albion ICU 修复。原生载荷来自此提交已发布且校验通过的官方预览 APK。

2026-10-08 同步至 `main-679948e`（`679948e792ee51514d0c2944b575541d6288b71b`），保留默认简体中文、上游字体修复及额外字体目录挂载、Albion ICU 修复。补齐 4 项后台下载与暂停提示。新增 Steam 库、快捷方式和共享字体脚本在 Linux 中验证；同步仍以官方已发布预览提交为准。

2026-10-07 同步至 `main-ef38c97`（`ef38c975b994bb131eb327454c836c0ea84efd20`）。语言入口改为复用上游 `core.AppLanguage`，未选择语言时默认简体中文，用户仍可通过设置切换语言；Steam 跟随应用语言。上游已提供系统字体挂载及 fontconfig 配置，复用该修复并保留 OEM、下载字体目录挂载。保留 Albion ICU 修复，并合并新的 Windows 组件和 SSBS 启动逻辑。

后续中文分支以 `Droid-Deck/DroidDeck-CI` 的 `catalog.json` 中已发布的 `preview.commit` 为同步目标，不跟随尚未发布的 main 提交或 PR 测试构建。2026-10-05 已同步至 `main-eaa4a7a`（`eaa4a7a0472abc211364c3286f78dd9c48c463e1`），补齐 92 项新增中文资源，并迁移简体中文目录到 `values-zh-rCN`。

中文版升级保持包名 `com.droiddeck.launcher.zh`、应用名和现有签名，保留默认中文及 Linux 字体挂载。官方预览 APK 仅作为对应提交的原生载荷来源，不直接替代中文版；复用前校验官方 SHA-256、签名及脚本一致性，原生文件必须与此次预览提交匹配。历史预览 `main-eaa4a7a` 原始 APK 的 SHA-256 为 `2a6980a2c9a379feadc75d367252f7cbab6625bd77e3d597562222caa229ca38`。

## 手动更新上游

先提交或暂存本地工作，确保工作区干净，再执行：

```sh
git fetch upstream
git switch main
git pull --ff-only origin main
git merge --ff-only upstream/main
git push origin main
git switch zh-CN
git merge <catalog-preview-commit>
python -m unittest tools.tests.test_localization
git push origin zh-CN
```

`--ff-only` 在分支已分叉时会停止，避免覆盖已有提交。请检查意外进入 `main` 的改动，再决定如何保留。中文分支合并有冲突时，手动解决冲突，执行 `git add` 和 `git commit`，检查翻译、构建并在设备上验证后推送。无需强制推送。

也可以在 GitHub 的 `main` 页面使用 **Sync fork → Update branch** 同步，然后在本地合并到 `zh-CN`。不要用会丢弃改动的同步选项处理中文分支。

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
