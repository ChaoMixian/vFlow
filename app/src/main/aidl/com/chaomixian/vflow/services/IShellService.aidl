// IShellService.aidl
package com.chaomixian.vclick.services;

// 注意：这个接口必须放在 app/src/main/aidl/com/chaomixian/vclick/services/ 目录下

interface IShellService {
    /**
     * 销毁服务 - Shizuku 要求的标准方法
     */
    void destroy() = 16777114;
    /**
     * 执行 shell 命令
     */
    String exec(String command) = 1;
    /**
     * 退出服务
     */
    void exit() = 2;
}