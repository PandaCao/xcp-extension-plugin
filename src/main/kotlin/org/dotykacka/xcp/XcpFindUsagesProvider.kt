package org.dotykacka.xcp

import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet

class XcpFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner {
        return DefaultWordsScanner(
            XcpLexer(),
            TokenSet.create(
                XcpTokenTypes.IDENTIFIER,
                XcpTokenTypes.KEYWORD,
                XcpTokenTypes.STRING,
                XcpTokenTypes.JS_IDENTIFIER,
                XcpTokenTypes.JS_FUNCTION_DECLARATION,
                XcpTokenTypes.JS_FUNCTION_CALL,
                XcpTokenTypes.JS_LOCAL_VARIABLE,
                XcpTokenTypes.JS_PROPERTY
            ),
            TokenSet.create(
                XcpTokenTypes.LINE_COMMENT,
                XcpTokenTypes.BLOCK_COMMENT,
                XcpTokenTypes.JS_LINE_COMMENT,
                XcpTokenTypes.JS_BLOCK_COMMENT
            ),
            TokenSet.create(
                XcpTokenTypes.STRING,
                XcpTokenTypes.JS_STRING
            )
        )
    }

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean {
        return psiElement.containingFile?.fileType == XcpFileType
    }

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element.node?.elementType) {
        XcpTokenTypes.JS_FUNCTION_DECLARATION -> "function"
        XcpTokenTypes.STRING -> "declaration"
        else -> "symbol"
    }

    override fun getDescriptiveName(element: PsiElement): String = element.text.trim('"', '\'')

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = getDescriptiveName(element)
}
