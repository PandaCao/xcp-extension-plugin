package org.dotykacka.xcp

import com.intellij.psi.tree.IElementType

object XcpTokenTypes {
    val LINE_COMMENT = XcpTokenType("LINE_COMMENT")
    val BLOCK_COMMENT = XcpTokenType("BLOCK_COMMENT")
    val STRING = XcpTokenType("STRING")
    val JS_STRING_DELIMITER = XcpTokenType("JS_STRING_DELIMITER")
    val JS_LINE_COMMENT = XcpTokenType("JS_LINE_COMMENT")
    val JS_BLOCK_COMMENT = XcpTokenType("JS_BLOCK_COMMENT")
    val JS_STRING = XcpTokenType("JS_STRING")
    val JS_NUMBER = XcpTokenType("JS_NUMBER")
    val JS_KEYWORD = XcpTokenType("JS_KEYWORD")
    val JS_IDENTIFIER = XcpTokenType("JS_IDENTIFIER")
    val JS_FUNCTION_DECLARATION = XcpTokenType("JS_FUNCTION_DECLARATION")
    val JS_FUNCTION_CALL = XcpTokenType("JS_FUNCTION_CALL")
    val JS_LOCAL_VARIABLE = XcpTokenType("JS_LOCAL_VARIABLE")
    val JS_PROPERTY = XcpTokenType("JS_PROPERTY")
    val JS_OPERATION = XcpTokenType("JS_OPERATION")
    val NUMBER = XcpTokenType("NUMBER")
    val IDENTIFIER = XcpTokenType("IDENTIFIER")
    val KEYWORD = XcpTokenType("KEYWORD")
    val COLON = XcpTokenType("COLON")
    val COMMA = XcpTokenType("COMMA")
    val L_BRACE = XcpTokenType("L_BRACE")
    val R_BRACE = XcpTokenType("R_BRACE")
    val L_BRACKET = XcpTokenType("L_BRACKET")
    val R_BRACKET = XcpTokenType("R_BRACKET")
}

class XcpTokenType(debugName: String) : IElementType(debugName, XcpLanguage)
