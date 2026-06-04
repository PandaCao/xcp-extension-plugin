package org.dotykacka.xcp

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.TokenType
import java.util.ArrayDeque

object XcpValidator {
    fun validate(text: String): List<XcpIssue> {
        return buildList {
            validateRequiredTopLevelKeys(text, this)
            validateStartPhase(text, this)
            validateInputVariablesAreDeclared(text, this)
            validateTableViewVariablesAreDeclared(text, this)
            validateTransitionTargetsExist(text, this)
            validateFunctionsStringQuoteStyle(text, this)
            validateBalancedDelimiters(text, this)
        }
    }

    private fun validateRequiredTopLevelKeys(text: String, issues: MutableList<XcpIssue>) {
        val keys = collectTopLevelKeys(text).map { it.name }.toSet()
        for (required in REQUIRED_TOP_LEVEL_KEYS) {
            if (required !in keys) {
                issues += XcpIssue(
                    HighlightSeverity.WARNING,
                    "XCP process is missing required top-level field '$required'",
                    TextRange(0, text.length.coerceAtMost(1))
                )
            }
        }
    }

    private fun validateStartPhase(text: String, issues: MutableList<XcpIssue>) {
        val startPhase = Regex("""\bstart_phase\s*:\s*(['"])(.*?)\1""").find(text) ?: return
        val phaseId = startPhase.groupValues[2]
        val phasesBlock = Regex("""\bphases\s*:\s*\[""", RegexOption.DOT_MATCHES_ALL).find(text) ?: return
        val phaseIds = Regex("""\bid\s*:\s*(['"])(.*?)\1""")
            .findAll(text, phasesBlock.range.last)
            .map { it.groupValues[2] }
            .toSet()

        if (phaseId !in phaseIds) {
            issues += XcpIssue(
                HighlightSeverity.WARNING,
                "start_phase '$phaseId' does not match any phase id",
                TextRange(startPhase.range.first, startPhase.range.last + 1)
            )
        }
    }

