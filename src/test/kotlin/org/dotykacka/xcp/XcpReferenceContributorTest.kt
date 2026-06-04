package org.dotykacka.xcp

import com.intellij.testFramework.fixtures.BasePlatformTestCase

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
}
