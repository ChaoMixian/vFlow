# vFlow Remote Web Editor API Documentation

## Table of Contents
- [Overview](#overview)
- [Authentication](#authentication)
- [API Response Format](#api-response-format)
- [Error Codes](#error-codes)
- [Workflows](#workflows)
- [Workflow Execution](#workflow-execution)
- [Modules](#modules)
- [Magic Variables](#magic-variables)
- [Folders](#folders)
- [Import/Export](#importexport)
- [System](#system)

---

## Overview

**Base URL**: `http://<device-ip>:8080/api/v1`

**Protocol**: HTTP/HTTPS

**Content-Type**: `application/json`

**Authentication**: Bearer Token

**API Version**: 1.0.0

### Features
- Workflow management (CRUD operations)
- Workflow execution with async support
- Module definitions and schemas
- Magic variables for workflow steps
- Folder organization
- Import/Export workflows
- System information and statistics

---

## Authentication

### Generate Token

Generates a new access token for the web editor.

**Endpoint**: `POST /api/v1/auth/token`

**Request Body**:
```json
{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "deviceName": "My MacBook Pro",
  "timestamp": 1704067200000
}
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "refreshToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 3600,
    "tokenType": "Bearer",
    "deviceInfo": {
      "brand": "Xiaomi",
      "model": "Mi 14",
      "androidVersion": "14"
    }
  }
}
```

**Error Responses**:
- `400 Bad Request`: Invalid request parameters
- `429 Too Many Requests`: Rate limit exceeded
- `500 Internal Server Error`: Server error

---

### Refresh Token

Refreshes an expired access token.

**Endpoint**: `POST /api/v1/auth/refresh`

**Headers**:
```
Authorization: Bearer <refresh_token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresIn": 3600
  }
}
```

**Error Responses**:
- `401 Unauthorized`: Invalid refresh token
- `500 Internal Server Error`: Server error

---

### Verify Token

Verifies if a token is valid.

**Endpoint**: `GET /api/v1/auth/verify`

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "valid": true,
    "deviceInfo": {
      "brand": "Xiaomi",
      "model": "Mi 14",
      "androidVersion": "14",
      "apiLevel": 34
    },
    "expiresAt": 1704070800
  }
}
```

**Error Responses**:
- `401 Unauthorized`: Invalid or expired token

---

### Revoke Token

Revokes a token (logout).

**Endpoint**: `POST /api/v1/auth/revoke`

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Token revoked successfully",
  "data": null
}
```

---

## API Response Format

All API responses follow a standard format:

**Success Response**:
```json
{
  "code": 0,
  "message": "success",
  "data": { /* response data */ }
}
```

**Error Response**:
```json
{
  "code": 1001,
  "message": "Workflow not found",
  "data": null,
  "details": {
    "workflowId": "550e8400-e29b-41d4-a716-446655440000"
  }
}
```

---

## Error Codes

| Code | Message | Description |
|------|---------|-------------|
| 0 | success | Request successful |
| 1001 | Workflow not found | Workflow does not exist |
| 1002 | Invalid workflow data | Workflow data validation failed |
| 1003 | Workflow execution failed | Execution error |
| 1004 | Workflow already exists | Duplicate workflow |
| 2001 | Module not found | Module does not exist |
| 2002 | Invalid module parameters | Parameter validation failed |
| 3001 | Folder not found | Folder does not exist |
| 3002 | Folder not empty | Cannot delete non-empty folder |
| 5001 | Execution not found | Execution record does not exist |
| 5002 | Execution already stopped | Cannot stop stopped execution |
| 6001 | Invalid authentication | Invalid or missing token |
| 6002 | Token expired | Authentication token has expired |
| 6003 | Insufficient permissions | User lacks required permissions |
| 7001 | Rate limit exceeded | Too many requests |
| 8001 | Invalid file format | Uploaded file format not supported |
| 8002 | File size exceeded | File too large |
| 9001 | Internal server error | Unexpected server error |
| 9002 | Service unavailable | Service temporarily unavailable |

---

## Workflows

### List Workflows

Retrieves a list of workflows with optional filtering and pagination.

**Endpoint**: `GET /api/v1/workflows`

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| folderId | string | No | - | Filter by folder ID |
| includeDisabled | boolean | No | true | Include disabled workflows |
| sortBy | string | No | order | Sort field: name, modifiedAt, order |
| order | string | No | asc | Sort order: asc, desc |
| limit | integer | No | 50 | Max results per page |
| offset | integer | No | 0 | Pagination offset |
| search | string | No | - | Search in name/description |
| tags | string | No | - | Filter by tags (comma-separated) |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "workflows": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Auto Reply WeChat",
        "description": "Automatically reply to WeChat messages",
        "isEnabled": true,
        "isFavorite": true,
        "folderId": "folder-001",
        "order": 0,
        "stepCount": 15,
        "modifiedAt": 1704067200000,
        "tags": ["social", "automation"],
        "version": "1.2.0",
        "triggerConfig": {
          "type": "notification_received",
          "packageName": "com.tencent.mm"
        },
        "maxExecutionTime": 60,
        "author": "vFlow User",
        "vFlowLevel": 2
      }
    ],
    "total": 50,
    "limit": 50,
    "offset": 0
  }
}
```

---

### Get Workflow Detail

Retrieves detailed information about a specific workflow.

**Endpoint**: `GET /api/v1/workflows/{workflowId}`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| workflowId | string | Yes | Workflow UUID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "Auto Reply WeChat",
    "description": "Automatically reply to WeChat messages",
    "isEnabled": true,
    "isFavorite": true,
    "folderId": "folder-001",
    "order": 0,
    "steps": [
      {
        "id": "step-001",
        "moduleId": "trigger_notification",
        "indentationLevel": 0,
        "parameters": {
          "packageName": {
            "type": "string",
            "value": "com.tencent.mm"
          },
          "titleContains": {
            "type": "string",
            "value": ""
          },
          "contentContains": {
            "type": "string",
            "value": ""
          }
        }
      },
      {
        "id": "step-002",
        "moduleId": "if_condition",
        "indentationLevel": 0,
        "parameters": {
          "condition": {
            "type": "string",
            "value": "{{step-001.content}} contains \"hello\""
          }
        }
      },
      {
        "id": "step-003",
        "moduleId": "http_request",
        "indentationLevel": 1,
        "parameters": {
          "url": {
            "type": "string",
            "value": "https://api.example.com/reply"
          },
          "method": {
            "type": "string",
            "value": "POST"
          },
          "body": {
            "type": "string",
            "value": "{{step-001.content}}"
          }
        }
      },
      {
        "id": "step-004",
        "moduleId": "endif",
        "indentationLevel": 0,
        "parameters": {}
      }
    ],
    "triggerConfig": {
      "type": "notification_received",
      "packageName": "com.tencent.mm"
    },
    "modifiedAt": 1704067200000,
    "version": "1.2.0",
    "maxExecutionTime": 60,
    "metadata": {
      "author": "vFlow User",
      "homepage": "https://vflow.app/workflows/auto-reply-wechat",
      "vFlowLevel": 2,
      "tags": ["social", "automation"],
      "description": "Automatically reply to WeChat messages"
    }
  }
}
```

**Error Responses**:
- `404 Not Found`: Workflow not found

---

### Create Workflow

Creates a new workflow.

**Endpoint**: `POST /api/v1/workflows`

**Headers**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body**:
```json
{
  "name": "My New Workflow",
  "description": "This is a new workflow",
  "folderId": "folder-001",
  "steps": [],
  "isEnabled": false,
  "tags": ["automation"],
  "maxExecutionTime": 60,
  "triggerConfig": {
    "type": "manual"
  }
}
```

**Response** (201 Created):
```json
{
  "code": 0,
  "message": "Workflow created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "My New Workflow",
    "createdAt": 1704067200000
  }
}
```

**Error Responses**:
- `400 Bad Request`: Invalid workflow data
- `404 Not Found`: Folder not found
- `409 Conflict`: Workflow with same name already exists

---

### Update Workflow

Updates an existing workflow.

**Endpoint**: `PUT /api/v1/workflows/{workflowId}`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| workflowId | string | Yes | Workflow UUID |

**Headers**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body** (all fields optional):
```json
{
  "name": "Updated Workflow Name",
  "description": "Updated description",
  "isEnabled": true,
  "isFavorite": false,
  "folderId": "folder-002",
  "order": 5,
  "steps": [
    {
      "id": "step-001",
      "moduleId": "delay",
      "indentationLevel": 0,
      "parameters": {
        "duration": {
          "type": "number",
          "value": 1000
        }
      }
    }
  ],
  "triggerConfig": {
    "type": "manual"
  },
  "maxExecutionTime": 120,
  "tags": ["automation", "test"]
}
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Workflow updated successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "updatedAt": 1704067200000
  }
}
```

**Error Responses**:
- `400 Bad Request`: Invalid workflow data
- `404 Not Found`: Workflow not found

---

### Delete Workflow

Deletes a workflow.

**Endpoint**: `DELETE /api/v1/workflows/{workflowId}`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| workflowId | string | Yes | Workflow UUID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Workflow deleted successfully",
  "data": {
    "deleted": true,
    "deletedAt": 1704067200000
  }
}
```

**Error Responses**:
- `404 Not Found`: Workflow not found

---

### Duplicate Workflow

Creates a copy of an existing workflow.

**Endpoint**: `POST /api/v1/workflows/{workflowId}/duplicate`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| workflowId | string | Yes | Workflow UUID |

**Headers**:
```
Authorization: Bearer <token>
```

**Request Body** (optional):
```json
{
  "newName": "Copy of My Workflow",
  "targetFolderId": "folder-002"
}
```

**Response** (201 Created):
```json
{
  "code": 0,
  "message": "Workflow duplicated successfully",
  "data": {
    "newWorkflowId": "660e8400-e29b-41d4-a716-446655440001",
    "name": "Copy of My Workflow",
    "createdAt": 1704067200000
  }
}
```

**Error Responses**:
- `404 Not Found`: Workflow not found

---

### Enable Workflow

Enables a workflow.

**Endpoint**: `POST /api/v1/workflows/{workflowId}/enable`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| workflowId | string | Yes | Workflow UUID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Workflow enabled",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "isEnabled": true,
    "updatedAt": 1704067200000
  }
}
```

**Error Responses**:
- `404 Not Found`: Workflow not found

---

### Disable Workflow

Disables a workflow.

**Endpoint**: `POST /api/v1/workflows/{workflowId}/disable`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| workflowId | string | Yes | Workflow UUID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Workflow disabled",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "isEnabled": false,
    "updatedAt": 1704067200000
  }
}
```

**Error Responses**:
- `404 Not Found`: Workflow not found

---

### Batch Operations

Performs batch operations on multiple workflows.

**Endpoint**: `POST /api/v1/workflows/batch`

**Headers**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body**:
```json
{
  "action": "delete",
  "workflowIds": [
    "550e8400-e29b-41d4-a716-446655440000",
    "550e8400-e29b-41d4-a716-446655440001"
  ]
}
```

**Available Actions**:
- `delete`: Delete workflows
- `enable`: Enable workflows
- `disable`: Disable workflows
- `move`: Move workflows to folder (requires `targetFolderId`)

**Move Action Example**:
```json
{
  "action": "move",
  "workflowIds": ["id1", "id2"],
  "targetFolderId": "folder-002"
}
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Batch operation completed",
  "data": {
    "succeeded": ["id1", "id2"],
    "failed": [],
    "skipped": []
  }
}
```

---

## Workflow Execution

### Execute Workflow

Executes a workflow.

**Endpoint**: `POST /api/v1/workflows/{workflowId}/execute`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| workflowId | string | Yes | Workflow UUID |

**Headers**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body** (optional):
```json
{
  "inputVariables": {
    "myVar": {
      "type": "string",
      "value": "Hello World"
    }
  },
  "async": true,
  "timeout": 60
}
```

**Response** (202 Accepted):
```json
{
  "code": 0,
  "message": "Workflow execution started",
  "data": {
    "executionId": "exec-550e8400-e29b-41d4-a716-446655440000",
    "workflowId": "550e8400-e29b-41d4-a716-446655440000",
    "status": "running",
    "startedAt": 1704067200000
  }
}
```

**Synchronous Execution Response** (200 OK) when `async: false`:
```json
{
  "code": 0,
  "message": "Workflow completed",
  "data": {
    "executionId": "exec-550e8400-e29b-41d4-a716-446655440000",
    "status": "completed",
    "startedAt": 1704067200000,
    "completedAt": 1704067260000,
    "duration": 60000,
    "outputs": {
      "step-001": {
        "result": {
          "type": "string",
          "value": "Success"
        }
      }
    }
  }
}
```

**Error Responses**:
- `404 Not Found`: Workflow not found
- `409 Conflict`: Workflow is disabled
- `422 Unprocessable Entity`: Workflow validation failed

---

### Get Execution Status

Retrieves the status of a workflow execution.

**Endpoint**: `GET /api/v1/executions/{executionId}`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| executionId | string | Yes | Execution UUID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "executionId": "exec-550e8400-e29b-41d4-a716-446655440000",
    "workflowId": "550e8400-e29b-41d4-a716-446655440000",
    "workflowName": "Auto Reply WeChat",
    "status": "running",
    "currentStepIndex": 5,
    "totalSteps": 10,
    "currentStep": {
      "id": "step-005",
      "moduleId": "http_request",
      "name": "HTTP Request"
    },
    "startedAt": 1704067200000,
    "completedAt": null,
    "duration": null,
    "outputs": {
      "step-001": {
        "notificationContent": {
          "type": "string",
          "value": "Hello"
        }
      }
    },
    "error": null,
    "variables": {
      "counter": {
        "type": "number",
        "value": 5
      }
    }
  }
}
```

**Status Values**:
- `running`: Currently executing
- `completed`: Finished successfully
- `failed`: Failed with error
- `cancelled`: Cancelled by user
- `timeout`: Exceeded max execution time

**Error Responses**:
- `404 Not Found`: Execution not found

---

### Stop Execution

Stops a running workflow execution.

**Endpoint**: `POST /api/v1/executions/{executionId}/stop`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| executionId | string | Yes | Execution UUID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Execution stopped",
  "data": {
    "executionId": "exec-550e8400-e29b-41d4-a716-446655440000",
    "stoppedAt": 1704067260000,
    "status": "cancelled"
  }
}
```

**Error Responses**:
- `404 Not Found`: Execution not found
- `409 Conflict`: Execution already stopped

---

### Get Execution Logs

Retrieves logs for a workflow execution.

**Endpoint**: `GET /api/v1/executions/{executionId}/logs`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| executionId | string | Yes | Execution UUID |

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| level | string | No | - | Filter by log level: info, warning, error |
| stepIndex | integer | No | - | Filter by step index |
| limit | integer | No | 100 | Max log entries |
| offset | integer | No | 0 | Pagination offset |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "logs": [
      {
        "timestamp": 1704067200000,
        "level": "info",
        "stepIndex": 0,
        "moduleId": "delay",
        "message": "Starting step: delay",
        "details": {
          "duration": 1000
        }
      },
      {
        "timestamp": 1704067201000,
        "level": "info",
        "stepIndex": 0,
        "moduleId": "delay",
        "message": "Completed step: delay",
        "details": {
          "actualDuration": 1001
        }
      },
      {
        "timestamp": 1704067202000,
        "level": "error",
        "stepIndex": 1,
        "moduleId": "http_request",
        "message": "HTTP request failed",
        "details": {
          "error": "Connection timeout",
          "url": "https://api.example.com"
        }
      }
    ],
    "total": 50,
    "limit": 100,
    "offset": 0
  }
}
```

