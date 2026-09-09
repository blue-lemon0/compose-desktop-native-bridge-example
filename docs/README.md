# Bubble Wrap — 学习文档

本项目是一个 Compose Multiplatform "泡泡纸模拟器"，同时演示了
[compose-desktop-native](https://github.com/bitsycore/compose-desktop-native) 的
**bridge（桥接）插件**：一套共享的 Compose 代码，跑在三种完全不同的底层上：

1. **Android** — Google 的 `androidx.compose.*` 构件
2. **JVM（Compose Desktop）** — 标准 JVM 桌面，内嵌精简 JRE
3. **Native（Kotlin/Native）** — 自包含原生可执行文件，**没有 JVM**，用 SDL3 + Skia 渲染

本节文档目标是让你从零理解这个项目的每一层，尤其是大多数人最困惑的
"同一份代码怎么既能编译成 APK、又能编译成 exe"。

---

## 目录

- [1. 项目结构](#1-项目结构)
- [2. 一套代码，三种底层：bridge 插件做了什么](#2-一套代码三种底层bridge-插件做了什么)
- [3. 两条桌面路径的本质差异（native vs JVM）](#3-两条桌面路径的本质差异native-vs-jvm)
- [4. 打包原理：native 文件夹 vs jpackage 安装程序](#4-打包原理native-文件夹-vs-jpackage-安装程序)
- [5. 平台差异与难点](#5-平台差异与难点)
- [6. 疑难排查（Troubleshooting）](#6-疑难排查troubleshooting)
- [7. 版本与依赖地图](#7-版本与依赖地图)
- [8. Git 协作：fork 工作流](#8-git-协作fork-工作流)

---

## 1. 项目结构

```
compose-desktop-native-bridge-example/
├── settings.gradle.kts          # 仓库(仓库源)、插件版本声明、bridge 插件
├── build.gradle.kts             # 根构建脚本：版本定义
├── local.properties             # sdk.dir=Android SDK 路径（本机）
├── gradle/wrapper/              # Gradle 版本固定（镜像加速）
├── androidApp/                  # 纯 Android 壳
│   └── build.gradle.kts         # com.android.application，一个 Activity
├── shared/                      # 核心：整个 App 作为 KMP 库
│   ├── build.gradle.kts         # 所有 source set + 桌面打包配置
│   └── src/
│       ├── commonMain/kotlin/App.kt          # 唯一的 UI（所有平台共用）
│       ├── jvmMain/kotlin/MainJvm.kt         # JVM 入口
│       ├── nativeMain/kotlin/Main.kt         # Native 入口
│       └── commonMain/composeResources/      # 图片、字符串
└── docs/                        # 本学习文档
```

**核心：`shared/src/commonMain` 只包含一份纯 Compose 的 `App()`**，没有任何平台代码。
平台差异全部收敛在 `jvmMain/MainJvm.kt` 和 `nativeMain/Main.kt` 两个入口函数里。

---

## 2. 一套代码，三种底层：bridge 插件做了什么

`shared/build.gradle.kts` 里声明的是**官方** `org.jetbrains.compose.*` 坐标：

```kotlin
implementation("org.jetbrains.compose.ui:ui:$cmp")
implementation("org.jetbrains.compose.material3:material3:$cmpMaterial3")
```

而 `settings.gradle.kts` 里有一行关键插件：

```kotlin
plugins { id("com.bitsycore.compose-desktop-native.bridge") version "0.4.1" }
```

**bridge 插件的作用**：在 Kotlin/Native 桌面 target 上，把 `org.jetbrains.compose.*`
的坐标**替换**（substitute）成 compose-desktop-native 自己的 klib。其他 target（Android、
JVM）保持用官方构件。

这样：
- **Android**：`org.jetbrains.compose.*` 已经会重定向到 Google 的 `androidx.compose.*`
- **JVM**：用官方 Compose Desktop（AWT/Swing 窗口，Skia 渲染）
- **Native**：用 bitsycore 的 klib（SDL3 窗口 + Skia 渲染，无 JVM）

一句话：**同一份 `App()` 代码，三种不同的"窗户"和"画布"，bridge 负责在编译期切换。**

> 底层原理：Gradle 的 "dependency substitution" 机制。用户代码永远写官方坐标，
> 插件在解析时把它们替换成自己编好的原生 klib。除非遇到版本元的依赖元数据问题
> （见第 7 节），否则用户无感知。

---

## 3. 两条桌面路径的本质差异（native vs JVM）

这是理解本项目最关键的一节。两条路径**各自独立**，但代码共享：

| 维度 | **native** | **JVM（Compose Desktop）** |
|------|-----------|------------------------------|
| 入口函数 | `bubblewrap.main()` | `bubblewrap.MainJvmKt.main()` |
| 入口文件 | `nativeMain/kotlin/Main.kt` | `jvmMain/kotlin/MainJvm.kt` |
| 窗口实现 | `nativeComposeWindow(...)`（SDL3） | `Window{ application }`（AWT/Swing） |
| 运行时 | **没有 JVM**，编译成机器码 | 需要 JVM（精简版 jlink） |
| 渲染 | Skia（原生链接） | Skia（通过 JNI → skiko dll） |
| 产物 | `shared.exe` + 几个文件 | 安装程序 / 可分发目录 |
| 启动 | 直接执行机器码 | Java 虚拟机启动 |

**关键理解**：两者是**两条独立的编译产物**，不是同一个 exe 的两种模式。
`./gradlew :shared:linkDebugExecutableMingwX64` 产出 native exe；
`./gradlew :shared:packageDistributionForCurrentOS` 产出 JVM 安装程序。
它们都加载同一个 `App()`，但各自编译、各自打包、各自运行。

---

## 4. 打包原理：native 文件夹 vs jpackage 安装程序

### 4.1 Native 打包 → 一个文件夹

`./gradlew :shared:linkDebugExecutableMingwX64` 产物在
`shared/build/bin/mingwX64/debugExecutable/`：

```
shared.exe                  # 37MB — 自包含可执行文件（SDL3 已静态编入）
skiko-windows-x64.dll      # 13.5MB — Skia 渲染动态库
icudtl.dat                 # 10MB  — ICU 国际化数据(文字排版)
data.kres                  # 1.8KB — Compose 资源包(bubble.png + 字符串)
```

**重要**：README 说 "distributable is just the executable"，但实测 `data.kres`
和 `icudtl.dat` 是运行时必需的资源，应随 exe 一起分发。**稳定分发 = 整个文件夹**。

### 4.2 JVM 打包 → jpackage 安装程序

`./gradlew :shared:packageDistributionForCurrentOS` 产物在
`shared/build/compose/binaries/main/`，`BubbleWrap-1.0.0.exe`（安装器）和 `.msi`。

安装后目录结构（以安装到 `C:\Program Files\BubbleWrap` 为例）：

```
C:\Program Files\BubbleWrap\
├── BubbleWrap.exe        # 0.57MB — jpackage 启动器(launcher)，不含业务逻辑
├── app/
│   ├── BubbleWrap.cfg    # 启动配置：mainclass + classpath + JVM 参数
│   ├── shared-jvm-*.jar  # 你自己的应用代码
│   ├── *-desktop-*.jar   # 全部依赖 jar
│   └── skiko-windows-x64.dll
└── runtime/              # jlink 精简出的 JRE
    ├── bin/              # java.exe + 系统 dll
    └── lib/modules       # 49MB — 只有用到的 Java 模块
```

**BubbleWrap.cfg 关键内容**：
```
app.mainclass=bubblewrap.MainJvmKt
java-options=-Dskiko.library.path=$APPDIR   # 告诉 skiko 去哪找渲染 dll
```

### 4.3 安装程序是怎么实现的？

**不是自己写的，也没有自定义安装页面。** 完整链路：

```
Compose 打包插件(org.jetbrains.compose)
        │  把 jar + runtime + 配置 组装成 "app image"
        ▼
JDK 自带工具 jpackage
        │  Windows 下调用微软 WiX Toolset
        ▼
WiX (candle + light) 生成安装器
        │
        ▼
BubbleWrap-1.0.0.exe（可双击执行，里面打包了上面整个目录）
```

- **UI**：安装向导界面是 **WiX 提供的默认 UI**，项目没有写任何安装页面代码。
- **依赖**：`wix311-binaries.zip` 在第一次打包时从 GitHub 自动下载（构建日志见过
  `> Task :downloadWix`）。
- 安装器本质上是一个 **自解压程序**：把 `app/` + `runtime/` 解压到目标目录，
  并创建开始菜单 / 添加删除程序注册表项。

---

## 5. 平台差异与难点

### 5.1 依赖来源（这是本项目最大的坑）

本项目依赖来自**四个不同仓库**，很多坐标只在特定仓库存在：

| 仓库 | 用途 |
|------|------|
| **Google Maven** `dl.google.com/.../maven2` | `androidx.lifecycle:*:2.11.0`、`androidx.compose.runtime:runtime-retain:1.11.2` 的 native klib |
| **Maven Central** | `org.jetbrains.compose.runtime:runtime-saveable-mingwx64` 等 |
| **GitHub Packages (bitsycore)** | bridge 插件、`com.bitsycore.compose:*`、skiko fork |
| **JetBrains Space** | Compose dev 版 |

**坑**：`androidx.lifecycle`、`runtime-retain` 这些坐标的 mingwX64 klib **只在 Google Maven**，
不在 Maven Central，也不在阿里云镜像。如果配置里 `google()` 被镜像顶掉或顺序错误，
就会报 "Could not find ... klib"。本机已配置 `google()` 在前、阿里云镜像仅做尾部兜底。

### 5.2 为什么首次链接要 8 分钟？

`linkDebugExecutableMingwX64` 首次要：
1. 从 Google Maven / Central / GitHub Packages 拉取**每一个 native klib**（几百个）
2. Kotlin/Native 把 klib 编译成机器码
3. 链接所有 klib + SDL3 静态库
4. provisioning skiko DLL 和 ICU 数据

这些都无法缓存到磁盘时极慢；二次构建因配置缓存会快很多。

### 5.3 需要 JDK 21+（用 JDK 24）

bridge 插件需要 JVM 21+。本机参数：
```
JAVA_HOME = C:\Users\<你的用户名>\.jdks\openjdk-24.0.1
PATH 追加  msys64\mingw64\bin（链接器 gcc）
```

---

## 6. 疑难排查（Troubleshooting）

### 6.1 native exe 双击"黑窗口/闪退"

**症状**：双击 `shared.exe` 时，先弹出一个**黑色控制台窗口**，然后才出现真正的泡泡纸窗口。

**根因**：Kotlin/Native **默认把 Windows 可执行文件编译成 "console (CUI)" 子系统**。
CUI 程序由系统提供一个控制台承载 stdout/stderr，所以双击时 Windows 先开黑窗。
对比：jpackage 生成的 `BubbleWrap.exe` 是 "Windows GUI (2)" 子系统，所以无黑窗。
（通过 PE 头的 Optional Header → Subsystem 字段区分：`2`=GUI，`3`=CUI。）

**修复**：给 native executable 加链接器参数指定 GUI 子系统即可，在 `shared/build.gradle.kts`：
```kotlin
mingwX64 {
    binaries {
        executable {
            linkerOpts("-Wl,--subsystem,windows")
        }
    }
}
```
说明：
- 必须用 `binaries.executable { linkerOpts }`（binaries DSL），**不是** compilation 的 `linkerOpts`
  —— 后者只对旧式 `compilation.outputKinds` 生效，不作用于 binaries DSL 创建的 exe。
- `linkerOpts` 是**累加**的，不会覆盖 bridge 插件注入的图标参数和 entryPoint。
- 改后验证：重新 `linkDebugExecutableMingwX64` → PE subsystem 变成 `GUI (2)` → 双击无黑窗。
- `stdout`/`stderr` 会失去控制台（GUI 程序本来就不需要），但如果仍需看日志，建议写文件。

> 附带说明：真正**闪退**（窗口全无）时，多半是 `data.kres` 不在 exe 旁 / 工作目录不对，
> 资源加载失败即退出。稳定分发始终应包含完整文件夹
> （exe + skiko-windows-x64.dll + icudtl.dat + data.kres）。

### 6.2 native 链接报 "Could not find ...-mingwx64-*.klib"

**原因**：这些坐标发布在 **Google Maven**。确认 `google()` 在依赖仓库列表里且排在镜像前，
不要用阿里云 google 镜像顶掉官方 `dl.google.com`。

### 6.3 Android 安装后崩溃（资源缺失）

**修复**：`shared/build.gradle.kts` 增加
```kotlin
android { androidResources.enable = true }
compose.resources { publicResClass = true }
```
否则 Android 产物里缺少 `assets/composeResources/...`，访问 `Res.*` 崩溃。

### 6.4 缺 JDK

报 JVM 相关错误时，检查 `JAVA_HOME` 是否为 21+。bridge 0.4.1 用 JDK 24 验证通过。

---

## 7. 版本与依赖地图

| 组件 | 版本 |
|------|------|
| Gradle | 9.6.1（腾讯云镜像下载） |
| Kotlin | 见 `settings.gradle.kts` `kotlinVersion` |
| Compose Multiplatform | `composeMultiplatformVersion`（约 1.12.0-beta02） |
| bridge 插件 | 0.4.1 |
| compose-desktop-native | 0.4.1 |
| androidx.lifecycle | 2.11.0 |
| compose.runtime | 1.11.1（native）/ 1.11.2（原子族） |
| skiko（bitsycore fork） | 见编译日志 |

**本机关键配置**（不在 git 里的）：
```
local.properties      →  sdk.dir=<你的 Android SDK 路径>
~/.gradle             →  gpr.user / gpr.token（GitHub Packages 认证）
```

---

## 8. Git 协作：fork 工作流

main 保持纯净、改动放旁支的 Fork 维护模型，以及同步官方 / 合并改动 / 发起 PR 的常用命令。

[阅读完整文档 → fork-workflow.md](fork-workflow.md)
