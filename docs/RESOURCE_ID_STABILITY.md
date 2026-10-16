# 资源 ID 稳定性：含资源补丁的放行条件

## 问题描述

补丁整体替换 `resources.arsc`，但**只携带差分 dex**——设备上未变更的类仍是旧版本编译产物，
它们内联的资源 ID 是**旧版本**的。如果新版本增删资源导致既有资源 ID 重排，这些旧类就会
静默引用到别的资源。

实测（ACS r11 → r12）：10308 个共有资源里 **981 个 ID 漂移**，表现为主页「打开项目」
按钮显示成「找不到未解析的类名」——没有任何报错、没有任何日志，纯静默错乱。

## 根本原因

`R.string.foo` 编译后是内联进调用点的**字面量**（`0x7f0f0001`），而不是符号引用：

```java
// 源码
setTitle(R.string.app_name);

// 编译后（javac/d8 内联）
const v0, 0x7f0f0001        # ← 字面量写死在指令里
invoke-virtual {p0, v0}, setTitle(I)V
```

资源 ID 的分配顺序由 aapt2 决定：**默认每次构建都可能重新分配**。版本间插入一个新
string 就可能让后面所有 ID 后移。此时：

- **更新的类**（差分 dex 覆盖）：重新编译后内联的是新 ID，正确；
- **未变更的类**（留在设备上）：内联的仍是旧 ID，解析到错误的资源——静默错乱。

二进制 XML（`res/layout/*.xml` 等）中的 `@string/foo` 引用同样会内联 ID，同理受影响。

## 两种构建配置的差异

| 配置 | R 字段 | 调用点 | 漂移后果 |
|---|---|---|---|
| `android.nonFinalResIds=false`（旧版 AGP 默认） | `public static final int` | ID **内联**进指令 | 错位→静默显示错误内容 |
| `android.nonFinalResIds=true`（**AGP 8+ 默认**） | `public static int`（无 final） | 走 `R.string.foo` getter，运行期取 ID | 仍可能错位，但风险面缩小到二进制 XML |

> ⚠️ 注意：`nonFinalResIds=true` **只解决 Java/Kotlin 调用点**。二进制 XML 里的资源引用
> 不受它影响，依然内联 ID。因此它降低风险但不能单独保证安全——真正的保证是 ID 本身
> 不变（见下）。

## 解决方案：aapt2 --stable-ids

构建时注入 ID 映射文件，让 aapt2 **固定已有资源的 ID、新资源只追加**。
这一步在**你的应用项目**（补丁的消费方）中配置。

映射文件用 aapt2 自带的 `--emit-ids` 生成（实测格式）：

```bash
# 1. 用「上一个已发布版本」的资源链接一次，导出 ID 映射
aapt2 link -o baseline.apk --manifest AndroidManifest.xml \
    --emit-ids stable-ids.txt <compiled-resources.zip>

# stable-ids.txt 内容形如：
#   com.example.app:string/app_name = 0x7f010000
#   com.example.app:layout/main = 0x7f020000

# 2. 新版本构建时注入该文件（固定已有 ID；新资源追加到末尾）
aapt2 link -o release.apk --manifest AndroidManifest.xml \
    --stable-ids stable-ids.txt <compiled-resources.zip>
```

在 Gradle/AGP 中透传：把 `--stable-ids <file>` 加入 aapt2 的链接参数
（AGP 8+ 可用 `androidComponents` API 或 `aaptOptions.additionalParameters` 视版本而定），
并确保映射文件来自**上一个已发布 APK**。

> 注意：`styleable` 会被 aapt2 合成 ID，但 `--stable-ids` 不接受该类型条目，
> 生成映射文件时应过滤掉（Tinker 同样如此处理）。

启用后实测：ACS r11 → r12 **0 漂移**（对照：未启用时 981 个漂移）。

## SDK 侧的检测与放行

### 生成侧（本仓库）

`PatchGenerator` 在生成**含资源的补丁**时自动执行两项检测：

1. **资源 ID 对齐校验**（`ArscParser` + `ArscResourceIds`）
   解析 base 与 new APK 的 `resources.arsc`，比对「资源名 → ID」映射：
   - 共有资源 ID 全部一致 → `patch.json` 写 `"resourceIdsStable": true`；
   - 任一资源换 ID 或被删除 → 写 `"resourceIdsStable": false`；
   - arsc 缺失/解析失败 → 按不稳定处理（无法证明对齐，宁可不放行）。

   判据语义：**新增资源不破坏稳定性**（占用新 ID，不影响旧内联值）；
   **重排或删除即不稳定**。

2. **R 字段内联检测**（`RFieldAnalyzer`）
   dex 层实测 base APK 的 `R`/`R$*` 类静态 int 字段是否带 `ACC_FINAL`：
   - 至少一个 final → `patch.json` 写 `"rFieldsFinal": true`，并在生成日志给出警告；
   - 全部非 final → 写 `"rFieldsFinal": false`。

   这是对**实际产物**的检测，比读 Gradle 配置可靠（配置可能未透传、AGP 行为可能变化）。

生成的 `patch.json` 片段示例：

```json
{
  "patchId": "patch_1792...",
  "resourceIdsStable": true,
  "rFieldsFinal": false
}
```

不含资源变更的补丁**不写**这两个字段（标记对无资源补丁无意义）。

### 消费侧（App）

含资源的补丁（zip 内有 `resources.arsc` 或 `res/` 条目）**必须**带
`resourceIdsStable=true` 才放行：

```java
JSONObject(json).optBoolean("resourceIdsStable", false)
```

缺失或 `false` 都会被拒绝——覆盖「清单残留旧补丁」「设备上有历史下载文件」
「稳定化未生效」三种情形。补丁本身受 `updater.json` 的 SHA-256 保护，
因此「有标记」等价于「生成时校验通过」。

## Gradle 插件侧提示

`generateDebugPatch` / `generateReleasePatch` 任务会读取
`android.nonFinalResIds` 与 AGP 版本，在构建日志给出提示：

- 显式 `android.nonFinalResIds=false` → 警告（配置被改回旧行为）；
- AGP 主版本 < 8 且未显式设置 → 警告（默认内联）；
- AGP 8+ 且未显式设置 → 不打扰。

## 故障排查

**现象**：补丁应用后部分界面显示无关文字/错误资源，无报错。

**排查步骤**：

1. 检查 `patch.json` 的 `resourceIdsStable`：
   ```bash
   unzip -p patch.zip patch.json | grep resourceIdsStable
   ```
2. 若为 `false`：本次补丁的资源 ID 未对齐，必须改用完整 APK 更新，或在构建中启用
   `aapt2 --stable-ids` 后重新生成补丁。
3. 若字段缺失且补丁确实含资源：说明补丁由旧版工具生成，消费侧会拒绝——用 1.5.2+
   的 patch-cli/插件重新生成。
4. 独立复核（不依赖本 SDK）：直接比对两个 APK 的资源 ID：
   ```bash
   aapt2 dump resources base.apk  > base.txt
   aapt2 dump resources new.apk   > new.txt
   diff <(grep -oP 'resource \S+ \S+' base.txt | sort) \
        <(grep -oP 'resource \S+ \S+' new.txt  | sort)
   ```
   `resource 0x... name` 行中同名资源的 ID 不一致即存在漂移。

## 相关文档

- [resources.arsc 压缩问题](RESOURCES_ARSC_COMPRESSION_EXPLAINED.md) —— arsc 必须 STORE 存储
- [DEX 热更新限制](DEX_HOT_UPDATE_LIMITATION.md) —— 类加载与重启生效机制
