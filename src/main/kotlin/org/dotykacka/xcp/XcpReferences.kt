package org.dotykacka.xcp

import com.intellij.openapi.util.TextRange
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiReferenceBase
import com.intellij.psi.PsiReferenceContributor
import com.intellij.psi.PsiReferenceProvider
import com.intellij.psi.PsiReferenceRegistrar
import com.intellij.psi.TokenType
import com.intellij.util.ProcessingContext

class XcpReferenceContributor : PsiReferenceContributor() {
    override fun registerReferenceProviders(registrar: PsiReferenceRegistrar) {
        registrar.registerReferenceProvider(
            PlatformPatterns.psiElement(),
            object : PsiReferenceProvider() {
                override fun getReferencesByElement(element: PsiElement, context: ProcessingContext): Array<PsiReference> {
                    return XcpReferenceFactory.createAll(element)
                }
            }
        )
    }
}

object XcpReferenceFactory {
    fun create(element: PsiElement): PsiReference? {
        return createAll(element).firstOrNull()
    }

    fun createAll(element: PsiElement): Array<PsiReference> {
        return XcpPsiReference.createAll(element)
    }
}

private class XcpPsiReference(
    element: PsiElement,
    rangeInElement: TextRange,
    private val targetName: String,
    private val kind: XcpReferenceKind
) : PsiReferenceBase<PsiElement>(element, rangeInElement, false) {
    override fun resolve(): PsiElement? {
        val file = element.containingFile ?: return null
        val declarations = XcpStructure.from(file.text)
        val target = when (kind) {
            XcpReferenceKind.VARIABLE -> declarations.variableDeclarations[targetName]
            XcpReferenceKind.PHASE -> declarations.phaseDeclarations[targetName]
            XcpReferenceKind.FUNCTION -> declarations.functionDeclarations[targetName]
        } ?: return null

        return file.findElementAt(target.start)
    }

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        fun createAll(element: PsiElement): Array<PsiReference> {
            val file = element.containingFile ?: return PsiReference.EMPTY_ARRAY
            val structure = XcpStructure.from(file.text)
            val absoluteStart = element.textRange.startOffset
            val token = structure.tokens.firstOrNull { it.start == absoluteStart } ?: return PsiReference.EMPTY_ARRAY
            return structure.referenceUsagesFor(token).map { usage ->
                val rangeInElement = TextRange(
                    usage.range.startOffset - absoluteStart,
                    usage.range.endOffset - absoluteStart
                )
                XcpPsiReference(element, rangeInElement, usage.name, usage.kind)
            }.toTypedArray()
        }
    }
}

enum class XcpReferenceKind {
    VARIABLE,
    PHASE,
    FUNCTION
}

data class XcpReferenceUsage(
    val kind: XcpReferenceKind,
    val name: String,
    val range: TextRange
)

data class XcpDeclaration(
    val name: String,
    val start: Int,
    val end: Int
)

