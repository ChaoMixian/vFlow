package com.chaomixian.vflow.ui.chat

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.highlightedCodeBlock
import com.mikepenz.markdown.compose.elements.highlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.model.rememberMarkdownState

@Composable
fun ChatMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val state = rememberMarkdownState(markdown)
    Markdown(
        state,
        modifier = modifier,
        colors = markdownColor(text = contentColor),
        components = markdownComponents(
            codeBlock = highlightedCodeBlock,
            codeFence = highlightedCodeFence,
        ),
    )
}
