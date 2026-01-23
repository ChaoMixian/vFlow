# vFlow 异常处理规范

本文档定义了vFlow项目中所有模块在异常情况下的统一处理规范。遵循这些规范可以确保整个系统的行为一致性，提供可预测的用户体验。

## 设计原则

### 1. 用户控制优先
vFlow是一个工作流自动化工具，用户应该有完全的控制权来决定如何处理失败情况：
- **通过"异常处理策略"控制行为**：重试、忽略错误继续、停止工作流
- **统一的错误处理机制**：所有失败情况都返回 `ExecutionResult.Failure`
- **灵活性**：同一个模块在不同工作流中可以有不同的错误处理行为

### 2. Success vs Failure 的清晰语义

**Success**：操作**成功完成**
- 操作完全成功，没有遇到任何错误
- 返回有效输出数据
- 不会触发"异常处理策略"

**Failure**：操作**失败或业务未完成**
- 业务查找失败（可能需要重试）
- 技术错误（系统级问题）
- 参数错误（配置问题）
- **触发"异常处理策略"**，用户可以选择：
  - **重试**：自动重试 N 次
  - **忽略错误继续**：生成默认输出（包含 `VNull` 和 `error` 信息）
  - **停止工作流**：终止执行

### 3. 容错性保持
虽然查找失败返回 `Failure`，但系统的容错性通过"异常处理策略"实现：
- 用户可以选择"忽略错误继续"，效果等同于返回 `VNull`
- 用户可以选择"重试"，自动处理临时性失败
- 用户可以选择"停止工作流"，在关键步骤失败时终止

---

## Success vs Failure 判断标准

### ✅ 返回 Success 的场景

#### 1. 操作完全成功
```kotlin
// ✅ 正确：操作成功完成
CaptureScreenModule → Success + VImage
ClickModule → Success + VBoolean(true)
DelayModule → Success + VBoolean(true)
```

#### 2. 部分成功但有有效输出
```kotlin
// ✅ 正确：文本处理（部分文本被处理）
TextProcessingModule → Success + VString("处理后的文本")

// ✅ 正确：数据转换成功
CalculationModule → Success + VNumber(42.0)
```

### ❌ 返回 Failure 的场景

#### 1. 业务查找失败（可能需要重试）
```kotlin
// ✅ 正确：查找类模块未找到时返回 Failure
FindImageModule (未找到) → Failure("未找到图片", "在屏幕上未找到与模板图片匹配的区域。相似度要求: 80%")
FindTextModule (未找到) → Failure("未找到文本", "未在屏幕上找到匹配的文本: '登录'")
GetVariableModule (变量不存在) → Failure("变量不存在", "找不到变量 'username' 的值")
```

**用户可以通过"异常处理策略"选择行为**：
- **重试**：UI 可能还在加载，等待一段时间后重试
- **忽略错误继续**：输出 `VNull` + `error` 信息，继续执行后续步骤
- **停止工作流**：关键元素不存在，终止工作流

#### 2. 技术错误（系统级问题）
```kotlin
// ✅ 正确：技术错误返回 Failure
NetworkModule (超时) → Failure("网络超时", "连接超时，请检查网络连接")
CaptureScreenModule (权限拒绝) → Failure("权限不足", "需要 Shizuku 或 Root 权限")
FileModule (文件不存在) → Failure("文件不存在", "文件 /sdcard/test.png 不存在")
```

#### 3. 参数错误（配置问题）
```kotlin
// ✅ 正确：参数错误返回 Failure
FindImageModule (未设置模板) → Failure("参数错误", "请先设置模板图片")
FindTextModule (搜索文本为空) → Failure("参数缺失", "目标文本不能为空")
```

---

## 异常处理策略的工作流程

vFlow 的 WorkflowExecutor 提供三种"异常处理策略"：

### 1. 停止工作流（默认）
```
模块返回 Failure
    ↓
工作流立即终止
    ↓
显示错误通知
```

### 2. 忽略错误继续
```
模块返回 Failure
    ↓
生成默认输出：
    1. 如果模块提供了 partialOutputs，使用它们作为基础
    2. 否则所有输出设为 VNull
    3. 没有提供的输出补充为 VNull
    4. 添加 error 字段（错误信息）
    5. 添加 success 字段（false）
    ↓
继续执行下一个步骤
```

