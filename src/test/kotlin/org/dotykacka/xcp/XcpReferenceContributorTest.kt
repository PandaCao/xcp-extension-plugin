package org.dotykacka.xcp

import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.find.findUsages.FindUsagesOptions
import com.intellij.navigation.NavigationItem
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.usageView.UsageInfo

class XcpReferenceContributorTest : BasePlatformTestCase() {
    fun testTransitionTargetResolvesToPhaseId() {
        myFixture.configureByText(
            "process.xcp",
            """
                {
                    id: "example",
                    version: "1.0",
                    name: "Example",
                    title: "Example",
                    start_phase: "start",
                    variables: [],
                    phases: [
                        { id: "start", transitions: [{ target: "do<caret>ne" }] },
                        { id: "done", name: "Done" }
                    ]
                }
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()

        assertEquals("\"done\"", reference.resolve()?.text)
    }

    fun testInputItemResolvesToVariableId() {
        myFixture.configureByText(
            "process.xcp",
            """
                {
                    id: "example",
                    version: "1.0",
                    name: "Example",
                    title: "Example",
                    start_phase: "start",
                    variables: [
                        { id: "client_contact", type: "contact" }
                    ],
                    input: {
                        items: [
                            "client_<caret>contact:w!"
                        ]
                    },
                    phases: [
                        { id: "start", name: "Start" }
                    ]
                }
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()

        assertEquals("\"client_contact\"", reference.resolve()?.text)
    }

    fun testVarValueResolvesToVariableId() {
        myFixture.configureByText(
            "process.xcp",
            """
                {
                    id: "example",
                    version: "1.0",
                    name: "Example",
                    title: "Example",
                    start_phase: "start",
                    variables: [
                        { id: "client_contact", type: "contact" }
                    ],
                    phases: [
                        {
                            id: "start",
                            actions: [
                                { action: "setTitle", inputs: { text: { var: "client_<caret>contact" } } }
                            ]
                        }
                    ]
                }
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()

        assertEquals("\"client_contact\"", reference.resolve()?.text)
    }

    fun testSetVariablesInputKeyResolvesToVariableId() {
        myFixture.configureByText(
            "process.xcp",
            """
                {
                    id: "example",
                    version: "1.0",
                    name: "Example",
                    title: "Example",
                    start_phase: "start",
                    variables: [
                        { id: "client_contact", type: "contact" },
                        { id: "source_contact", type: "contact" }
                    ],
                    phases: [
                        {
                            id: "start",
                            actions: [
                                {
                                    action: "setVariables",
                                    inputs: {
                                        client_<caret>contact: { var: "source_contact" }
                                    }
                                }
                            ]
                        }
                    ]
                }
            """.trimIndent()
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()

        assertEquals("\"client_contact\"", reference.resolve()?.text)
    }

    fun testJsExpressionFunctionCallResolvesToFunctionDeclaration() {
        myFixture.configureByText(
            "process.xcp",
            xcpWithFunctionJsExpression("getTerminal<caret>Evrecords(client_contact.evrecords, seznam_terminalu)")
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()

        assertEquals("getTerminalEvrecords", reference.resolve()?.text)
    }

    fun testJsExpressionVariableArgumentResolvesToVariableDeclaration() {
        myFixture.configureByText(
            "process.xcp",
            xcpWithFunctionJsExpression("getTerminalEvrecords(client_<caret>contact.evrecords, seznam_terminalu)")
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()

        assertEquals("\"client_contact\"", reference.resolve()?.text)
    }

    fun testJsExpressionSecondVariableArgumentResolvesToVariableDeclaration() {
        myFixture.configureByText(
            "process.xcp",
            xcpWithFunctionJsExpression("getTerminalEvrecords(client_contact.evrecords, seznam_<caret>terminalu)")
        )

        val reference = myFixture.getReferenceAtCaretPositionWithAssertion()

        assertEquals("\"seznam_terminalu\"", reference.resolve()?.text)
    }

    fun testFunctionDeclarationHighlightsUsages() {
        myFixture.configureByText(
            "process.xcp",
            xcpWithFunctionJsExpression("getTerminalEvrecords(client_contact.evrecords, seznam_terminalu)")
                .replace("function getTerminalEvrecords", "function getTerminal<caret>Evrecords")
        )

        @Suppress("UNCHECKED_CAST")
        val handler = XcpHighlightUsagesHandlerFactory().createHighlightUsagesHandler(
            myFixture.editor,
            myFixture.file
        ) as HighlightUsagesHandlerBase<PsiElement>
        handler.computeUsages(handler.targets)
        val highlightedText = handler.readUsages.map { it.substring(myFixture.file.text) }

        assertTrue(highlightedText.contains("getTerminalEvrecords"))
        assertTrue(highlightedText.count { it == "getTerminalEvrecords" } >= 2)
    }

    fun testFunctionDeclarationFindUsagesHandlerCollectsDeclarationAndUsage() {
        myFixture.configureByText(
            "process.xcp",
            xcpWithFunctionJsExpression("getTerminalEvrecords(client_contact.evrecords, seznam_terminalu)")
                .replace("function getTerminalEvrecords", "function getTerminal<caret>Evrecords")
        )

        val handler = XcpFindUsagesHandlerFactory().createFindUsagesHandler(myFixture.file.findElementAt(myFixture.caretOffset)!!, false)
        val usages = mutableListOf<UsageInfo>()

        handler.processElementUsages(
            myFixture.file.findElementAt(myFixture.caretOffset)!!,
            { usages += it; true },
            FindUsagesOptions(myFixture.project)
        )

        assertTrue(usages.size >= 2)
        val lineTexts = usages.mapNotNull { usage ->
            val range = usage.navigationRange ?: return@mapNotNull null
            assertTrue(usage.element is com.intellij.psi.PsiFile)
            myFixture.file.text.substring(range.startOffset, range.endOffset)
        }
        assertTrue(lineTexts.any { it.contains("function getTerminalEvrecords") })
        assertTrue(lineTexts.any { it.contains("getTerminalEvrecords(client_contact.evrecords") })
    }

    fun testGotoDeclarationOnFunctionDeclarationShowsTargets() {
        myFixture.configureByText(
            "process.xcp",
            xcpWithFunctionJsExpression("getTerminalEvrecords(client_contact.evrecords, seznam_terminalu)")
                .replace("function getTerminalEvrecords", "function getTerminal<caret>Evrecords")
        )

        val declaration = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val targets = XcpGotoDeclarationHandler().getGotoDeclarationTargets(declaration, myFixture.caretOffset, myFixture.editor)

        assertTrue(targets.isNotEmpty())
        assertTrue(targets.any { it.text == "getTerminalEvrecords" })
    }

    fun testGotoDeclarationTargetsIncludeLineNumberPresentation() {
        myFixture.configureByText(
            "process.xcp",
            xcpWithFunctionJsExpression("getTerminalEvrecords(client_contact.evrecords, seznam_terminalu)")
                .replace("function getTerminalEvrecords", "function getTerminal<caret>Evrecords")
        )

        val declaration = myFixture.file.findElementAt(myFixture.caretOffset)!!
        val targets = XcpGotoDeclarationHandler().getGotoDeclarationTargets(declaration, myFixture.caretOffset, myFixture.editor)

        val presentations = targets.mapNotNull { (it as? NavigationItem)?.presentation?.presentableText }
        assertTrue(presentations.isNotEmpty())
        assertTrue(presentations.all { it.contains("(line ") })
    }

    private fun xcpWithFunctionJsExpression(expression: String): String {
        return """
            {
                id: "example",
                version: "1.0",
                name: "Example",
                title: "Example",
                start_phase: "start",
                variables: [
                    { id: "client_contact", type: "contact" },
                    { id: "seznam_terminalu", type: "terminal[]" }
                ],
                functions: "
                    function getTerminalEvrecords(evrecords, terminals) {
                        return terminals;
                    }
                ",
                phases: [
                    {
                        id: "start",
                        actions: [
                            { action: "setVariables", inputs: { terminal_evrecords: { js: "$expression" } } }
                        ]
                    }
                ]
            }
        """.trimIndent()
    }
}
