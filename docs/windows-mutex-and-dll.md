# Windows 有没有"包名互斥"机制？—— 双实例与 DLL 锁

> 问题：Android 里应用有 `packageName`，相同包名不能同时装两个实例。
> Windows 桌面程序也有类似的"包名冲突"机制吗？为什么装了 BubbleWrap 后还能跑 native exe？
> 为什么 native exe 有时会"黑窗口闪退"？

---

## 1. 直接回答

**Windows 桌面程序没有 Android 那样的"包名注册表/唯一实例"机制。**

Android 依靠的是系统级的 `packageName`（包管理服务）+ 四条组件注册
（Activity/Service/Receiver/ContentProvider），相同包名直接无法共存。

Windows 桌面程序是**裸的进程**：OS 只根据**路径**来辨识一个可执行文件。
两个不同路径的 exe（即使功能/代码完全相同）在 Windows 看来就是**两个无关进程**，
可以同时运行、互不干扰。

**实测证据**：`C:\Program Files\BubbleWrap\BubbleWrap.exe`（jpackage 版）运行期间，
同时启动 `shared\build\bin\mingwX64\debugExecutable\shared.exe`（native 版），
**两个进程同时存活，无冲突**。

## 2. "单实例"是软件自己实现的，不是操作系统强制的

如果开发者想让应用"同时只能开一个"，必须**自己写代码**实现，常用几种方式：

| 机制 | 原理 | 说明 |
|------|------|------|
| **命名互斥对象** | `CreateMutex`（Windows API）：同名 mutex 已存在则说明有另一实例 | 最常见，跨进程 |
| **命名管道 / 本地 socket** | 新实例尝试连一个固定端口，连上则说明已有实例，并通知旧实例"带参数激活" | 常用于"激活已有窗口" |
| **全局对象 / `Global\` 命名空间** | 用系统级命名空间锁，跨会话 | 需要权限 |
| **文件锁** | 在固定路径放一个独占锁文件 | 简单但可能被误删 |

本项目（无论 native 还是 jpackage 版）**都没有实现单实例**，所以多个实例能同时开。

## 3. 那你为什么感觉"装了安装版后 native 打不开"？

最可能不是"冲突"，而是**操作细节**：

### 3.1 资源/工作目录问题（最常见）
native exe 依赖旁边的 `data.kres`（资源包）等文件。如果：
- 你只拷了 `shared.exe`，没带 `data.kres`/`icudtl.dat`
- 或双击时工作目录不对，native 找不到资源

SDL 窗口初始化后，资源加载失败直接退出 → 表现为**黑窗口一闪而过 / 无提示闪退**。

### 3.2 DLL 丢失
如果 native exe 单独存在且系统 DLL 缓存里没有 skiko 相关 dll，会报
`0xc0000135`（STATUS_DLL_NOT_FOUND）。这种一般是**系统错误对话框**而不是黑窗。

### 3.3 进程残留
如果之前有 native 进程没被正确关闭（比如异常退出的僵尸实例），会占用文件句柄，
导致新实例启动异常。先看一下任务管理器里有没有 `shared.exe` 残留。

## 4. 关于 DLL 锁：真的会锁吗？

Windows 对**正在被进程加载的 DLL** 会加文件锁。如果一个进程已加载
`skiko-windows-x64.dll`，你**无法删除/覆盖**这个 DLL 文件（文件被占用），会报
"文件正在使用"。

但注意：**两个进程各加载各路径的 skiko dll 是允许的**，只要路径不同就不互斥。
jpackage 版用 `app/skiko-windows-x64.dll`，native 用 `debugExecutable/skiko-windows-x64.dll`，
路径不同，互不干扰。

表：什么时候 DLL 会真的冲突？
| 场景 | 冲突？ |
|------|--------|
| 两个 exe 各自目录各有一份同名 dll | ❌ 不冲突 |
| 同一 exe 反复启动 | ❌ 正常（OS 处理引用计数） |
| 进程 A 运行时，覆盖 A 正在用的 dll | ✅ 会报"文件使用中" |
| 进程 A 用的是 `B` 目录的 dll，B 目录被整个删除 | ✅ 会出错 |

## 5. 结论

1. Windows **没有** Android 式的包名互斥，两个不同路径的同类 exe 可并行运行
2. "装了安装版后 native 打不开"大概率是 native 自身的资源/工作目录问题，不是安装版的锅
3. 若真要"同一应用只能开一个"，需要自己用 `CreateMutex` 实现（本项目未实现）
4. 稳定运行 native：**把 exe 放在完整文件夹里**，别只拷一个 exe
