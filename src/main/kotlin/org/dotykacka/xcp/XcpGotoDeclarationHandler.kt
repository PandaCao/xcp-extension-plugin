package org.dotykacka.xcp

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

class XcpGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(element: PsiElement?, offset: Int, editor: Editor): Array<PsiElement> {
        val psiElement = element ?: return PsiElement.EMPTY_ARRAY
        val file = psiElement.containingFile ?: return PsiElement.EMPTY_ARRAY
        val structure = XcpStructure.from(file.text)
        val token = structure.tokens.firstOrNull { it.start <= offset && offset < it.end } ?: return PsiElement.EMPTY_ARRAY
        val declaration = structure.declarationUsageFor(token) ?: return PsiElement.EMPTY_ARRAY

        val targets = mutableListOf<PsiElement>()
        createTarget(file, declaration)?.let(targets::add)

        for (usage in structure.referenceUsages()) {
            if (usage.kind != declaration.kind || usage.name != declaration.name) continue
            createTarget(file, usage)?.let(targets::add)
        }

        return targets.toTypedArray()
    }

    private fun createTarget(file: PsiFile, usage: XcpReferenceUsage): PsiElement? {
        val element = file.findElementAt(usage.range.startOffset) ?: return null
        return XcpNavigationTarget(file, element, usage.name)
    }
}
