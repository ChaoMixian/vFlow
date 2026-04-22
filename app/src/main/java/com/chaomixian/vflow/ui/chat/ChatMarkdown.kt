package com.chaomixian.vflow.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

@Composable
fun ChatMarkdownContent(
    markdown: String,
    modifier: Modifier = Modifier,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val blocks = remember(markdown) { parseMarkdownBlocks(markdown) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> MarkdownText(
                    text = block.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )

                is MarkdownBlock.Heading -> MarkdownText(
                    text = block.text,
                    style = when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                )

                is MarkdownBlock.BulletItem -> MarkdownListItem(
                    marker = "\u2022",
                    text = block.text,
                    color = contentColor,
                )

                is MarkdownBlock.OrderedItem -> MarkdownListItem(
                    marker = "${block.index}.",
                    text = block.text,
                    color = contentColor,
                )

                is MarkdownBlock.BlockQuote -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(20.dp),
                    tonalElevation = 0.dp,
                ) {
                    MarkdownText(
                        text = block.text,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor.copy(alpha = 0.9f),
                    )
                }

                is MarkdownBlock.CodeBlock -> Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(22.dp),
                    tonalElevation = 0.dp,
                ) {
                    Text(
                        text = block.code,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = contentColor,
                    )
                }

                MarkdownBlock.HorizontalRule -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                )
            }
        }
    }
}

@Composable
private fun MarkdownListItem(
    marker: String,
    text: String,
    color: Color,
) {
    MarkdownText(
        text = "$marker $text",
        style = MaterialTheme.typography.bodyLarge,
        color = color,
    )
}

@Composable
private fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle,
    color: Color,
    fontWeight: FontWeight? = null,
) {
    val uriHandler = LocalUriHandler.current
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, color, style, fontWeight, linkColor) {
        buildMarkdownAnnotatedString(text, color, linkColor, fontWeight)
    }
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = style.copy(color = color, fontWeight = fontWeight),
        onClick = { offset ->
            annotated.getStringAnnotations(tag = "url", start = offset, end = offset)
                .firstOrNull()
                ?.let { uriHandler.openUri(it.item) }
        },
    )
}

private fun buildMarkdownAnnotatedString(
    text: String,
    color: Color,
    linkColor: Color,
    fontWeight: FontWeight?,
): AnnotatedString {
    return buildAnnotatedString {
        appendInlineMarkdown(
            builder = this,
            text = text,
            baseColor = color,
            linkColor = linkColor,
            inheritedWeight = fontWeight,
        )
    }
}

private fun appendInlineMarkdown(
    builder: AnnotatedString.Builder,
    text: String,
    baseColor: Color,
    linkColor: Color,
    inheritedWeight: FontWeight?,
) {
    var index = 0
    while (index < text.length) {
        when {
            text.startsWith("**", index) -> {
                val end = text.indexOf("**", index + 2)
                if (end > index + 2) {
                    builder.pushStyle(
                        SpanStyle(
                            color = baseColor,
                            fontWeight = FontWeight.Bold,
                        )
                    )
                    appendInlineMarkdown(
                        builder,
                        text.substring(index + 2, end),
                        baseColor,
                        linkColor,
                        FontWeight.Bold,
                    )
                    builder.pop()
                    index = end + 2
                } else {
                    builder.append(text[index])
                    index += 1
                }
            }

            text.startsWith("`", index) -> {
                val end = text.indexOf('`', index + 1)
                if (end > index + 1) {
                    builder.pushStyle(
                        SpanStyle(
                            background = baseColor.copy(alpha = 0.1f),
                            color = baseColor,
                            fontFamily = FontFamily.Monospace,
                        )
                    )
                    builder.append(text.substring(index + 1, end))
                    builder.pop()
                    index = end + 1
                } else {
                    builder.append(text[index])
                    index += 1
                }
            }

            text.startsWith("[", index) -> {
                val closeBracket = text.indexOf(']', index + 1)
                val openParen = closeBracket.takeIf { it >= 0 }?.let { text.indexOf('(', it + 1) } ?: -1
                val closeParen = openParen.takeIf { it >= 0 }?.let { text.indexOf(')', it + 1) } ?: -1
                if (closeBracket > index && openParen == closeBracket + 1 && closeParen > openParen + 1) {
                    val label = text.substring(index + 1, closeBracket)
                    val url = text.substring(openParen + 1, closeParen)
                    builder.pushStringAnnotation(tag = "url", annotation = url)
                    builder.pushStyle(
                        SpanStyle(
                            color = linkColor,
                            textDecoration = TextDecoration.Underline,
                            fontWeight = inheritedWeight,
                        )
                    )
                    appendInlineMarkdown(builder, label, linkColor, linkColor, inheritedWeight)
                    builder.pop()
                    builder.pop()
                    index = closeParen + 1
                } else {
                    builder.append(text[index])
                    index += 1
                }
            }

            text.startsWith("*", index) || text.startsWith("_", index) -> {
                val marker = text[index]
                val end = text.indexOf(marker, index + 1)
                if (end > index + 1) {
                    builder.pushStyle(
                        SpanStyle(
                            color = baseColor,
                            fontWeight = inheritedWeight,
                            fontStyle = FontStyle.Italic,
                        )
                    )
                    appendInlineMarkdown(
                        builder,
                        text.substring(index + 1, end),
                        baseColor,
                        linkColor,
                        inheritedWeight,
                    )
                    builder.pop()
                    index = end + 1
                } else {
                    builder.append(text[index])
                    index += 1
                }
            }

            else -> {
                builder.pushStyle(SpanStyle(color = baseColor, fontWeight = inheritedWeight))
                builder.append(text[index])
                builder.pop()
                index += 1
            }
        }
    }
}

