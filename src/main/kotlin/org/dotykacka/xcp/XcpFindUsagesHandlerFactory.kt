package org.dotykacka.xcp

import com.intellij.find.findUsages.FindUsagesHandler
import com.intellij.find.findUsages.FindUsagesHandlerFactory
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.SearchScope
import com.intellij.usageView.UsageInfo
import com.intellij.util.Processor

class XcpFindUsagesHandlerFactory : FindUsagesHandlerFactory() {
    override fun canFindUsages(element: PsiElement): Boolean {
        return element.containingFile?.fileType == XcpFileType
    }

    override fun createFindUsagesHandler(element: PsiElement, forHighlightUsages: Boolean): FindUsagesHandler {
        val target = usageTargetAt(element) ?: return FindUsagesHandler.NULL_HANDLER
        return XcpFindUsagesHandler(element, target)
    }
}

private class XcpFindUsagesHandler(
    element: PsiElement,
    private val target: XcpReferenceUsage
) : FindUsagesHandler(element) {
    override fun getPrimaryElements(): Array<PsiElement> {
        val file = myPsiElement.containingFile ?: return super.getPrimaryElements()
        val structure = XcpStructure.from(file.text)
        val declStart = declarationStartFor(target, structure) ?: return super.getPrimaryElements()
        val declaration = file.findElementAt(declStart) ?: return super.getPrimaryElements()
        return arrayOf(declaration)
    }

    override fun processElementUsages(
        element: PsiElement,
        processor: Processor<in UsageInfo>,
        options: FindUsagesOptions
    ): Boolean {
        val psiFile = element.containingFile ?: return true
        val structure = XcpStructure.from(psiFile.text)

        val declStart = declarationStartFor(target, structure)
        if (declStart != null) {
            if (!processor.process(lineUsageInfo(psiFile, declStart))) return false
        }

        for (usage in structure.referenceUsages()) {
            if (usage.kind != target.kind || usage.name != target.name) continue
            if (!processor.process(lineUsageInfo(psiFile, usage.range.startOffset))) return false
        }

        return true
    }

    override fun findReferencesToHighlight(element: PsiElement, searchScope: SearchScope): Collection<com.intellij.psi.PsiReference> {
        return emptyList()
    }
}

private fun declarationStartFor(target: XcpReferenceUsage, structure: XcpStructure): Int? {
    return when (target.kind) {
        XcpReferenceKind.VARIABLE -> structure.variableDeclarations[target.name]?.start
        XcpReferenceKind.PHASE -> structure.phaseDeclarations[target.name]?.start
        XcpReferenceKind.FUNCTION -> structure.functionDeclarations[target.name]?.start
    }
}

private fun usageTargetAt(element: PsiElement): XcpReferenceUsage? {
    val file = element.containingFile ?: return null
    val structure = XcpStructure.from(file.text)
    val token = structure.tokens.firstOrNull { it.start <= element.textRange.startOffset && element.textRange.startOffset < it.end }
        ?: return null
    return structure.usageTargetFor(token)
}

private fun lineUsageInfo(file: PsiFile, offset: Int): UsageInfo {
    val document = file.viewProvider.document
    if (document != null) {
        val line = document.getLineNumber(offset.coerceIn(0, document.textLength))
        val start = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        return UsageInfo(file, start, end, true)
    }
    return UsageInfo(file)
}
