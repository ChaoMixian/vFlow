package com.chaomixian.vflow.core.workflow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkflowJsonImportParserTest {
    private val parser = WorkflowJsonImportParser()

    @Test
    fun `parses backup format with workflows and folders`() {
        val json = """
            {
              "workflows": [
                {
                  "_meta": { "name": "测试工作流" },
                  "id": "workflow-1",
                  "name": "测试工作流",
                  "steps": [
                    {
                      "moduleId": "vflow.trigger.time",
                      "parameters": { "time": "08:00" },
                      "id": "step-1"
                    },
                    {
                      "moduleId": "vflow.action.log",
                      "parameters": {},
                      "id": "step-2"
                    }
                  ],
                  "folderId": "folder-1"
                }
              ],
              "folders": [
                {
                  "id": "folder-1",
                  "name": "默认文件夹"
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(json)

        assertEquals(1, parsed.workflows.size)
        assertEquals(1, parsed.folders.size)
        assertEquals("workflow-1", parsed.workflows.first().id)
        assertEquals("默认文件夹", parsed.folders.first().name)
        assertEquals("folder-1", parsed.workflows.first().folderId)
    }

    @Test
    fun `sanitizes missing workflow identity fields`() {
        val json = """
            {
              "name": "",
              "steps": [
                {
                  "moduleId": "vflow.action.log",
                  "parameters": {},
                  "id": "step-1"
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(json)
        val workflow = parsed.workflows.single()

        assertTrue(workflow.id.isNotBlank())
        assertEquals("未命名工作流", workflow.name)
        assertEquals("1.0.0", workflow.version)
        assertEquals(1, workflow.vFlowLevel)
        assertNotNull(workflow.tags)
    }

    @Test
    fun `keeps folder id null when source folder is blank`() {
        val json = """
            [
              {
                "id": "workflow-1",
                "name": "测试工作流",
                "folderId": "",
                "steps": [
                  {
                    "moduleId": "vflow.action.log",
                    "parameters": {},
                    "id": "step-1"
                  }
                ]
              }
            ]
        """.trimIndent()

        val parsed = parser.parse(json)

        assertNull(parsed.workflows.single().folderId)
    }

    @Test
    fun `parses repository workflow metadata from meta block and legacy trigger config`() {
        val json = """
            {
              "_meta": {
                "id": "repo-workflow",
                "name": "仓库工作流",
                "version": "2.3.4",
                "vFlowLevel": 3,
                "description": "来自仓库",
                "author": "Repo Author",
                "homepage": "https://example.com/workflow",
                "tags": ["仓库", "测试"]
              },
              "id": "workflow-1",
              "name": "",
              "steps": [
                {
                  "moduleId": "vflow.action.log",
                  "parameters": {},
                  "id": "step-1"
                }
              ],
              "triggerConfig": {
                "type": "vflow.trigger.time",
                "time": "09:30"
              }
            }
        """.trimIndent()

        val parsed = parser.parse(json)
        val workflow = parsed.workflows.single()

        assertEquals("workflow-1", workflow.id)
        assertEquals("仓库工作流", workflow.name)
        assertEquals("2.3.4", workflow.version)
        assertEquals(3, workflow.vFlowLevel)
        assertEquals("来自仓库", workflow.description)
        assertEquals("Repo Author", workflow.author)
        assertEquals("https://example.com/workflow", workflow.homepage)
        assertEquals(listOf("仓库", "测试"), workflow.tags)
        assertEquals(1, workflow.triggers.size)
        assertEquals("vflow.trigger.time", workflow.triggers.single().moduleId)
        assertEquals("09:30", workflow.triggers.single().parameters["time"])
        assertEquals(1, workflow.steps.size)
        assertEquals("vflow.action.log", workflow.steps.single().moduleId)
    }

    @Test
    fun `ignores null string fields in repository workflow object`() {
        val json = """
            {
              "_meta": {
                "name": "仓库工作流"
              },
              "id": "workflow-1",
              "description": null,
              "author": null,
              "homepage": null,
              "folderId": null,
              "steps": [
                {
                  "moduleId": "vflow.action.log",
                  "parameters": null,
                  "id": "step-1"
                }
              ]
            }
        """.trimIndent()

        val parsed = parser.parse(json)
        val workflow = parsed.workflows.single()

        assertEquals("仓库工作流", workflow.name)
        assertEquals("", workflow.description)
        assertEquals("", workflow.author)
        assertEquals("", workflow.homepage)
        assertNull(workflow.folderId)
        assertEquals(emptyMap<String, Any?>(), workflow.steps.single().parameters)
    }
}
