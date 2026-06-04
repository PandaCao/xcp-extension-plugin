package org.dotykacka.xcp

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import java.util.ArrayDeque

class XcpAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? PsiFile ?: return
        if (file.fileType != XcpFileType) return

        val text = file.text
        validateRequiredTopLevelKeys(text, holder)
        validateStartPhase(text, holder)
        validateInputVariablesAreDeclared(text, holder)
        validateFunctionsStringQuoteStyle(text, holder)
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

    private fun validateInputVariablesAreDeclared(text: String, holder: AnnotationHolder) {
        val tokens = scanTokens(text).significant()
        val variablesRange = findTopLevelValueRange(tokens, "variables", text) ?: return
        val declaredVariables = collectVariableIds(tokens, variablesRange, text)
        if (declaredVariables.isEmpty()) return

        val references = buildList {
            findTopLevelValueRange(tokens, "input", text)?.let { addAll(collectInputReferences(tokens, it, text)) }
            addAll(collectNestedInputsFormReferences(tokens, text))
        }

        for (reference in references.distinctBy { it.name to it.start }) {
            if (reference.name !in declaredVariables) {
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    "Input variable '${reference.name}' is not declared in top-level variables"
                )
                    .range(TextRange(reference.start, reference.end))
                    .create()
            }
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

    private fun validateFunctionsStringQuoteStyle(text: String, holder: AnnotationHolder) {
        var outerQuote: Char? = null
        for (token in scanTokens(text)) {
            when (token.type) {
                XcpTokenTypes.JS_STRING_DELIMITER -> {
                    val quote = text.getOrNull(token.start)
                    outerQuote = if (outerQuote == null) quote else null
                }
                XcpTokenTypes.JS_STRING -> {
                    val activeOuterQuote = outerQuote ?: continue
                    if (text.getOrNull(token.start) == activeOuterQuote) {
                        val preferredQuote = if (activeOuterQuote == '"') "'" else "\""
                        holder.newAnnotation(
                            HighlightSeverity.WARNING,
                            "Use $preferredQuote inside this functions block because the outer delimiter is $activeOuterQuote"
                        )
                            .range(TextRange(token.start, token.start + 1))
                            .create()
                    }
                }
            }
        }
    }


    private fun collectTopLevelKeys(text: String): List<XcpNamedRange> {
        val tokens = scanTokens(text).significant()
        val result = mutableListOf<XcpNamedRange>()
        var depth = 0
        for (i in tokens.indices) {
            val token = tokens[i]
            when (token.type) {
                XcpTokenTypes.L_BRACE, XcpTokenTypes.L_BRACKET -> depth++
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> depth--
                else -> if (token.isNameToken()) {
                    if (depth == 1 && tokens.getOrNull(i + 1)?.type == XcpTokenTypes.COLON) {
                        result += XcpNamedRange(token.unquotedText(text), token.start, token.end)
                    }
                }
            }
        }
        return result
    }

    private fun findTopLevelValueRange(tokens: List<XcpToken>, key: String, text: String): IntRange? {
        var depth = 0
        for (i in tokens.indices) {
            val token = tokens[i]
            when (token.type) {
                XcpTokenTypes.L_BRACE, XcpTokenTypes.L_BRACKET -> depth++
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> depth--
                else -> if (token.isNameToken()) {
                    if (depth == 1 && token.unquotedText(text) == key && tokens.getOrNull(i + 1)?.type == XcpTokenTypes.COLON) {
                        val valueStart = i + 2
                        return balancedValueRange(tokens, valueStart)
                    }
                }
            }
        }
        return null
    }

    private fun balancedValueRange(tokens: List<XcpToken>, valueStart: Int): IntRange? {
        val first = tokens.getOrNull(valueStart) ?: return null
        if (first.type != XcpTokenTypes.L_BRACE && first.type != XcpTokenTypes.L_BRACKET) {
            return valueStart..valueStart
        }

        var depth = 0
        for (i in valueStart until tokens.size) {
            when (tokens[i].type) {
                XcpTokenTypes.L_BRACE, XcpTokenTypes.L_BRACKET -> depth++
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> {
                    depth--
                    if (depth == 0) return valueStart..i
                }
            }
        }
        return valueStart until tokens.size
    }

    private fun collectVariableIds(tokens: List<XcpToken>, variablesRange: IntRange, text: String): Set<String> {
        val result = mutableSetOf<String>()
        var depth = 0
        for (i in variablesRange) {
            val token = tokens.getOrNull(i) ?: continue
            when (token.type) {
                XcpTokenTypes.L_BRACE, XcpTokenTypes.L_BRACKET -> depth++
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> depth--
                else -> if (token.isNameToken()) {
                    if (depth == 2 && token.unquotedText(text) == "id" && tokens.getOrNull(i + 1)?.type == XcpTokenTypes.COLON) {
                        val value = tokens.getOrNull(i + 2)
                        if (value?.type == XcpTokenTypes.STRING) result += value.unquotedText(text)
                    }
                }
            }
        }
        return result
    }

    private fun collectInputReferences(tokens: List<XcpToken>, inputRange: IntRange, text: String): List<XcpNamedRange> {
        val result = mutableListOf<XcpNamedRange>()
        var depth = 0
        var inItemsArray = false
        var itemsArrayDepth = -1

        for (i in inputRange) {
            val token = tokens.getOrNull(i) ?: continue
            when (token.type) {
                XcpTokenTypes.L_BRACE, XcpTokenTypes.L_BRACKET -> {
                    depth++
                    if (inItemsArray && token.type == XcpTokenTypes.L_BRACKET && itemsArrayDepth == -1) itemsArrayDepth = depth
                }
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> {
                    if (inItemsArray && depth == itemsArrayDepth) {
                        inItemsArray = false
                        itemsArrayDepth = -1
                    }
                    depth--
                }
                else -> if (token.isNameToken()) {
                    val name = token.unquotedText(text)
                    if (name == "items" && tokens.getOrNull(i + 1)?.type == XcpTokenTypes.COLON) {
                        inItemsArray = true
                    } else if (inItemsArray && token.type == XcpTokenTypes.STRING) {
                        inputItemVariableName(token, text)?.let { result += it }
                    } else if (name == "id" && tokens.getOrNull(i + 1)?.type == XcpTokenTypes.COLON) {
                        val value = tokens.getOrNull(i + 2)
                        if (value?.type == XcpTokenTypes.STRING) result += XcpNamedRange(value.unquotedText(text), value.start + 1, value.end - 1)
                    }
                }
            }
        }
        return result.distinctBy { it.name to it.start }
    }

    private fun collectNestedInputsFormReferences(tokens: List<XcpToken>, text: String): List<XcpNamedRange> {
        val result = mutableListOf<XcpNamedRange>()
        for (i in tokens.indices) {
            val token = tokens[i]
            if (!token.isNameToken() || token.unquotedText(text) != "inputs" || tokens.getOrNull(i + 1)?.type != XcpTokenTypes.COLON) {
                continue
            }

            val inputsRange = balancedValueRange(tokens, i + 2) ?: continue
            for (formRange in findObjectPropertyRanges(tokens, inputsRange, "form", text)) {
                result += collectInputReferences(tokens, formRange, text)
            }
        }
        return result
    }

    private fun findObjectPropertyRanges(tokens: List<XcpToken>, objectRange: IntRange, key: String, text: String): List<IntRange> {
        val result = mutableListOf<IntRange>()
        var depth = 0
        for (i in objectRange) {
            val token = tokens.getOrNull(i) ?: continue
            when (token.type) {
                XcpTokenTypes.L_BRACE, XcpTokenTypes.L_BRACKET -> depth++
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> depth--
                else -> if (depth == 1 &&
                    token.isNameToken() &&
                    token.unquotedText(text) == key &&
                    tokens.getOrNull(i + 1)?.type == XcpTokenTypes.COLON
                ) {
                    balancedValueRange(tokens, i + 2)?.let { result += it }
                }
            }
        }
        return result
    }

    private fun inputItemVariableName(token: XcpToken, text: String): XcpNamedRange? {
        val raw = token.unquotedText(text)
        val variableName = raw.substringBefore(':').substringBefore('(').trim()
        if (variableName.isEmpty()) return null
        val valueStart = token.start + 1
        return XcpNamedRange(variableName, valueStart, valueStart + variableName.length)
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

    private fun XcpToken.isNameToken(): Boolean {
        return type == XcpTokenTypes.IDENTIFIER || type == XcpTokenTypes.KEYWORD || type == XcpTokenTypes.STRING
    }

    private fun List<XcpToken>.significant(): List<XcpToken> {
        return filter {
            it.type != TokenType.WHITE_SPACE &&
                it.type != XcpTokenTypes.LINE_COMMENT &&
                it.type != XcpTokenTypes.BLOCK_COMMENT &&
                it.type != XcpTokenTypes.JS_LINE_COMMENT &&
                it.type != XcpTokenTypes.JS_BLOCK_COMMENT
        }
    }

    companion object {
        private val REQUIRED_TOP_LEVEL_KEYS = setOf("id", "version", "name", "title", "start_phase", "variables", "phases")
    }
}

private data class XcpToken(val type: com.intellij.psi.tree.IElementType, val start: Int, val end: Int)
private data class XcpNamedRange(val name: String, val start: Int, val end: Int)
