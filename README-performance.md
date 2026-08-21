# Create — Performance Build (Forge 1.20.1)

*[English](#english) · [中文](#中文)*

---

## English

Unofficial performance build of Create 6.0.8 for Minecraft 1.20.1 / Forge. One commit on top of
upstream `mc1.20.1/dev` removes per-tick allocation from
train simulation. **No behaviour changes are intended** — trains route, collide, and signal exactly
as they do in stock Create.

- **Base**: upstream `mc1.20.1/dev` @ `5881242` (Create 6.0.8, Forge 47.1.33, MC 1.20.1)
- **Branch**: `perf/optimized-build-1.20.1`
- **Download**: [Releases](https://github.com/A-BenLi06/Create/releases/tag/create-6.0.8-1.20.1-performance-v1)
- **1.21.1 equivalent**: branch `perf/optimized-build-1.21.1`

### Install

1. Download `create-1.20.1-6.0.8-performance.jar` from the release page.
2. **Back up your world.**
3. Remove the official `create-1.20.1-6.0.8.jar` from `mods/`, then drop this jar in.
   Never keep both — Forge will refuse to start, or worse, load the wrong one.
4. Repeat on **both server and client**. The two must match.

Requirements are identical to stock Create 6.0.8: Minecraft 1.20.1, Forge 47.1.33 or newer.
Addons that work with stock 6.0.8 (Steam 'n' Rails, Copycats, …) work here too — no public
signatures changed.

To revert, reinstall the official jar. Nothing about the save format changes, so downgrading is
safe at any point.

### Verify the download

```
sha256  48bc811bbda2dce00053d322383a9b17cb70c4495517dd608dcc5731b0bbef05
```

```bash
# Linux / macOS
sha256sum create-1.20.1-6.0.8-performance.jar
```
```powershell
# Windows
Get-FileHash -Algorithm SHA256 create-1.20.1-6.0.8-performance.jar
```

### What changed

Four files, all under `content/trains/entity/`. Every change removes allocation from a path that
runs every tick, for every carriage.

| File | Change |
|---|---|
| `Carriage.java` | Dropped two `Function` wrappers and a `MutableDouble` in `travel` for plain locals. Hoisted the portal callback into a `private final IPortalListener` field instead of rebuilding a lambda per bogey. Moved the signal listeners out of the wheel loop. |
| `Train.java` | `frontSignalListener` / `backSignalListener` allocated a fresh lambda on every call; now lazily cached. In `findCollidingTrain`: `Math.pow(x, 2)` → `x * x`, `diff.normalize()` and `diff.length()` hoisted out of the loop, and the nested `betweenBits` loops split into `findCollisionPosition`. |
| `TravellingPoint.java` | `follow(other)` gains a two-slot selector cache — each point alternates between exactly two targets (`prevPoint` and `nextPoint`), so two slots cover it. Cleared from `Carriage.setTrain`. |
| `Navigation.java` | The pathfinding loop allocated a fresh `ArrayList` every iteration; now one list reused via `clear()`. |

### Building from source

```bash
git clone -b perf/optimized-build-1.20.1 https://github.com/A-BenLi06/Create.git
cd Create
./gradlew assemble -x javadoc -x javadocJar
# output: build/libs/create-1.20.1-6.0.8.jar
```

Needs JDK 17. `:javadoc` is skipped because it fails on a pre-existing encoding issue in
`DumpRailwaysCommand.java` when the platform default charset is not UTF-8 — unrelated to these
changes, and it does not affect the mod jar.

### Caveats

- Compiled and bytecode-checked, but **not runtime-tested**. Test on a copy of your world first.
- Unofficial. Do not report issues with this jar to the Create team — reproduce on stock Create
  6.0.8 first.
- Create's code is MIT; assets under `src/main/resources/assets/` are All Rights Reserved by the
  Create Team and are redistributed here unmodified as part of the build.

---

## 中文

Create 6.0.8 非官方性能版本，对应 Minecraft 1.20.1 / Forge。
在上游 `mc1.20.1/dev` 基础上加了一个提交，用于消除列车模拟中每 tick 的对象分配。
**不改变任何游戏行为** —— 寻路、碰撞、信号逻辑与原版完全一致。

- **基线**：上游 `mc1.20.1/dev` @ `5881242`（Create 6.0.8、Forge 47.1.33、MC 1.20.1）
- **分支**：`perf/optimized-build-1.20.1`
- **下载**：[Releases](https://github.com/A-BenLi06/Create/releases/tag/create-6.0.8-1.20.1-performance-v1)
- **1.21.1 对应版本**：分支 `perf/optimized-build-1.21.1`

### 安装

1. 从 release 页面下载 `create-1.20.1-6.0.8-performance.jar`。
2. **先备份存档。**
3. 删除 `mods/` 里官方的 `create-1.20.1-6.0.8.jar`，再放入本 jar。
   **不要两个同时存在** —— Forge 会启动失败，或加载到错误的那个。
4. **服务端和客户端都要换**，两边版本必须一致。

依赖要求与原版 Create 6.0.8 完全相同：Minecraft 1.20.1、Forge 47.1.33 或更高。
原本能配原版 6.0.8 的附属（Steam 'n' Rails、Copycats 等）在这里同样可用 —— 没有改动任何公开签名。

想回退直接换回官方 jar。存档格式没有任何变化，任何时候降级都是安全的。

### 校验下载

```
sha256  48bc811bbda2dce00053d322383a9b17cb70c4495517dd608dcc5731b0bbef05
```

```bash
# Linux / macOS
sha256sum create-1.20.1-6.0.8-performance.jar
```
```powershell
# Windows
Get-FileHash -Algorithm SHA256 create-1.20.1-6.0.8-performance.jar
```

### 改了什么

共 4 个文件，都在 `content/trains/entity/` 下。每一处都是在「每 tick、每节车厢」都会走到的路径上去掉分配。

| 文件 | 改动 |
|---|---|
| `Carriage.java` | `travel` 里两个 `Function` 包装和 `MutableDouble` 换成普通局部变量。传送门回调提为 `private final IPortalListener` 字段，不再每个转向架重建一个 lambda。信号监听器移出轮对循环。 |
| `Train.java` | `frontSignalListener` / `backSignalListener` 原本每次调用都新建 lambda，改为惰性缓存。`findCollidingTrain` 里：`Math.pow(x, 2)` → `x * x`；`diff.normalize()` 和 `diff.length()` 提到循环外；嵌套的 `betweenBits` 双循环拆成 `findCollisionPosition`。 |
| `TravellingPoint.java` | `follow(other)` 增加两槽选择器缓存 —— 每个点只在两个目标（`prevPoint` 和 `nextPoint`）之间交替，两槽刚好够用。由 `Carriage.setTrain` 负责清除。 |
| `Navigation.java` | 寻路循环原本每轮新建一个 `ArrayList`，改为复用同一个并 `clear()`。 |

### 从源码构建

```bash
git clone -b perf/optimized-build-1.20.1 https://github.com/A-BenLi06/Create.git
cd Create
./gradlew assemble -x javadoc -x javadocJar
# 产物：build/libs/create-1.20.1-6.0.8.jar
```

需要 JDK 17。跳过 `:javadoc` 是因为当系统默认字符集不是 UTF-8 时，它会在
`DumpRailwaysCommand.java` 上因编码问题失败 —— 这是 Create 自带的问题，与本改动无关，也不影响 mod jar。

### 注意事项

- 已完成编译与字节码核对，但**未做运行时测试**。请先在存档副本上试。
- 非官方版本。用本 jar 遇到问题不要提给 Create 团队 —— 请先在原版 6.0.8 上复现。
- Create 的代码为 MIT 协议；`src/main/resources/assets/` 下的素材版权归 Create Team 所有
  （All Rights Reserved），本构建产物中原样包含这些素材。
