// 文件: main/java/com/chaomixian/vflow/core/execution/LuaExecutor.kt
package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.workflow.module.interaction.Coordinate
import com.chaomixian.vflow.core.workflow.module.interaction.ScreenElement
import kotlinx.coroutines.runBlocking
import org.luaj.vm2.*
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * Lua脚本执行器。
 * 负责创建Lua环境，将vFlow模块作为函数暴露给脚本，并执行脚本。
 */
class LuaExecutor(private val executionContext: ExecutionContext) {

    /**
     * 执行一段Lua脚本。
     * @param script 要执行的Lua代码。
     * @param inputs 从工作流传递给脚本的输入变量Map。
     * @return 脚本返回的 a table，已转换为 Kotlin Map。
     */
    fun execute(script: String, inputs: Map<String, Any?>): Map<String, Any?> {
        // 创建标准的 globals 环境
        val globals = JsePlatform.standardGlobals()

        // 注入 vFlow 模块函数 (支持多级路径 vflow.device.click)
        injectVFlowFunctions(globals)

        // 注入输入变量 inputs
        // 确保 inputs 是一个全局变量，脚本中可以直接访问 inputs.xxx
        val luaInputs = LuaValueConverter.coerceToLua(inputs)
        globals.set("inputs", luaInputs)

        try {
            // 加载并执行脚本
            // 使用 chunk name "script" 以便在报错时显示
            val chunk = globals.load(script, "script")
            val result = chunk.call()

            // 处理并返回结果
            if (result.istable()) {
                return LuaValueConverter.coerceToKotlin(result) as? Map<String, Any?> ?: emptyMap()
            }
            return emptyMap()

        } catch (e: LuaError) {
            // 增强异常处理，尝试提取行号和具体错误信息
            val errorMsg = e.message ?: "Unknown Lua Error"
            // 抛出更友好的异常，上层 execute 捕获后会显示给用户
            throw RuntimeException("Lua执行出错: $errorMsg", e)
        } catch (e: Exception) {
            throw RuntimeException("脚本执行发生系统错误: ${e.localizedMessage}", e)
        }
    }

    /**
     * 将所有已注册的 vFlow 模块注入到 Lua 全局环境中。
     * 递归构建 LuaTable 树结构。
     */
    private fun injectVFlowFunctions(globals: Globals) {
        val vflowTable = LuaTable() // 根表 'vflow'
        ModuleRegistry.getAllModules().forEach { module ->
            // 分割ID路径, e.g., "vflow.device.click" -> ["vflow", "device", "click"]
            val path = module.id.split('.')

            if (path.isNotEmpty() && path.first() == "vflow") {
                var currentTable = vflowTable
                // 遍历路径中间部分 (e.g., "device")
                for (i in 1 until path.size - 1) {
                    val part = path[i]
                    var nextTable = currentTable.get(part)
                    if (nextTable.isnil()) {
                        nextTable = LuaTable()
                        currentTable.set(part, nextTable)
                    }
                    currentTable = nextTable.checktable()
                }

                // 在叶子节点设置函数
                // e.g., 在 'device' table 上设置 'click' 函数
                if (path.size > 1) {
                    val functionName = path.last()
                    currentTable.set(functionName, ModuleFunction(module, executionContext))
                }
            }
        }
        globals.set("vflow", vflowTable)
    }
}

/**
 * 将 ActionModule 包装成 Lua 函数的适配器类。
 */
class ModuleFunction(
    private val module: ActionModule,
    private val executionContext: ExecutionContext
) : OneArgFunction() {
    override fun call(arg: LuaValue): LuaValue {
        val params = mutableMapOf<String, Any?>()
        if (arg.istable()) {
            val table = arg.checktable()
            // 将 Lua Table 参数转换为 Kotlin Map 传给模块
            val kotlinParams = LuaValueConverter.coerceToKotlin(table)
            if (kotlinParams is Map<*, *>) {
                kotlinParams.forEach { (k, v) ->
                    params[k.toString()] = v
                }
            }
        }

        // 使用 runBlocking 执行挂起的 execute 方法
        val result: ExecutionResult = runBlocking {
            val moduleContext = executionContext.copy(
                variables = params,
                magicVariables = mutableMapOf() // 在 Lua 中调用时，通常传递的是解析后的值
            )
            module.execute(moduleContext) { /* Lua调用暂不向上传递进度，以免刷屏 */ }
        }

        return when (result) {
            is ExecutionResult.Success -> {
                if (result.outputs.size == 1) {
                    // 如果只有一个输出，直接返回该值，方便脚本使用
                    val singleOutput = result.outputs.values.first()
                    LuaValueConverter.coerceToLua(singleOutput)
                } else {
                    // 如果有多个输出，返回 Table
                    LuaValueConverter.coerceToLua(result.outputs)
                }
            }
            is ExecutionResult.Failure -> {
                // 抛出 Lua 错误，脚本可以使用 pcall 捕获
                LuaValue.error("Module '${module.id}' failed: ${result.errorMessage}")
            }
            else -> LuaValue.NIL
        }
    }
}


