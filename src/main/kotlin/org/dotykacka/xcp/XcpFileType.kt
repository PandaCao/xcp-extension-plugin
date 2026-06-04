package org.dotykacka.xcp

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

object XcpFileType : LanguageFileType(XcpLanguage) {
    override fun getName(): String = "XCP Process"
    override fun getDescription(): String = "1CLICK process definition"
    override fun getDefaultExtension(): String = "xcp"
    override fun getIcon(): Icon? = null
}
