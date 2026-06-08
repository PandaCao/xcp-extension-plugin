package org.dotykacka.xcp

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactory
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Consumer

class XcpHighlightUsagesHandlerFactory : HighlightUsagesHandlerFactory {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile): HighlightUsagesHandlerBase<*>? {
        if (file.fileType != XcpFileType) return null
        val structure = XcpStructure.from(file.text)
        val offset = editor.caretModel.offset
        val token = structure.tokens.firstOrNull { it.start <= offset && offset < it.end } ?: return null
        val target = structure.usageTargetFor(token) ?: return null
        return XcpHighlightUsagesHandler(editor, file, target, structure)
    }
}

private class XcpHighlightUsagesHandler(
    editor: Editor,
    file: PsiFile,
    private val target: XcpReferenceUsage,
    private val structure: XcpStructure
) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {
    override fun getTargets(): List<PsiElement> {
        return listOfNotNull(myFile.findElementAt(target.range.startOffset))
    }

    override fun selectTargets(targets: List<PsiElement>, selectionConsumer: Consumer<in List<PsiElement>>) {
        selectionConsumer.consume(targets)
    }

    override fun computeUsages(targets: List<PsiElement>) {
        val declRange = declarationRangeFor(target, structure) ?: target.range
        myReadUsages.add(declRange)
        for (usage in structure.referenceUsages()) {
            if (usage.kind == target.kind && usage.name == target.name) {
                myReadUsages.add(usage.range)
            }
        }
    }
}

private fun declarationRangeFor(target: XcpReferenceUsage, structure: XcpStructure): TextRange? {
    val decl = when (target.kind) {
        XcpReferenceKind.VARIABLE -> structure.variableDeclarations[target.name]
        XcpReferenceKind.PHASE -> structure.phaseDeclarations[target.name]
        XcpReferenceKind.FUNCTION -> structure.functionDeclarations[target.name]
    } ?: return null
    return TextRange(decl.start, decl.end)
}
