# 开发环境搭建与验证

本页面向从干净克隆开始的组员，配置文件以仓库当前版本为准。自行安装 JDK 和 Android SDK 即可，不需要复制其他成员的 `.local-tools/`、缓存、代理配置或 Android Studio 设置。

本轮已安装 SDK Command-line Tools 17.0、Platform Tools、Android 35 平台和 Build Tools 35.0.0，并完成下载校验；本机使用已有 JDK 18，组员建议统一 JDK 17。Gradle 与 API 35 模拟器启动、Debug 构建、26 个 JVM 测试、10 个设备测试和 lint 已实际通过，见[框架验证结果](testing/framework-results.md)。JDK 17 的独立构建由仓库 CI 继续核验，不把本机 JDK 18 的结果冒充为 17 的结果。

## 1. 固定版本与原因

| 项目 | 本仓库配置 | 说明 |
|---|---|---|
| Android Gradle Plugin | 8.9.2 | AGP 8.9 的官方兼容基线包括 Gradle 8.11.1、JDK 17、Build Tools 35.0.0 和最高 API 35 |
| Gradle | 8.11.1 | 使用仓库官方 Wrapper，无需安装全局 Gradle |
| Kotlin / Compose 编译插件 | 2.1.20 / 2.1.20 | 编译插件与 Kotlin 同版本；不要另填旧版 Compose Compiler 扩展版本 |
| KSP | 2.1.20-1.0.32 | 随仓库固定，生成 Room 代码；升级 Kotlin 时需一起检查 |
| Compose BOM | 2025.04.01 | 固定本项目 UI 依赖组合，不随官网示例自动升级 |
| Room | 2.7.1 | runtime、ktx、compiler 和测试库保持一致 |
| Java 源码 / Kotlin JVM 目标 | 17 / 17 | 推荐组员使用 JDK 17 运行 Gradle；不等于手机需要 Java 17 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 | 编译需要平台 35，设备最低 Android 8.0/API 26 |
| SDK Build Tools | 35.0.0 | 与本次构建基线一致 |
| SDK 命令行工具 | 本次搭建为 17.0 | Studio 路线可管理本机工具；记录自己的实际版本 |

