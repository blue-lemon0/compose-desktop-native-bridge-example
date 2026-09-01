# 安装程序到底怎么来的？—— jpackage 与 WiX 原理

> 一句话回答：**项目没有写任何安装页面，安装程序 100% 由 JDK 自带的 `jpackage` 工具
> 生成**，Windows 下它调用微软开源的 **WiX Toolset** 来产出安装器。

---

## 1. 打包流水线全景

Compose Desktop 的 `:-packageDistributionForCurrentOS` 任务背后是一条完整链路：

```
你的 jar + 依赖 jar + skiko dll
        │
        ▼
  ① Compose 插件 组装 "app image"（目录形态：exe启动器 + app/ + runtime/）
        │
        ▼
  ② JDK 自带 jpackage   （可独立使用，不依赖 Gradle）
        │   Windows 下需要 WiX
        ▼
  ③ WiX Toolset  (candle.exe 编译 .wxs, light.exe 链接 .msi)
        │
        ▼
  ④ 最终产物
     ├── BubbleWrap-1.0.0.exe   ← 自解压安装器（配了 WiX 时）
     └── BubbleWrap-1.0.0.msi   ← MSI 安装包
```

构建日志中能看到这个过程：
```
> Task :downloadWix       ← 第一次从 GitHub 下载 wix311-binaries.zip
> Task :shared:createRuntimeImage   ← jlink 精简 JRE
> Task :shared:packageExe
> Task :shared:packageMsi
```

---

## 2. jpackage 是什么

**jpackage** 是 JDK（9+ 引入，正式功能在 14+）自带的命令行打包工具，用来把 Java 应用
打包成原生安装程序。它**不是** Compose 独有的——任何 JVM 应用都能用。

关键能力：
- **jlink** 生成精简运行时：只包含应用真正用到的 Java 模块，而不是完整 JRE
- 生成平台原生启动器（Windows 的 `.exe`，macOS 的 `.app`，Linux 的 bin）
- 调用平台安装工具（Windows 用 WiX，macOS 用 pkgbuild，Linux 用 fpm/deb/rpm）

## 3. 安装后的目录为什么长这样

安装器本质上是一个**自解压**，把构建时的 "app image" 原样解压到目标目录：

```
BubbleWrap/
├── BubbleWrap.exe     # 启动器：定位 runtime，读 cfg，启动 JVM
├── app/
│   ├── BubbleWrap.cfg # 元数据：mainclass + classpath + JVM 参数
│   ├── *.jar          # 你的类 + 依赖
│   └── skiko-windows-x64.dll
└── runtime/           # jlink 精简 JRE（最小可用 JVM）
    ├── bin/java.exe
    └── lib/modules    # 模块镜像，只含用到的模块
```

## 4. BubbleWrap.exe 启动时做了什么

`BubbleWrap.exe` 是一个**非常小的本地启动器**（0.57MB），它：
1. 读取同目录 `app/BubbleWrap.cfg`
2. 用 `runtime/bin/java.exe` 启动 JVM
3. 把 cfg 里的 classpath 全部加入
4. 调用 `app.mainclass=bubblewrap.MainJvmKt` 的 `main()`

配置里关键的 JVM 参数：
```
-Dskiko.library.path=$APPDIR     # 告诉 skiko 到 app/ 目录找渲染 dll
-Dcompose.application.resources.dir=$APPDIR\resources  # 资源目录
```

## 5. 有没有"免安装"形态？

有。jpackage 支持 `--type app-image`（即"目录形态"，不解压不安装），
Compose 里对应 `nativeDistributions.targetFormats` 之外的 `PackageType.AppImage`。
产物就是一个可直接双击运行的目录，无需安装器、无需管理员权限。

## 6. 与 native exe 的本质区别

| | native exe | jpackage 安装版 |
|---|---|---|
| 运行时 | 无 JVM，机器码 | 内嵌精简 JRE |
| 启动 | OS 直接加载执行 | 先起 java.exe 再跑字节码 |
| 安装 | 无需安装，拷贝文件夹 | 需要安装器解压到 Program Files |
| 速度 | 冷启动更快 | 有 JVM 启动开销 |