data class XcpStructure(
    val text: String,
    val tokens: List<XcpTokenInfo>,
    val variableDeclarations: Map<String, XcpDeclaration>,
    val phaseDeclarations: Map<String, XcpDeclaration>,
    val functionDeclarations: Map<String, XcpDeclaration>
) {
    fun referenceUsages(): List<XcpReferenceUsage> {
        return tokens.mapIndexed { i, token -> referenceUsagesFor(token, i) }.flatten()
    }

    fun referenceUsagesFor(token: XcpTokenInfo): List<XcpReferenceUsage> {
        val tokenIndex = tokens.indexOfFirst { it.start == token.start && it.end == token.end }
        if (tokenIndex == -1) return emptyList()
        return referenceUsagesFor(token, tokenIndex)
    }

    fun referenceUsageFor(token: XcpTokenInfo): XcpReferenceUsage? {
        return referenceUsagesFor(token).firstOrNull()
    }

    fun declarationUsageFor(token: XcpTokenInfo): XcpReferenceUsage? {
        val tokenIndex = tokens.indexOfFirst { it.start == token.start && it.end == token.end }
        if (tokenIndex == -1) return null

        if (token.type == XcpTokenTypes.JS_FUNCTION_DECLARATION) {
            return XcpReferenceUsage(
                XcpReferenceKind.FUNCTION,
                token.unquotedText(text),
                token.contentRange()
            )
        }

        val previousKey = previousKeyName(tokenIndex)
        if (previousKey != "id") return null

        return when {
            isDeclarationInTopLevelArray(tokenIndex, "variables") -> XcpReferenceUsage(
                XcpReferenceKind.VARIABLE,
                token.unquotedText(text),
                token.contentRange()
            )
            isDeclarationInTopLevelArray(tokenIndex, "phases") -> XcpReferenceUsage(
                XcpReferenceKind.PHASE,
                token.unquotedText(text),
                token.contentRange()
            )
            else -> null
        }
    }

    fun usageTargetFor(token: XcpTokenInfo): XcpReferenceUsage? {
        return declarationUsageFor(token) ?: referenceUsageFor(token)
    }

    private fun referenceUsagesFor(token: XcpTokenInfo, tokenIndex: Int): List<XcpReferenceUsage> {
        if (isVariableDeclaration(tokenIndex) || isPhaseDeclaration(tokenIndex) || isFunctionDeclaration(tokenIndex)) return emptyList()

        val jsStringUsages = jsPropertyStringUsages(token, tokenIndex)
        if (jsStringUsages.isNotEmpty()) return jsStringUsages

        val jsTokenUsage = jsTokenUsage(token)
        if (jsTokenUsage != null) return listOf(jsTokenUsage)

        val itemUsage = formItemVariableUsage(token, tokenIndex)
        if (itemUsage != null) return listOf(itemUsage)

        if (isInsideTopLevelArray(tokenIndex, "table_view")) {
            return listOf(stringUsage(token, XcpReferenceKind.VARIABLE))
        }

        val previousKey = previousKeyName(tokenIndex)
        if (previousKey == "start_phase" || previousKey == "target" || previousKey == "transitions") {
            return listOf(stringUsage(token, XcpReferenceKind.PHASE))
        }
        if (previousKey == "var") {
            return listOf(stringUsage(token, XcpReferenceKind.VARIABLE))
        }
        if (isSetVariablesInputKey(tokenIndex)) {
            return listOf(
                XcpReferenceUsage(
                    XcpReferenceKind.VARIABLE,
                    token.unquotedText(text),
                    token.contentRange()
                )
            )
        }

        return emptyList()
    }

    private fun jsTokenUsage(token: XcpTokenInfo): XcpReferenceUsage? {
        return when (token.type) {
            XcpTokenTypes.JS_FUNCTION_CALL -> XcpReferenceUsage(
                XcpReferenceKind.FUNCTION,
                token.unquotedText(text),
                token.contentRange()
            )
            XcpTokenTypes.JS_IDENTIFIER -> {
                val name = token.unquotedText(text)
                if (name in variableDeclarations) {
                    XcpReferenceUsage(XcpReferenceKind.VARIABLE, name, token.contentRange())
                } else {
                    null
                }
            }
            else -> null
        }
    }

    private fun jsPropertyStringUsages(token: XcpTokenInfo, tokenIndex: Int): List<XcpReferenceUsage> {
        if (token.type != XcpTokenTypes.STRING || previousKeyName(tokenIndex) != "js") return emptyList()
        return scanJsExpressionReferences(token)
    }

    private fun scanJsExpressionReferences(token: XcpTokenInfo): List<XcpReferenceUsage> {
        val contentStart = token.start + 1
        val contentEnd = token.end - 1
        if (contentStart >= contentEnd) return emptyList()

        val result = mutableListOf<XcpReferenceUsage>()
        var index = contentStart
        var previousSignificantChar: Char? = null
        while (index < contentEnd) {
            val c = text[index]
            when {
                c.isWhitespace() -> index++
                c == '"' || c == '\'' || c == '`' -> index = skipQuotedJsString(index, contentEnd, c)
                c == '/' && index + 1 < contentEnd && text[index + 1] == '/' -> {
                    index += 2
                    while (index < contentEnd && text[index] != '\n' && text[index] != '\r') index++
                }
                c == '/' && index + 1 < contentEnd && text[index + 1] == '*' -> {
                    index += 2
                    while (index < contentEnd - 1 && !(text[index] == '*' && text[index + 1] == '/')) index++
                    index = (index + 2).coerceAtMost(contentEnd)
                }
                isJsIdentifierStart(c) -> {
                    val start = index
                    index++
                    while (index < contentEnd && isJsIdentifierPart(text[index])) index++
                    val name = text.substring(start, index)
                    val next = nextNonWhitespaceChar(index, contentEnd)
                    if (previousSignificantChar != '.' && name !in JS_RESERVED_WORDS) {
                        val kind = when {
                            next == '(' && name in functionDeclarations -> XcpReferenceKind.FUNCTION
                            next != '(' && name in variableDeclarations -> XcpReferenceKind.VARIABLE
                            else -> null
                        }
                        if (kind != null) {
                            result += XcpReferenceUsage(kind, name, TextRange(start, index))
                        }
                    }
                    previousSignificantChar = null
                }
                else -> {
                    previousSignificantChar = if (c.isWhitespace()) previousSignificantChar else c
                    index++
                }
            }
        }
        return result
    }

    private fun formItemVariableUsage(token: XcpTokenInfo, tokenIndex: Int): XcpReferenceUsage? {
        if (token.type != XcpTokenTypes.STRING) return null
        if (!isInsidePropertyArray(tokenIndex, "items")) return null
        val raw = token.unquotedText(text)
        val prefix = raw.substringBefore(':').substringBefore('(')
        val name = prefix.trim()
        if (name.isEmpty() || name !in variableDeclarations) return null
        val nameStart = token.start + 1 + prefix.indexOf(name)
        return XcpReferenceUsage(
            XcpReferenceKind.VARIABLE,
            name,
            TextRange(nameStart, nameStart + name.length)
        )
    }

    private fun stringUsage(token: XcpTokenInfo, kind: XcpReferenceKind): XcpReferenceUsage {
        return XcpReferenceUsage(
            kind,
            token.unquotedText(text),
            token.contentRange()
        )
    }

    private fun isVariableDeclaration(tokenIndex: Int): Boolean {
        return isDeclarationInTopLevelArray(tokenIndex, "variables")
    }

    private fun isPhaseDeclaration(tokenIndex: Int): Boolean {
        return isDeclarationInTopLevelArray(tokenIndex, "phases")
    }

    private fun isFunctionDeclaration(tokenIndex: Int): Boolean {
        return tokens.getOrNull(tokenIndex)?.type == XcpTokenTypes.JS_FUNCTION_DECLARATION
    }

    private fun isDeclarationInTopLevelArray(tokenIndex: Int, arrayKey: String): Boolean {
        if (previousKeyName(tokenIndex) != "id") return false
        val range = findTopLevelValueRange(arrayKey) ?: return false
        if (tokenIndex !in range) return false
        return relativeDepthAt(range.first, tokenIndex) == 2
    }

    private fun isInsideTopLevelArray(tokenIndex: Int, arrayKey: String): Boolean {
        val range = findTopLevelValueRange(arrayKey) ?: return false
        return tokenIndex in range
    }

    private fun isInsidePropertyArray(tokenIndex: Int, propertyKey: String): Boolean {
        for (i in tokens.indices) {
            val token = tokens[i]
            if (!token.isNameToken() || token.unquotedText(text) != propertyKey || tokens.getOrNull(i + 1)?.type != XcpTokenTypes.COLON) {
                continue
            }
            val range = balancedValueRange(i + 2) ?: continue
            if (tokenIndex in range &&
                tokens.getOrNull(i + 2)?.type == XcpTokenTypes.L_BRACKET &&
                relativeDepthAt(range.first, tokenIndex) == 1
            ) return true
        }
        return false
    }

    private fun isSetVariablesInputKey(tokenIndex: Int): Boolean {
        val token = tokens.getOrNull(tokenIndex) ?: return false
        if (!token.isNameToken() || tokens.getOrNull(tokenIndex + 1)?.type != XcpTokenTypes.COLON) return false
        val inputsProperty = propertyValueRangeContainingToken(tokenIndex, "inputs") ?: return false
        if (relativeDepthAt(inputsProperty.valueRange.first, tokenIndex) != 1) return false
        val stepRange = objectRangeContaining(inputsProperty.keyIndex) ?: return false
        return objectHasStringProperty(stepRange, "action", "setVariables")
    }

    private fun propertyValueRangeContainingToken(tokenIndex: Int, propertyKey: String): XcpPropertyValueRange? {
        for (i in tokens.indices) {
            val token = tokens[i]
            if (!token.isNameToken() || token.unquotedText(text) != propertyKey || tokens.getOrNull(i + 1)?.type != XcpTokenTypes.COLON) {
                continue
            }
            val range = balancedValueRange(i + 2) ?: continue
            if (tokenIndex in range) return XcpPropertyValueRange(i, range)
        }
        return null
    }

    private fun objectHasStringProperty(objectRange: IntRange, key: String, value: String): Boolean {
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
                    val valueToken = tokens.getOrNull(i + 2)
                    if (valueToken?.type == XcpTokenTypes.STRING && valueToken.unquotedText(text) == value) return true
                }
            }
        }
        return false
    }

    private fun objectRangeContaining(tokenIndex: Int): IntRange? {
        var depth = 0
        for (i in tokenIndex downTo 0) {
            when (tokens[i].type) {
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> depth++
                XcpTokenTypes.L_BRACE -> {
                    if (depth == 0) return balancedValueRange(i)
                    depth--
                }
                XcpTokenTypes.L_BRACKET -> depth--
            }
        }
        return null
    }

    private fun previousKeyName(tokenIndex: Int): String? {
        if (tokens.getOrNull(tokenIndex - 1)?.type != XcpTokenTypes.COLON) return null
        val key = tokens.getOrNull(tokenIndex - 2) ?: return null
        return if (key.isNameToken()) key.unquotedText(text) else null
    }

    private fun findTopLevelValueRange(key: String): IntRange? {
        var depth = 0
        for (i in tokens.indices) {
            val token = tokens[i]
            when (token.type) {
                XcpTokenTypes.L_BRACE, XcpTokenTypes.L_BRACKET -> depth++
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> depth--
                else -> if (token.isNameToken()) {
                    if (depth == 1 && token.unquotedText(text) == key && tokens.getOrNull(i + 1)?.type == XcpTokenTypes.COLON) {
                        return balancedValueRange(i + 2)
                    }
                }
            }
        }
        return null
    }

    private fun balancedValueRange(valueStart: Int): IntRange? {
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

    private fun relativeDepthAt(rangeStart: Int, tokenIndex: Int): Int {
        var depth = 0
        for (i in rangeStart..tokenIndex) {
            when (tokens[i].type) {
                XcpTokenTypes.L_BRACE, XcpTokenTypes.L_BRACKET -> depth++
                XcpTokenTypes.R_BRACE, XcpTokenTypes.R_BRACKET -> depth--
            }
        }
        return depth
    }

    private fun skipQuotedJsString(start: Int, end: Int, quote: Char): Int {
        var index = start + 1
        var escaped = false
        while (index < end) {
            val c = text[index]
            if (escaped) {
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == quote) {
                return index + 1
            }
            index++
        }
        return end
    }

    private fun nextNonWhitespaceChar(start: Int, end: Int): Char? {
        var index = start
        while (index < end && text[index].isWhitespace()) index++
        return if (index < end) text[index] else null
    }

    companion object {
        fun from(text: String): XcpStructure {
            val tokens = scanTokens(text).filter {
                it.type != TokenType.WHITE_SPACE &&
                    it.type != XcpTokenTypes.LINE_COMMENT &&
                    it.type != XcpTokenTypes.BLOCK_COMMENT &&
                    it.type != XcpTokenTypes.JS_LINE_COMMENT &&
                    it.type != XcpTokenTypes.JS_BLOCK_COMMENT
            }
            val structure = XcpStructure(text, tokens, emptyMap(), emptyMap(), emptyMap())
            return structure.copy(
                variableDeclarations = structure.collectDeclarations("variables"),
                phaseDeclarations = structure.collectDeclarations("phases"),
                functionDeclarations = structure.collectFunctionDeclarations()
            )
        }

        private fun scanTokens(text: String): List<XcpTokenInfo> {
            val lexer = XcpLexer()
            lexer.start(text)
            val result = mutableListOf<XcpTokenInfo>()
            while (lexer.tokenType != null) {
                val type = lexer.tokenType
                if (type != null) result += XcpTokenInfo(type, lexer.tokenStart, lexer.tokenEnd)
                lexer.advance()
            }
            return result
        }

        private val JS_RESERVED_WORDS = setOf(
            "break", "case", "catch", "const", "continue", "debugger", "default", "delete", "do", "else",
            "false", "finally", "for", "function", "if", "in", "instanceof", "let", "new", "null", "return",
            "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "with",
            "yield"
        )
    }

    private fun collectDeclarations(arrayKey: String): Map<String, XcpDeclaration> {
        val range = findTopLevelValueRange(arrayKey) ?: return emptyMap()
        val result = linkedMapOf<String, XcpDeclaration>()
        for (i in range) {
            val token = tokens.getOrNull(i) ?: continue
            if (token.type == XcpTokenTypes.STRING && isDeclarationInTopLevelArray(i, arrayKey)) {
                val name = token.unquotedText(text)
                result[name] = XcpDeclaration(name, token.start + 1, token.end - 1)
            }
        }
        return result
    }

    private fun collectFunctionDeclarations(): Map<String, XcpDeclaration> {
        val result = linkedMapOf<String, XcpDeclaration>()
        for (token in tokens) {
            if (token.type == XcpTokenTypes.JS_FUNCTION_DECLARATION) {
                val name = token.unquotedText(text)
                result[name] = XcpDeclaration(name, token.start, token.end)
            }
        }
        return result
    }

    private fun isJsIdentifierStart(c: Char): Boolean = c == '_' || c == '$' || Character.isLetter(c)

    private fun isJsIdentifierPart(c: Char): Boolean = isJsIdentifierStart(c) || Character.isDigit(c)
}

private data class XcpPropertyValueRange(
    val keyIndex: Int,
    val valueRange: IntRange
)

data class XcpTokenInfo(
    val type: com.intellij.psi.tree.IElementType,
    val start: Int,
    val end: Int
) {
    fun unquotedText(text: String): String {
        val raw = text.substring(start, end)
        return if (raw.length >= 2 && (raw.first() == '"' || raw.first() == '\'')) raw.substring(1, raw.length - 1) else raw
    }

    fun isNameToken(): Boolean {
        return type == XcpTokenTypes.IDENTIFIER || type == XcpTokenTypes.KEYWORD || type == XcpTokenTypes.STRING
    }

    fun contentRange(): TextRange {
        return if (type == XcpTokenTypes.STRING && end - start >= 2) {
            TextRange(start + 1, end - 1)
        } else {
            TextRange(start, end)
        }
    }
}