版本依据见 [AGP 8.9 官方兼容表](https://developer.android.com/build/releases/agp-8-9-0-release-notes)、[Java 与 Android 构建](https://developer.android.com/build/jdks)、[Compose 编译插件配置](https://developer.android.com/develop/ui/compose/compiler)。版本锁定服务于复现，不表示这是所有依赖的最新版本；遇到兼容警告先留证并核对，勿一次升级整套工具链。

Wrapper 的 `gradle/wrapper/gradle-wrapper.properties` 固定发行包 SHA-256：

```text
f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6
```

Wrapper 下载时校验此值；不要通过删除校验来解决下载损坏。原理见 [Gradle Wrapper 官方文档](https://docs.gradle.org/8.11.1/userguide/gradle_wrapper.html)。本页版本选择必须结合实际构建验证，不能只凭兼容表认为全部任务已通过。

## 2. 路线 A：Android Studio，推荐

1. 从 [Android Studio 官网](https://developer.android.com/studio)安装支持 AGP 8.9 的版本，打开仓库根目录，而不是仅打开 `app/`。
2. 在 SDK Manager 安装 **Android SDK Platform 35**；在 SDK Tools 中安装 **Build Tools 35.0.0、Platform Tools、Command-line Tools**。需要模拟器时另装 Android Emulator 与相应系统镜像。接受自己确认过的 SDK 许可证。
3. 在 `Settings > Build, Execution, Deployment > Build Tools > Gradle` 选择 JDK 17，Gradle 分发选择仓库 Wrapper。Studio 自己的运行 JDK 与构建项目的 Gradle JDK 是两回事。
4. 确认项目本地 `local.properties` 的 `sdk.dir` 指向自己的 SDK。Studio 通常自动生成；若没有，可使用下一节的本地配置命令。这个文件已忽略，不提交。
5. 让 Gradle Sync 完成。不要接受 IDE 自动升级 AGP、Gradle 或 Kotlin 的建议作为首次运行步骤。
6. 新开 PowerShell，将 `JAVA_HOME` 配为同一个 JDK 17，再运行下方验证命令。Studio 可通过 Gradle 面板运行相同任务。

Windows 本地配置示例：在仓库根目录执行，提示时填写自己的安装目录；文档不包含任何成员的实际路径。

```powershell
$env:JAVA_HOME = Read-Host '输入本机 JDK 17 根目录（不是 bin 目录）'
$taskSdkDir = (Read-Host '输入本机 Android SDK 根目录').Trim()
$env:ANDROID_HOME = $taskSdkDir
$taskSdkProperty = $taskSdkDir.Replace('\', '/')
Set-Content -LiteralPath .\local.properties -Value "sdk.dir=$taskSdkProperty" -Encoding ascii
& (Join-Path $env:JAVA_HOME 'bin/java.exe') -version
.\scripts\android.ps1 --version
.\scripts\android.ps1 :app:testDebugUnitTest :app:assembleDebug
```

这里用 ASCII 写入 Java properties，SDK 路径建议选择仅含英文、数字和常规分隔符的目录。若 SDK 位于包含非 ASCII 字符的位置，优先让 Android Studio 正确生成 properties 转义，不直接使用上述 ASCII 写入命令。

`JAVA_HOME` 示例只对当前终端生效；需要长期使用时在操作系统用户环境变量中配置，并同步 Studio 的 Gradle JDK。不要把本机 JDK 路径写入公开 `gradle.properties`。

## 3. 路线 B：仅命令行

先安装 JDK 17，再从 [官方 Command-line Tools 下载入口](https://developer.android.com/studio#command-tools)获取适合操作系统的工具。Windows 可使用本机公共 SDK，也可在忽略目录 `.local-tools/android-sdk/` 下建立独立 SDK。

解压后的目录结构需要是 `SDK根/cmdline-tools/latest/bin/sdkmanager`，而不是多嵌套一层 `cmdline-tools`。`latest` 仅作为初始工具入口；需要复现本次工具版本时用它安装 `cmdline-tools;17.0`。以下命令假设已把 `ANDROID_HOME` 设置为本机选定 SDK 根目录：

```powershell
$taskSdkManager = Join-Path $env:ANDROID_HOME 'cmdline-tools/latest/bin/sdkmanager.bat'
& $taskSdkManager "--sdk_root=$env:ANDROID_HOME" --install 'cmdline-tools;17.0' 'platform-tools' 'platforms;android-35' 'build-tools;35.0.0'
& $taskSdkManager "--sdk_root=$env:ANDROID_HOME" --licenses
& $taskSdkManager "--sdk_root=$env:ANDROID_HOME" --list_installed
.\scripts\android.ps1 --version
.\scripts\android.ps1 :app:testDebugUnitTest :app:assembleDebug
```

若工具已装在 `cmdline-tools/17.0/`，把命令中的 `latest` 改为 `17.0` 即可。`sdkmanager` 的包名包含分号，在 PowerShell 必须保留引号。许可证需逐项阅读并由安装者接受。使用方法见 [sdkmanager 官方说明](https://developer.android.com/tools/sdkmanager)。

Linux/macOS 设置本机 `JAVA_HOME`、`ANDROID_HOME` 并确保 SDK 可发现；可让 Studio 生成 `local.properties`，或自行写入正确的 `sdk.dir`。然后使用同一 Wrapper：

```bash
chmod +x gradlew
./gradlew --version
./gradlew :app:testDebugUnitTest :app:assembleDebug
./gradlew :app:lintDebug
```

在这些系统中 `sdkmanager`/`avdmanager` 不带 `.bat`，`adb`/`emulator` 不带 `.exe`；安装包名和 Gradle 任务保持不变。Apple Silicon 设备选择与宿主匹配的 ARM64 镜像，不照搬 Windows x86_64 镜像。

## 4. 仓库 Windows 启动脚本与本地文件

`scripts/android.ps1` 将本终端的构建相关缓存放在项目忽略目录：

| 设置 | 位置/行为 |
|---|---|
| `GRADLE_USER_HOME` | `.local-tools/gradle-home/` |
| `ANDROID_USER_HOME` | `.local-tools/android-user/` |
| `ANDROID_AVD_HOME` | `.local-tools/android-avd/` |
| SDK | 只有 `.local-tools/android-sdk/` 存在时才设置 `ANDROID_HOME` 与 `ANDROID_SDK_ROOT` 指向它 |
| `JAVA_HOME` | 已设置时保留；未设置则尝试从 PATH 中的 java 定位，建议自己明确配置 |
| 默认任务 | 不传参数时执行 `:app:testDebugUnitTest :app:assembleDebug` |

脚本不自动安装 JDK/SDK，也不创建模拟器。使用已有 Studio SDK 的组员无需创建 `.local-tools/android-sdk/`；保留自己的 `local.properties` 即可。不要创建空的该目录，否则脚本会把它当作 SDK。若同时配置 SDK 环境变量与 `local.properties`，保持一致。

由脚本启动的 AVD 路径可能与 Studio 默认 AVD 路径不同；使用哪一套就保持创建和启动时的环境一致。Studio 构建也可在本机 Gradle 设置选择相同用户目录，但这属于本地配置。

`.local-tools/`、`local.properties`、`.gradle/`、构建产物、签名密钥和私人代理均不上传。源码、官方 Wrapper 文件、构建脚本和 Room schema 才属于协作材料。

## 5. 构建、单测与静态检查

下面是待执行命令及成功判定，不是已通过声明：

| 检查 | Windows 命令 | 预期产物/成功条件 |
|---|---|---|
| 版本 | `.\scripts\android.ps1 --version` | 输出 Gradle 8.11.1 与实际 JVM 信息 |
| JVM 单元测试 | `.\scripts\android.ps1 :app:testDebugUnitTest` | `app/build/reports/tests/testDebugUnitTest/index.html`，测试执行且零失败 |
| Debug APK | `.\scripts\android.ps1 :app:assembleDebug` | `app/build/outputs/apk/debug/app-debug.apk`，任务成功 |
| Android Lint | `.\scripts\android.ps1 :app:lintDebug` | `app/build/reports/lint-results-debug.html`，无阻断错误；未运行不写通过 |
| 设备测试 | `.\scripts\android.ps1 :app:connectedDebugAndroidTest` | 需已连接设备；结果通常在 `app/build/reports/androidTests/connected/` |

JVM 单测不需要模拟器，但 Android Gradle 工程仍需已配置 SDK 和下载构建依赖。设备测试用于 Room/Android/UI 行为；若任务输出 `NO-SOURCE` 或没有执行测试，不能当作设备行为验证通过。输出路径若因插件变化不同，以 Gradle 日志实际路径为准。

首次联网下载完成后可以尝试 `--offline`，缓存不全时失败是正常现象。不要把应用离线运行与首次构建无需联网混为一谈。

## 6. 模拟器或真机

### Studio 设备路线

在 Device Manager 新建普通手机 AVD，选择 API 35 镜像；Windows/Linux 的 x86_64 主机可选 AOSP x86_64。最低兼容验证还需另选 API 26 及以上设备，不能仅凭 API 35 测试覆盖全部最低版本行为。详情见 [创建 AVD](https://developer.android.com/studio/run/managing-avds)。

本轮 API 35 AOSP x86_64 镜像处于安装准备阶段，尚不表示已经启动成功。无需为了复用方案下载其他成员的整个 AVD 目录。

### Windows 命令行设备路线

将 SDK 的 `platform-tools/`、`emulator/` 加入当前终端 PATH，并保持 `ANDROID_AVD_HOME` 与创建时一致。使用项目隔离目录时可先执行一次 `scripts/android.ps1 --version` 让环境变量生效：

```powershell
.\scripts\android.ps1 --version
$env:Path = "$env:ANDROID_HOME\platform-tools;$env:ANDROID_HOME\emulator;$env:Path"
$taskSdkManager = Join-Path $env:ANDROID_HOME 'cmdline-tools/17.0/bin/sdkmanager.bat'
$taskAvdManager = Join-Path $env:ANDROID_HOME 'cmdline-tools/17.0/bin/avdmanager.bat'
& $taskSdkManager "--sdk_root=$env:ANDROID_HOME" --install 'emulator' 'system-images;android-35;default;x86_64'
& $taskAvdManager create avd --name suixuan_api35 --package 'system-images;android-35;default;x86_64'
emulator -list-avds
emulator -accel-check
emulator -avd suixuan_api35
```

首次创建会询问硬件配置，可使用默认配置或先通过 `avdmanager list device` 查询可用设备。模拟器需在独立终端启动；其运行期间，在第二个终端配置相同 SDK 路径后执行：

```powershell
adb devices
adb wait-for-device
adb shell getprop sys.boot_completed
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n com.suixuan.app/.MainActivity
.\scripts\android.ps1 :app:connectedDebugAndroidTest
```

`sys.boot_completed` 返回 `1` 后再测试。存在多个设备时，先指定目标序列号：ADB 使用 `-s`，Gradle 使用终端本地 `ANDROID_SERIAL`；不要把其他成员的设备标识提交。真机开启开发者模式与 USB 调试，解锁后确认本机授权，`adb devices` 应显示 `device`，而非 `unauthorized/offline`。

## 7. 常见问题

| 现象 | 检查与处理 |
|---|---|
| Java/字节码版本不兼容 | 比较 `java -version`、Wrapper JVM 与 Studio Gradle JDK；统一 JDK 17，变更后先运行 Wrapper `--stop` 再重试。PATH 中 java 若是跳转程序，明确设置 `JAVA_HOME` |
| SDK location not found | 检查 `local.properties` 与 `ANDROID_HOME`；确认不存在误建的空 `.local-tools/android-sdk/`，并核对 `platforms/android-35/` |
| 缺 build-tools/platform | 用 SDK Manager 或 `sdkmanager --list_installed` 核对 35 平台、35.0.0 工具和许可证 |
| License not accepted | 对实际使用的 SDK 根执行 `sdkmanager --licenses`；不能接受另一套 SDK 目录的许可证后认为已解决 |
| 国内网络超时、TLS、依赖无法解析 | 先检查官方 Gradle 分发、Google Maven 与 Maven Central 是否可达；代理仅放本机 Studio 设置或当前 Gradle 用户目录的私有 `gradle.properties`。不提交代理、密码或带凭据 URL，不改成未知来源依赖 |
| 下载校验不匹配 | 保留错误，检查官方来源和文件完整性，重新下载损坏包；不移除 SHA 校验或混用其他版本 ZIP |
| Windows 提示 `Could not move temporary workspace` | 本轮遇到过构建转换缓存目录被占用；停止当前项目的 daemon 后重试通过。可运行 `scripts/android.ps1 --stop` 再执行原任务，保留下载缓存，不批量删除 SDK 或禁用系统安全软件。工程已限制两个 worker 并关闭文件监视 |
| C 盘缓存增长/Studio 与终端重复下载 | Windows 脚本已隔离 Gradle/Android 用户目录；Studio 若使用默认目录仍会另建缓存。先确认各自路径和磁盘空间，再按工具说明处理，不批量删除未知目录 |
| PowerShell 阻止脚本 | 可改用 `gradlew.bat` 并自行设置本机环境；确需运行脚本时只按学校/设备策略配置当前进程权限，不修改整机策略作为默认步骤 |
| 模拟器无硬件加速 | 检查 BIOS/UEFI 虚拟化与 `emulator -accel-check`；Windows 按官方指引配置 Windows Hypervisor Platform，Linux 检查 KVM，macOS 使用对应宿主镜像；也可使用真机 |
| 模拟器黑屏/图形异常 | 先核对镜像与 CPU 架构，重启 AVD，按官方文档尝试合适图形模式；不要清除用户数据作为未说明的默认修复 |
| adb 找不到设备 | 确认 SDK 下同一套 adb、USB 授权/驱动和模拟器开机状态；多设备明确目标，查看 `adb devices` 原因 |
| 依赖升级/IDE建议与仓库不同 | 先按固定版本复现，记录警告或失败；版本变更作为独立任务同时更新配置、验证和文档 |
| GitHub CI 提示找不到 `tools` 包 | 旧 `setup-android@v3` 默认值包含退役包。当前流程已移除该 action，直接使用固定 Ubuntu 24.04 的预装 SDK；用 JDK 21 安装明确所需的包，再切 JDK 17 构建，不请求退役包 |

虚拟化/图形支持取决于实际硬件，参考 [Android Emulator 加速官方文档](https://developer.android.com/studio/run/emulator-acceleration)。不要因为一台模拟器启动失败而把 JVM 单测或已成功构建的 APK 描述为未生成，也不要反过来用构建成功代替设备检查。

## 8. 最小成功判定与组员检查单

环境的最小成功是：Wrapper 输出预期版本；JVM 单测实际执行且通过；Debug APK 成功生成。设备验证另行确认安装、启动、数据与 UI 行为；二者分别记录，不相互冒充。

- [ ] 使用干净克隆与自己的 JDK/SDK，未复制他人的私有工具配置。
- [ ] JDK、SDK 包、Wrapper 和项目固定版本已核对；Studio 与终端选择一致。
- [ ] 单测报告存在且零失败，记录实际执行数量与提交。
- [ ] APK 已生成；若运行 lint，记录其真实结果和报告。
- [ ] ADB 已连接正确设备；安装和启动结果有记录，设备测试实际执行。
- [ ] 人工检查候选新增/编辑/删除、空池/单项、转盘指向与结果、历史一条、重启数据及后台返回。
- [ ] 未实现工具按真实状态显示，未将占位页当成功演示。
- [ ] 公布结果前删除日志里的本机路径、身份和代理；`local.properties`、缓存、密钥仍被忽略。

实际用例和完整完成条件见[验收矩阵](testing/acceptance.md)。环境搭好只说明开发入口可复用，不等于整项课程实验已经完成。
