package com.chaomixian.vflow.server.wrappers

import org.json.JSONObject

/**
 * Wrapper接口
 * 用于不需要连接Android系统服务的简单Wrapper
 */
interface IWrapper {
    /**
     * 处理方法调用
     */
    fun handle(method: String, params: JSONObject): JSONObject
}
