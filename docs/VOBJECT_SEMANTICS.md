# VObject 语义规范与类型系统

本文档详细介绍vFlow的核心类型系统——VObject，包括其设计理念、类型层次、转换规则和语义规范。

---

## 目录

1. [设计理念](#设计理念)
2. [VObject接口](#VObject接口)
3. [类型层次](#类型层次)
4. [基础类型详解](#基础类型详解)
5. [类型转换规则](#类型转换规则)
6. [属性访问系统](#属性访问系统)
7. [VNull的特殊行为](#VNull的特殊行为)
8. [与静态类型系统的集成](#与静态类型系统的集成)

---

## 设计理念

### 核心原则

VObject类型系统的设计遵循以下原则：

1. **统一的运行时表示**
   - 所有的运行时数据都必须封装为VObject
   - 模块之间只传递VObject，不传递原生类型

2. **类型安全与灵活性兼顾**
   - 提供静态类型信息（`VType`）用于UI提示和验证
   - 运行时使用强制转换（Coercion）处理类型转换

3. **容错性优先**
   - 转换失败不应抛出异常，而应返回安全的默认值
   - 支持链式安全调用（Safe Navigation）

4. **与业务语义解耦**
   - VObject是数据容器，不包含业务逻辑
   - 业务逻辑由模块（Module）实现

---

## VObject接口

```kotlin
interface VObject {
    /** 原始数据 (Java/Android 原生对象) */
    val raw: Any?

    /** 此对象的类型定义 */
    val type: VType

    /** 获取属性（支持魔法变量属性访问） */
    fun getProperty(propertyName: String): VObject?

    // --- 类型强制转换 (Coercion) ---
    fun asString(): String
    fun asNumber(): Double?
    fun asBoolean(): Boolean
    fun asList(): List<VObject>
}
```

### 接口方法详解

#### `raw: Any?`
存储原始数据的容器。可以是：
- Kotlin原生类型：`String`, `Double`, `Boolean`, `List`, `Map`
- Android原生类型：`Bitmap`, `Rect` 等
- 自定义业务对象：`NotificationObject`, `ScreenNode` 等

#### `type: VType`
对象的类型定义，用于：
- UI层面的类型提示和验证
- 属性访问的类型推断
- 模块输入输出的类型匹配

#### `getProperty(propertyName: String): VObject?`
实现"魔法变量"属性访问：
```
{{image.width}}   → image.getProperty("width")
{{text.length}}   → text.getProperty("length")
{{list.first}}    → list.getProperty("first")
```

**容错规则**：
- 属性不存在 → 返回`VNull`
- 属性访问失败 → 返回`VNull`
- `VNull`的任何属性 → 返回`VNull`自身（链式安全）

#### 类型转换方法

这些方法实现了**强制转换（Coercion）**，即"尽力转换"的语义：

| 方法 | 语义 | 失败行为 |
|------|------|---------|
| `asString()` | 转换为文本表示 | 返回空字符串`""` |
| `asNumber()` | 转换为数字 | 返回`null` |
| `asBoolean()` | 转换为布尔值 | 返回`false` |
| `asList()` | 转换为列表 | 返回单元素列表`[this]` |

---

## 类型层次

### 类型树结构

```
VType (根类型)
├── ANY (任意类型)
│   ├── 基础类型
│   │   ├── STRING (文本)
│   │   ├── NUMBER (数字)
│   │   ├── BOOLEAN (布尔)
│   │   └── NULL (空)
│   ├── 集合类型
│   │   ├── LIST (列表)
│   │   └── DICTIONARY (字典)
│   └── 复杂业务类型
│       ├── IMAGE (图片)
│       ├── DATE (日期)
│       ├── TIME (时间)
│       ├── UI_ELEMENT (界面元素)
│       ├── UI_COMPONENT (UI组件)
│       ├── COORDINATE (坐标)
│       ├── NOTIFICATION (通知)
│       └── APP (应用)
```

### 类型继承关系

- 所有类型都继承自`ANY`
- `ANY`的父类型是`ANY`自身（循环引用，表示顶层类型）
- 类型关系用于类型匹配和属性继承

---

## 基础类型详解

### 1. VString (文本)

```kotlin
@Parcelize
data class VString(override val raw: String) : EnhancedBaseVObject()
```

#### 转换规则

| 方法 | 行为 | 示例 |
|------|------|------|
| `asString()` | 返回原始字符串 | `"hello"` → `"hello"` |
| `asNumber()` | 尝试解析为数字 | `"123"` → `123.0`<br>`"abc"` → `null` |
| `asBoolean()` | 空串、"false"、"0"为false，其他为true | `""` → `false`<br>`"false"` → `false`<br>`"0"` → `false`<br>`"hello"` → `true` |
| `asList()` | 返回包含自身的单元素列表 | `"abc"` → `[VString("abc")]` |

#### 属性列表

| 属性名 | 类型 | 描述 |
|--------|------|------|
| `length` / `len` / `长度` | NUMBER | 字符串长度 |
| `uppercase` / `大写` | STRING | 转为大写 |
| `lowercase` / `小写` | STRING | 转为小写 |
| `trim` / `trimmed` / `去除首尾空格` | STRING | 去除首尾空格 |
| `removeSpaces` / `remove_space` / `去除空格` | STRING | 去除所有空格 |
| `isempty` / `为空` | BOOLEAN | 是否为空字符串 |

---

### 2. VNumber (数字)

```kotlin
@Parcelize
data class VNumber(override val raw: Double) : EnhancedBaseVObject()
```

#### 转换规则

| 方法 | 行为 | 示例 |
|------|------|------|
| `asString()` | 转为字符串表示 | `123.45` → `"123.45"` |
| `asNumber()` | 返回自身 | `123.0` → `123.0` |
| `asBoolean()` | 非零为true，零为false | `0` → `false`<br>`1.5` → `true` |
| `asList()` | 返回包含自身的单元素列表 | `123` → `[VNumber(123)]` |

#### 属性列表

| 属性名 | 类型 | 描述 |
|--------|------|------|
| `int` / `整数部分` | NUMBER | 整数部分（向下取整） |
| `round` / `四舍五入` | NUMBER | 四舍五入 |
| `abs` / `绝对值` | NUMBER | 绝对值 |

---

### 3. VBoolean (布尔)

```kotlin
@Parcelize
data class VBoolean(override val raw: Boolean) : EnhancedBaseVObject()
```

#### 转换规则

| 方法 | 行为 | 示例 |
|------|------|------|
| `asString()` | 转为字符串 | `true` → `"true"`<br>`false` → `"false"` |
| `asNumber()` | true→1.0, false→0.0 | `true` → `1.0`<br>`false` → `0.0` |
| `asBoolean()` | 返回自身 | `true` → `true` |
| `asList()` | 返回包含自身的单元素列表 | `true` → `[VBoolean(true)]` |

#### 属性列表

| 属性名 | 类型 | 描述 |
|--------|------|------|
| `not` / `反转` | BOOLEAN | 逻辑非 |

---

### 4. VList (列表)

```kotlin
@Parcelize
data class VList(override val raw: List<VObject>) : EnhancedBaseVObject()
```

#### 转换规则

| 方法 | 行为 | 示例 |
|------|------|------|
| `asString()` | 逗号连接所有元素的字符串 | `[1, 2, 3]` → `"1, 2, 3"` |
| `asNumber()` | 返回列表大小 | `[1, 2, 3]` → `3.0` |
| `asBoolean()` | 非空为true，空列表为false | `[]` → `false`<br>`[1]` → `true` |
| `asList()` | 返回自身 | `[1, 2]` → `[1, 2]` |

#### 属性列表

| 属性名 | 类型 | 描述 | 容错行为 |
|--------|------|------|----------|
| `count` / `size` / `数量` | NUMBER | 列表大小 | 始终有效 |
| `first` / `第一个` | ANY | 第一个元素 | 空列表 → `VNull` |
| `last` / `最后一个` | ANY | 最后一个元素 | 空列表 → `VNull` |
| `isempty` / `为空` | BOOLEAN | 是否为空 | 始终有效 |
| `random` / `随机` | ANY | 随机一个元素 | 空列表 → `VNull` |

#### 数字索引访问

支持Python风格的索引访问：

```
{{list.0}}      → 第一个元素
{{list.1}}      → 第二个元素
{{list.-1}}     → 最后一个元素
{{list.-2}}     → 倒数第二个元素
{{list.999}}    → 越界 → VNull
```

---

### 5. VDictionary (字典)

```kotlin
@Parcelize
data class VDictionary(override val raw: Map<String, VObject>) : EnhancedBaseVObject()
```

#### 转换规则

| 方法 | 行为 | 示例 |
|------|------|------|
| `asString()` | JSON风格字符串 | `{"a": 1}` → `"{\"a\": \"1\"}"` |
| `asNumber()` | 返回字典大小 | `{"a": 1}` → `1.0` |
| `asBoolean()` | 非空为true，空字典为false | `{}` → `false`<br>`{"a": 1}` → `true` |
| `asList()` | 返回values列表 | `{"a": 1, "b": 2}` → `[1, 2]` |

#### 属性列表

| 属性名 | 类型 | 描述 |
|--------|------|------|
| `count` / `size` / `数量` | NUMBER | 字典大小 |
| `keys` / `键` | LIST | 所有键的列表 |
| `values` / `值` | LIST | 所有值的列表 |

#### 动态Key访问

支持直接访问字典中的Key：

```
{{dict.name}}      → dict.getProperty("name")
{{dict.age}}       → dict.getProperty("age")
{{dict.notexist}}  → dict.getProperty("notexist") → VNull
```

**访问优先级**：
1. 内置属性（`count`, `keys`, `values`）
2. 字典Key（直接匹配）
3. 兜底返回`VNull`

---

### 6. VNull (空)

```kotlin
object VNull : EnhancedBaseVObject()
```

#### 特殊行为

VNull是一个**单例对象**，具有特殊的null安全行为：

| 方法 | 行为 |
|------|------|
| `raw` | `null` |
| `asString()` | `""` (空字符串) |
| `asNumber()` | `0.0` |
| `asBoolean()` | `false` |
| `asList()` | `[]` (空列表) |

#### 链式安全调用

VNull最重要的特性是支持**链式安全调用**：

```
{{nullObj.prop1.prop2.prop3}}  → VNull
```

**实现**：
```kotlin
override fun getProperty(propertyName: String): VObject? {
    return this  // 始终返回自身
}
```

这意味着：
- `VNull`的任何属性访问都不会崩溃
- 可以安全地访问深层嵌套属性
- 配合IF模块的"不存在"判断来处理

**使用场景**：
```kotlin
// 查找元素未找到
val element = findElement()  // 返回 VNull

// 访问其属性不会崩溃
val text = element.getProperty("text")  // 返回 VNull
val length = text.getProperty("length") // 返回 VNull

// 条件判断
if (element 不存在) {  // true
    // 处理未找到的情况
}
```

---

## 类型转换规则

### 1. asString() 规则

| 类型 | 行为 |
|------|------|
| VString | 返回原始字符串 |
| VNumber | 返回数字的字符串表示 |
| VBoolean | `"true"` 或 `"false"` |
| VList | 逗号连接所有元素 |
| VDictionary | JSON风格字符串 |
| VNull | `""` |
| 其他 | `toString()` 或 `""` |

### 2. asNumber() 规则

| 类型 | 行为 |
|------|------|
| VString | 尝试解析，失败返回`null` |
| VNumber | 返回原始值 |
| VBoolean | `true`→`1.0`, `false`→`0.0` |
| VList / VDictionary | 返回容器大小 |
| VNull | `0.0` |

### 3. asBoolean() 规则

| 类型 | 行为 | 伪真值条件 |
|------|------|-----------|
| VString | 空串、"false"、"0"为false | 非空且非"false"且非"0" |
| VNumber | 零为false，非零为true | `!= 0` |
| VBoolean | 返回原始值 | `== true` |
| VList / VDictionary | 空容器为false | `size > 0` |
| VNull | 始终为false | 永远false |

**重要说明**：
vFlow的布尔转换与Python略有不同：

| 值 | Python | vFlow |
|---|--------|-------|
| `""` | `False` | `False` ✅ |
| `"0"` | `True` | `False` ❌ |
| `"false"` | `True` | `False` ❌ |
| `[]` | `False` | `False` ✅ |
| `{}` | `False` | `False` ✅ |

**设计理由**：
- vFlow的用户主要是非程序员
- 字符串"0"和"false"应该被理解为"假"
- 这更符合直觉和业务逻辑

### 4. asList() 规则

| 类型 | 行为 |
|------|------|
| VList | 返回自身 |
| 其他 | 返回包含自身的单元素列表 |

**设计理由**：
- `ForEach`模块可以遍历任何对象
- 单个对象被视为单元素列表
- 空对象（VNull）返回空列表

---

## 属性访问系统

### 属性注册机制

VObject使用**属性注册表（PropertyRegistry）**来管理属性：

```kotlin
object VStringCompanion {
    val registry = PropertyRegistry().apply {
        register("length", "len", "长度", getter = { host ->
            VNumber((host as VString).raw.length.toDouble())
        })
    }
}
```

**特性**：
- 所有实例共享同一个注册表（节省内存）
- 支持多个别名（如`length`, `len`, `长度`）
- 类型安全的属性访问

### 属性访问流程

```
{{step1.output.width}}
       ↓
解析为: step1.output.getProperty("width")
       ↓
查找属性注册表
       ↓
找到: 返回属性值
未找到: 返回 VNull
```

### 内置属性 vs 动态属性

| 类型 | 内置属性 | 动态属性 |
|------|---------|---------|
| VString | ✅ length, uppercase, ... | ❌ 无 |
| VNumber | ✅ int, abs, ... | ❌ 无 |
| VList | ✅ count, first, ... | ✅ 数字索引 (0, 1, -1, ...) |
| VDictionary | ✅ count, keys, ... | ✅ 字典Key |

### 属性访问的容错性

所有属性访问都是容错的：

```kotlin
val list = VList(emptyList())
val first = list.getProperty("first")  // → VNull (不是崩溃)
val length = first.getProperty("length") // → VNull (链式安全)
```

---

## VNull的特殊行为

### 单例模式

VNull是一个单例对象（object），整个应用只有一个实例：

```kotlin
object VNull : EnhancedBaseVObject()
```

**使用建议**：
- 始终使用`VNull`而非`null`
- 模块返回"未找到"时返回`VNull`
- 不要创建`null`的VObject包装

### 链式安全调用示例

```kotlin
// 假设查找元素未找到
val element = findElement()  // VNull

// 以下操作都不会崩溃
val text = element.getProperty("text")        // VNull
val upper = text.getProperty("uppercase")     // VNull
val length = upper.getProperty("length")      // VNull

// 条件判断
if (element 不存在) {  // true
    // 处理未找到的情况
}
```

### VNull vs Kotlin null

| 场景 | Kotlin null | VNull |
|------|-------------|-------|
| 表示"无值" | ✅ | ✅ |
| 可以调用方法 | ❌ 编译错误 | ✅ 可以 |
| 可以访问属性 | ❌ 编译错误 | ✅ 可以（返回VNull） |
| 可以放入集合 | ✅ 需要可空类型 | ✅ 直接放入 |
| 类型安全 | ✅ 编译期检查 | ⚠️ 运行时检查 |

**结论**：
- VNull提供了类似Kotlin的null安全性
- 但更灵活，不需要显式处理可空类型
- 非常适合动态语言特性的工作流系统

---

## 与静态类型系统的集成

### VTypeRegistry

`VTypeRegistry`提供了静态类型信息：

```kotlin
object VTypeRegistry {
    val STRING = SimpleVType("vflow.type.string", "文本", ANY, listOf(
        VPropertyDef("length", "长度", ANY),
        VPropertyDef("uppercase", "大写", ANY),
        ...
    ))
    ...
}
```

**用途**：
1. **UI类型提示**：显示可用的输入/输出类型
2. **类型验证**：验证模块连接是否匹配
3. **属性推断**：推断属性访问的返回类型

### 类型推断

```kotlin
// IfModule中的类型推断
private fun resolvePropertyPath(baseTypeId: String?, propertyPath: List<String>): String? {
    var currentTypeId = baseTypeId
    for (propertyName in propertyPath) {
        currentTypeId = VTypeRegistry.getPropertyType(currentTypeId, propertyName)
        if (currentTypeId == null) return null
    }
    return currentTypeId
}
```

**示例**：
```
{{image.width}}  → IMAGE + "width" → NUMBER
{{text.length}}  → STRING + "length" → NUMBER
{{list.first}}   → LIST + "first" → ANY
```

### 运行时 vs 编译时

| 维度 | 编译时（VType） | 运行时（VObject） |
|------|----------------|------------------|
| 类型检查 | 静态类型检查 | 动态类型转换 |
| 属性访问 | 从注册表获取 | 运行时查找 |
| 类型安全 | 编译期保证 | 运行时容错 |
| 灵活性 | 较低 | 较高 |

**设计理念**：
- **编译时**：提供类型提示和验证，帮助用户正确使用模块
- **运行时**：提供容错性和灵活性，确保工作流稳定运行

---

## 最佳实践

### 1. 模块开发者

#### ✅ 推荐做法

```kotlin
// 返回类型明确
override fun getOutputs(step: ActionStep?): List<OutputDefinition> = listOf(
    OutputDefinition("result", "结果", VTypeRegistry.STRING.id)
)

// 返回VNull而非null
override suspend fun execute(...): ExecutionResult {
    val result = findSomething() ?: VNull
    return ExecutionResult.Success(mapOf("result" to result))
}

// 捕获异常，返回Failure
override suspend fun execute(...): ExecutionResult {
    return try {
        val result = doSomething()
        ExecutionResult.Success(mapOf("result" to result))
    } catch (e: Exception) {
        ExecutionResult.Failure("操作失败", e.message)
    }
}
```

#### ❌ 避免的做法

```kotlin
// ❌ 不要返回null
override suspend fun execute(...): ExecutionResult {
    val result = findSomething()  // 可能是null
    return ExecutionResult.Success(mapOf("result" to result!!))
}

// ❌ 不要抛出异常
override suspend fun execute(...): ExecutionResult {
    if (invalid) throw IllegalArgumentException("Invalid")  // 会中断工作流
    ...
}
```

### 2. 条件判断

#### ✅ 推荐做法

```
// 判断查找是否成功
如果 {{element}} 不存在
    → 处理未找到的情况

// 判断内容是否为空
如果 {{text}} 为空
    → 处理空文本的情况

// 组合判断
如果 {{list}} 不存在 或 {{list}} 为空
    → 处理无数据的情况
```

### 3. 属性访问

#### ✅ 推荐做法

```
// 链式访问（安全）
{{image.width}}
{{element.text.length}}

// 处理可能的VNull
如果 {{image.width}} 存在
    → 使用宽度值
```

---

## 参考资料

- [异常处理规范](EXCEPTION_HANDLING.md)
- [VObject.kt 源码](../app/src/main/java/com/chaomixian/vflow/core/types/VObject.kt)
- [VTypeRegistry.kt 源码](../app/src/main/java/com/chaomixian/vflow/core/types/VTypeRegistry.kt)

---

**版本**: 1.0.0
**最后更新**: 2025-01-23
**维护者**: vFlow Team
