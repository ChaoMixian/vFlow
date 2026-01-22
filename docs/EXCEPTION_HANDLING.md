# vFlow 异常处理规范

本文档定义了vFlow项目中所有模块在异常情况下的统一处理规范。遵循这些规范可以确保整个系统的行为一致性，提供可预测的用户体验。

## 设计原则

### 1. 容错性优先
vFlow是一个工作流自动化工具，不同于传统的编程环境。当某个操作失败时，我们应该：
- **避免整个工作流中断**
- **提供合理的默认值或返回值**
- **让后续的条件判断模块决定如何处理**

### 2. 显式优于隐式
- 返回值应该明确表示操作的状态
- 使用`VNull`表示"未找到"或"无意义的结果"
- 使用空容器（`VList([])`、`VDictionary(emptyMap())`）表示"查找成功但无结果"

### 3. 与成熟项目的对比
参考Python等成熟项目的做法，但根据工作流工具的特性进行调整：

| 场景 | Python行为 | vFlow行为 | 理由 |
|------|-----------|-----------|------|
| 列表越界 | 抛出`IndexError` | 返回`VNull` | 工作流不应因单个错误中断 |
| 字典Key不存在 | 返回`None` | 返回`VNull` | 语义一致 |
| 空列表的布尔值 | `False` | 存在=`True` | 区分"存在"和"有内容" |

---

## 核心约定

### 约定1: 查找类操作的返回值

查找操作（Find、Get、Search等）根据结果类型的不同，采用不同的返回值约定：

#### 返回单一对象的查找
```kotlin
// ✅ 正确示例
GetElement → 找到返回 VScreenElement, 未找到返回 VNull
GetVariable → 变量存在返回值, 不存在返回 VNull
```

#### 返回集合的查找
```kotlin
// ✅ 正确示例
FindNotification → 找到返回 VList([...]), 未找到返回 VList([])
FindText → 找到返回 VList([...]), 未找到返回 VList([])
```

**理由**：
- 返回单一对象时，使用`VNull`可以明确表示"未找到"
- 返回集合时，空集合本身就是一个有效的结果（"查找完成，但没有匹配项"）

### 约定2: 容器访问的容错性

所有容器访问操作（索引、属性访问）都应该容错：

```kotlin
// ✅ 正确实现
VList[index] 越界 → VNull
VList.first (空列表) → VNull
VList.last (空列表) → VNull
VList.random (空列表) → VNull
VDictionary[key] 不存在 → VNull
```

**理由**：
- 工作流中访问可能不存在的数据是很常见的
- 抛出异常会导致整个工作流中断
- 返回`VNull`允许后续的IF模块判断并处理

### 约定3: 条件判断的操作符语义

IF模块的条件判断操作符有明确的语义定义：

| 操作符 | 语义 | 检查内容 |
|--------|------|---------|
| 存在 | 对象不为null | `input != null` |
| 不存在 | 对象为null | `input == null` |
| 为空 | 内容为空 | 空字符串、空容器、null属性等 |
| 不为空 | 内容不为空 | 有实际内容 |

**关键区别**：
- `OP_EXISTS` / `OP_NOT_EXISTS`：检查**对象本身**是否存在
- `OP_IS_EMPTY` / `OP_IS_NOT_EMPTY`：检查对象的**内容**是否为空

**示例**：
```kotlin
// 查找通知未找到时的返回值
val result = VList(emptyList())  // 空列表

// 条件判断
result 存在 → true   // 对象本身存在
result 不存在 → false
result 为空 → true   // 内容为空
result 不为空 → false
```

### 约定4: 特殊类型的null处理

#### VScreenElement.text 为null
`VScreenElement`的`text`属性可能为null（例如图片按钮没有文本）。此时应该：

```kotlin
// ✅ 正确处理
val element = VScreenElement(bounds, null, ...)  // text为null

// 条件判断
element 存在 → true              // 对象存在
element.text 为空 → true         // null视为"空"
element 包含"abc" → false        // 空字符串不包含"abc"
```

**实现**：
```kotlin
is VScreenElement -> {
    val text = input1.text
    if (text == null) {
        // text为null时，视为"空字符串"进行文本操作
        when (operator) {
            OP_IS_EMPTY -> true
            OP_IS_NOT_EMPTY -> false
            else -> evaluateTextCondition("", operator, value1)
        }
    } else {
        evaluateTextCondition(text, operator, value1)
    }
}
```

#### VNumber 转换失败
当`VNumber.toDoubleValue()`返回null时（理论上不应发生，但为了防御性编程）：

```kotlin
// ✅ 正确处理
val value = input1.toDoubleValue()
if (value == null) false  // 所有数字操作都返回false
else evaluateNumberCondition(value, operator, value1, value2)
```