---

### List Executions

Lists workflow execution history.

**Endpoint**: `GET /api/v1/executions`

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| workflowId | string | No | - | Filter by workflow ID |
| status | string | No | - | Filter by status |
| startDate | long | No | - | Filter by start time (epoch ms) |
| endDate | long | No | - | Filter by end time (epoch ms) |
| limit | integer | No | 20 | Max results per page |
| offset | integer | No | 0 | Pagination offset |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "executions": [
      {
        "executionId": "exec-001",
        "workflowId": "wf-001",
        "workflowName": "Auto Reply WeChat",
        "status": "completed",
        "startedAt": 1704067200000,
        "completedAt": 1704067260000,
        "duration": 60000,
        "triggeredBy": "manual"
      },
      {
        "executionId": "exec-002",
        "workflowId": "wf-001",
        "workflowName": "Auto Reply WeChat",
        "status": "failed",
        "startedAt": 1704067000000,
        "completedAt": 1704067050000,
        "duration": 50000,
        "triggeredBy": "notification",
        "error": "Connection timeout"
      }
    ],
    "total": 100,
    "limit": 20,
    "offset": 0
  }
}
```

---

### Delete Execution History

Deletes execution history records.

**Endpoint**: `DELETE /api/v1/executions`

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| executionId | string | No | - | Specific execution ID |
| workflowId | string | No | - | Delete all for workflow |
| olderThan | long | No | - | Delete records older than (epoch ms) |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Execution history deleted",
  "data": {
    "deletedCount": 50
  }
}
```