    private fun validateInputVariablesAreDeclared(text: String, issues: MutableList<XcpIssue>) {
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
                issues += XcpIssue(
                    HighlightSeverity.WARNING,
                    "Input variable '${reference.name}' is not declared in top-level variables",
                    TextRange(reference.start, reference.end)
                )
            }
        }
    }

    private fun validateTableViewVariablesAreDeclared(text: String, issues: MutableList<XcpIssue>) {
        val tokens = scanTokens(text).significant()
        val variablesRange = findTopLevelValueRange(tokens, "variables", text) ?: return
        val tableViewRange = findTopLevelValueRange(tokens, "table_view", text) ?: return
        val declaredVariables = collectVariableIds(tokens, variablesRange, text) + SPECIAL_VIEW_FIELDS

        for (reference in collectStringArrayValues(tokens, tableViewRange, text)) {
            if (reference.name !in declaredVariables) {
                issues += XcpIssue(
                    HighlightSeverity.WARNING,
                    "table_view variable '${reference.name}' is not declared in top-level variables",
                    TextRange(reference.start, reference.end)
                )
            }
        }
    }

    private fun validateTransitionTargetsExist(text: String, issues: MutableList<XcpIssue>) {
        val tokens = scanTokens(text).significant()
        val phasesRange = findTopLevelValueRange(tokens, "phases", text) ?: return
        val phaseIds = collectPhaseIds(tokens, phasesRange, text)
        if (phaseIds.isEmpty()) return

        for (target in collectTransitionTargets(tokens, phasesRange, text)) {
            if (target.name !in phaseIds) {
                issues += XcpIssue(
                    HighlightSeverity.WARNING,
                    "Transition target '${target.name}' does not match any phase id",
                    TextRange(target.start, target.end)
                )
            }
        }
    }

    private fun validateBalancedDelimiters(text: String, issues: MutableList<XcpIssue>) {
        val stack = ArrayDeque<Pair<Char, Int>>()
        for (token in scanTokens(text)) {
            when (token.type) {
                XcpTokenTypes.L_BRACE -> stack.push('}' to token.start)
                XcpTokenTypes.L_BRACKET -> stack.push(']' to token.start)
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> {
                    val expected = if (token.type == XcpTokenTypes.R_BRACE) '}' else ']'
                    if (stack.isEmpty() || stack.pop().first != expected) {
                        issues += XcpIssue(
                            HighlightSeverity.ERROR,
                            "Unmatched '${text[token.start]}'",
                            TextRange(token.start, token.end)
                        )
                    }
                }
            }
        }
        for ((expected, offset) in stack) {
            issues += XcpIssue(
                HighlightSeverity.ERROR,
                "Missing closing '$expected'",
                TextRange(offset, offset + 1)
            )
        }
    }

    private fun validateFunctionsStringQuoteStyle(text: String, issues: MutableList<XcpIssue>) {
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
                        issues += XcpIssue(
                            HighlightSeverity.WARNING,
                            "Use $preferredQuote inside this functions block because the outer delimiter is $activeOuterQuote",
                            TextRange(token.start, token.start + 1)
                        )
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

    private fun collectPhaseIds(tokens: List<XcpToken>, phasesRange: IntRange, text: String): Set<String> {
        val result = mutableSetOf<String>()
        var depth = 0
        for (i in phasesRange) {
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

    private fun collectTransitionTargets(tokens: List<XcpToken>, phasesRange: IntRange, text: String): List<XcpNamedRange> {
        val result = mutableListOf<XcpNamedRange>()
        for (i in phasesRange) {
            val token = tokens.getOrNull(i) ?: continue
            if (!token.isNameToken() || token.unquotedText(text) != "transitions" || tokens.getOrNull(i + 1)?.type != XcpTokenTypes.COLON) {
                continue
            }

            val transitionsRange = balancedValueRange(tokens, i + 2) ?: continue
            val transitionValue = tokens.getOrNull(i + 2)
            if (transitionValue?.type == XcpTokenTypes.STRING) {
                result += stringValueRange(transitionValue, text)
            } else {
                result += findObjectPropertyValues(tokens, transitionsRange, "target", text)
                    .filter { tokens.getOrNull(it.tokenIndex)?.type == XcpTokenTypes.STRING }
                    .map { stringValueRange(tokens[it.tokenIndex], text) }
            }
        }
        return result.distinctBy { it.name to it.start }
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

    private fun findObjectPropertyValues(tokens: List<XcpToken>, objectRange: IntRange, key: String, text: String): List<XcpTokenIndex> {
        val result = mutableListOf<XcpTokenIndex>()
        for (i in objectRange) {
            val token = tokens.getOrNull(i) ?: continue
            if (token.isNameToken() && token.unquotedText(text) == key && tokens.getOrNull(i + 1)?.type == XcpTokenTypes.COLON) {
                tokens.getOrNull(i + 2)?.let { result += XcpTokenIndex(i + 2) }
            }
        }
        return result
    }

    private fun collectStringArrayValues(tokens: List<XcpToken>, range: IntRange, text: String): List<XcpNamedRange> {
        return range
            .mapNotNull { tokens.getOrNull(it) }
            .filter { it.type == XcpTokenTypes.STRING }
            .map { stringValueRange(it, text) }
    }

    private fun stringValueRange(token: XcpToken, text: String): XcpNamedRange {
        val name = token.unquotedText(text)
        return XcpNamedRange(name, token.start + 1, token.end - 1)
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

    private val REQUIRED_TOP_LEVEL_KEYS = setOf("id", "version", "name", "title", "start_phase", "variables", "phases")
    private val SPECIAL_VIEW_FIELDS = setOf(
        "title",
        "def_name",
        "process_duration",
        "health",
        "phase_name"
    )
}

data class XcpIssue(val severity: HighlightSeverity, val message: String, val range: TextRange)

private data class XcpToken(val type: com.intellij.psi.tree.IElementType, val start: Int, val end: Int)
private data class XcpNamedRange(val name: String, val start: Int, val end: Int)
private data class XcpTokenIndex(val tokenIndex: Int)
