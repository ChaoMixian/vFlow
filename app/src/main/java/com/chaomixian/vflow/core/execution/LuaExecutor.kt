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
        val globals = JsePlatform.standardGlobals()

        // 注入模块函数
        injectVFlowFunctions(globals)

        // 注入输入变量
        val luaInputs = LuaValueConverter.coerceToLua(inputs)
        globals.set("inputs", luaInputs)

        // 加载并执行脚本
        val chunk = globals.load(script, "vflow_script")
        val result = chunk.call()

        // 处理并返回结果
        if (result.istable()) {
            return LuaValueConverter.coerceToKotlin(result) as? Map<String, Any?> ?: emptyMap()
        }
        return emptyMap()
    }

    /**
     * 将所有已注册的 vFlow 模块注入到 Lua 全局环境中。
     */
    private fun injectVFlowFunctions(globals: Globals) {
        val vflowTable = LuaTable() // 这将是全局的 'vflow' table
        ModuleRegistry.getAllModules().forEach { module ->
            // 分割ID路径, e.g., "vflow.other.toast" -> ["vflow", "other", "toast"]
            val path = module.id.split('.')

            // **修正逻辑**: 我们从路径的第二部分开始构建table，以避免创建 vflow.vflow
            if (path.isNotEmpty() && path.first() == "vflow") {
                var currentTable = vflowTable
                // 循环从第二个元素到倒数第二个元素
                // e.g., 对于 ["vflow", "other", "toast"], 循环处理 "other"
                for (i in 1 until path.size - 1) {
                    val part = path[i]
                    var nextTable = currentTable.get(part)
                    if (nextTable.isnil()) {
                        nextTable = LuaTable()
                        currentTable.set(part, nextTable)
                    }
                    currentTable = nextTable.checktable()
                }

                // 在正确的table上设置函数
                // e.g., 在 'other' table 上设置 'toast' 函数
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
 * 将 ActionModule 包装成 a 函数的适配器类。
 */
class ModuleFunction(
    private val module: ActionModule,
    private val executionContext: ExecutionContext
) : OneArgFunction() {
    override fun call(arg: LuaValue): LuaValue {
        val params = mutableMapOf<String, Any?>()
        if (arg.istable()) {
            val table = arg.checktable()
            for (key in table.keys()) {
                params[key.tojstring()] = LuaValueConverter.coerceToKotlin(table.get(key))
            }
        }

        val result: ExecutionResult = runBlocking {
            val moduleContext = executionContext.copy(
                variables = params,
                magicVariables = mutableMapOf() // 在 a 中调用时，不处理魔法变量
            )
            module.execute(moduleContext) { /* no-op progress */ }
        }

        return when (result) {
            is ExecutionResult.Success -> {
                if (result.outputs.size == 1) {
                    // 如果只有一个输出，直接返回该输出的值
                    val singleOutput = result.outputs.values.first()
                    LuaValueConverter.coerceToLua(singleOutput)
                } else {
                    // 如果有多个输出，返回一个包含所有输出的table
                    LuaValueConverter.coerceToLua(result.outputs)
                }
            }
            is ExecutionResult.Failure -> {
                // 抛出 a 错误
                LuaValue.error("${module.id} execution failed: ${result.errorMessage}")
            }
            else -> LuaValue.NIL
        }
    }
}


/**
 * 用于在 Kotlin 类型和 LuaValue 之间进行转换的帮助对象。
 */
object LuaValueConverter {
    /**
     * 将 Kotlin/Java 对象转换为对应的 LuaValue。
     * @param value 要转换的 Kotlin 对象。
     * @return 转换后的 aValue。
     */
    fun coerceToLua(value: Any?): LuaValue {
        return when (value) {
            null -> LuaValue.NIL
            is String -> LuaValue.valueOf(value)
            is Int -> LuaValue.valueOf(value)
            is Double -> LuaValue.valueOf(value)
            is Float -> LuaValue.valueOf(value.toDouble())
            is Long -> LuaValue.valueOf(value.toDouble())
            is Boolean -> LuaValue.valueOf(value)
            is Map<*, *> -> {
                val table = LuaTable()
                value.forEach { k, v -> table.set(k.toString(), coerceToLua(v)) }
                table
            }
            is List<*> -> {
                val table = LuaTable()
                value.forEachIndexed { i, v -> table.set(i + 1, coerceToLua(v)) }
                table
            }
            // 转换已知的 vFlow 变量类型
            is TextVariable -> LuaValue.valueOf(value.value)
            is NumberVariable -> LuaValue.valueOf(value.value)
            is BooleanVariable -> LuaValue.valueOf(value.value)
            is DictionaryVariable -> coerceToLua(value.value)
            is ListVariable -> coerceToLua(value.value)
            is ScreenElement -> coerceToLua(mapOf("bounds" to value.bounds.toShortString(), "text" to value.text))
            is Coordinate -> coerceToLua(mapOf("x" to value.x, "y" to value.y))
            else -> LuaValue.NIL // 对于不支持的类型，返回 nil
        }
    }

    /**
     * 将 aValue 转换为对应的 Kotlin 对象。
     * @param value 要转换的 aValue。
     * @return 转换后的 Kotlin 对象。
     */
    fun coerceToKotlin(value: LuaValue): Any? {
        return when {
            value.isnil() -> null
            value.isboolean() -> value.toboolean()
            value.islong() -> value.tolong()
            value.isnumber() -> value.todouble() // 统一将数字转为 Double
            value.isstring() -> value.tojstring()
            value.istable() -> {
                val table = value.checktable()
                // 通过检查 key[1] 是否存在来判断是数组还是Map
                if (!table.get(1).isnil() || table.keyCount() == 0) {
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
                    for (key in table.keys()) {
                        map[key.tojstring()] = coerceToKotlin(table.get(key))
                    }
                    map
                }
            }
            else -> value.touserdata()
        }
    }
}