/**
 * Lua 类型转换器。
 * 处理 Kotlin List/Map 与 Lua Table 之间的双向深度转换。
 */
object LuaValueConverter {
    /**
     * Kotlin -> Lua
     */
    fun coerceToLua(value: Any?): LuaValue {
        return when (value) {
            null -> LuaValue.NIL
            is String -> LuaValue.valueOf(value)
            is Boolean -> LuaValue.valueOf(value)
            // 数字类型统一处理
            is Int -> LuaValue.valueOf(value)
            is Long -> LuaValue.valueOf(value.toDouble()) // Luaj 对 Long 支持有限，转 Double
            is Float -> LuaValue.valueOf(value.toDouble())
            is Double -> LuaValue.valueOf(value)
            // vFlow 变量类型拆箱
            is TextVariable -> LuaValue.valueOf(value.value)
            is NumberVariable -> LuaValue.valueOf(value.value)
            is BooleanVariable -> LuaValue.valueOf(value.value)
            is DictionaryVariable -> coerceToLua(value.value)
            is ListVariable -> coerceToLua(value.value)
            is ScreenElement -> coerceToLua(mapOf("bounds" to value.bounds.toShortString(), "text" to value.text))
            is Coordinate -> coerceToLua(mapOf("x" to value.x, "y" to value.y))

            // 集合类型递归转换
            is Map<*, *> -> {
                val table = LuaTable()
                value.forEach { (k, v) ->
                    table.set(coerceToLua(k), coerceToLua(v))
                }
                table
            }
            is List<*> -> {
                val table = LuaTable()
                value.forEachIndexed { i, v ->
                    // Lua 数组索引从 1 开始
                    table.set(i + 1, coerceToLua(v))
                }
                table
            }
            // 数组
            is Array<*> -> {
                val table = LuaTable()
                value.forEachIndexed { i, v ->
                    table.set(i + 1, coerceToLua(v))
                }
                table
            }
            else -> LuaValue.valueOf(value.toString()) // 兜底转字符串
        }
    }

    /**
     * Lua -> Kotlin
     */
    fun coerceToKotlin(value: LuaValue): Any? {
        return when {
            value.isnil() -> null
            value.isboolean() -> value.toboolean()
            // 智能数字转换
            value.isnumber() -> {
                val doubleVal = value.todouble()
                // 如果没有小数部分，且在 Long 范围内，转为 Long
                if (doubleVal % 1.0 == 0.0 && doubleVal >= Long.MIN_VALUE && doubleVal <= Long.MAX_VALUE) {
                    doubleVal.toLong()
                } else {
                    doubleVal
                }
            }
            value.isstring() -> value.tojstring()
            value.istable() -> {
                val table = value.checktable()
                // 启发式判断：如果 key 包含 1 且 key 数量 > 0，且没有非数字key，尝试转为 List
                // 简易判断：只要 table[1] 存在，优先视为 List，否则视为 Map
                // 这是一个常见的 Lua Table 互操作权衡
                if (!table.get(1).isnil()) {
                    val list = mutableListOf<Any?>()
                    var i = 1
                    while (true) {
                        val v = table.get(i)
                        if (v.isnil()) break
                        list.add(coerceToKotlin(v))
                        i++
                    }
                    list
                } else {
                    val map = mutableMapOf<String, Any?>()
                    var key = LuaValue.NIL
                    while (true) {
                        val next = table.next(key)
                        key = next.arg1()
                        if (key.isnil()) break
                        val v = next.arg(2)
                        // 强制将 Key 转为 String，因为 vFlow 的变量 Map Key 必须是 String
                        map[key.tojstring()] = coerceToKotlin(v)
                    }
                    map
                }
            }
            else -> value.toString()
        }
    }
}