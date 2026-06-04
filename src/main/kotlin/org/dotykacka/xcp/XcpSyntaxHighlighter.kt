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
        XcpTokenTypes.JS_LINE_COMMENT, XcpTokenTypes.JS_BLOCK_COMMENT -> JS_COMMENT_KEYS
        XcpTokenTypes.STRING -> STRING_KEYS
        XcpTokenTypes.JS_STRING, XcpTokenTypes.JS_STRING_DELIMITER -> JS_STRING_KEYS
        XcpTokenTypes.NUMBER -> NUMBER_KEYS
        XcpTokenTypes.JS_NUMBER -> JS_NUMBER_KEYS
        XcpTokenTypes.KEYWORD -> KEYWORD_KEYS
        XcpTokenTypes.JS_KEYWORD -> JS_KEYWORD_KEYS
        XcpTokenTypes.IDENTIFIER -> IDENTIFIER_KEYS
        XcpTokenTypes.JS_IDENTIFIER, XcpTokenTypes.JS_LOCAL_VARIABLE -> JS_LOCAL_VARIABLE_KEYS
        XcpTokenTypes.JS_FUNCTION_DECLARATION -> JS_FUNCTION_DECLARATION_KEYS
        XcpTokenTypes.JS_FUNCTION_CALL -> JS_FUNCTION_CALL_KEYS
        XcpTokenTypes.JS_PROPERTY -> JS_PROPERTY_KEYS
        XcpTokenTypes.COLON, XcpTokenTypes.COMMA, XcpTokenTypes.JS_OPERATION -> OPERATION_KEYS
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
        val JS_COMMENT = TextAttributesKey.createTextAttributesKey("XCP_JS_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)
        val JS_STRING = TextAttributesKey.createTextAttributesKey("XCP_JS_STRING", DefaultLanguageHighlighterColors.STRING)
        val JS_NUMBER = TextAttributesKey.createTextAttributesKey("XCP_JS_NUMBER", DefaultLanguageHighlighterColors.NUMBER)
        val JS_KEYWORD = TextAttributesKey.createTextAttributesKey("XCP_JS_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)
        val JS_FUNCTION_DECLARATION = TextAttributesKey.createTextAttributesKey(
            "XCP_JS_FUNCTION_DECLARATION",
            DefaultLanguageHighlighterColors.FUNCTION_DECLARATION
        )
        val JS_FUNCTION_CALL = TextAttributesKey.createTextAttributesKey(
            "XCP_JS_FUNCTION_CALL",
            DefaultLanguageHighlighterColors.FUNCTION_CALL
        )
        val JS_LOCAL_VARIABLE = TextAttributesKey.createTextAttributesKey(
            "XCP_JS_LOCAL_VARIABLE",
            DefaultLanguageHighlighterColors.LOCAL_VARIABLE
        )
        val JS_PROPERTY = TextAttributesKey.createTextAttributesKey(
            "XCP_JS_PROPERTY",
            DefaultLanguageHighlighterColors.INSTANCE_FIELD
        )
        val OPERATION = TextAttributesKey.createTextAttributesKey("XCP_OPERATION", DefaultLanguageHighlighterColors.OPERATION_SIGN)
        val BRACE = TextAttributesKey.createTextAttributesKey("XCP_BRACE", DefaultLanguageHighlighterColors.BRACES)
        val BRACKET = TextAttributesKey.createTextAttributesKey("XCP_BRACKET", DefaultLanguageHighlighterColors.BRACKETS)
        val BAD_CHAR = TextAttributesKey.createTextAttributesKey("XCP_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER)

        private val COMMENT_KEYS = arrayOf(COMMENT)
        private val STRING_KEYS = arrayOf(STRING)
        private val NUMBER_KEYS = arrayOf(NUMBER)
        private val KEYWORD_KEYS = arrayOf(KEYWORD)
        private val IDENTIFIER_KEYS = arrayOf(IDENTIFIER)
        private val JS_COMMENT_KEYS = arrayOf(JS_COMMENT)
        private val JS_STRING_KEYS = arrayOf(JS_STRING)
        private val JS_NUMBER_KEYS = arrayOf(JS_NUMBER)
        private val JS_KEYWORD_KEYS = arrayOf(JS_KEYWORD)
        private val JS_FUNCTION_DECLARATION_KEYS = arrayOf(JS_FUNCTION_DECLARATION)
        private val JS_FUNCTION_CALL_KEYS = arrayOf(JS_FUNCTION_CALL)
        private val JS_LOCAL_VARIABLE_KEYS = arrayOf(JS_LOCAL_VARIABLE)
        private val JS_PROPERTY_KEYS = arrayOf(JS_PROPERTY)
        private val OPERATION_KEYS = arrayOf(OPERATION)
        private val BRACE_KEYS = arrayOf(BRACE)
        private val BRACKET_KEYS = arrayOf(BRACKET)
        private val BAD_CHAR_KEYS = arrayOf(BAD_CHAR)
    }
}

class XcpSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter = XcpSyntaxHighlighter()
}
