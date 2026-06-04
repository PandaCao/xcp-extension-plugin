package org.dotykacka.xcp

import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class XcpLexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset = 0
    private var endOffset = 0
    private var tokenStart = 0
    private var tokenEnd = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        tokenStart = startOffset
        tokenEnd = startOffset
        locateToken()
    }

    override fun getState(): Int = 0
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        tokenStart = tokenEnd
        locateToken()
    }

    private fun locateToken() {
        if (tokenStart >= endOffset) {
            tokenType = null
            tokenEnd = tokenStart
            return
        }

        val c = buffer[tokenStart]
        when {
            c.isWhitespace() -> readWhile { it.isWhitespace() }.also { tokenType = TokenType.WHITE_SPACE }
            c == '/' && peek(1) == '/' -> readLineComment()
            c == '/' && peek(1) == '*' -> readBlockComment()
            c == '"' || c == '\'' -> readString(c)
            c == '-' || c.isDigit() -> readNumberOrBad()
            isIdentifierStart(c) -> readIdentifier()
            else -> readPunctuation(c)
        }
    }

    private fun readLineComment() {
        tokenEnd = tokenStart + 2
        while (tokenEnd < endOffset && buffer[tokenEnd] != '\n' && buffer[tokenEnd] != '\r') tokenEnd++
        tokenType = XcpTokenTypes.LINE_COMMENT
    }

    private fun readBlockComment() {
        tokenEnd = tokenStart + 2
        while (tokenEnd < endOffset - 1 && !(buffer[tokenEnd] == '*' && buffer[tokenEnd + 1] == '/')) tokenEnd++
        tokenEnd = (tokenEnd + 2).coerceAtMost(endOffset)
        tokenType = XcpTokenTypes.BLOCK_COMMENT
    }

    private fun readString(quote: Char) {
        tokenEnd = tokenStart + 1
        var escaped = false
        while (tokenEnd < endOffset) {
            val c = buffer[tokenEnd++]
            if (escaped) {
                escaped = false
            } else if (c == '\\') {
                escaped = true
            } else if (c == quote) {
                break
            }
        }
        tokenType = XcpTokenTypes.STRING
    }

    private fun readNumberOrBad() {
        var index = tokenStart
        if (buffer[index] == '-') index++
        var hasDigit = false
        while (index < endOffset && buffer[index].isDigit()) {
            index++
            hasDigit = true
        }
        if (index < endOffset && buffer[index] == '.') {
            index++
            while (index < endOffset && buffer[index].isDigit()) {
                index++
                hasDigit = true
            }
        }
        if (index < endOffset && (buffer[index] == 'e' || buffer[index] == 'E')) {
            val exponent = index
            index++
            if (index < endOffset && (buffer[index] == '+' || buffer[index] == '-')) index++
            var exponentDigit = false
            while (index < endOffset && buffer[index].isDigit()) {
                index++
                exponentDigit = true
            }
            if (!exponentDigit) index = exponent
        }
        tokenEnd = if (hasDigit) index else tokenStart + 1
        tokenType = if (hasDigit) XcpTokenTypes.NUMBER else TokenType.BAD_CHARACTER
    }

    private fun readIdentifier() {
        tokenEnd = tokenStart + 1
        while (tokenEnd < endOffset && isIdentifierPart(buffer[tokenEnd])) tokenEnd++
        val text = buffer.subSequence(tokenStart, tokenEnd).toString()
        tokenType = if (text in XcpReference.KEYWORDS) XcpTokenTypes.KEYWORD else XcpTokenTypes.IDENTIFIER
    }

    private fun readPunctuation(c: Char) {
        tokenEnd = tokenStart + 1
        tokenType = when (c) {
            ':' -> XcpTokenTypes.COLON
            ',' -> XcpTokenTypes.COMMA
            '{' -> XcpTokenTypes.L_BRACE
            '}' -> XcpTokenTypes.R_BRACE
            '[' -> XcpTokenTypes.L_BRACKET
            ']' -> XcpTokenTypes.R_BRACKET
            else -> TokenType.BAD_CHARACTER
        }
    }

    private fun readWhile(predicate: (Char) -> Boolean) {
        tokenEnd = tokenStart + 1
        while (tokenEnd < endOffset && predicate(buffer[tokenEnd])) tokenEnd++
    }

    private fun peek(delta: Int): Char? {
        val index = tokenStart + delta
        return if (index < endOffset) buffer[index] else null
    }

    private fun isIdentifierStart(c: Char): Boolean = c == '_' || c == '$' || Character.isLetter(c)
    private fun isIdentifierPart(c: Char): Boolean = isIdentifierStart(c) || c == '-' || Character.isDigit(c)
}
