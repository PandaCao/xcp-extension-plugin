package org.dotykacka.xcp

import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import javax.swing.Icon

class XcpNavigationTarget(
    private val file: PsiFile,
    private val targetElement: PsiElement,
    private val targetName: String
) : FakePsiElement(), NavigationItem {
    override fun getParent(): PsiElement = file

    override fun getContainingFile(): PsiFile = file

    override fun getTextRange(): TextRange = targetElement.textRange

    override fun getTextOffset(): Int = targetElement.textOffset

    override fun getName(): String {
        val text = file.text
        val offset = targetElement.textOffset
        val lineNumber = text.lineNumberAt(offset)
        val lineContent = text.lineContentAt(offset).trim()
        val truncated = if (lineContent.length > 80) lineContent.take(80) + "…" else lineContent
        return "$lineNumber: $truncated"
    }

    override fun getText(): String = targetElement.text

    override fun getNavigationElement(): PsiElement = targetElement

    override fun navigate(requestFocus: Boolean) {
        OpenFileDescriptor(project, file.virtualFile, targetElement.textOffset).navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = file.virtualFile != null

    override fun canNavigateToSource(): Boolean = canNavigate()

    override fun getPresentation(): ItemPresentation {
        val displayName = name
        return object : ItemPresentation {
            override fun getPresentableText(): String = displayName
            override fun getLocationString(): String? = null
            override fun getIcon(unused: Boolean): Icon? = file.getIcon(0)
        }
    }
}

private fun String.lineContentAt(offset: Int): String {
    val bounded = offset.coerceIn(0, length)
    var start = bounded
    while (start > 0 && this[start - 1] != '\n') start--
    var end = bounded
    while (end < length && this[end] != '\n') end++
    return substring(start, end)
}

private fun String.lineNumberAt(offset: Int): Int {
    val boundedOffset = offset.coerceIn(0, length)
    var line = 1
    for (index in 0 until boundedOffset) {
        if (this[index] == '\n') {
            line++
        }
    }
    return line
}