---

## Modules

### List Module Categories

Retrieves all module categories.

**Endpoint**: `GET /api/v1/modules/categories`

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "categories": [
      {
        "id": "trigger",
        "name": "触发器",
        "nameEn": "Triggers",
        "icon": "ic_trigger",
        "description": "工作流触发条件",
        "descriptionEn": "Workflow trigger conditions",
        "order": 0
      },
      {
        "id": "ui_interaction",
        "name": "界面交互",
        "nameEn": "UI Interaction",
        "icon": "ic_ui",
        "description": "UI自动化操作",
        "descriptionEn": "UI automation operations",
        "order": 1
      },
      {
        "id": "logic",
        "name": "逻辑控制",
        "nameEn": "Logic Control",
        "icon": "ic_logic",
        "description": "条件判断与循环",
        "descriptionEn": "Conditions and loops",
        "order": 2
      },
      {
        "id": "data",
        "name": "数据",
        "nameEn": "Data",
        "icon": "ic_data",
        "description": "变量与数据处理",
        "descriptionEn": "Variables and data processing",
        "order": 3
      },
      {
        "id": "file",
        "name": "文件",
        "nameEn": "File",
        "icon": "ic_file",
        "description": "文件操作",
        "descriptionEn": "File operations",
        "order": 4
      },
      {
        "id": "network",
        "name": "网络",
        "nameEn": "Network",
        "icon": "ic_network",
        "description": "网络请求",
        "descriptionEn": "Network requests",
        "order": 5
      },
      {
        "id": "system",
        "name": "应用与系统",
        "nameEn": "App & System",
        "icon": "ic_system",
        "description": "系统控制与应用管理",
        "descriptionEn": "System control and app management",
        "order": 6
      },
      {
        "id": "shizuku",
        "name": "Shizuku",
        "icon": "ic_shizuku",
        "description": "高级系统操作",
        "descriptionEn": "Advanced system operations",
        "order": 7
      }
    ]
  }
}
```

---

### List Modules

Retrieves modules, optionally filtered by category.

**Endpoint**: `GET /api/v1/modules`

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| category | string | No | - | Filter by category ID |
| search | string | No | - | Search in module name/description |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "modules": [
      {
        "id": "delay",
        "metadata": {
          "name": "延迟",
          "nameEn": "Delay",
          "icon": "ic_delay",
          "category": "system",
          "description": "延迟指定时间",
          "descriptionEn": "Delay for specified time"
        },
        "blockBehavior": {
          "blockType": "none",
          "canStartWorkflow": true,
          "endBlockId": null
        }
      },
      {
        "id": "http_request",
        "metadata": {
          "name": "HTTP请求",
          "nameEn": "HTTP Request",
          "icon": "ic_http",
          "category": "network",
          "description": "发送HTTP请求",
          "descriptionEn": "Send HTTP request"
        },
        "blockBehavior": {
          "blockType": "none",
          "canStartWorkflow": true,
          "endBlockId": null
        }
      },
      {
        "id": "if_condition",
        "metadata": {
          "name": "If条件",
          "nameEn": "If Condition",
          "icon": "ic_if",
          "category": "logic",
          "description": "条件判断",
          "descriptionEn": "Conditional statement"
        },
        "blockBehavior": {
          "blockType": "if",
          "canStartWorkflow": false,
          "endBlockId": "endif"
        }
      }
    ]
  }
}
```