private fun parseMarkdownBlocks(markdown: String): List<MarkdownBlock> {
    val normalized = markdown.replace("\r\n", "\n").trim()
    if (normalized.isBlank()) return listOf(MarkdownBlock.Paragraph(""))
    val lines = normalized.split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0
    while (index < lines.size) {
        val rawLine = lines[index]
        val line = rawLine.trimEnd()
        if (line.isBlank()) {
            index += 1
            continue
        }
        when {
            line.startsWith("```") -> {
                val codeLines = mutableListOf<String>()
                index += 1
                while (index < lines.size && !lines[index].trimStart().startsWith("```")) {
                    codeLines += lines[index]
                    index += 1
                }
                blocks += MarkdownBlock.CodeBlock(codeLines.joinToString("\n").trimEnd())
                if (index < lines.size) index += 1
            }

            line.matches(Regex("^#{1,6}\\s+.*$")) -> {
                val level = line.takeWhile { it == '#' }.length
                blocks += MarkdownBlock.Heading(level = level, text = line.drop(level).trim())
                index += 1
            }

            line.matches(Regex("^([-*_])(?:\\s*\\1){2,}\\s*$")) -> {
                blocks += MarkdownBlock.HorizontalRule
                index += 1
            }

            line.matches(Regex("^\\s*[-*+]\\s+.+$")) -> {
                blocks += MarkdownBlock.BulletItem(text = line.replaceFirst(Regex("^\\s*[-*+]\\s+"), ""))
                index += 1
            }

            line.matches(Regex("^\\s*\\d+\\.\\s+.+$")) -> {
                val match = Regex("^\\s*(\\d+)\\.\\s+(.+)$").find(line)
                blocks += MarkdownBlock.OrderedItem(
                    index = match?.groupValues?.get(1)?.toIntOrNull() ?: 1,
                    text = match?.groupValues?.get(2).orEmpty(),
                )
                index += 1
            }

            line.startsWith(">") -> {
                val quoteLines = mutableListOf<String>()
                while (index < lines.size && lines[index].trimStart().startsWith(">")) {
                    quoteLines += lines[index].trimStart().removePrefix(">").trimStart()
                    index += 1
                }
                blocks += MarkdownBlock.BlockQuote(quoteLines.joinToString("\n").trim())
            }

            else -> {
                val paragraphLines = mutableListOf<String>()
                while (index < lines.size) {
                    val current = lines[index]
                    val trimmed = current.trim()
                    if (trimmed.isBlank() ||
                        trimmed.startsWith("```") ||
                        trimmed.matches(Regex("^#{1,6}\\s+.*$")) ||
                        trimmed.matches(Regex("^([-*_])(?:\\s*\\1){2,}\\s*$")) ||
                        trimmed.matches(Regex("^\\s*[-*+]\\s+.+$")) ||
                        trimmed.matches(Regex("^\\s*\\d+\\.\\s+.+$")) ||
                        trimmed.startsWith(">")
                    ) {
                        break
                    }
                    paragraphLines += current.trimEnd()
                    index += 1
                }
                blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString("\n").trim())
            }
        }
    }
    return blocks
}

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class BulletItem(val text: String) : MarkdownBlock
    data class OrderedItem(val index: Int, val text: String) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data class CodeBlock(val code: String) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
}
