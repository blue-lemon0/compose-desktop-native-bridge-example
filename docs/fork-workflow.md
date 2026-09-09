# Fork 工作流

记录 fork 一个仓库后，如何长期维护本地代码：既能跟随上游更新，又能保存自己的改动，
且互不干扰。

## 核心矛盾

一个常见的错误思路：

> 把改动合并进 `main`，同时让 `main` 继续跟随官方更新。

这两件事**冲突**：

- 把改动合并进 `main` 后，`main` 就不再等于官方代码。
- 之后官方更新，`git merge upstream/main` 会在改动区产生冲突，需逐个手动解决。
- 改动越多，同步越难。

结论：**`main` 保持纯净，还是掺入改动，只能选一个。**

## 推荐的模型

`main` 专做"官方镜像"，改动放旁支：

```
官方 upstream/main
      │  git fetch + merge（只进不出）
你的 main ─── 纯镜像，永远等于官方，同步零冲突
      │
      │  需要"官方+改动"时：git merge main
      ▼
旁支 dev / feature ─── 所有改动所在
```

两条纪律：

1. `main` 从不直接改，只用它 `git merge upstream/main` 收官方更新。
2. 一切改动放旁支，需要时把干净的 `main` 合进旁支。

## 常用命令

**远程配置（首次）**

```bash
git remote add upstream <官方地址>   # 官方仓库
git remote set-url origin <你的fork> # 你的 fork
```

**同步官方更新（保持 main 干净）**

```bash
git checkout main
git fetch upstream
git merge upstream/main     # main 未被改过，永不冲突
git push origin main
```

**在旁支上并入官方最新代码**

```bash
git checkout dev
git merge main              # 把官方合进来，冲突在旁支一次性解决
```

**新建改动分支**

```bash
git checkout main
git checkout -b feature-xxx
git push -u origin feature-xxx
```

## 什么时候用 Pull Request

PR 用于**跨仓库贡献**：把你的 fork 分支提交给上游官方。

```text
fork 分支 → PR → 官方 main
```

- 只有官方才可能"拉取"你的代码，PR 是唯一正规途径。
- fork 页面上"Compare & Pull Request"提示条，正是为贡献官方而设。
- 仅自用/留存改动时，无需 PR，直接忽略该提示。

## 给官方贡献的标准流程

```bash
git checkout main && git checkout -b my-fix
# 修改、提交
git push -u origin my-fix
```

在 GitHub 网页打开 fork → "Compare & Pull Request" →
`base` 选官方仓库，`compare` 选 my-fix → 创建 PR。

官方合并后：

```bash
git checkout main
git branch -d my-fix
git fetch upstream && git merge upstream/main
```
