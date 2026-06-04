package org.dotykacka.xcp

import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class XcpAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val file = element as? PsiFile ?: return
        if (file.fileType != XcpFileType) return

        for (issue in XcpValidator.validate(file.text)) {
            holder.newAnnotation(issue.severity, issue.message)
                .range(issue.range)
                .create()
        }
    }
}
