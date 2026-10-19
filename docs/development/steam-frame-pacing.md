# Steam 菜单帧率与 HUD 修复（2026-10-18）

已实现 HUD 呈现统计、回调生命周期和叠加层内容复用，并继续按用户指出的大幅连续滑动排查。新增定位到 Steam 横向虚拟列表的触摸命中问题：同页对照从约 38 fps 改善到约 108 fps。当前仍不能宣称所有页面稳定 120 fps。

设备为 Xiaomi 25091RP04C，SM8750P / Adreno 830，Android 17，画面 3040×1904。系统上限为 120 Hz，应用请求 120 Hz 模式，但 SurfaceFlinger 实际周期一直为 6,060,606 ns，即 165 Hz。不能把请求频率当作实际面板频率。

## 大幅连续滑动：本轮结论

此前 400 像素、1 秒慢滑的约 103 fps 只适用于那个负载，不能用于评价用户的大幅快速滑动。本轮采用原生触摸从 x=2520 滑到 520，再反向，y=650，每次 350 ms、间隔 80 ms，在 3040×1904 画面上覆盖约三分之二屏宽。

| 场景 | 手势中央区间 FPS | P95 | 单手势低于 40 fps |
|---|---:|---:|---:|
| 默认 sysmem，大幅连续滑动 60 秒 | 37.51 | 18.18 ms | 73 / 122 |
| 同页固定起点，对照 30 秒 | 38.10 | 18.18 ms | 36 / 61 |
| 同页固定起点，修正触摸命中 30 秒 | 107.74 | 12.12 ms | 1 / 61 |
| 最终包自动加载，连续大滑 60 秒 | 114.57 | 12.12 ms | 0 / 120 |

同页对照在同一会话中进行，两组都先将真实首页横向列表的 scrollLeft 设为 700，再以相同原生手势采样。SurfaceFlinger 整段 FPS 从 38.678 变为 112.575；这里的改善主要是减少手势没有推动菜单的停顿，不应解释成 GPU 拷贝速度提升了三倍。

页面追踪中 24 次手势的 48 个触摸起止事件均到达 Chromium，但部分回滑的起点落在全屏 hero 背景图。例：一次正向滑动让 scrollLeft 从 895.96 变为 1538.05，随后的反向滑动却从 1725.25 变为 1750.17，仍在沿前一次惯性移动。横向 `.ReactVirtualized__Grid` 的 pointer-events 为 none，回滑没有命中预期的滚动区域。

修复通过会话启动时已有的本地 CDP 通道，为 Gamepad UI 加入一条仅针对横向虚拟列表的 pointer-events:auto 样式，保留原页面内容、点击和游戏启动逻辑。样式按固定 id 幂等安装，等待初始页面但不阻塞 Steam 启动；新一轮客户端启动会重新安装。桌面 Steam 沿用原有辅助进程，失败时继续原界面。未改写 Steam 客户端文件，也没有永久打开额外远程接口。

新测试包已验证自动加载；卡片点击可以进入游戏详情，返回后仍在首页，样式继续存在。最终 60 秒实际完成 120 次大幅连续滑动，手势中央 250 ms 区间合计 30 秒，平均 114.57 fps，中位手势 112 fps，最低手势窗口 88 fps，0 个手势窗口低于 40 fps。SurfaceFlinger 整段 116.604 fps，HUD 30 次快照均值 115.43 fps，差约 1.0%。该整段窗口有 962 个合成丢弃计数，P95 12.12 ms，仍不代表均匀稳定 120 fps。

最终会话为 `2026-10-18-17-steam`，READY、未暂停，兼容样式只安装一份。临时环境覆盖为空，采样监听器和两项设备探针均已清理。新结果只对当前客户端构建、页面和设备有效。

已撤回的额外实验：清除强制 sysmem 的自动模式在大幅滑动对照中约 41.41 fps，默认 sysmem 在相同 200 ms 手势间隔下约 99.39 fps，所以保留原选项。BGRA 同格式缓冲和 vkCmdCopyImage 显示正常，但连续大滑仍约 41.38 fps，GPU 等待无明确下降；相关产品代码与临时测试已撤回。没有引入 HAL 私有格式依赖。

