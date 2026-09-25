// 文件: main/java/com/chaomixian/vflow/ui/webview/WebViewConfirmationActivity.kt
// 描述: WebView 模块（非后台模式）的确认界面。
//      应用栏：返回按钮（WebView 历史后退，永不完成模块）、页面标题
//      （文档标题，无标题时使用 URL 域名）、确认按钮（显式确认闸门 ——
//      模块暂停直到按下，确认当前显示的页面即应返回的页面）。
//
// File: main/java/com/chaomixian/vflow/ui/webview/WebViewConfirmationActivity.kt
// Description: The confirmation screen of the WebView module (non-headless
//      mode). App bar: a Back button (WebView history back, never completes
//      the module), the page title (document title, falling back to the
//      URL's domain), and a Check button (an explicit confirmation gate —
//      the module stays paused until it is pressed, confirming that the
//      currently displayed page is the page to return).

package com.chaomixian.vflow.ui.webview

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.webkit.WebView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.module.network.webview.WebViewSessionManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.snackbar.Snackbar

/**
 * 非后台模式的确认界面。
 *
 * 应用栏：
 *  - 返回按钮：普通的 WebView 导航（goBack）；永不完成模块，
 *    无历史时禁用。
 *  - 标题：页面的文档标题；页面没有标题时使用 URL 的域名。
 *  - 确认按钮：显式的确认闸门。模块保持暂停，直到用户按下它，
 *    确认当前显示的页面就是应返回的页面。此后模块才求值返回变量
 *    并产出输出。
 *
 * 界面只持有临时 UI/WebView 状态：可序列化的模块配置保留在宿主
 * （步骤参数）中；本界面仅通过启动 Intent 接收会话 id。
 *
 * The confirmation screen for non-headless mode.
 *
 * App bar:
 *  - Back button: plain WebView navigation (goBack); it never completes
 *    the module and is disabled when there is no history.
 *  - Title: the page's document title; falls back to the URL's domain
 *    when the page has no title.
 *  - Check button: the explicit confirmation gate. The module stays
 *    paused until the user presses it, confirming that the currently
 *    displayed page is the page to return. Only then does the module
 *    evaluate the return variable and produce outputs.
 *
 * The screen only holds transient UI/WebView state: the serializable
 * module configuration is owned by the host (step parameters); this
 * screen only receives the session id through the launch Intent.
 */
class WebViewConfirmationActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private var webView: WebView? = null
    private var session: com.chaomixian.vflow.core.workflow.module.network.webview.WebViewSession? = null
    private var confirmed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview_confirmation)

        toolbar = findViewById(R.id.toolbar_webview_confirmation)
        webView = findViewById(R.id.webview_confirmation)

        val sessionId = intent?.getStringExtra(EXTRA_SESSION_ID).orEmpty()
        val currentSession = WebViewSessionManager.get(sessionId)
        if (currentSession == null || webView == null) {
            finish()
            return
        }
        session = currentSession

        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            val view = webView
            if (view != null && view.canGoBack()) view.goBack()
        }
        toolbar.title = WebViewUiLogic.resolveToolbarTitle(null, currentSession.spec.url)

        // 系统返回手势与应用栏返回按钮行为一致：导航 WebView 历史；
        // 在没有 Check 的情况下离开界面则取消模块（onDestroy 处理）。
        // The system back gesture mirrors the app-bar Back button:
        // navigate WebView history; leaving the screen without Check
        // cancels the module (handled in onDestroy).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val view = webView
                if (view != null && view.canGoBack()) {
                    view.goBack()
                } else {
                    finish()
                }
            }
        })

        currentSession.attachWebView(webView!!, this)
    }

    fun onPageTitleReceived(title: String?, url: String?) {
        toolbar.title = WebViewUiLogic.resolveToolbarTitle(title, url)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_webview_confirmation, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_webview_check -> {
                onCheckPressed()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onCheckPressed() {
        if (confirmed) return
        confirmed = true
        val view = webView
        if (view == null) {
            finish()
            return
        }
        // 告知用户模块正在收尾（收集很快）。
        // Tell the user the module is wrapping up (collection is quick).
        runCatching {
            Snackbar.make(
                findViewById(android.R.id.content),
                R.string.webview_confirmation_check,
                Snackbar.LENGTH_SHORT
            ).show()
        }
        session?.confirmFromUi()
    }

    override fun onDestroy() {
        val currentSession = session
        session = null
        val view = webView
        // WebView 的销毁由会话负责（cleanup()）；这里只解除界面的引用。
        // The WebView's destruction is the session's job (cleanup()); here
        // we only release the screen's references.
        webView = null
        if (currentSession != null) {
            currentSession.onActivityDestroyed(confirmed)
        }
        if (view != null) {
            runCatching { (view.parent as? android.view.ViewGroup)?.removeView(view) }
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SESSION_ID = "sessionId"
    }
}