---

### Get Module Detail

Retrieves detailed information about a specific module.

**Endpoint**: `GET /api/v1/modules/{moduleId}`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| moduleId | string | Yes | Module ID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "http_request",
    "metadata": {
      "name": "HTTP请求",
      "nameEn": "HTTP Request",
      "icon": "ic_http",
      "category": "network",
      "description": "发送HTTP/HTTPS请求",
      "descriptionEn": "Send HTTP/HTTPS request",
      "helpUrl": "https://docs.vflow.app/modules/http-request"
    },
    "blockBehavior": {
      "blockType": "none",
      "canStartWorkflow": true,
      "endBlockId": null
    },
    "inputs": [
      {
        "id": "url",
        "type": "string",
        "label": "URL",
        "labelEn": "URL",
        "description": "请求地址",
        "descriptionEn": "Request URL",
        "defaultValue": null,
        "required": true,
        "uiType": "text_field",
        "constraints": {
          "pattern": "^https?://.*",
          "minLength": 1,
          "maxLength": 2000
        }
      },
      {
        "id": "method",
        "type": "string",
        "label": "请求方法",
        "labelEn": "Method",
        "description": "HTTP请求方法",
        "descriptionEn": "HTTP request method",
        "defaultValue": "GET",
        "required": true,
        "uiType": "dropdown",
        "constraints": {
          "options": ["GET", "POST", "PUT", "DELETE", "PATCH"]
        }
      },
      {
        "id": "headers",
        "type": "dictionary",
        "label": "请求头",
        "labelEn": "Headers",
        "description": "自定义HTTP请求头",
        "descriptionEn": "Custom HTTP headers",
        "defaultValue": null,
        "required": false,
        "uiType": "key_value_editor"
      },
      {
        "id": "body",
        "type": "string",
        "label": "请求体",
        "labelEn": "Body",
        "description": "POST请求体",
        "descriptionEn": "POST request body",
        "defaultValue": null,
        "required": false,
        "uiType": "code_editor",
        "constraints": {
          "language": "json"
        }
      },
      {
        "id": "timeout",
        "type": "number",
        "label": "超时时间",
        "labelEn": "Timeout",
        "description": "请求超时时间（秒）",
        "descriptionEn": "Request timeout (seconds)",
        "defaultValue": 30,
        "required": false,
        "uiType": "number_slider",
        "constraints": {
          "min": 1,
          "max": 300,
          "step": 1
        }
      }
    ],
    "outputs": [
      {
        "id": "statusCode",
        "type": "number",
        "label": "状态码",
        "labelEn": "Status Code",
        "description": "HTTP响应状态码",
        "descriptionEn": "HTTP response status code"
      },
      {
        "id": "body",
        "type": "string",
        "label": "响应体",
        "labelEn": "Response Body",
        "description": "HTTP响应内容",
        "descriptionEn": "HTTP response content"
      },
      {
        "id": "headers",
        "type": "dictionary",
        "label": "响应头",
        "labelEn": "Response Headers",
        "description": "HTTP响应头",
        "descriptionEn": "HTTP response headers"
      }
    ],
    "examples": [
      {
        "name": "GET Example",
        "description": "Simple GET request",
        "parameters": {
          "url": "https://api.example.com/users",
          "method": "GET"
        }
      }
    ]
  }
}
```

**Error Responses**:
- `404 Not Found`: Module not found

---

### Get Module Input Schema

Retrieves the input UI schema for a module.

**Endpoint**: `GET /api/v1/modules/{moduleId}/input-schema`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| moduleId | string | Yes | Module ID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "schema": [
      {
        "key": "url",
        "type": "text_field",
        "label": "URL",
        "placeholder": "https://api.example.com",
        "required": true,
        "validation": {
          "pattern": "^https?://.*",
          "message": "Invalid URL format"
        },
        "autocomplete": {
          "type": "variable",
          "allowMagicVariables": true
        }
      },
      {
        "key": "method",
        "type": "dropdown",
        "label": "请求方法",
        "required": true,
        "options": [
          { "value": "GET", "label": "GET" },
          { "value": "POST", "label": "POST" },
          { "value": "PUT", "label": "PUT" },
          { "value": "DELETE", "label": "DELETE" }
        ]
      },
      {
        "key": "timeout",
        "type": "number_slider",
        "label": "超时时间",
        "min": 1,
        "max": 300,
        "step": 1,
        "unit": "秒",
        "defaultValue": 30
      },
      {
        "key": "headers",
        "type": "key_value_editor",
        "label": "请求头",
        "keyPlaceholder": "Header name",
        "valuePlaceholder": "Header value",
        "allowVariables": true
      }
    ]
  }
}
```