原始记录包括 `menu-wide-touch-final*`、`menu-wide-continuous*`、`menu-wide-hit-control*`、`menu-wide-hit-auto*`、`scroll-profile-80.json`、`scroll-profile-200.json`、`menu-wide-bgra*`。临时 GPU 环境覆盖已清空。

## 实现与上游整合

上游 `ae94d9db` 已包含刷新率模式只升不降的修复，采用上游 `PerfMode.selectMode()` 与眼镜刷新率限制，保留其测试。本地模式请求回归另存为 `PerfModeRequestTest`，不覆盖上游同名测试。

- `CompositorHost` 保留上游眼镜 pacing 和驱动初始化接口，统一更新输出、会话与图层请求；暂停时移除 Choreographer callback，回调先检查运行状态。
- `SessionActivity` 在普通平板上使用当前显示频率，而不是尚未生效的窗口模式请求；保留上游眼镜输出和镜像上限。
- `compositor.c` 优先使用已知输出频率，回退估算使用 Choreographer 帧时间差，过滤重复/倒序时间，避免合成线程阻塞污染节奏估计。
- 会话脚本在无限帧率时不再把启动快照 `BL_REFRESH` 转成固定 `gamescope -r`，改用实时 Wayland 输出；显式 `BL_FPS` 限制仍保留。整合后实测曾出现 `BL_REFRESH=60`、`gamescope -r 60`，同时实时输出已是 165 Hz，页面 rAF 约 55 Hz；此时滑动仅 51.67 fps、P95 24.24 ms，属于发现后修复的启动限帧问题。
- `PerfHud` 停止时移除已排队 tick，启动后等待完整一秒。普通 SurfaceControl 路径按新游戏内容事务的 present fence 时间去重，并显示帧间隔 P95。
- 仅有完成事务 latch 时间时明确标为“合成”；无有效证据或其他显示路径标为“提交”。插帧状态及错误提示保留，不使用陈旧呈现值。
- `sc_layer.c` 非阻塞读取呈现 fence 并回收 fd，图层重建/隐藏时重置统计，以代号排除旧回调；几何更新、隐藏、退休和辅助图层均不计为新游戏帧。
- 内容未变化的叠加层复用已有显示 buffer，只更新位置和透明属性，避免每个游戏帧重复拷贝。未知序号、新内容、重新显示仍走原路径。
- 驱动声明支持时启用已有外部所有权屏障所需的 `VK_EXT_queue_family_foreign`。

已在本地合并上游 17 个提交，合并提交为 `e92b60dc`，未推送。下载冲突保留上游断点校验、多 CDN 回退和本分支错误提示/重试，已保存镜像前缀迁入上游等价设置。原有桌面应用和存储页面未提交修改已恢复。

上游集中翻译与本分支分文件翻译产生 171 项重复定义。已核对格式参数一致，保留分文件译文并移除主文件重复项；默认资源和简体中文资源均无重复键。

## 真实滑动结果

前两轮来自上游合并前的优化测试包；后两轮为整合后的启动限帧排查及修复验收。

Android 触摸输入在 Steam 首页游戏卡片上交替横移 400 像素，每次 1 秒，手势间隔约 80 ms。SurfaceFlinger timestats 与去重呈现时间为主要依据；页面 rAF 仅观察上游节奏。

| 同一测试包与原分辨率 | 手势数 | 整段 SurfaceFlinger FPS | 手势中央区间 FPS | 滑动区间 P95 |
|---|---:|---:|---:|---:|
| 第一轮约 62 秒 | 52 | 106.980 | 107.57 | 12.12 ms |
| 第二轮约 61 秒 | 53 | 80.434 | 87.36 | 18.18 ms |
| 上游整合后，触发启动 `-r 60` | 53 | 49.285 | 51.67 | 24.24 ms |
| 最终包，取消意外启动限帧 | 53 | 102.353 | 103.49 | 12.12 ms |