---

## 各类型操作符支持矩阵

### 文本类型 (VString, String)

| 操作符 | 支持 | null/空值行为 |
|--------|------|---------------|
| 存在/不存在 | ✅ | 检查对象是否为null |
| 为空/不为空 | ✅ | `""` → 为空 |
| 等于/不等于 | ✅ | `null` → 转换为`""` |
| 包含/不包含 | ✅ | `null` → 转换为`""` |
| 开头是/结尾是 | ✅ | `null` → 转换为`""` |
| 匹配正则 | ✅ | `null` → 转换为`""` |

### 数字类型 (VNumber, Number)

| 操作符 | 支持 | null/无效值行为 |
|--------|------|-----------------|
| 存在/不存在 | ✅ | 检查对象是否为null |
| 等于/不等于/大于/小于/... | ✅ | 转换失败 → false |
| 介于 | ✅ | 任意值无效 → false |

### 布尔类型 (VBoolean, Boolean)

| 操作符 | 支持 | null行为 |
|--------|------|----------|
| 存在/不存在 | ✅ | 检查对象是否为null |
| 为真/为假 | ✅ | null → false |

### 列表类型 (VList, Collection)

| 操作符 | 支持 | 空列表行为 |
|--------|------|-----------|
| 存在/不存在 | ✅ | `VList([])` 存在 → true |
| 为空/不为空 | ✅ | `VList([])` 为空 → true |
| 包含/不包含 | ❌ | 不支持，返回false |

**注意**：列表不支持"包含"操作符，应使用循环或其他模块实现。

### 字典类型 (VDictionary, Map)

| 操作符 | 支持 | 空字典行为 |
|--------|------|-----------|
| 存在/不存在 | ✅ | `VDictionary({})` 存在 → true |
| 为空/不为空 | ✅ | `VDictionary({})` 为空 → true |
| 包含/不包含 | ❌ | 不支持，返回false |

**注意**：字典不支持"包含"操作符来检查键，应使用`dictionary.hasKey`属性访问。

### UI元素类型 (VScreenElement)

| 操作符 | 支持 | text为null行为 |
|--------|------|----------------|
| 存在/不存在 | ✅ | 对象存在 → true |
| 为空/不为空 | ✅ | null → 为空 → true |
| 其他文本操作 | ✅ | null → 视为空字符串 |

---

## 模块实现检查清单

所有新模块都应该遵循以下检查清单：

### ✅ 查找类模块
- [ ] 返回单一对象：未找到返回`VNull`
- [ ] 返回集合：未找到返回空容器（`VList([])`）
- [ ] 文档化返回值约定

### ✅ 访问类模块
- [ ] 索引越界返回`VNull`
- [ ] Key不存在返回`VNull`
- [ ] 属性访问失败返回`VNull`

### ✅ 转换类模块
- [ ] 转换失败返回`VNull`（而非抛异常）
- [ ] 文档化转换失败的条件

### ✅ 所有模块
- [ ] 在`execute()`中捕获可能的异常
- [ ] 返回`ExecutionResult.Failure`而非让异常传播
- [ ] 提供清晰的错误消息

---

## 迁移指南

### 现有模块需要修复的情况

#### ❌ 错误示例1：抛出异常
```kotlin
// ❌ 错误：可能抛出IndexOutOfBoundsException
override suspend fun execute(...): ExecutionResult {
    val list = getSomeList()
    val first = list[0]  // 可能崩溃
    return ExecutionResult.Success(mapOf("result" to first))
}
```

#### ✅ 正确示例1：容错处理
```kotlin
// ✅ 正确：返回VNull
override suspend fun execute(...): ExecutionResult {
    val list = getSomeList()
    val first = list.firstOrNull() ?: VNull
    return ExecutionResult.Success(mapOf("result" to first))
}
```

#### ❌ 错误示例2：未找到时返回null
```kotlin
// ❌ 错误：返回null会导致后续模块崩溃
override suspend fun execute(...): ExecutionResult {
    val element = findElement()
    return ExecutionResult.Success(mapOf("result" to element))  // element可能是null
}
```

#### ✅ 正确示例2：返回VNull
```kotlin
// ✅ 正确：明确返回VNull
override suspend fun execute(...): ExecutionResult {
    val element = findElement() ?: VNull
    return ExecutionResult.Success(mapOf("result" to element))
}
```

---

## 参考资料

- [VObject语义规范](VOBJECT_SEMANTICS.md) - 详细的VObject类型系统说明
- [Python Truth Value Testing](https://docs.python.org/3/library/stdtypes.html#truth-value-testing)
- [Workflow Automation Best Practices](https://n8n.io/)

---

**版本**: 1.0.0
**最后更新**: 2025-01-23
**维护者**: vFlow Team