---

## Magic Variables

Retrieves available magic variable references for a workflow. Magic variables allow you to reference outputs from previous steps in your workflow.

**Endpoint**: `GET /api/v1/workflows/{workflowId}/magic-variables`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| workflowId | string | Yes | Workflow UUID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "magicVariables": [
      {
        "key": "{{step-001.statusCode}}",
        "label": "延迟 - 实际延迟",
        "type": "number",
        "stepId": "step-001",
        "stepName": "延迟",
        "outputId": "actualDuration",
        "outputName": "实际延迟",
        "category": "Steps"
      },
      {
        "key": "{{step-002.body}}",
        "label": "HTTP请求 - 响应体",
        "type": "string",
        "stepId": "step-002",
        "stepName": "HTTP请求",
        "outputId": "body",
        "outputName": "响应体",
        "category": "Steps"
      }
    ],
    "systemVariables": [
      {
        "key": "{{trigger.data}}",
        "label": "触发器数据",
        "type": "any",
        "description": "触发器传入的原始数据"
      },
      {
        "key": "{{current_time}}",
        "label": "当前时间",
        "type": "number",
        "description": "当前时间戳（毫秒）"
      },
      {
        "key": "{{device_info}}",
        "label": "设备信息",
        "type": "dictionary",
        "description": "设备相关信息"
      }
    ]
  }
}
```

---

## Folders

### List Folders

Retrieves all folders.

**Endpoint**: `GET /api/v1/folders`

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| parentId | string | No | - | Filter by parent folder ID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "folders": [
      {
        "id": "folder-001",
        "name": "My Workflows",
        "parentId": null,
        "order": 0,
        "workflowCount": 15,
        "subfolderCount": 2,
        "createdAt": 1704067200000,
        "modifiedAt": 1704067200000
      },
      {
        "id": "folder-002",
        "name": "Automation",
        "parentId": "folder-001",
        "order": 0,
        "workflowCount": 8,
        "subfolderCount": 0,
        "createdAt": 1704067200000,
        "modifiedAt": 1704067200000
      }
    ],
    "total": 2
  }
}
```

