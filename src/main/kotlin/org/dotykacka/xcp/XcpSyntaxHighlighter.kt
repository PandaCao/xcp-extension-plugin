package org.dotykacka.xcp

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class XcpSyntaxHighlighter : SyntaxHighlighter {
    override fun getHighlightingLexer(): Lexer = XcpLexer()

    override fun getTokenHighlights(tokenType: IElementType?): Array<TextAttributesKey> = when (tokenType) {
        XcpTokenTypes.LINE_COMMENT, XcpTokenTypes.BLOCK_COMMENT -> COMMENT_KEYS
        XcpTokenTypes.STRING -> STRING_KEYS
        XcpTokenTypes.NUMBER -> NUMBER_KEYS
        XcpTokenTypes.KEYWORD -> KEYWORD_KEYS
        XcpTokenTypes.IDENTIFIER -> IDENTIFIER_KEYS
        XcpTokenTypes.COLON, XcpTokenTypes.COMMA -> OPERATION_KEYS
        XcpTokenTypes.L_BRACE, XcpTokenTypes.R_BRACE -> BRACE_KEYS
        XcpTokenTypes.L_BRACKET, XcpTokenTypes.R_BRACKET -> BRACKET_KEYS
        TokenType.BAD_CHARACTER -> BAD_CHAR_KEYS
        else -> TextAttributesKey.EMPTY_ARRAY
    }

    companion object {
        val COMMENT = TextAttributesKey.createTextAttributesKey("XCP_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val STRING = TextAttributesKey.createTextAttributesKey("XCP_STRING", DefaultLanguageHighlighterColors.STRING)
        val NUMBER = TextAttributesKey.createTextAttributesKey("XCP_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val KEYWORD = TextAttributesKey.createTextAttributesKey("XCP_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val IDENTIFIER = TextAttributesKey.createTextAttributesKey("XCP_IDENTIFIER", DefaultLanguageHighlighterColors.INSTANCE_FIELD)
        val OPERATION = TextAttributesKey.createTextAttributesKey("XCP_OPERATION", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val BRACE = TextAttributesKey.createTextAttributesKey("XCP_BRACE", DefaultLanguageHighlighterColors.BRACES)
        val BRACKET = TextAttributesKey.createTextAttributesKey("XCP_BRACKET", DefaultLanguageHighlighterColors.BRACKETS)
        val BAD_CHAR = TextAttributesKey.createTextAttributesKey("XCP_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private val COMMENT_KEYS = arrayOf(COMMENT)
        private val STRING_KEYS = arrayOf(STRING)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val KEYWORD_KEYS = arrayOf(KEYWORD)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val OPERATION_KEYS = arrayOf(OPERATION)
        private val BRACE_KEYS = arrayOf(BRACE)
        private val BRACKET_KEYS = arrayOf(BRACKET)
        private val BAD_CHAR_KEYS = arrayOf(BAD_CHAR)
    }
}

class XcpSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = XcpSyntaxHighlighter()
}
