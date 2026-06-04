package org.dotykacka.xcp

import org.junit.Assert.assertTrue
import org.junit.Test

class XcpValidatorTest {
    @Test
    fun reportsUndeclaredVariablesFromTopLevelInputItems() {
        val text = """
            {
                id: "example",
                version: "1.0",
                name: "Example",
                title: "Example",
                start_phase: "start",
                variables: [
                    { id: "declared", type: "text" }
                ],
                input: {
                    items: [
                        "missing:w!",
                        "declared:w"
                    ]
                },
                phases: [
                    { id: "start", name: "Start" }
                ]
            }
        """.trimIndent()

        val issues = XcpValidator.validate(text).map { it.message }

        assertTrue(issues.any { it.contains("Input variable 'missing' is not declared") })
    }

    @Test
    fun reportsUndeclaredVariablesFromNestedFormItems() {
        val text = """
            {
                id: "example",
                version: "1.0",
                name: "Example",
                title: "Example",
                start_phase: "start",
                variables: [
                    { id: "declared", type: "text" }
                ],
                phases: [
                    {
                        id: "start",
                        name: "Start",
                        steps: [
                            {
                                action: "userTask",
                                inputs: {
                                    form: {
                                        items: [
                                            "missing:r",
                                            "declared:r"
                                        ]
                                    }
                                }
                            }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val issues = XcpValidator.validate(text).map { it.message }

        assertTrue(issues.any { it.contains("Input variable 'missing' is not declared") })
    }

    @Test
    fun reportsUndeclaredVariablesFromTableView() {
        val text = """
            {
                id: "example",
                version: "1.0",
                name: "Example",
                title: "Example",
                start_phase: "start",
                table_view: [
                    "phase_name",
                    "missing"
                ],
                variables: [
                    { id: "declared", type: "text" }
                ],
                phases: [
                    { id: "start", name: "Start" }
                ]
            }
        """.trimIndent()

        val issues = XcpValidator.validate(text).map { it.message }

        assertTrue(issues.any { it.contains("table_view variable 'missing' is not declared") })
    }

    @Test
    fun reportsTransitionsToMissingPhaseIds() {
        val text = """
            {
                id: "example",
                version: "1.0",
                name: "Example",
                title: "Example",
                start_phase: "start",
                variables: [],
                phases: [
                    {
                        id: "start",
                        name: "Start",
                        transitions: [
                            { target: "missing" },
                            { target: "done" }
                        ]
                    },
                    { id: "done", name: "Done" }
                ]
            }
        """.trimIndent()

        val issues = XcpValidator.validate(text).map { it.message }

        assertTrue(issues.any { it.contains("Transition target 'missing' does not match any phase id") })
    }
}