---

### Get Folder Detail

Retrieves detailed information about a folder.

**Endpoint**: `GET /api/v1/folders/{folderId}`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| folderId | string | Yes | Folder UUID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "folder-001",
    "name": "My Workflows",
    "parentId": null,
    "order": 0,
    "workflowCount": 15,
    "subfolderCount": 2,
    "workflows": [
      {
        "id": "wf-001",
        "name": "Workflow 1",
        "isEnabled": true,
        "order": 0
      }
    ],
    "subfolders": [
      {
        "id": "folder-002",
        "name": "Automation",
        "order": 0
      }
    ],
    "createdAt": 1704067200000,
    "modifiedAt": 1704067200000
  }
}
```

**Error Responses**:
- `404 Not Found`: Folder not found

---

### Create Folder

Creates a new folder.

**Endpoint**: `POST /api/v1/folders`

**Headers**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body**:
```json
{
  "name": "New Folder",
  "parentId": "folder-001",
  "order": 0
}
```

**Response** (201 Created):
```json
{
  "code": 0,
  "message": "Folder created successfully",
  "data": {
    "id": "folder-003",
    "name": "New Folder",
    "createdAt": 1704067200000
  }
}
```

**Error Responses**:
- `400 Bad Request`: Invalid folder data
- `404 Not Found`: Parent folder not found
- `409 Conflict`: Folder with same name exists

---

### Update Folder

Updates an existing folder.

**Endpoint**: `PUT /api/v1/folders/{folderId}`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| folderId | string | Yes | Folder UUID |

**Headers**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body** (all fields optional):
```json
{
  "name": "Updated Folder Name",
  "parentId": "folder-002",
  "order": 5
}
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Folder updated successfully",
  "data": {
    "id": "folder-001",
    "updatedAt": 1704067200000
  }
}
```

**Error Responses**:
- `400 Bad Request`: Invalid folder data
- `404 Not Found`: Folder not found

---

### Delete Folder

Deletes a folder.

**Endpoint**: `DELETE /api/v1/folders/{folderId}`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| folderId | string | Yes | Folder UUID |

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| deleteWorkflows | boolean | No | false | Delete workflows in folder |
| moveWorkflowsTo | string | No | - | Move workflows to this folder ID |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Folder deleted successfully",
  "data": {
    "deleted": true,
    "deletedAt": 1704067200000,
    "workflowsDeleted": 0,
    "workflowsMoved": 15
  }
}
```

