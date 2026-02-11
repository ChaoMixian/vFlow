package com.chaomixian.vflow.core.execution

import com.chaomixian.vflow.core.logging.DebugLogger
import com.chaomixian.vflow.core.module.*
import com.chaomixian.vflow.core.types.VObjectFactory
import kotlinx.coroutines.runBlocking
import org.mozilla.javascript.*

/**
 * JavaScript 脚本执行器。
 * 负责创建 JavaScript 环境，将 vFlow 模块作为函数暴露给脚本，并执行脚本。
 */
class JsExecutor(private val executionContext: ExecutionContext) {

    companion object {
        private const val TAG = "JsExecutor"
    }

    /**
     * 执行一段 JavaScript 脚本。
     * @param script 要执行的 JS 代码。
     * @param inputs 从工作流传递给脚本的输入变量 Map (可读写)。
     * @return 脚本返回的对象，已转换为 Kotlin Map。
     */
    fun execute(script: String, inputs: MutableMap<String, Any?>): Map<String, Any?> {
        val context = Context.enter()
        try {
            // 设置优化级别，-1 表示解释模式，0+ 表示优化模式
            context.optimizationLevel = -1

            // 创建标准作用域
            val scope = context.initStandardObjects()

            // 注入 context (作为 vflowContext)
            val contextObj = context.newObject(scope)
            contextObj.setPrototype(scope)
            contextObj.setParentScope(scope)
            ScriptableObject.putProperty(scope, "context", contextObj)

            // 注入 inputs (直接作为对象)
            val inputsObj = context.newObject(scope)
            inputs.forEach { (key, value) ->
                inputsObj.put(key, inputsObj, JsValueConverter.coerceToJs(context, scope, value))
            }
            ScriptableObject.putProperty(scope, "inputs", inputsObj)

            // 注入 sys (魔法变量)
            val sysObj = context.newObject(scope)
            executionContext.magicVariables.forEach { (key, vObj) ->
                sysObj.put(key, sysObj, JsValueConverter.coerceToJs(context, scope, vObj))
            }
            ScriptableObject.putProperty(scope, "sys", sysObj)

            // 注入 vars (命名变量)
            val varsObj = context.newObject(scope)
            executionContext.namedVariables.forEach { (key, vObj) ->
                varsObj.put(key, varsObj, JsValueConverter.coerceToJs(context, scope, vObj))
            }
            ScriptableObject.putProperty(scope, "vars", varsObj)

            // 自动构建并注入 vFlow 模块树
            injectVFlowModules(context, scope)

            DebugLogger.d(TAG, "开始执行 JavaScript 脚本...")
            val result = context.evaluateString(scope, script, "vflow_script", 1, null)

            // 处理结果
            return when (result) {
                is NativeObject, is ScriptableObject -> {
                    JsValueConverter.coerceToKotlin(result) as? Map<String, Any?> ?: emptyMap()
                }
                is NativeArray -> {
                    val listResult = JsValueConverter.coerceToKotlin(result) as? List<*>
                    mapOf("result" to listResult)
                }
                is org.mozilla.javascript.Undefined -> emptyMap()
                else -> mapOf("result" to JsValueConverter.coerceToKotlin(result))
            }

        } catch (e: RhinoException) {
            val errorMsg = "JavaScript Error at line ${e.lineNumber()}: ${e.columnNumber()}"
            DebugLogger.e(TAG, errorMsg)
            throw RuntimeException(errorMsg, e)
        } catch (e: Exception) {
            DebugLogger.e(TAG, "Script execution failed", e)
            throw RuntimeException("Execution failed: ${e.message}", e)
        } finally {
            Context.exit()
        }
    }

