// 文件: main/java/com/chaomixian/vflow/core/execution/LuaExecutor.kt
package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import kotlinx.coroutines.runBlocking
import org.luaj.vm2.*
import org.luaj.vm2.lib.OneArgFunction
import org.luaj.vm2.lib.jse.JsePlatform

/**
 * Lua脚本执行器。
 * 负责创建Lua环境，将vFlow模块作为函数暴露给脚本，并执行脚本。
 */
class LuaExecutor(private val executionContext: ExecutionContext) {

    companion object {
        private const val TAG = "LuaExecutor"
    }

    /**
     * 执行一段Lua脚本。
     * @param script 要执行的Lua代码。
     * @param inputs 从工作流传递给脚本的输入变量Map (可读写)。
     * @return 脚本返回的 Table，已转换为 Kotlin Map。
     */
    fun execute(script: String, inputs: MutableMap<String, Any?>): Map<String, Any?> {
        // 创建标准的 Lua 环境
        val globals = JsePlatform.standardGlobals()

        // 注入变量代理 (Direct Memory Access)
        // 'inputs': 当前脚本的输入参数，读写直接修改传入的 map
        globals.set("inputs", MapProxy(inputs))

        // 'sys': 访问执行上下文中的魔法变量 (只读/读写取决于需求，这里给读写权限)
        globals.set("sys", MapProxy(executionContext.magicVariables))

        // 'vars': 访问全局命名变量
        globals.set("vars", MapProxy(executionContext.namedVariables))

        // 自动构建并注入 vFlow 模块树 (_G.vflow)
        injectVFlowModules(globals)

        try {
            DebugLogger.d(TAG, "开始执行 Lua 脚本...")
            // 加载并执行脚本
            val chunk = globals.load(script, "vflow_script")
            val result = chunk.call()

            // 处理结果
            if (result.istable()) {
                return LuaValueConverter.coerceToKotlin(result) as? Map<String, Any?> ?: emptyMap()
            }
            return emptyMap()

        } catch (e: LuaError) {
            val errorMsg = "Lua Error at line ${e.message}"
            DebugLogger.e(TAG, errorMsg)
            throw RuntimeException(errorMsg, e)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Script execution failed", e)
            throw RuntimeException("Execution failed: ${e.message}", e)
        }
    }

    /**
     * 标准化模块接口映射。
     * 扫描 ModuleRegistry 中的所有模块，解析 ID (如 vflow.device.click)，
     * 并在 Lua 环境中构建对应的 Table 树和 Function。
     */
    private fun injectVFlowModules(globals: Globals) {
        // 获取或创建根表 'vflow'
        var vflowTable = globals.get("vflow")
        if (vflowTable.isnil()) {
            vflowTable = LuaTable()
            globals.set("vflow", vflowTable)
        } else {
            vflowTable = vflowTable.checktable()
        }

        val allModules = ModuleRegistry.getAllModules()

        for (module in allModules) {
            // 仅处理标准命名空间的模块 (vflow.*)
            if (!module.id.startsWith("vflow.")) continue

            // 拆分 ID，例如 ["vflow", "device", "click"]
            val parts = module.id.split('.')
            // 从第二部分开始遍历 (跳过 "vflow")
            var currentTable = vflowTable as LuaTable

            // 构建中间路径 (如 "device")
            for (i in 1 until parts.size - 1) {
                val partName = parts[i]
                var nextTable = currentTable.get(partName)
                if (nextTable.isnil()) {
                    nextTable = LuaTable()
                    currentTable.set(partName, nextTable)
                }
                currentTable = nextTable.checktable()
            }

            // 在叶子节点绑定函数 (如 "click")
            val functionName = parts.last()
            // 注入通用包装函数
            currentTable.set(functionName, ModuleWrapperFunction(module, executionContext))
        }
    }
}

/**
 * 通用模块包装函数。
 * 将 Lua 函数调用桥接到 Kotlin 的 ActionModule.execute。
 * * Lua 调用示例: vflow.device.toast({ message = "Hello" })
 */
class ModuleWrapperFunction(
    private val module: ActionModule,
    private val executionContext: ExecutionContext
) : OneArgFunction() {

    override fun call(arg: LuaValue): LuaValue {
        // 解析参数
        // Lua 传递过来的是一个 Table，我们需要将其转换为 Map 传给模块
        val params = mutableMapOf<String, Any?>()
        if (arg.istable()) {
            val kotlinParams = LuaValueConverter.coerceToKotlin(arg)
            if (kotlinParams is Map<*, *>) {
                kotlinParams.forEach { (k, v) ->
                    params[k.toString()] = v
                }
            }
        }

        // 准备模块上下文
        // 注意：这里我们创建了一个新的 Context 用于模块执行，
        // 但我们可以将上面创建的 MapProxy 再次利用，或者让模块只需处理 params
        val moduleContext = executionContext.copy(
            variables = params,
            // 魔法变量通常在进入 Lua 前已经解析，这里传空或传引用均可
            magicVariables = mutableMapOf()
        )

        // 同步执行挂起函数
        // 因为 Luaj 是阻塞式的，我们需要用 runBlocking 等待协程结果
        var executionResult: ExecutionResult
        runBlocking {
            executionResult = module.execute(moduleContext) { progress ->
                // 可选：将进度回传给 Lua (需要 Lua 支持回调机制，暂略)
                DebugLogger.d("LuaExecutor", "[Lua->${module.metadata.name}] ${progress.message}")
            }
        }

        // 将结果转换回 Lua
        return when (executionResult) {
            is ExecutionResult.Success -> {
                val outputs = (executionResult as ExecutionResult.Success).outputs
                if (outputs.isEmpty()) {
                    LuaValue.TRUE // 成功但无返回值
                } else if (outputs.size == 1 && outputs.containsKey("result")) {
                    // 如果只有一个名为 result 的输出，直接解包返回
                    LuaValueConverter.coerceToLua(outputs["result"])
                } else {
                    // 否则返回包含所有输出的 Table
                    LuaValueConverter.coerceToLua(outputs)
                }
            }
            is ExecutionResult.Failure -> {
                val fail = executionResult as ExecutionResult.Failure
                // 抛出 Lua 错误，脚本可以用 pcall 捕获
                error("Module Error: ${fail.errorTitle} - ${fail.errorMessage}")
            }
            else -> LuaValue.NIL
        }
    }
}