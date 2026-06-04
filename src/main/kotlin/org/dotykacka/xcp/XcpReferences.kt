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
                    if (element.node?.elementType != XcpTokenTypes.STRING) return PsiReference.EMPTY_ARRAY
                    val reference = XcpReferenceFactory.create(element) ?: return PsiReference.EMPTY_ARRAY
                    return arrayOf(reference)
                }
            }
        )
    }
}

object XcpReferenceFactory {
    fun create(element: PsiElement): PsiReference? {
        val stringElement = element.takeIf { it.node?.elementType == XcpTokenTypes.STRING }
            ?: element.parent?.takeIf { it.node?.elementType == XcpTokenTypes.STRING }
            ?: return null
        return XcpPsiReference.create(stringElement)
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
        } ?: return null

        return file.findElementAt(target.start)
    }

    override fun getVariants(): Array<Any> = emptyArray()

    companion object {
        fun create(element: PsiElement): XcpPsiReference? {
            val file = element.containingFile ?: return null
            val structure = XcpStructure.from(file.text)
            val absoluteStart = element.textRange.startOffset
            val token = structure.tokens.firstOrNull { it.start == absoluteStart && it.type == XcpTokenTypes.STRING } ?: return null
            val usage = structure.referenceUsageFor(token) ?: return null
            val rangeInElement = TextRange(
                usage.range.startOffset - absoluteStart,
                usage.range.endOffset - absoluteStart
            )
            return XcpPsiReference(element, rangeInElement, usage.name, usage.kind)
        }
    }
}

private enum class XcpReferenceKind {
    VARIABLE,
    PHASE
}

private data class XcpReferenceUsage(
    val kind: XcpReferenceKind,
    val name: String,
    val range: TextRange
)

private data class XcpDeclaration(
    val name: String,
    val start: Int,
    val end: Int
)

private data class XcpStructure(
    val text: String,
    val tokens: List<XcpTokenInfo>,
    val variableDeclarations: Map<String, XcpDeclaration>,
    val phaseDeclarations: Map<String, XcpDeclaration>
) {
    fun referenceUsageFor(token: XcpTokenInfo): XcpReferenceUsage? {
        val tokenIndex = tokens.indexOfFirst { it.start == token.start && it.end == token.end }
        if (tokenIndex == -1 || isVariableDeclaration(tokenIndex) || isPhaseDeclaration(tokenIndex)) return null

        val itemUsage = formItemVariableUsage(token, tokenIndex)
        if (itemUsage != null) return itemUsage

        if (isInsideTopLevelArray(tokenIndex, "table_view")) {
            return stringUsage(token, XcpReferenceKind.VARIABLE)
        }

        val previousKey = previousKeyName(tokenIndex)
        if (previousKey == "start_phase" || previousKey == "target" || previousKey == "transitions") {
            return stringUsage(token, XcpReferenceKind.PHASE)
        }

        return null
    }

    private fun formItemVariableUsage(token: XcpTokenInfo, tokenIndex: Int): XcpReferenceUsage? {
        if (!isInsidePropertyArray(tokenIndex, "items")) return null
        val raw = token.unquotedText(text)
        val name = raw.substringBefore(':').substringBefore('(').trim()
        if (name.isEmpty()) return null
        return XcpReferenceUsage(
            XcpReferenceKind.VARIABLE,
            name,
            TextRange(token.start + 1, token.start + 1 + name.length)
        )
    }

    private fun stringUsage(token: XcpTokenInfo, kind: XcpReferenceKind): XcpReferenceUsage {
        return XcpReferenceUsage(
            kind,
            token.unquotedText(text),
            TextRange(token.start + 1, token.end - 1)
        )
    }

    private fun isVariableDeclaration(tokenIndex: Int): Boolean {
        return isDeclarationInTopLevelArray(tokenIndex, "variables")
    }

    private fun isPhaseDeclaration(tokenIndex: Int): Boolean {
        return isDeclarationInTopLevelArray(tokenIndex, "phases")
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
            if (tokenIndex in range && tokens.getOrNull(i + 2)?.type == XcpTokenTypes.L_BRACKET) return true
        }
        return false
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

    companion object {
        fun from(text: String): XcpStructure {
            val tokens = scanTokens(text).filter {
                it.type != TokenType.WHITE_SPACE &&
                    it.type != XcpTokenTypes.LINE_COMMENT &&
                    it.type != XcpTokenTypes.BLOCK_COMMENT &&
                    it.type != XcpTokenTypes.JS_LINE_COMMENT &&
                    it.type != XcpTokenTypes.JS_BLOCK_COMMENT
            }
            val structure = XcpStructure(text, tokens, emptyMap(), emptyMap())
            return structure.copy(
                variableDeclarations = structure.collectDeclarations("variables"),
                phaseDeclarations = structure.collectDeclarations("phases")
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
}

private data class XcpTokenInfo(
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
}
