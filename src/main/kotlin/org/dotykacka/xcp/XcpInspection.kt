package org.dotykacka.xcp

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElementVisitor
import com.intellij.psi.PsiFile

class XcpInspection : LocalInspectionTool() {
    override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor {
        return object : PsiElementVisitor() {
            override fun visitFile(file: PsiFile) {
                if (file.fileType != XcpFileType) return

                for (issue in XcpValidator.validate(file.text)) {
                    holder.registerProblem(
                        file,
                        issue.message,
                        issue.toProblemHighlightType(),
                        issue.range
                    )
                }
            }
        }
    }

    private fun XcpIssue.toProblemHighlightType(): ProblemHighlightType {
        return when (severity) {
            HighlightSeverity.ERROR -> ProblemHighlightType.GENERIC_ERROR
            else -> ProblemHighlightType.GENERIC_ERROR_OR_WARNING
        }
    }
}