**示例1：模块提供 partialOutputs（推荐）**
```kotlin
// FindImageModule 返回 Failure，并提供语义化的部分输出
ExecutionResult.Failure(
    "未找到图片",
    "在屏幕上未找到与模板图片匹配的区域。相似度要求: 80%。",
    partialOutputs = mapOf(
        "count" to VNumber(0.0),              // 找到 0 个（语义化）
        "all_results" to emptyList<Any>(),   // 空列表（语义化）
        "first_result" to VNull             // 没有"第一个"
    )
)

// WorkflowExecutor 的 POLICY_SKIP 策略处理
val skipOutputs = mutableMapOf<String, VObject>(
    "count" to VNumber(0.0),           // 来自 partialOutputs
    "all_results" to VList(emptyList()), // 来自 partialOutputs
    "first_result" to VNull,          // 来自 partialOutputs
    "error" to VString("未找到图片..."), // 自动添加
    "success" to VBoolean(false)      // 自动添加
)
```

**示例2：模块不提供 partialOutputs**
```kotlin
// GetVariableModule 返回 Failure，不提供 partialOutputs
ExecutionResult.Failure(
    "变量不存在",
    "找不到变量 'username' 的值"
    // partialOutputs 默认为 emptyMap()
)

// WorkflowExecutor 的 POLICY_SKIP 策略处理
val skipOutputs = mutableMapOf<String, VObject>(
    "value" to VNull,                      // 默认值：VNull
    "error" to VString("找不到变量..."),   // 自动添加
    "success" to VBoolean(false)          // 自动添加
)
```

**为什么模块应该提供 `partialOutputs`？**

| 输出 | 没有 partialOutputs | 有 partialOutputs | 优势 |
|------|-------------------|------------------|------|
| `count` | `VNull` | `VNumber(0)` | ✅ 语义清晰："找到0个" |
| `all_results` | `VNull` | `VList([])` | ✅ 语义清晰："列表为空" |
| `first_result` | `VNull` | `VNull` | ✅ 一致：没有"第一个" |

**用户的使用体验**：
```kotlin
// 场景1：用户想知道"找到了多少个"
IF {{count}} == 0  → true ✅ (语义化)
IF {{count}} > 0   → false ✅ (语义化)

// 场景2：用户想用 count 做计算
{{count}} + 10     // → 0 + 10 = 10 ✅ (数学运算正确)
                   // 如果是 VNull.asNumber() → 0.0，结果一样但语义不清晰

// 场景3：用户遍历所有结果
FOR each item IN {{all_results}}  // 遍历空列表 ✅ (不会崩溃)
                                 // 如果是 VNull.asList() → []，结果一样
```

### 3. 重试
```
模块返回 Failure
    ↓
等待重试间隔（默认 1000ms）
    ↓
重新执行模块
    ↓
如果仍然失败，继续重试（最多 N 次）
    ↓
如果所有重试都失败，按照"停止工作流"或"忽略错误继续"处理
```

**示例配置**：
```
┌─────────────────────────────────────┐
│  查找图片模块                        │
├─────────────────────────────────────┤
│  模板图片: login_button.png         │
│  相似度: 80%                         │
│                                     │
│  异常处理策略:                       │
│  ● 重试 (3次，间隔1秒)               │
│    ○ 停止工作流                      │
│    ○ 忽略错误继续                    │
└─────────────────────────────────────┘

执行流程：
1. 第一次执行：未找到图片 → Failure
2. 等待 1 秒
3. 第二次执行：未找到图片 → Failure
4. 等待 1 秒
5. 第三次执行：未找到图片 → Failure
6. 等待 1 秒
7. 第四次执行：找到图片 → Success ✅
```

---

## 模块实现示例

### ✅ 正确示例：查找类模块