**Error Responses**:
- `400 Bad Request`: Folder not empty and no action specified
- `404 Not Found`: Folder not found

---

## Import/Export

### Export Workflow

Exports a workflow.

**Endpoint**: `GET /api/v1/workflows/{workflowId}/export`

**Path Parameters**:
| Parameter | Type | Required | Description |
|-----------|------|----------|-------------|
| workflowId | string | Yes | Workflow UUID |

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| format | string | No | json | Export format: json, zip |
| includeSteps | boolean | No | true | Include workflow steps |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK) for JSON format:
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "workflow": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "My Workflow",
      "description": "Workflow description",
      "version": "1.0.0",
      "steps": []
    },
    "exportedAt": 1704067200000,
    "format": "json"
  }
}
```

**Response** (200 OK) for ZIP format:
```
Content-Type: application/zip
Content-Disposition: attachment; filename="workflow-name.zip"

<binary zip data>
```

---

### Import Workflow

Imports a workflow.

**Endpoint**: `POST /api/v1/workflows/import`

**Headers**:
```
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**Request Body** (multipart/form-data):
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| file | File | Yes | Workflow file (JSON or ZIP) |
| override | boolean | No | Override existing workflow |
| folderId | string | No | Target folder ID |

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Workflow imported successfully",
  "data": {
    "imported": [
      {
        "workflowId": "550e8400-e29b-41d4-a716-446655440000",
        "name": "Imported Workflow"
      }
    ],
    "skipped": [],
    "errors": [],
    "total": 1
  }
}
```

**Error Responses**:
- `400 Bad Request`: Invalid file format
- `413 Payload Too Large`: File size exceeded

---

### Batch Export

Exports multiple workflows.

**Endpoint**: `POST /api/v1/workflows/export-batch`

**Headers**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**Request Body**:
```json
{
  "workflowIds": [
    "wf-001",
    "wf-002"
  ],
  "format": "zip",
  "includeSteps": true
}
```

**Response** (200 OK):
```
Content-Type: application/zip
Content-Disposition: attachment; filename="workflows-export.zip"

