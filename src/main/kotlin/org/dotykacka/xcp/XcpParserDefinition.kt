package org.dotykacka.xcp

import com.intellij.lang.ASTNode
import com.intellij.lang.LanguageParserDefinitions
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class XcpParserDefinition : ParserDefinition {
    override fun createLexer(project: Project?): Lexer = XcpLexer()

    override fun createParser(project: Project?): PsiParser {
        return PsiParser { root, builder ->
            val marker = builder.mark()
            while (!builder.eof()) {
                builder.advanceLexer()
            }
            marker.done(root)
            builder.treeBuilt
        }
    }

    override fun getFileNodeType(): IFileElementType = FILE
    override fun getWhitespaceTokens(): TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
    override fun getCommentTokens(): TokenSet = COMMENTS
    override fun getStringLiteralElements(): TokenSet = STRINGS

    override fun createElement(node: ASTNode): PsiElement {
        throw UnsupportedOperationException("XCP does not define custom PSI elements")
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = XcpFile(viewProvider)

    companion object {
        val FILE = IFileElementType(XcpLanguage)
        private val COMMENTS = TokenSet.create(
            XcpTokenTypes.LINE_COMMENT,
            XcpTokenTypes.BLOCK_COMMENT,
            XcpTokenTypes.JS_LINE_COMMENT,
            XcpTokenTypes.JS_BLOCK_COMMENT
        )
        private val STRINGS = TokenSet.create(
            XcpTokenTypes.STRING,
            XcpTokenTypes.JS_STRING,
            XcpTokenTypes.JS_STRING_DELIMITER
        )
    }
}