    /**
     * 标准化模块接口映射。
     * 扫描 ModuleRegistry 中的所有模块，解析 ID (如 vflow.device.click)，
     * 并在 JavaScript 环境中构建对应的对象树和函数。
     */
    private fun injectVFlowModules(context: Context, scope: Scriptable) {
        // 获取或创建根对象 'vflow'
        val vflowObjAny = scope.get("vflow", scope)
        val vflowObj: Scriptable = if (vflowObjAny is Scriptable) {
            vflowObjAny
        } else {
            val newObj = context.newObject(scope)
            ScriptableObject.putProperty(scope, "vflow", newObj)
            newObj
        }

        val allModules = ModuleRegistry.getAllModules()

        for (module in allModules) {
            // 仅处理标准命名空间的模块 (vflow.*)
            if (!module.id.startsWith("vflow.")) continue

            // 拆分 ID，例如 ["vflow", "device", "click"]
            val parts = module.id.split('.')
            var currentObj: Scriptable = vflowObj

            // 构建中间路径 (如 "device")
            for (i in 1 until parts.size - 1) {
                val partName = parts[i]
                val nextObjAny = currentObj.get(partName, currentObj)

                // 如果属性不存在或不是 Scriptable，创建新的对象
                if (nextObjAny !is Scriptable) {
                    val newObj = context.newObject(scope)
                    currentObj.put(partName, currentObj, newObj)
                    currentObj = newObj
                } else {
                    currentObj = nextObjAny
                }
            }

            // 在叶子节点绑定函数 (如 "click")
            val functionName = parts.last()
            val moduleFunction = JsModuleWrapperFunction(module, executionContext, context, scope)
            currentObj.put(functionName, currentObj, moduleFunction)
        }
    }
}

/**
 * JavaScript 模块包装函数。
 * 将 JavaScript 函数调用桥接到 Kotlin 的 ActionModule.execute。
 * JS 调用示例: vflow.device.toast({ message: "Hello" })
 */
class JsModuleWrapperFunction(
    private val module: ActionModule,
    private val executionContext: ExecutionContext,
    private val rhinoContext: Context,
    private val rhinoScope: Scriptable
) : BaseFunction() {

    override fun call(
        cx: org.mozilla.javascript.Context,
        scope: Scriptable,
        thisObj: Scriptable,
        args: Array<Any?>
    ): Any? {
        // 解析参数
        val params = mutableMapOf<String, Any?>()
        if (args.isNotEmpty() && args[0] is Scriptable) {
            val argObj = args[0] as Scriptable
            val ids = argObj.ids
            for (id in ids) {
                val key = id.toString()
                val propValue = argObj.get(key, argObj)
                if (propValue != org.mozilla.javascript.Context.getUndefinedValue()) {
                    params[key] = JsValueConverter.coerceToKotlin(propValue)
                }
            }
        }

        // 准备模块上下文
        val moduleContext = executionContext.copy(
            variables = ExecutionContext.mutableMapToVObjectMap(params),
            magicVariables = mutableMapOf()
        )

        // 同步执行挂起函数
        var executionResult: ExecutionResult
        runBlocking {
            executionResult = module.execute(moduleContext) { progress ->
                DebugLogger.d("JsExecutor", "[JS->${module.metadata.name}] ${progress.message}")
            }
        }

        // 将结果转换回 JavaScript
        return when (executionResult) {
            is ExecutionResult.Success -> {
                val outputs = (executionResult as ExecutionResult.Success).outputs
                if (outputs.isEmpty()) {
                    org.mozilla.javascript.Context.getUndefinedValue()
                } else if (outputs.size == 1 && outputs.containsKey("result")) {
                    JsValueConverter.coerceToJs(cx, rhinoScope, outputs["result"])
                } else {
                    JsValueConverter.coerceToJs(cx, rhinoScope, outputs)
                }
            }
            is ExecutionResult.Failure -> {
                val fail = executionResult as ExecutionResult.Failure
                // 抛出 JavaScript 错误，脚本可以用 try-catch 捕获
                throw org.mozilla.javascript.JavaScriptException(
                    "${fail.errorTitle}: ${fail.errorMessage}",
                    "ModuleError",
                    0
                )
            }
            else -> org.mozilla.javascript.Context.getUndefinedValue()
        }
    }

    override fun getArity(): Int = 1
}
