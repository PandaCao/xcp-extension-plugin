package org.dotykacka.xcp

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiReference

class XcpFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, XcpLanguage) {
    override fun getFileType(): FileType = XcpFileType
    override fun toString(): String = "XCP Process File"

    override fun findReferenceAt(offset: Int): PsiReference? {
        val element = findElementAt(offset) ?: return null
        return XcpReferenceFactory.createAll(element).firstOrNull { reference ->
            reference.rangeInElement.shiftRight(element.textRange.startOffset).containsOffset(offset)
        }
    }
}
