package org.dotykacka.xcp

import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

class XcpBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> = PAIRS
    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true
    override fun getCodeConstructStart(file: PsiFile?, openingBraceOffset: Int): Int = openingBraceOffset

    companion object {
        private val PAIRS = arrayOf(
            BracePair(XcpTokenTypes.L_BRACE, XcpTokenTypes.R_BRACE, true),
            BracePair(XcpTokenTypes.L_BRACKET, XcpTokenTypes.R_BRACKET, false)
        )
    }
}
