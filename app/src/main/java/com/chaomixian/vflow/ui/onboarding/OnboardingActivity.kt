package com.chaomixian.vflow.ui.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.edit
import com.chaomixian.vflow.R
import com.chaomixian.vflow.core.workflow.WorkflowManager
import com.chaomixian.vflow.core.workflow.model.ActionStep
import com.chaomixian.vflow.core.workflow.model.Workflow
import com.chaomixian.vflow.permissions.Permission
import com.chaomixian.vflow.permissions.PermissionManager
import com.chaomixian.vflow.services.ShellManager
import com.chaomixian.vflow.ui.common.BaseActivity
import com.chaomixian.vflow.ui.main.MainActivity
import kotlinx.coroutines.launch
import java.util.UUID

class OnboardingActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OnboardingScreen(onFinish = { completeOnboarding() })
                }
            }
        }
    }

    private fun completeOnboarding() {
        createTutorialWorkflow()
        val prefs = getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean("is_first_run", false).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun createTutorialWorkflow() {
        val workflowManager = WorkflowManager(this)
        if (workflowManager.getAllWorkflows().any { it.name == "Hello vFlow" }) return

        val steps = listOf(
            ActionStep("vflow.trigger.manual", emptyMap()),
            ActionStep("vflow.device.delay", mapOf("duration" to 1000.0)),
            ActionStep("vflow.device.toast", mapOf("message" to "🎉 恭喜！vFlow 配置成功，您的第一个工作流执行完毕！"))
        )

        val workflow = Workflow(
            id = UUID.randomUUID().toString(),
            name = "Hello vFlow",
            steps = steps,
            isFavorite = true
        )
        workflowManager.saveWorkflow(workflow)
    }
}

// --- 主要屏幕 UI ---

@OptIn(ExperimentalFoundationApi::class, ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { 5 }) // 5页: 欢迎 -> 概念 -> Shell -> 权限 -> 完成
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize()) {
        // 顶部跳过按钮
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (pagerState.currentPage < 4) {
                TextButton(onClick = onFinish) {
                    Text("跳过", color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        // 中间内容区域
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            userScrollEnabled = false // 禁止滑动，强制通过交互进入下一页
        ) { pageIndex ->
            when (pageIndex) {
                0 -> WelcomePage()
                1 -> ConceptPage()
                2 -> ShellConfigPage(
                    onNext = { scope.launch { pagerState.animateScrollToPage(3) } }
                )
                3 -> PermissionsPage(
                    onNext = { scope.launch { pagerState.animateScrollToPage(4) } }
                )
                4 -> CompletionPage(onFinish = onFinish)
            }
        }

        // 底部导航栏
        AnimatedVisibility(
            visible = pagerState.currentPage in listOf(0, 1),
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            BottomNavigation(pagerState) {
                scope.launch {
                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                }
            }
        }
    }
}

// --- 各个页面组件 ---

@Composable
fun WelcomePage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_workflows),
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .padding(bottom = 32.dp),
            contentScale = ContentScale.Fit
        )
        Text(
            text = "欢迎来到 vFlow",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height( 16.dp))
        Text(
            text = "强大的安卓自动化工具\n自动处理繁琐的手机操作",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 28.sp
        )
    }
}

@Composable
fun ConceptPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_workflows),
            contentDescription = null,
            modifier = Modifier
                .size(100.dp)
                .padding(bottom = 32.dp)
        )
        Text(
            text = "工作流与模块",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(24.dp))

        ConceptItem(
            icon = Icons.Rounded.Polymer,
            title = "模块 (Module)",
            desc = "一个个独立的功能积木，如“点击”、“打开应用”或“判断”。"
        )
        Spacer(modifier = Modifier.height(16.dp))
        ConceptItem(
            icon = Icons.Rounded.Schema,
            title = "工作流 (Workflow)",
            desc = "将模块拼接在一起，形成完整的自动化任务脚本。"
        )
    }
}

