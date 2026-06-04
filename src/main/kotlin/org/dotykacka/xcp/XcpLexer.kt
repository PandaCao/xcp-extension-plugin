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
    private var inFunctionsString = false
    private var functionsStringQuote: Char = 0.toChar()
    private var previousSignificantIdentifier: String? = null
    private var lastSignificantTokenType: IElementType? = null
    private var previousJsKeyword: String? = null
    private var previousJsTokenText: String? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        tokenStart = startOffset
        tokenEnd = startOffset
        inFunctionsString = initialState == STATE_FUNCTIONS_STRING_SINGLE || initialState == STATE_FUNCTIONS_STRING_DOUBLE
        functionsStringQuote = if (initialState == STATE_FUNCTIONS_STRING_SINGLE) '\'' else if (initialState == STATE_FUNCTIONS_STRING_DOUBLE) '"' else 0.toChar()
        previousSignificantIdentifier = null
        lastSignificantTokenType = null
        previousJsKeyword = null
        previousJsTokenText = null
        locateToken()
    }

    override fun getState(): Int = when {
        inFunctionsString && functionsStringQuote == '\'' -> STATE_FUNCTIONS_STRING_SINGLE
        inFunctionsString && functionsStringQuote == '"' -> STATE_FUNCTIONS_STRING_DOUBLE
        else -> 0
    }
    override fun getTokenType(): IElementType? = tokenType
    override fun getTokenStart(): Int = tokenStart
    override fun getTokenEnd(): Int = tokenEnd
    override fun getBufferSequence(): CharSequence = buffer
    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        updateSignificantToken()
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
            inFunctionsString -> readFunctionsStringToken(c)
            c.isWhitespace() -> readWhile { it.isWhitespace() }.also { tokenType = TokenType.WHITE_SPACE }
            c == '/' && peek(1) == '/' -> readLineComment()
            c == '/' && peek(1) == '*' -> readBlockComment()
            isFunctionsStringStart(c) -> readFunctionsStringDelimiter(c)
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

    private fun readFunctionsStringDelimiter(quote: Char) {
        tokenEnd = tokenStart + 1
        functionsStringQuote = quote
        inFunctionsString = true
        tokenType = XcpTokenTypes.JS_STRING_DELIMITER
    }

    private fun readFunctionsStringToken(c: Char) {
        when {
            c == functionsStringQuote && isFunctionsStringEndDelimiter() -> readFunctionsStringEndDelimiter()
            c.isWhitespace() -> readWhile { it.isWhitespace() }.also { tokenType = TokenType.WHITE_SPACE }
            c == '/' && peek(1) == '/' -> readJsLineComment()
            c == '/' && peek(1) == '*' -> readJsBlockComment()
            c == '"' || c == '\'' || c == '`' -> readJsNestedString(c)
            c == '-' || c.isDigit() -> readNumberOrBad().also { tokenType = XcpTokenTypes.JS_NUMBER }
            isIdentifierStart(c) -> readJsIdentifier()
            else -> readJsOperation()
        }
    }

    private fun readFunctionsStringEndDelimiter() {
        tokenEnd = tokenStart + 1
        tokenType = XcpTokenTypes.JS_STRING_DELIMITER
        inFunctionsString = false
        functionsStringQuote = 0.toChar()
    }

    private fun readJsLineComment() {
        tokenEnd = tokenStart + 2
        while (tokenEnd < endOffset && buffer[tokenEnd] != '\n' && buffer[tokenEnd] != '\r') tokenEnd++
        tokenType = XcpTokenTypes.JS_LINE_COMMENT
    }

    private fun readJsBlockComment() {
        tokenEnd = tokenStart + 2
        while (tokenEnd < endOffset - 1 && !(buffer[tokenEnd] == '*' && buffer[tokenEnd + 1] == '/')) tokenEnd++
        tokenEnd = (tokenEnd + 2).coerceAtMost(endOffset)
        tokenType = XcpTokenTypes.JS_BLOCK_COMMENT
    }

    private fun readJsNestedString(quote: Char) {
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
            } else if (c == functionsStringQuote && quote != '`') {
                tokenEnd--
                break
            }
        }
        tokenType = XcpTokenTypes.JS_STRING
    }

    private fun readJsIdentifier() {
        tokenEnd = tokenStart + 1
        while (tokenEnd < endOffset && isIdentifierPart(buffer[tokenEnd])) tokenEnd++
        val text = buffer.subSequence(tokenStart, tokenEnd).toString()
        tokenType = when {
            text in JS_KEYWORDS -> XcpTokenTypes.JS_KEYWORD
            previousJsKeyword == "function" -> XcpTokenTypes.JS_FUNCTION_DECLARATION
            previousJsKeyword == "var" || previousJsKeyword == "let" || previousJsKeyword == "const" -> XcpTokenTypes.JS_LOCAL_VARIABLE
            previousJsTokenText == "." -> XcpTokenTypes.JS_PROPERTY
            nextNonWhitespaceChar() == '(' -> XcpTokenTypes.JS_FUNCTION_CALL
            else -> XcpTokenTypes.JS_IDENTIFIER
        }
    }

    private fun readJsOperation() {
        tokenEnd = tokenStart + 1
        tokenType = XcpTokenTypes.JS_OPERATION
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
        tokenType = if (text in XcpReference.KEYWORDS || isFieldKey()) XcpTokenTypes.KEYWORD else XcpTokenTypes.IDENTIFIER
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

    private fun isFunctionsStringStart(c: Char): Boolean {
        return (c == '"' || c == '\'') &&
            previousSignificantIdentifier == "functions" &&
            lastSignificantTokenType == XcpTokenTypes.COLON
    }

    private fun isFunctionsStringEndDelimiter(): Boolean {
        val previous = previousNonWhitespaceChar()
        if (previous != null && previous != '\n' && previous != '\r' && previous != '}' && previous != ';') {
            return false
        }

        var index = tokenStart + 1
        while (index < endOffset && buffer[index].isWhitespace()) index++
        return index >= endOffset || buffer[index] == ',' || buffer[index] == '}' || buffer[index] == ']'
    }

    private fun previousNonWhitespaceChar(): Char? {
        var index = tokenStart - 1
        while (index >= startOffset && buffer[index].isWhitespace() && buffer[index] != '\n' && buffer[index] != '\r') index--
        return if (index >= startOffset) buffer[index] else null
    }

    private fun updateSignificantToken() {
        if (tokenType == null || tokenType == TokenType.WHITE_SPACE ||
            tokenType == XcpTokenTypes.LINE_COMMENT || tokenType == XcpTokenTypes.BLOCK_COMMENT ||
            tokenType == XcpTokenTypes.JS_LINE_COMMENT || tokenType == XcpTokenTypes.JS_BLOCK_COMMENT
        ) {
            return
        }

        if (tokenType == XcpTokenTypes.IDENTIFIER || tokenType == XcpTokenTypes.KEYWORD) {
            previousSignificantIdentifier = buffer.subSequence(tokenStart, tokenEnd).toString()
        }
        if (inFunctionsString || tokenType in JS_SIGNIFICANT_TYPES) {
            val text = buffer.subSequence(tokenStart, tokenEnd).toString()
            previousJsKeyword = when {
                tokenType == XcpTokenTypes.JS_KEYWORD -> text
                tokenType == XcpTokenTypes.JS_OPERATION && text == "," -> previousJsKeyword
                else -> null
            }
            previousJsTokenText = text
        }
        lastSignificantTokenType = tokenType
    }

    private fun nextNonWhitespaceChar(): Char? {
        var index = tokenEnd
        while (index < endOffset && buffer[index].isWhitespace()) index++
        return if (index < endOffset) buffer[index] else null
    }

    private fun isFieldKey(): Boolean {
        val text = buffer.subSequence(tokenStart, tokenEnd).toString()
        if (text !in XcpReference.FIELD_KEYS) return false
        var index = tokenEnd
        while (index < endOffset && buffer[index].isWhitespace()) index++
        return index < endOffset && buffer[index] == ':'
    }

    private fun isIdentifierStart(c: Char): Boolean = c == '_' || c == '$' || Character.isLetter(c)
    private fun isIdentifierPart(c: Char): Boolean = isIdentifierStart(c) || c == '-' || Character.isDigit(c)

    companion object {
        private const val STATE_FUNCTIONS_STRING_SINGLE = 1
        private const val STATE_FUNCTIONS_STRING_DOUBLE = 2

        private val JS_KEYWORDS = setOf(
            "break", "case", "catch", "const", "continue", "debugger", "default", "delete", "do", "else",
            "false", "finally", "for", "function", "if", "in", "instanceof", "let", "new", "null", "return",
            "switch", "this", "throw", "true", "try", "typeof", "undefined", "var", "void", "while", "with",
            "yield"
        )

        private val JS_SIGNIFICANT_TYPES = setOf(
            XcpTokenTypes.JS_STRING_DELIMITER,
            XcpTokenTypes.JS_STRING,
            XcpTokenTypes.JS_NUMBER,
            XcpTokenTypes.JS_KEYWORD,
            XcpTokenTypes.JS_IDENTIFIER,
            XcpTokenTypes.JS_FUNCTION_DECLARATION,
            XcpTokenTypes.JS_FUNCTION_CALL,
            XcpTokenTypes.JS_LOCAL_VARIABLE,
            XcpTokenTypes.JS_PROPERTY,
            XcpTokenTypes.JS_OPERATION
        )
    }
}