<binary zip data>
```

---

### Batch Import

Imports multiple workflows.

**Endpoint**: `POST /api/v1/workflows/import-batch`

**Headers**:
```
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**Request Body** (multipart/form-data):
| Field | Type | Required | Description |
|-------|------|----------|-------------|
| file | File | Yes | ZIP file containing workflows |
| override | boolean | No | Override existing workflows |
| folderId | string | No | Target folder ID |

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "Batch import completed",
  "data": {
    "imported": [
      {
        "workflowId": "wf-001",
        "name": "Workflow 1"
      },
      {
        "workflowId": "wf-002",
        "name": "Workflow 2"
      }
    ],
    "skipped": [
      {
        "name": "Workflow 3",
        "reason": "Already exists"
      }
    ],
    "errors": [
      {
        "filename": "workflow4.json",
        "error": "Invalid JSON format"
      }
    ],
    "total": 4,
    "importedCount": 2,
    "skippedCount": 1,
    "errorCount": 1
  }
}
```

---

## System

### Get Device Info

Retrieves device information.

**Endpoint**: `GET /api/v1/system/info`

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "device": {
      "brand": "Xiaomi",
      "model": "Mi 14",
      "manufacturer": "Xiaomi",
      "androidVersion": "14",
      "apiLevel": 34,
      "sdkVersion": "1.0.0"
    },
    "permissions": [
      {
        "name": "ACCESSIBILITY_SERVICE",
        "granted": true,
        "description": "无障碍服务权限"
      },
      {
        "name": "WRITE_EXTERNAL_STORAGE",
        "granted": true,
        "description": "存储权限"
      },
      {
        "name": "SYSTEM_ALERT_WINDOW",
        "granted": false,
        "description": "悬浮窗权限"
      }
    ],
    "capabilities": {
      "hasRoot": false,
      "hasShizuku": true,
      "hasCoreService": true,
      "supportedFeatures": [
        "opencv_image_matching",
        "ml_kit_ocr",
        "lua_scripting",
        "javascript_scripting"
      ]
    },
    "server": {
      "version": "1.0.0",
      "startTime": 1704067200000,
      "uptime": 86400000
    }
  }
}
```

---

### Get System Statistics

Retrieves system statistics.

**Endpoint**: `GET /api/v1/system/stats`

**Query Parameters**:
| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| period | string | No | today | Period: today, week, month, all |

**Headers**:
```
Authorization: Bearer <token>
```

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "workflowCount": 50,
    "enabledWorkflowCount": 30,
    "folderCount": 5,
    "totalExecutions": 1000,
    "todayExecutions": 50,
    "successfulExecutions": 950,
    "failedExecutions": 50,
    "successRate": 0.95,
    "averageExecutionTime": 5000,
    "storageUsage": {
      "usedBytes": 10485760,
      "totalBytes": 104857600,
      "used": "10MB",
      "total": "100MB",
      "percentage": 10
    },
    "memoryUsage": {
      "usedBytes": 52428800,
      "totalBytes": 209715200,
      "used": "50MB",
      "total": "200MB",
      "percentage": 25
    },
    "topWorkflows": [
      {
        "workflowId": "wf-001",
        "name": "Auto Reply",
        "executionCount": 100
      }
    ]
  }
}
```

---

### Health Check

Checks API server health status.

**Endpoint**: `GET /api/v1/system/health`

**Response** (200 OK):
```json
{
  "code": 0,
  "message": "healthy",
  "data": {
    "status": "healthy",
    "version": "1.0.0",
    "timestamp": 1704067200000,
    "uptime": 86400000
  }
}
```

---

## Rate Limiting

The API implements rate limiting to prevent abuse:

| Endpoint Type | Limit | Window |
|--------------|-------|--------|
| Authentication | 5 requests | 1 minute |
| Workflow Execution | 10 requests | 1 minute |
| Query Operations | 100 requests | 1 minute |
| Modify Operations | 60 requests | 1 minute |

**Rate Limit Response** (429 Too Many Requests):
```json
{
  "code": 7001,
  "message": "Rate limit exceeded",
  "data": null,
  "details": {
    "limit": 100,
    "remaining": 0,
    "resetAt": 1704067260000
  }
}
```

**Headers**:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1704067260000
```

---

## Best Practices

### 1. Authentication
- Always include the Bearer token in request headers
- Refresh tokens before they expire
- Store tokens securely on the client side

### 2. Error Handling
- Always check the `code` field in responses
- Handle rate limit errors gracefully with exponential backoff
- Display user-friendly error messages based on error codes

### 3. Performance
- Use pagination for large datasets
- Request only necessary fields

### 4. Workflow Execution
- Use `async: true` for long-running workflows
- Poll execution status via GET /api/v1/executions/{id}
- Implement proper timeout handling

### 5. Import/Export
- Use batch operations for multiple workflows
- Compress large workflow collections
- Validate workflow data before import

---

## Changelog

### Version 1.0.0 (2025-02-26)
- Initial API release
- Workflow CRUD operations
- Workflow execution with async support
- Module definitions and schemas
- Magic variables for workflow steps
- Folder organization
- Import/Export functionality
- System information and statistics
- Authentication and rate limiting

---

## Support

For API support and questions:
- Documentation: https://chaomixian.github.io/vFlow-Docs/
- Issues: https://github.com/ChaoMixian/vFlow/issues