第二轮前尝试临时切换 1920×1200，Xwayland 返回 `BadMatch`，所以仍是原分辨率复测。不能将文件名 `menu-resolution-1920` 当成实际使用了低分辨率的证据。结束时恢复命令成功。两轮结果说明当前体验确有波动，不能挑选较好的一轮宣称稳定 120。

第一轮只有一个游戏图层。SurfaceFlinger 计数 6370 帧，轮询捕获窗口内 6368 个唯一呈现时间，覆盖约 99.97%；图层 droppedFrames 为 1916。该字段是合成器丢弃计数，不等于错过物理刷新的次数。每次手势开头/结尾各剔除 100 ms，中央区间共 41.6 秒，仍可能受菜单边界和吸附动画影响。

第一轮页面 rAF 接近 165 Hz，HUD 的 30 次一秒快照均值为 103.93 fps，与整段 SurfaceFlinger 的 106.98 fps 相差约 2.85%。快照和整段窗口没有严格对齐，不能把这个差值解释成精确计数误差。另一组 60 秒持续动画中，HUD 快照均值 113.965、SurfaceFlinger 114.221，相差约 0.22%，只用于核验 HUD。

165 Hz 面板的呈现间隔按约 6.06 ms 量化，常见 6.06/12.12 ms 交替；个别一秒窗口超过 120 fps 不代表均匀稳定的 120 fps。

最终包实测约 60 秒、53 次真实滑动，只有一个游戏图层，HUD 30 次快照均值 102.67 fps，SurfaceFlinger 102.353 fps，差约 0.31%。图层 droppedFrames 为 1609，说明提高上游节奏后，后段交付仍有损失。页面回调主要恢复约 6.06 ms 节奏，整段 rAF 平均 143.16 Hz；触发启动限帧的一轮主要约 18.18 ms，整段 rAF 平均 72.54 Hz。这些回调频率不能当成上屏 FPS。

最终实机会话 `2026-10-18-11-steam` 为 READY、未暂停，分辨率 3040×1904、实际输出 165 Hz。进程参数已确认不含意外的 `-r 60`。采样监听器、诊断数组和设备时钟小程序已清理，timestats 已关闭，Steam 保持运行。

## 剩余瓶颈与撤销的实验

原 HUD 在收到 buffer 时就计数，此后仍可能被替换、拷贝失败或被 SurfaceFlinger 丢弃，解释了原数字高于体感的实现原因。

显示链路仍是 gamescope dma-buf → DroidDeck 全屏拷贝 → 线性 AHardwareBuffer → SurfaceFlinger。日志为 `zero-copy=0`、`layer copy`，最终 HWC 表中游戏层为 `CLIENT` 且需要旋转，Android RenderEngine 也在使用 GPU 合成。现场还有 Android 控件和第三方悬浮层，尚不能单独归因于其中某层。

第一轮真实滑动的一个 10 秒窗口中，render_scene 平均 3.32 ms、最大 22.90 ms，GPU fence 等待平均 2.82 ms、最大 22.55 ms。尖峰超过 120 fps 的 8.33 ms 预算。等待包含 GPU 排队和调度，不能全部算作纯拷贝成本，也不能直接与 RenderEngine 耗时相加。图层池未出现耗尽丢弃，暂不支持增加 buffer 数量。

- 标准 AHB 导入原型在当前自定义驱动上出现压缩布局错误和条纹，已全部撤销，画面恢复正常。
- 提前发送 Wayland frame callback 的实验没有确认收益，且样本期间发生图层重建，已恢复原时机。
- 没有删除 GPU fence wait，也没有宣称零拷贝。完整异步拷贝还需独立在途命令、导出 acquire fence，以及源/目标 buffer 生命周期管理，尚未实施。
- 未修改持久分辨率、驱动和 GPU 调试选项；没有取得有效的低分辨率对照。

