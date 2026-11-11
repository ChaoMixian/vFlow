const fs = require('fs');
const path = 'app/src/main/java/com/chaomixian/vflow/services/UiRecordingService.kt';
let s = fs.readFileSync(path, 'utf8');

function replaceBetween(startMarker, endMarker, replacement) {
  const start = s.indexOf(startMarker);
  if (start < 0) throw new Error('start not found: ' + startMarker);
  const end = s.indexOf(endMarker, start);
  if (end < 0) throw new Error('end not found: ' + endMarker);
  s = s.slice(0, start) + replacement + s.slice(end);
}

s = s.replace('    private var ballView: TextView? = null\n', '');
s = s.replace('    private var ballParams: WindowManager.LayoutParams? = null\n', '');

const showOverlay = `    private fun showOverlay(): Boolean {
        if (overlayView != null) return true
        if (!Settings.canDrawOverlays(this)) {
            toast("请先授予悬浮窗权限")
            stopSelf()
            return false
        }
        val density = resources.displayMetrics.density
        fun dp(value: Int): Int = (value * density).toInt()

        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(6), dp(8), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0xF0202124.toInt())
                setStroke(dp(1), 0x40FFFFFF)
            }
            elevation = dp(6).toFloat()
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val title = TextView(this).apply {
            text = "UI 录制"
            setTextColor(Color.WHITE)
            textSize = 11f
            setTypeface(typeface, Typeface.BOLD)
        }
        val timer = TextView(this).apply {
            text = "00:00"
            setTextColor(0xFFBBBBBB.toInt())
            textSize = 10f
            setPadding(dp(8), 0, 0, 0)
        }
        timerTextView = timer
        header.addView(title)
        header.addView(timer)
        panel.addView(header)

        val buttonRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, dp(4), 0, 0)
            gravity = Gravity.CENTER_VERTICAL
        }
        val startPause = Button(this).apply {
            text = if (recording && paused) "继续" else if (recording) "暂停" else "开始"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            minWidth = 0
            minHeight = 0
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0xFF2E7D32.toInt())
            }
            setOnClickListener {
                when {
                    !recording -> startRecording()
                    paused -> resumeRecording()
                    else -> pauseRecording()
                }
            }
        }
        val end = Button(this).apply {
            text = "结束"
            textSize = 10f
            setTextColor(Color.WHITE)
            setPadding(dp(8), dp(3), dp(8), dp(3))
            minWidth = 0
            minHeight = 0
            background = GradientDrawable().apply {
                cornerRadius = dp(8).toFloat()
                setColor(0xFFC62828.toInt())
            }
            setOnClickListener { endRecording() }
        }
        startPauseButton = startPause
        endButton = end
        buttonRow.addView(startPause)
        buttonRow.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(8), 1)
        })
        buttonRow.addView(end)
        panel.addView(buttonRow)
        panelView = panel

        val params = createOverlayParams().apply {
            x = (screenWidth - dp(190)).coerceAtLeast(0)
            y = (screenHeight / 3).coerceAtLeast(dp(80))
        }
        panelParams = params
        attachDragToAll(panel, params)
        windowManager.addView(panel, params)
        overlayView = panel
        panel.post {
            params.x = (screenWidth - panel.width).coerceAtLeast(0)
            params.y = params.y.coerceIn(0, (screenHeight - panel.height).coerceAtLeast(0))
            runCatching { windowManager.updateViewLayout(panel, params) }
        }
        return true
    }

`;

const drag = `    private fun attachDragToAll(view: View, params: WindowManager.LayoutParams) {
        attachDrag(view, params)
        if (view is android.view.ViewGroup) {
            for (index in 0 until view.childCount) {
                attachDragToAll(view.getChildAt(index), params)
            }
        }
    }

    private fun attachDrag(handle: View, params: WindowManager.LayoutParams) {
        var initialX = 0
        var initialY = 0
        var touchX = 0f
        var touchY = 0f
        var moved = false
        handle.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > dp(4) || kotlin.math.abs(dy) > dp(4)) moved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    overlayView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) {
                        if (view.isClickable) view.performClick()
                    } else {
                        snapOverlayToEdge(view, params)
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (moved) snapOverlayToEdge(view, params)
                    true
                }
                else -> false
            }
        }
    }

    private fun snapOverlayToEdge(view: View, params: WindowManager.LayoutParams) {
        val width = overlayView?.width?.takeIf { it > 0 } ?: view.width.takeIf { it > 0 } ?: 0
        val height = overlayView?.height?.takeIf { it > 0 } ?: view.height.takeIf { it > 0 } ?: 0
        params.x = if (params.x + width / 2 < screenWidth / 2) 0 else (screenWidth - width).coerceAtLeast(0)
        params.y = params.y.coerceIn(0, (screenHeight - height).coerceAtLeast(0))
        overlayView?.let { runCatching { windowManager.updateViewLayout(it, params) } }
    }

`;

replaceBetween('    private fun showOverlay(): Boolean {', '    private fun attachDrag(', showOverlay);
replaceBetween('    private fun attachDrag(', '    private fun createOverlayParams()', drag);

s = s.replace(/\n\s*collapseOverlay\(\)/g, '');
s = s.replace('        ballView = null\n', '');
s = s.replace('        ballParams = null\n', '');
s = s.replace('import android.graphics.drawable.GradientDrawable\n', 'import android.graphics.drawable.ColorDrawable\nimport android.graphics.drawable.GradientDrawable\n');
if (!s.includes('import android.graphics.drawable.ColorDrawable')) {
  throw new Error('ColorDrawable import missing');
}

const oldInputColors = /            val isNight = \(resources[\s\S]*?setHintTextColor\([^\n]*\n/;
if (!oldInputColors.test(s)) throw new Error('input colors block not found');
s = s.replace(oldInputColors, '            setTextColor(Color.WHITE)\n            setHintTextColor(0xFFB0B0B0.toInt())\n');

s = s.replace(
`        dialog.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }`,
`        dialog.window?.apply {
            setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
            setBackgroundDrawable(ColorDrawable(0xFF202124.toInt()))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)
        }`);

s = s.replace(
`        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {`,
`        dialog.setOnShowListener {
            val titleId = resources.getIdentifier("alertTitle", "id", "android")
            dialog.findViewById<TextView>(titleId)?.setTextColor(Color.WHITE)
            dialog.findViewById<TextView>(android.R.id.message)?.setTextColor(0xFFE0E0E0.toInt())
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {`);

if (s.includes('ballView') || s.includes('ballParams') || s.includes('collapseOverlay') || s.includes('expandOverlay')) {
  throw new Error('ball/expand/collapse references remain');
}

fs.writeFileSync(path, s);
console.log('rewritten', path);