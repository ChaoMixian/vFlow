package com.chaomixian.vflow.server.wrappers

import org.json.JSONObject
import java.io.PrintWriter

/**
 * 支持长连接事件推送的 Wrapper 接口。
 */
interface StreamingWrapper {
    /**
     * 处理流式请求。
     * 返回 true 表示该请求已被消费，调用方不应再继续按普通 request/response 处理。
     */
    fun handleStream(method: String, params: JSONObject, writer: PrintWriter): Boolean
}