早期左右按键和 CSS 平移动画与最终触摸拖动负载不同，不能直接计算前后提升比例。静态菜单停止提交新画面时，呈现 FPS 可降到 0，面板仍在刷新。

## 验证与构建边界

上游整合前，原生 5 项测试（ASAN/UBSAN）与 JVM 2 项定向测试通过，Release 构建及 lintVital 通过。现有 AppLanguageTest 的旧泛型调用导致测试编译失败，仅本地构建脚本排除该文件，未修改其源码，未宣称全量测试通过。

上游整合后，原生库重新编译成功，原生 5 项测试、实时输出/启动限帧 2 项、Steam 下载 10 项、GitHub 路由 1 项、JVM 12 项均通过。JVM 覆盖模式请求、输出/图层频率、镜像配置迁移和断点续传；Kotlin/Java 与测试源码编译通过。保留上游 PerfModeTest，但本轮未执行其 API 31/34 测试，不宣称全量测试通过。日志为 `build/frame-pacing-tools/upstream-build.log`。

触摸命中修复的 agent、桌面启动和输出节奏 46 项回归通过，覆盖页面未就绪重试和超时退出；最终打包及 lintVital 通过。新包不保留 BGRA / vkCmdCopyImage 实验，原生源文件已恢复并重新编译。日志为 `build/frame-pacing-tools/touch-scroll-tests.log`、`touch-fix-apk.log`。

整合后 `assembleRelease` 和 lintVital 通过，实验包为 `app/build/outputs/apk/release/DroidDeck-frame-pacing-upstream-experimental.apk`，已安装到平板，Steam 首页正常显示，无条纹，会话 READY。包内原生库与当前构建一致，包含上游新增的手部模型资源和更新后的会话脚本。

SHA-256：`50827527a10634df00c1df7b8b8d03004b91c94e616b979e6f955d8e1e5dd0b8`。

当前安装的触摸修复实验包：`app/build/outputs/apk/release/DroidDeck-menu-swipe-fix-experimental.apk`。

SHA-256：`d81eea694b1edccd0afc354da3ba2ecc3687cace85d6311614325347f8525723`。包内 agent、会话脚本及原生库与当前构建已逐项比对；前一个 upstream 实验包保留作旧对照。

本地 Windows NDK 不完整，原生库使用 WSL NDK 25c 构建，保留 LSFG/Win-FG。实验包复用基础 APK 已准备的运行时二进制并叠加当前资源、脚本，跳过缺失准备产物的 attestation 检查；未重建上游全部 Linux/VR 二进制，不能代替正式发布流水线。较早的 `DroidDeck-frame-pacing-experimental.apk` 保留为合并前对照。

本地备份位于 `build/frame-pacing-before-upstream/`，含工作文件压缩包、tracked patch、原 HEAD 和 stash 标识；stash 保留供恢复。

## 原始证据

本地 `.codex-remote-attachments/frame-pacing/` 保存 `menu-live-refresh-final*`、`menu-upstream-integrated*`、`menu-verified-final*`、`menu-resolution-1920*`、`menu-native-final*`、`menu-early-callback*`、早期基线及 SurfaceFlinger 转储。`probe.py` 保存手势、设备单调时钟、呈现时间、页面回调和 HUD 快照。原始转储含设备窗口信息，不随报告提交。

SurfaceFlinger latency 仅保留有限滚动窗口，读取 HUD 必须独立执行，避免轮询漏帧；早期串行轮询产生的长间隔不用于最终结论。

Android [SurfaceFlinger timestats](https://developer.android.com/topic/performance/issues/slow-session) 与 [SurfaceTransaction 呈现回调及 fence API](https://developer.android.com/ndk/reference/group/native-activity) 支持这里对收到、提交、合成与呈现的区分。

Gamescope [启动参数源码](https://github.com/ValveSoftware/gamescope/blob/3.16.29/src/main.cpp) 将 `-r` 定义为 nested/game refresh，默认值为 0；该参数不是 Android 物理显示模式请求。
