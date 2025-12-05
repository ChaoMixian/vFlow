// 文件：main/java/com/chaomixian/vflow/ui/settings/KeyTesterActivity.kt
package com.chaomixian.vflow.ui.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.view.MenuItem
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.chaomixian.vflow.services.ShizukuManager
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 按键测试 Activity
 * 用于查找物理按键的设备路径 (Device Path) 和按键代码 (Code Name/Key Code)。
 * 直接显示 getevent -l 的原始数据。
 */
class KeyTesterActivity : AppCompatActivity() {

    private lateinit var logTextView: TextView
    private lateinit var statusTextView: TextView
    private var listenerJob: Job? = null
    private val scriptFileName = "vflow_key_tester.sh"
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())

    // 接收来自 Shell 脚本的广播
    private val keyEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_KEY_TEST_EVENT) {
                val rawData = intent.getStringExtra("data") ?: return
                displayRawData(rawData)
            }
        }
    }

    companion object {
        const val ACTION_KEY_TEST_EVENT = "com.chaomixian.vflow.KEY_TEST_EVENT"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.chaomixian.vflow.R.layout.activity_key_tester)

        val toolbar = findViewById<MaterialToolbar>(com.chaomixian.vflow.R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "按键测试器 (原始数据)"

        logTextView = findViewById(com.chaomixian.vflow.R.id.text_logs)
        statusTextView = findViewById(com.chaomixian.vflow.R.id.text_status)

        // 注册广播接收器
        val filter = IntentFilter(ACTION_KEY_TEST_EVENT)
        ContextCompat.registerReceiver(this, keyEventReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        startKeyListening()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(keyEventReceiver)
        } catch (e: Exception) {
            // 忽略未注册异常
        }
        stopKeyListening()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun startKeyListening() {
        if (!ShizukuManager.isShizukuActive(this)) {
            statusTextView.text = "Shizuku 未激活，无法监听按键。"
            return
        }

        statusTextView.text = "正在初始化监听脚本..."
        logTextView.text = "请按下物理按键...\n(将显示 getevent -l 原始输出)\n----------------------------------------\n"

        listenerJob = lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 创建临时的监听脚本
                // 仅过滤保留 EV_KEY 以避免触摸屏数据刷屏，但保留整行原始输出
                val scriptContent = """
                    #!/system/bin/sh
                    getevent -l | grep --line-buffered "EV_KEY" | while read line; do
                        am broadcast -a $ACTION_KEY_TEST_EVENT --es data "${'$'}line"
                    done
                """.trimIndent()

                val scriptFile = File(cacheDir, scriptFileName)
                scriptFile.writeText(scriptContent)
                scriptFile.setExecutable(true)

                withContext(Dispatchers.Main) {
                    statusTextView.text = "监听中... (过滤: EV_KEY)"
                }

                // 执行脚本 (会阻塞，直到被 kill)
                ShizukuManager.execShellCommand(this@KeyTesterActivity, "sh ${scriptFile.absolutePath}")

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    statusTextView.text = "监听出错: ${e.message}"
                }
            }
        }
    }

    private fun stopKeyListening() {
        listenerJob?.cancel()
        // 强制杀掉所有相关的 getevent 进程，防止后台残留
        lifecycleScope.launch(Dispatchers.IO) {
            ShizukuManager.execShellCommand(this@KeyTesterActivity, "pkill -f \"getevent -l\"")
            ShizukuManager.execShellCommand(this@KeyTesterActivity, "pkill -f $scriptFileName")
        }
    }

    // 直接显示原始数据
    private fun displayRawData(rawData: String) {
        val timestamp = timeFormat.format(Date())
        // 将新日志插到最前面，并附带时间戳
        val logLine = "[$timestamp] $rawData\n"
        logTextView.text = logLine + logTextView.text

        // 如果日志太长，为了性能可以截断（可选）
        if (logTextView.text.length > 10000) {
            logTextView.text = logTextView.text.substring(0, 10000)
        }
    }
}