@Composable
fun ConceptItem(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ShellConfigPage(onNext: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(MainActivity.PREFS_NAME, Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope() // 获取协程作用域

    var selectedMode by remember { mutableStateOf("none") } // none, shizuku, root
    var isVerified by remember { mutableStateOf(false) }
    var autoEnableAcc by remember { mutableStateOf(false) }
    var forceKeepAlive by remember { mutableStateOf(false) }

    // 动画状态
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = true

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Icon(
            imageVector = Icons.Rounded.Terminal,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Shell 增强模式",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "vFlow 可以利用 Shizuku 或 Root 权限执行更强大的操作（如模拟物理按键、后台截图等）。",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 选项卡片
        ModeSelectionCard(
            title = "Shizuku (推荐)",
            desc = "无需 Root，更安全。需预先激活 Shizuku。",
            isSelected = selectedMode == "shizuku",
            onClick = { selectedMode = "shizuku"; isVerified = false }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModeSelectionCard(
            title = "Root 权限",
            desc = "直接获取最高权限，功能最完整。",
            isSelected = selectedMode == "root",
            onClick = { selectedMode = "root"; isVerified = false }
        )
        Spacer(modifier = Modifier.height(8.dp))
        ModeSelectionCard(
            title = "暂不使用",
            desc = "仅使用无障碍服务，部分高级功能不可用。",
            isSelected = selectedMode == "none",
            onClick = { selectedMode = "none"; isVerified = true }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 验证区域
        AnimatedContent(targetState = selectedMode, label = "verification") { mode ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (mode != "none") {
                    if (!isVerified) {
                        Button(
                            onClick = {
                                if (mode == "shizuku") {
                                    if (ShellManager.isShizukuActive(context)) isVerified = true
                                    else Toast.makeText(context, "Shizuku 未运行或未授权", Toast.LENGTH_SHORT).show()
                                } else {
                                    if (ShellManager.isRootAvailable()) isVerified = true
                                    else Toast.makeText(context, "无法获取 Root 权限", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                        ) {
                            Text("检测权限并授权")
                        }
                    } else {
                        // 验证通过后的高级选项
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("权限验证通过", fontWeight = FontWeight.Bold)
                                }
                                Spacer(modifier = Modifier.height(12.dp))

                                // 自动开启无障碍
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.clickable { autoEnableAcc = !autoEnableAcc }
                                ) {
                                    Checkbox(checked = autoEnableAcc, onCheckedChange = { autoEnableAcc = it })
                                    Text("服务关闭时自动开启 (推荐)")
                                }

                                // Shizuku 特有的保活
                                if (mode == "shizuku") {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.clickable { forceKeepAlive = !forceKeepAlive }
                                    ) {
                                        Checkbox(checked = forceKeepAlive, onCheckedChange = { forceKeepAlive = it })
                                        Text("启用守护进程 (防杀后台)")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // 底部继续按钮
        val canProceed = selectedMode == "none" || isVerified
        Button(
            onClick = {
                // 保存设置
                prefs.edit {
                    putString("default_shell_mode", selectedMode)
                    putBoolean("autoEnableAccessibility", autoEnableAcc)
                    putBoolean("forceKeepAliveEnabled", forceKeepAlive)
                }
                // 如果开启了选项，在协程中执行
                scope.launch {
                    if (isVerified) {
                        if (autoEnableAcc) ShellManager.enableAccessibilityService(context)
                        // startWatcher 不是挂起函数，但放在协程里也没问题
                        if (forceKeepAlive && selectedMode == "shizuku") ShellManager.startWatcher(context)
                    }
                    onNext()
                }
            },
            enabled = canProceed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (selectedMode == "none") "继续 (不使用 Shell)" else "保存配置并继续")
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
fun ModeSelectionCard(title: String, desc: String, isSelected: Boolean, onClick: () -> Unit) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val borderWidth = if (isSelected) 2.dp else 0.dp

    OutlinedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(12.dp)),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        ),
        // 添加 BorderStroke 避免编译错误，虽然我们在 modifier 中用了 border
        border = BorderStroke(if (isSelected) 0.dp else 1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(selected = isSelected, onClick = null)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun PermissionsPage(onNext: () -> Unit) {
    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(false) }

    // 定义需要检查和申请的权限列表
    val permissions = listOf(
        PermissionManager.ACCESSIBILITY,
        PermissionManager.OVERLAY,
        PermissionManager.NOTIFICATIONS,
        PermissionManager.IGNORE_BATTERY_OPTIMIZATIONS, // 后台保活关键
        PermissionManager.STORAGE
    )

    // 检查是否全部授权的函数
    fun checkAllPermissions() {
        permissionsGranted = permissions.all { PermissionManager.isGranted(context, it) }
    }

    // 页面恢复时检查权限
    LaunchedEffect(Unit) { checkAllPermissions() }
    DisposableEffect(Unit) {
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(24.dp))
        Icon(Icons.Rounded.Shield, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Text("必要的权限", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("为了让自动化流畅运行，vFlow 需要以下权限。", color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(modifier = Modifier.height(24.dp))

        permissions.forEach { permission ->
            PermissionItemView(permission) { checkAllPermissions() }
            Spacer(modifier = Modifier.height(12.dp))
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth(),
            enabled = permissionsGranted
        ) {
            if (permissionsGranted) {
                Text("全部就绪，继续")
                Icon(Icons.Default.Check, null)
            } else {
                Text("请先授予所有权限")
            }
        }

        if (!permissionsGranted) {
            TextButton(onClick = onNext, modifier = Modifier.padding(top = 8.dp)) {
                Text("暂时跳过 (可能影响功能)", color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
fun PermissionItemView(permission: Permission, onCheckChanged: () -> Unit) {
    val context = LocalContext.current
    var isGranted by remember { mutableStateOf(PermissionManager.isGranted(context, permission)) }

    // 使用 Launcher 处理权限请求
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        isGranted = PermissionManager.isGranted(context, permission)
        onCheckChanged()
    }

    val requestRuntimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {
        isGranted = PermissionManager.isGranted(context, permission)
        onCheckChanged()
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f) else MaterialTheme.colorScheme.surface
        ),
        border = if(!isGranted) BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant) else null
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (isGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = permission.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = permission.description, style = MaterialTheme.typography.bodySmall, maxLines = 2)
            }
            if (!isGranted) {
                Button(
                    onClick = {
                        // 统一权限请求逻辑
                        val intent = PermissionManager.getSpecialPermissionIntent(context, permission)
                        if (intent != null) {
                            requestPermissionLauncher.launch(intent)
                        } else {
                            // 运行时权限
                            val perms = if (permission.runtimePermissions.isNotEmpty()) permission.runtimePermissions.toTypedArray() else arrayOf(permission.id)
                            requestRuntimeLauncher.launch(perms)
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("授权", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun CompletionPage(onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.RocketLaunch,
            contentDescription = null,
            modifier = Modifier.size(100.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))
        Text("准备起飞！", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            "您已经完成了所有基本配置。\n我们为您准备了一个示例工作流，\n现在就开始体验自动化吧！",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(48.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text("开始使用", fontSize = 18.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Default.KeyboardArrowRight, null)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BottomNavigation(pagerState: PagerState, onNext: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 指示器
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(pagerState.pageCount) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateFloatAsState(if (isSelected) 24f else 8f, label = "indicatorWidth")
                val color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(width.dp)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        FilledTonalButton(onClick = onNext) {
            Text("下一步")
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}