```kotlin
class FindImageModule : BaseModule() {
    override suspend fun execute(
        context: ExecutionContext,
        onProgress: suspend (ProgressUpdate) -> Unit
    ): ExecutionResult {
        // ... 执行图片匹配 ...

        if (matches.isEmpty()) {
            // ✅ 正确：返回 Failure，并提供 partialOutputs
            return ExecutionResult.Failure(
                "未找到图片",
                "在屏幕上未找到与模板图片匹配的区域。相似度要求: 80%。",
                // 提供 partialOutputs，让"跳过此步骤继续"时有语义化的默认值
                partialOutputs = mapOf(
                    "count" to VNumber(0.0),              // 找到 0 个（语义化）
                    "all_results" to emptyList<Any>(),   // 空列表（语义化）
                    "first_result" to VNull             // 没有"第一个"
                )
            )
        }

        // ✅ 正确：找到匹配，返回 Success
        return ExecutionResult.Success(mapOf(
            "first_result" to bestMatch,
            "all_results" to allMatches,
            "count" to VNumber(matches.size.toDouble())
        ))
    }
}
```

**用户的使用场景**：
- 场景1：UI 还在加载，需要等待 → 配置"重试 3 次"
- 场景2：图片可选，找不到也继续 → 配置"忽略错误继续"
  - 此时 `{{count}}` = `0`（语义清晰）
  - 此时 `{{all_results}}` = `[]`（可以安全遍历）
  - 此时 `{{first_result}}` = `VNull`（可以用 IF 检测）
- 场景3：图片必须存在 → 配置"停止工作流"

### ❌ 错误示例：查找类模块

```kotlin
class FindImageModule : BaseModule() {
    override suspend fun execute(...): ExecutionResult {
        // ... 执行图片匹配 ...

        if (matches.isEmpty()) {
            // ❌ 错误：返回 Success + VNull
            // 问题：
            // 1. 无法触发重试机制
            // 2. 用户无法通过"异常处理策略"控制行为
            // 3. 失去了统一的错误处理机制
            return ExecutionResult.Success(mapOf(
                "first_result" to VNull,
                "all_results" to emptyList<Any>()
            ))
        }
    }
}
```

### ✅ 正确示例：变量读取模块

```kotlin
class GetVariableModule : BaseModule() {
    override suspend fun execute(...): ExecutionResult {
        val variableValue = context.magicVariables["source"]

        if (variableValue == null) {
            // ✅ 正确：返回 Failure
            // 用户可以：
            // - 重试：变量可能稍后被设置
            // - 忽略错误继续：输出 VNull + error
            // - 停止工作流：变量必须存在
            return ExecutionResult.Failure(
                "变量不存在",
                "找不到变量 '${sourceRef}' 的值"
                // GetVariableModule 只有一个输出 value，不需要特殊的 partialOutputs
                // 使用默认的 VNull 即可
            )
        }

        return ExecutionResult.Success(mapOf("value" to variableValue))
    }
}
```

### ✅ 正确示例：网络模块

```kotlin
class NetworkModule : BaseModule() {
    override suspend fun execute(...): ExecutionResult {
        return try {
            val response = httpClient.execute(request)
            if (response.status == 200) {
                // ✅ 正确：成功
                ExecutionResult.Success(mapOf("result" to response.body))
            } else {
                // ✅ 正确：业务失败（HTTP 错误）
                ExecutionResult.Failure(
                    "请求失败",
                    "HTTP ${response.status}: ${response.message}"
                )
            }
        } catch (e: TimeoutException) {
            // ✅ 正确：技术错误（超时）
            ExecutionResult.Failure(
                "网络超时",
                "连接超时（${timeout}ms），请检查网络连接"
            )
        } catch (e: Exception) {
            // ✅ 正确：技术错误（其他异常）
            ExecutionResult.Failure(
                "网络错误",
                e.localizedMessage ?: "发生了未知错误"
            )
        }
    }
}
```

---

## 容器访问的容错性

虽然业务失败返回 `Failure`，但容器访问仍然保持容错性：

```kotlin
// ✅ 正确：容器访问容错
VList[index] 越界 → VNull
VList.first (空列表) → VNull
VList.last (空列表) → VNull
VDictionary[key] 不存在 → VNull
```

**理由**：
- 容器访问是数据操作，不应该抛出异常
- 返回 `VNull` 允许链式访问：`{{list.first.length}}` → `VNull.getProperty("length")` → `VNull`

---

## 与 IF 模块的配合

当用户选择"忽略错误继续"策略时，模块会输出 `error` 和 `success` 字段：

```kotlin
// WorkflowExecutor 的 POLICY_SKIP 处理
val skipOutputs = defaultOutputs.toMutableMap().apply {
    put("error", VString(result.errorMessage))
    put("success", VBoolean(false))
}
```

