package org.dotykacka.xcp

import com.intellij.psi.tree.IElementType

object XcpTokenTypes {
    val LINE_COMMENT = XcpTokenType("LINE_COMMENT")
    val BLOCK_COMMENT = XcpTokenType("BLOCK_COMMENT")
    val STRING = XcpTokenType("STRING")
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
