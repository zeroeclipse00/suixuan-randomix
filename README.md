# 随选 Suixuan

[![Android framework checks](https://github.com/zeroeclipse00/suixuan-randomix/actions/workflows/android.yml/badge.svg)](https://github.com/zeroeclipse00/suixuan-randomix/actions/workflows/android.yml)

基于 AppForge `RandomixDecisionMaker` 选题设计的离线随机决策工具箱，计划提供转盘、硬币、骰子、灵感球、字母抽取、情境模板和历史记录。

**当前已有可构建运行的 Android 框架：共享领域与 Room 数据层、转盘和历史最小流程已验证；其他工具保留独立待开发页面，便于组员继续分工。**

## 文档导航

| 文档 | 主要回答的问题 |
|---|---|
| [方案总览](实验2-随选-模块化任务方案.md) | 做什么、当前状态与阅读顺序 |
| [需求与范围](docs/requirements.md) | 课程约束、用户场景、核心与选做功能 |
| [架构与数据](docs/architecture.md) | 数据模型、接口、抽取状态和恢复规则 |
| [模块任务](docs/modules.md) | 每个模块要实现什么、依赖什么、如何完成 |
| [UI 与交互](docs/ui-design.md) | 页面、导航、视觉和异常状态 |
| [实施阶段](docs/implementation.md) | 按依赖组织工作，不设时间任务表 |
| [开发环境搭建](docs/development-environment.md) | 组员从干净克隆配置 JDK/SDK、构建、测试与设备排查 |
| [框架交接](docs/handoff.md) | 当前功能边界、组员任务包、共享接口与接手步骤 |
| [框架验证结果](docs/testing/framework-results.md) | 本轮实际构建、26 个 JVM 测试、10 个设备测试与限制 |
| [测试与验收](docs/testing/acceptance.md) | 需求对应测试、关键场景与通过条件 |
| [公开过程记录](docs/process/README.md) | 已完成工作、Token 口径与隐私边界 |

## 计划技术路线

Kotlin、Jetpack Compose、Material 3、ViewModel 和 Room。具体依赖版本将在环境核验与工程建立阶段固定。

当前转盘和历史无需联网；首次构建需下载官方依赖，网络与缓存配置见开发环境文档。

## 运行框架

按开发环境文档配置 JDK/SDK 后，Windows 可运行：

```powershell
.\scripts\android.ps1 :app:testDebugUnitTest :app:assembleDebug
```

macOS/Linux 使用 `./gradlew :app:testDebugUnitTest :app:assembleDebug`。Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。连接设备后再运行 `:app:connectedDebugAndroidTest`，详细步骤见开发环境文档。

## 开发轨迹与隐私

本仓库保留脱敏的实际工作摘要、功能状态、验证说明和聚合 Token 统计。原始对话、逐响应用量、会话标识、个人路径、课程附件及原始截图保留在本地，受 `.gitignore` 排除。详见[记录与公开规则](docs/process/privacy-and-recording.md)。

本地记录工具使用 Python 标准库：

```powershell
python scripts/sync_vibe_trace.py
python scripts/export_public_usage.py
```

同步工具需要先配置本地会话来源与记录边界；克隆仓库后不能直接获得原作者的私有记录。配置方式和示例见[工具使用说明](docs/process/tooling.md)。公开导出只保留阶段、调用数量和聚合用量，不复制原始对话或标识。

## 选题来源与实验交付

- [AppForge 任务列表](https://github.com/AppForge-Bench/AppForge/blob/main/tasks/tasks.json)
- [Randomix 原项目](https://github.com/m-i-n-a-r/randomix)
- [AppForge 参考论文](https://arxiv.org/abs/2510.07740)

课程最终交付包含源码与开发轨迹、1—2 分钟演示视频，以及不超过 6 页的开发过程、Token 消耗和 AI 使用反思报告。原课件和论文附件不随仓库分发。
