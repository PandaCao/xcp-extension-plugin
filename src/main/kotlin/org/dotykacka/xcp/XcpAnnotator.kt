package org.dotykacka.xcp

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import java.util.ArrayDeque

class XcpAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? PsiFile ?: return
        if (file.fileType != XcpFileType) return

        val text = file.text
        validateRequiredTopLevelKeys(text, holder)
        validateStartPhase(text, holder)
        validateBalancedDelimiters(text, holder)
    }

    private fun validateRequiredTopLevelKeys(text: String, holder: AnnotationHolder) {
        val keys = collectTopLevelKeys(text).map { it.name }.toSet()
        for (required in REQUIRED_TOP_LEVEL_KEYS) {
            if (required !in keys) {
                holder.newAnnotation(HighlightSeverity.WARNING, "XCP process is missing required top-level field '$required'")
                    .range(TextRange(0, text.length.coerceAtMost(1)))
                    .create()
            }
        }
    }

    private fun validateStartPhase(text: String, holder: AnnotationHolder) {
        val startPhase = Regex("""\bstart_phase\s*:\s*(['"])(.*?)\1""").find(text) ?: return
        val phaseId = startPhase.groupValues[2]
        val phasesBlock = Regex("""\bphases\s*:\s*\[""", RegexOption.DOT_MATCHES_ALL).find(text) ?: return
        val phaseIds = Regex("""\bid\s*:\s*(['"])(.*?)\1""")
            .findAll(text, phasesBlock.range.last)
            .map { it.groupValues[2] }
            .toSet()

        if (phaseId !in phaseIds) {
            holder.newAnnotation(HighlightSeverity.WARNING, "start_phase '$phaseId' does not match any phase id")
                .range(TextRange(startPhase.range.first, startPhase.range.last + 1))
                .create()
        }
    }

    private fun validateBalancedDelimiters(text: String, holder: AnnotationHolder) {
        val stack = ArrayDeque<Pair<Char, Int>>()
        for (token in scanTokens(text)) {
            when (token.type) {
                XcpTokenTypes.L_BRACE -> stack.push('}' to token.start)
                XcpTokenTypes.L_BRACKET -> stack.push(']' to token.start)
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> {
                    val expected = if (token.type == XcpTokenTypes.R_BRACE) '}' else ']'
                    if (stack.isEmpty() || stack.pop().first != expected) {
                        holder.newAnnotation(HighlightSeverity.ERROR, "Unmatched '${text[token.start]}'")
                            .range(TextRange(token.start, token.end))
                            .create()
                    }
                }
            }
        }
        for ((expected, offset) in stack) {
            holder.newAnnotation(HighlightSeverity.ERROR, "Missing closing '$expected'")
                .range(TextRange(offset, offset + 1))
                .create()
        }
    }

    private fun collectTopLevelKeys(text: String): List<XcpNamedRange> {
        val tokens = scanTokens(text)
        val result = mutableListOf<XcpNamedRange>()
        var depth = 0
        for (i in tokens.indices) {
            val token = tokens[i]
            when (token.type) {
                XcpTokenTypes.L_BRACE, XcpTokenTypes.L_BRACKET -> depth++
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> depth--
                XcpTokenTypes.IDENTIFIER, XcpTokenTypes.STRING -> {
                    if (depth == 1 && tokens.getOrNull(i + 1)?.type == XcpTokenTypes.COLON) {
                        result += XcpNamedRange(token.unquotedText(text), token.start, token.end)
                    }
                }
            }
        }
        return result
    }

    private fun scanTokens(text: String): List<XcpToken> {
        val lexer = XcpLexer()
        lexer.start(text)
        val tokens = mutableListOf<XcpToken>()
        while (lexer.tokenType != null) {
            val type = lexer.tokenType
            if (type != null) tokens += XcpToken(type, lexer.tokenStart, lexer.tokenEnd)
            lexer.advance()
        }
        return tokens
    }

    private fun XcpToken.unquotedText(text: String): String {
        val raw = text.substring(start, end)
        return if (raw.length >= 2 && (raw.first() == '"' || raw.first() == '\'')) raw.substring(1, raw.length - 1) else raw
    }

    companion object {
        private val REQUIRED_TOP_LEVEL_KEYS = setOf("id", "version", "name", "title", "start_phase", "variables", "phases")
    }
}

private data class XcpToken(val type: com.intellij.psi.tree.IElementType, val start: Int, val end: Int)
private data class XcpNamedRange(val name: String, val start: Int, val end: Int)