**用户可以通过 IF 模块判断**：

```
┌─────────────────────────────────────┐
│  查找图片                            │
│  策略：忽略错误继续                   │
└──────────┬──────────────────────────┘
           ↓
    ┌──────────────┐
    │  IF 模块     │
    │              │
    │  如果 {{error}} 不存在           │
    │      → 找到图片，继续执行         │
│                                       │
    │  否则                           │
    │      → 未找到图片，执行备用方案   │
    └──────────────┘
```

---

## 迁移指南

### 现有模块需要修复的情况

#### ❌ 错误示例1：未找到时返回 Success
```kotlin
// ❌ 错误：查找失败返回 Success
override suspend fun execute(...): ExecutionResult {
    val result = findSomething()
    if (result == null) {
        return ExecutionResult.Success(mapOf("value" to VNull))  // 错误！
    }
    return ExecutionResult.Success(mapOf("value" to result))
}
```

#### ✅ 正确示例1：未找到时返回 Failure
```kotlin
// ✅ 正确：查找失败返回 Failure
override suspend fun execute(...): ExecutionResult {
    val result = findSomething()
    if (result == null) {
        return ExecutionResult.Failure(
            "未找到",
            "找不到指定对象"
        )
    }
    return ExecutionResult.Success(mapOf("value" to result))
}
```

#### ❌ 错误示例2：吞掉异常
```kotlin
// ❌ 错误：捕获所有异常并返回 Success
override suspend fun execute(...): ExecutionResult {
    return try {
        doSomething()
        ExecutionResult.Success(mapOf("result" to value))
    } catch (e: Exception) {
        // 错误：吞掉异常，返回 Success + VNull
        ExecutionResult.Success(mapOf("result" to VNull))
    }
}
```

#### ✅ 正确示例2：异常返回 Failure
```kotlin
// ✅ 正确：异常返回 Failure
override suspend fun execute(...): ExecutionResult {
    return try {
        val value = doSomething()
        ExecutionResult.Success(mapOf("result" to value))
    } catch (e: Exception) {
        ExecutionResult.Failure(
            "操作失败",
            e.localizedMessage ?: "发生了未知错误"
        )
    }
}
```

---

## 模块实现检查清单

所有新模块都应该遵循以下检查清单：

### ✅ 查找类模块
- [ ] 找到目标时返回 `Success + 数据`
- [ ] 未找到时返回 `Failure + 清晰的错误信息`
- [ ] 错误信息包含帮助用户调试的细节（如：相似度要求、搜索文本）
- [ ] **推荐**：提供 `partialOutputs`，包含语义化的默认值
  - `count` → `VNumber(0)`
  - `all_results` → `VList([])` 或 `emptyList<Any>()`
  - `first_result` → `VNull`
  - 其他结果类输出 → 根据语义选择默认值

### ✅ 网络类模块
- [ ] 成功时返回 `Success + 数据`
- [ ] HTTP 错误返回 `Failure`
- [ ] 超时返回 `Failure`
- [ ] 其他异常返回 `Failure`

### ✅ 文件类模块
- [ ] 文件不存在返回 `Failure`
- [ ] 权限拒绝返回 `Failure`
- [ ] 读取失败返回 `Failure`

### ✅ 所有模块
- [ ] 在 `execute()` 中捕获可能的异常
- [ ] 返回 `ExecutionResult.Failure` 而非让异常传播
- [ ] 提供清晰的错误信息（包含上下文）
- [ ] **不要**返回 `Success + VNull` 来表示失败
- [ ] **考虑**：对于有多个输出的模块，是否需要提供 `partialOutputs`
  - 如果输出的语义在失败时有明确的默认值（如 `count=0`），应该提供
  - 如果只有单一输出，可以不提供（使用默认的 `VNull`）

---

## 参考资料

- [VObject语义规范](VOBJECT_SEMANTICS.md) - 详细的VObject类型系统说明
- [WorkflowExecutor.kt 源码](../app/src/main/java/com/chaomixian/vflow/core/execution/WorkflowExecutor.kt) - 错误处理策略的实现
- [n8n Error Handling](https://docs.n8n.io/flow-logic/error-handling/#error-data) - 工作流错误处理的最佳实践

---

**版本**: 2.0.0
**最后更新**: 2025-01-23
**维护者**: vFlow Team
