package org.dotykacka.xcp

import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.patterns.PlatformPatterns
import com.intellij.util.ProcessingContext

class XcpCompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val textBefore = parameters.editor.document.text.substring(0, parameters.offset)
                    val suggestions = when {
                        Regex("""type\s*:\s*['"][^'"]*$""").containsMatchIn(textBefore.takeLast(80)) -> XcpReference.VARIABLE_TYPES
                        Regex("""actions?\s*:\s*\{[^}]*$""", RegexOption.DOT_MATCHES_ALL).containsMatchIn(textBefore.takeLast(400)) -> XcpReference.ACTION_NAMES
                        Regex("""\{\s*\w*$""").containsMatchIn(textBefore.takeLast(40)) -> XcpReference.DYNAMIC_FIELD_KEYS + XcpReference.TOP_LEVEL_KEYS
                        else -> XcpReference.COMMON_KEYS + XcpReference.TOP_LEVEL_KEYS
                    }

                    suggestions.distinct().forEach {
                        result.addElement(LookupElementBuilder.create(it).withTypeText("XCP", true))
                    }
                }
            }
        )
    }
}
