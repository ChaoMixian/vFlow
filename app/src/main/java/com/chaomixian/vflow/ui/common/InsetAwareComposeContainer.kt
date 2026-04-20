package com.chaomixian.vflow.ui.common

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView

class InsetAwareComposeContainer(
    context: Context
) : FrameLayout(context) {

    val composeView = ComposeView(context).apply {
        layoutParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    var contentBottomInsetPx by mutableIntStateOf(0)

    init {
        addView(composeView)
    }
}
