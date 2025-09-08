// 文件路径: src/main/aidl/com/chaomixian/vflow/services/IShellService.aidl
package com.chaomixian.vflow.services;

/**
 * [移植自 vClick - 最终修正版]
 * 定义了 Shizuku Shell 服务的接口。
 * 使用与 vClick 完全一致的、带显式事务码的旧式定义，以确保最大兼容性。
 */
interface IShizukuUserService {
    /**
     * 销毁服务 - Shizuku 要求的标准方法。
     * 事务码 16777114 即 IBinder.LAST_CALL_TRANSACTION，这是 Shizuku 识别它的关键。
     */
    void destroy() = 16777114;

    /**
     * 执行 shell 命令。
     */
    String exec(String command) = 1;

    /**
     * 退出服务。
     */
    void exit() = 2;
}