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
}
