# vFlow 远程Web编辑器 API 文档

## 目录
- [概述](#概述)
- [认证](#认证)
- [API响应格式](#api响应格式)
- [错误码](#错误码)
- [工作流](#工作流)
- [工作流执行](#工作流执行)
- [模块](#模块)
- [魔法变量](#魔法变量)
- [文件夹](#文件夹)
- [导入/导出](#导入导出)
- [系统](#系统)

---

## 概述

**基础URL**: `http://<设备IP>:8080/api/v1`

**协议**: HTTP/HTTPS

**内容类型**: `application/json`

**认证方式**: Bearer Token

**API版本**: 1.0.0

### 功能特性
- 工作流管理（CRUD操作）
- 异步工作流执行
- 模块定义和Schema
- 工作流步骤的魔法变量
- 文件夹组织
- 工作流导入/导出
- 系统信息和统计

---

## 认证

### 生成Token

为Web编辑器生成新的访问令牌。

**接口**: `POST /api/v1/auth/token`

**请求体**:
```json
{
  "deviceId": "550e8400-e29b-41d4-a716-446655440000",
  "deviceName": "My MacBook Pro",
  "timestamp": 1704067200000
}
```

**响应** (200 OK):
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

**错误响应**:
- `400 Bad Request`: 请求参数无效
- `429 Too Many Requests`: 请求频率超限
- `500 Internal Server Error`: 服务器错误

---

### 刷新Token

刷新已过期的访问令牌。

**接口**: `POST /api/v1/auth/refresh`

**请求头**:
```
Authorization: Bearer <refresh_token>
```

**响应** (200 OK):
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

**错误响应**:
- `401 Unauthorized`: 无效的刷新令牌
- `500 Internal Server Error`: 服务器错误

---

### 验证Token

验证令牌是否有效。

**接口**: `GET /api/v1/auth/verify`

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

**错误响应**:
- `401 Unauthorized`: 无效或已过期的令牌

---

### 撤销Token

撤销令牌（退出登录）。

**接口**: `POST /api/v1/auth/revoke`

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
```json
{
  "code": 0,
  "message": "Token revoked successfully",
  "data": null
}
```

---

## API响应格式

所有API响应都遵循标准格式：

**成功响应**:
```json
{
  "code": 0,
  "message": "success",
  "data": { /* 响应数据 */ }
}
```

**错误响应**:
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

## 错误码

| 编码 | 消息 | 描述 |
|------|------|------|
| 0 | success | 请求成功 |
| 1001 | Workflow not found | 工作流不存在 |
| 1002 | Invalid workflow data | 工作流数据验证失败 |
| 1003 | Workflow execution failed | 执行错误 |
| 1004 | Workflow already exists | 重复的工作流 |
| 2001 | Module not found | 模块不存在 |
| 2002 | Invalid module parameters | 参数验证失败 |
| 3001 | Folder not found | 文件夹不存在 |
| 3002 | Folder not empty | 无法删除非空文件夹 |
| 5001 | Execution not found | 执行记录不存在 |
| 5002 | Execution already stopped | 无法停止已停止的执行 |
| 6001 | Invalid authentication | 无效或缺失的令牌 |
| 6002 | Token expired | 认证令牌已过期 |
| 6003 | Insufficient permissions | 用户权限不足 |
| 7001 | Rate limit exceeded | 请求过于频繁 |
| 8001 | Invalid file format | 不支持的文件格式 |
| 8002 | File size exceeded | 文件过大 |
| 9001 | Internal server error | 意外的服务器错误 |
| 9002 | Service unavailable | 服务暂时不可用 |

---

## 工作流

### 获取工作流列表

获取工作流列表，支持可选的筛选和分页。

**接口**: `GET /api/v1/workflows`

**查询参数**:
| 参数 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| folderId | string | 否 | - | 按文件夹ID筛选 |
| includeDisabled | boolean | 否 | true | 包含已禁用的工作流 |
| sortBy | string | 否 | order | 排序字段：name, modifiedAt, order |
| order | string | 否 | asc | 排序方式：asc, desc |
| limit | integer | 否 | 50 | 每页最大结果数 |
| offset | integer | 否 | 0 | 分页偏移量 |
| search | string | 否 | - | 在名称/描述中搜索 |
| tags | string | 否 | - | 按标签筛选（逗号分隔） |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "workflows": [
      {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "name": "自动回复微信",
        "description": "自动回复微信消息",
        "isEnabled": true,
        "isFavorite": true,
        "folderId": "folder-001",
        "order": 0,
        "stepCount": 15,
        "modifiedAt": 1704067200000,
        "tags": ["社交", "自动化"],
        "version": "1.2.0",
        "triggerConfig": {
          "type": "notification_received",
          "packageName": "com.tencent.mm"
        },
        "maxExecutionTime": 60,
        "author": "vFlow用户",
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

### 获取工作流详情

获取特定工作流的详细信息。

**接口**: `GET /api/v1/workflows/{workflowId}`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| workflowId | string | 是 | 工作流UUID |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "自动回复微信",
    "description": "自动回复微信消息",
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
      "author": "vFlow用户",
      "homepage": "https://vflow.app/workflows/auto-reply-wechat",
      "vFlowLevel": 2,
      "tags": ["社交", "自动化"],
      "description": "自动回复微信消息"
    }
  }
}
```

**错误响应**:
- `404 Not Found`: 工作流不存在

---

### 创建工作流

创建新的工作流。

**接口**: `POST /api/v1/workflows`

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**:
```json
{
  "name": "我的新工作流",
  "description": "这是一个新工作流",
  "folderId": "folder-001",
  "steps": [],
  "isEnabled": false,
  "tags": ["自动化"],
  "maxExecutionTime": 60,
  "triggerConfig": {
    "type": "manual"
  }
}
```

**响应** (201 Created):
```json
{
  "code": 0,
  "message": "Workflow created successfully",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "name": "我的新工作流",
    "createdAt": 1704067200000
  }
}
```

**错误响应**:
- `400 Bad Request`: 无效的工作流数据
- `404 Not Found`: 文件夹不存在
- `409 Conflict`: 同名工作流已存在

---

### 更新工作流

更新现有工作流。

**接口**: `PUT /api/v1/workflows/{workflowId}`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| workflowId | string | 是 | 工作流UUID |

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体** (所有字段可选):
```json
{
  "name": "更新后的工作流名称",
  "description": "更新后的描述",
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
  "tags": ["自动化", "测试"]
}
```

**响应** (200 OK):
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

**错误响应**:
- `400 Bad Request`: 无效的工作流数据
- `404 Not Found`: 工作流不存在

---

### 删除工作流

删除工作流。

**接口**: `DELETE /api/v1/workflows/{workflowId}`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| workflowId | string | 是 | 工作流UUID |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

**错误响应**:
- `404 Not Found`: 工作流不存在

---

### 复制工作流

创建现有工作流的副本。

**接口**: `POST /api/v1/workflows/{workflowId}/duplicate`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| workflowId | string | 是 | 工作流UUID |

**请求头**:
```
Authorization: Bearer <token>
```

**请求体** (可选):
```json
{
  "newName": "我的工作流副本",
  "targetFolderId": "folder-002"
}
```

**响应** (201 Created):
```json
{
  "code": 0,
  "message": "Workflow duplicated successfully",
  "data": {
    "newWorkflowId": "660e8400-e29b-41d4-a716-446655440001",
    "name": "我的工作流副本",
    "createdAt": 1704067200000
  }
}
```

**错误响应**:
- `404 Not Found`: 工作流不存在

---

### 启用工作流

启用工作流。

**接口**: `POST /api/v1/workflows/{workflowId}/enable`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| workflowId | string | 是 | 工作流UUID |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

**错误响应**:
- `404 Not Found`: 工作流不存在

---

### 禁用工作流

禁用工作流。

**接口**: `POST /api/v1/workflows/{workflowId}/disable`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| workflowId | string | 是 | 工作流UUID |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

**错误响应**:
- `404 Not Found`: 工作流不存在

---

### 批量操作

对多个工作流执行批量操作。

**接口**: `POST /api/v1/workflows/batch`

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**:
```json
{
  "action": "delete",
  "workflowIds": [
    "550e8400-e29b-41d4-a716-446655440000",
    "550e8400-e29b-41d4-a716-446655440001"
  ]
}
```

**可用操作**:
- `delete`: 删除工作流
- `enable`: 启用工作流
- `disable`: 禁用工作流
- `move`: 移动工作流到文件夹（需要 `targetFolderId`）

**移动操作示例**:
```json
{
  "action": "move",
  "workflowIds": ["id1", "id2"],
  "targetFolderId": "folder-002"
}
```

**响应** (200 OK):
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

## 工作流执行

### 执行工作流

执行工作流。

**接口**: `POST /api/v1/workflows/{workflowId}/execute`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| workflowId | string | 是 | 工作流UUID |

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体** (可选):
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

**响应** (202 Accepted):
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

**同步执行响应** (200 OK) 当 `async: false`:
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

**错误响应**:
- `404 Not Found`: 工作流不存在
- `409 Conflict`: 工作流已禁用
- `422 Unprocessable Entity`: 工作流验证失败

---

### 获取执行状态

获取工作流执行的状态。

**接口**: `GET /api/v1/executions/{executionId}`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| executionId | string | 是 | 执行UUID |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "executionId": "exec-550e8400-e29b-41d4-a716-446655440000",
    "workflowId": "550e8400-e29b-41d4-a716-446655440000",
    "workflowName": "自动回复微信",
    "status": "running",
    "currentStepIndex": 5,
    "totalSteps": 10,
    "currentStep": {
      "id": "step-005",
      "moduleId": "http_request",
      "name": "HTTP请求"
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

**状态值**:
- `running`: 正在执行
- `completed`: 成功完成
- `failed`: 执行失败
- `cancelled`: 用户取消
- `timeout`: 超出最大执行时间

**错误响应**:
- `404 Not Found`: 执行不存在

---

### 停止执行

停止正在运行的工作流执行。

**接口**: `POST /api/v1/executions/{executionId}/stop`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| executionId | string | 是 | 执行UUID |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

**错误响应**:
- `404 Not Found`: 执行不存在
- `409 Conflict`: 执行已停止

---

### 获取执行日志

获取工作流执行的日志。

**接口**: `GET /api/v1/executions/{executionId}/logs`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| executionId | string | 是 | 执行UUID |

**查询参数**:
| 参数 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| level | string | 否 | - | 按日志级别筛选：info, warning, error |
| stepIndex | integer | 否 | - | 按步骤索引筛选 |
| limit | integer | 否 | 100 | 最大日志条目数 |
| offset | integer | 否 | 0 | 分页偏移量 |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

### 获取执行历史

列出工作流执行历史。

**接口**: `GET /api/v1/executions`

**查询参数**:
| 参数 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| workflowId | string | 否 | - | 按工作流ID筛选 |
| status | string | 否 | - | 按状态筛选 |
| startDate | long | 否 | - | 按开始时间筛选（毫秒） |
| endDate | long | 否 | - | 按结束时间筛选（毫秒） |
| limit | integer | 否 | 20 | 每页最大结果数 |
| offset | integer | 否 | 0 | 分页偏移量 |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "executions": [
      {
        "executionId": "exec-001",
        "workflowId": "wf-001",
        "workflowName": "自动回复微信",
        "status": "completed",
        "startedAt": 1704067200000,
        "completedAt": 1704067260000,
        "duration": 60000,
        "triggeredBy": "manual"
      },
      {
        "executionId": "exec-002",
        "workflowId": "wf-001",
        "workflowName": "自动回复微信",
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

### 删除执行历史

删除执行历史记录。

**接口**: `DELETE /api/v1/executions`

**查询参数**:
| 参数 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| executionId | string | 否 | - | 特定的执行ID |
| workflowId | string | 否 | - | 删除该工作流的所有记录 |
| olderThan | long | 否 | - | 删除早于此时间的记录（毫秒） |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

## 模块

### 获取模块分类列表

获取所有模块分类。

**接口**: `GET /api/v1/modules/categories`

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

### 获取模块列表

获取模块列表，可按分类筛选。

**接口**: `GET /api/v1/modules`

**查询参数**:
| 参数 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| category | string | 否 | - | 按分类ID筛选 |
| search | string | 否 | - | 在模块名称/描述中搜索 |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

### 获取模块详情

获取特定模块的详细信息。

**接口**: `GET /api/v1/modules/{moduleId}`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| moduleId | string | 是 | 模块ID |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

**错误响应**:
- `404 Not Found`: 模块不存在

---

### 获取模块输入Schema

获取模块的输入UI Schema。

**接口**: `GET /api/v1/modules/{moduleId}/input-schema`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| moduleId | string | 是 | 模块ID |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

## 魔法变量

获取工作流可用的魔法变量引用。魔法变量允许你引用工作流中前序步骤的输出。

**接口**: `GET /api/v1/workflows/{workflowId}/magic-variables`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| workflowId | string | 是 | 工作流UUID |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

## 文件夹

### 获取文件夹列表

获取所有文件夹。

**接口**: `GET /api/v1/folders`

**查询参数**:
| 参数 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| parentId | string | 否 | - | 按父文件夹ID筛选 |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "folders": [
      {
        "id": "folder-001",
        "name": "我的工作流",
        "parentId": null,
        "order": 0,
        "workflowCount": 15,
        "subfolderCount": 2,
        "createdAt": 1704067200000,
        "modifiedAt": 1704067200000
      },
      {
        "id": "folder-002",
        "name": "自动化",
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

### 获取文件夹详情

获取文件夹的详细信息。

**接口**: `GET /api/v1/folders/{folderId}`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| folderId | string | 是 | 文件夹UUID |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "id": "folder-001",
    "name": "我的工作流",
    "parentId": null,
    "order": 0,
    "workflowCount": 15,
    "subfolderCount": 2,
    "workflows": [
      {
        "id": "wf-001",
        "name": "工作流1",
        "isEnabled": true,
        "order": 0
      }
    ],
    "subfolders": [
      {
        "id": "folder-002",
        "name": "自动化",
        "order": 0
      }
    ],
    "createdAt": 1704067200000,
    "modifiedAt": 1704067200000
  }
}
```

**错误响应**:
- `404 Not Found`: 文件夹不存在

---

### 创建文件夹

创建新文件夹。

**接口**: `POST /api/v1/folders`

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**:
```json
{
  "name": "新建文件夹",
  "parentId": "folder-001",
  "order": 0
}
```

**响应** (201 Created):
```json
{
  "code": 0,
  "message": "Folder created successfully",
  "data": {
    "id": "folder-003",
    "name": "新建文件夹",
    "createdAt": 1704067200000
  }
}
```

**错误响应**:
- `400 Bad Request`: 无效的文件夹数据
- `404 Not Found`: 父文件夹不存在
- `409 Conflict`: 同名文件夹已存在

---

### 更新文件夹

更新现有文件夹。

**接口**: `PUT /api/v1/folders/{folderId}`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| folderId | string | 是 | 文件夹UUID |

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体** (所有字段可选):
```json
{
  "name": "更新后的文件夹名称",
  "parentId": "folder-002",
  "order": 5
}
```

**响应** (200 OK):
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

**错误响应**:
- `400 Bad Request`: 无效的文件夹数据
- `404 Not Found`: 文件夹不存在

---

### 删除文件夹

删除文件夹。

**接口**: `DELETE /api/v1/folders/{folderId}`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| folderId | string | 是 | 文件夹UUID |

**查询参数**:
| 参数 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| deleteWorkflows | boolean | 否 | false | 删除文件夹中的工作流 |
| moveWorkflowsTo | string | 否 | - | 将工作流移动到此文件夹ID |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

**错误响应**:
- `400 Bad Request`: 文件夹非空且未指定操作
- `404 Not Found`: 文件夹不存在

---

## 导入/导出

### 导出工作流

导出工作流。

**接口**: `GET /api/v1/workflows/{workflowId}/export`

**路径参数**:
| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| workflowId | string | 是 | 工作流UUID |

**查询参数**:
| 参数 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| format | string | 否 | json | 导出格式：json, zip |
| includeSteps | boolean | 否 | true | 包含工作流步骤 |

**请求头**:
```
Authorization: Bearer <token>
```

**JSON格式响应** (200 OK):
```json
{
  "code": 0,
  "message": "success",
  "data": {
    "workflow": {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "name": "我的工作流",
      "description": "工作流描述",
      "version": "1.0.0",
      "steps": []
    },
    "exportedAt": 1704067200000,
    "format": "json"
  }
}
```

**ZIP格式响应** (200 OK):
```
Content-Type: application/zip
Content-Disposition: attachment; filename="workflow-name.zip"

<binary zip data>
```

---

### 导入工作流

导入工作流。

**接口**: `POST /api/v1/workflows/import`

**请求头**:
```
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求体** (multipart/form-data):
| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| file | File | 是 | 工作流文件（JSON或ZIP） |
| override | boolean | 否 | 覆盖现有工作流 |
| folderId | string | 否 | 目标文件夹ID |

**响应** (200 OK):
```json
{
  "code": 0,
  "message": "Workflow imported successfully",
  "data": {
    "imported": [
      {
        "workflowId": "550e8400-e29b-41d4-a716-446655440000",
        "name": "导入的工作流"
      }
    ],
    "skipped": [],
    "errors": [],
    "total": 1
  }
}
```

**错误响应**:
- `400 Bad Request`: 无效的文件格式
- `413 Payload Too Large`: 文件过大

---

### 批量导出

导出多个工作流。

**接口**: `POST /api/v1/workflows/export-batch`

**请求头**:
```
Authorization: Bearer <token>
Content-Type: application/json
```

**请求体**:
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

**响应** (200 OK):
```
Content-Type: application/zip
Content-Disposition: attachment; filename="workflows-export.zip"

<binary zip data>
```

---

### 批量导入

导入多个工作流。

**接口**: `POST /api/v1/workflows/import-batch`

**请求头**:
```
Authorization: Bearer <token>
Content-Type: multipart/form-data
```

**请求体** (multipart/form-data):
| 字段 | 类型 | 必填 | 描述 |
|------|------|------|------|
| file | File | 是 | 包含工作流的ZIP文件 |
| override | boolean | 否 | 覆盖现有工作流 |
| folderId | string | 否 | 目标文件夹ID |

**响应** (200 OK):
```json
{
  "code": 0,
  "message": "Batch import completed",
  "data": {
    "imported": [
      {
        "workflowId": "wf-001",
        "name": "工作流1"
      },
      {
        "workflowId": "wf-002",
        "name": "工作流2"
      }
    ],
    "skipped": [
      {
        "name": "工作流3",
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

## 系统

### 获取设备信息

获取设备信息。

**接口**: `GET /api/v1/system/info`

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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

### 获取系统统计

获取系统统计信息。

**接口**: `GET /api/v1/system/stats`

**查询参数**:
| 参数 | 类型 | 必填 | 默认值 | 描述 |
|------|------|------|--------|------|
| period | string | 否 | today | 时间段：today, week, month, all |

**请求头**:
```
Authorization: Bearer <token>
```

**响应** (200 OK):
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
        "name": "自动回复",
        "executionCount": 100
      }
    ]
  }
}
```

---

### 健康检查

检查API服务器健康状态。

**接口**: `GET /api/v1/system/health`

**响应** (200 OK):
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

## 限流

API实现了限流以防止滥用：

| 接口类型 | 限制 | 时间窗口 |
|----------|------|----------|
| 认证 | 5次请求 | 1分钟 |
| 工作流执行 | 10次请求 | 1分钟 |
| 查询操作 | 100次请求 | 1分钟 |
| 修改操作 | 60次请求 | 1分钟 |

**限流响应** (429 Too Many Requests):
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

**响应头**:
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1704067260000
```

---

## 最佳实践

### 1. 认证
- 始终在请求头中包含Bearer令牌
- 在令牌过期前刷新
- 在客户端安全存储令牌

### 2. 错误处理
- 始终检查响应中的`code`字段
- 使用指数退避优雅处理限流错误
- 根据错误码显示用户友好的错误消息

### 3. 性能
- 对大数据集使用分页
- 只请求必要的字段

### 4. 工作流执行
- 对长时间运行的工作流使用`async: true`
- 通过GET /api/v1/executions/{id}轮询执行状态
- 实现适当的超时处理

### 5. 导入/导出
- 对多个工作流使用批量操作
- 压缩大型工作流集合
- 导入前验证工作流数据

---

## 更新日志

### Version 1.0.0 (2025-02-26)
- 初始API发布
- 工作流CRUD操作
- 异步工作流执行
- 模块定义和Schema
- 工作流步骤的魔法变量
- 文件夹组织
- 导入/导出功能
- 系统信息和统计
- 认证和限流

---

## 支持

如需API支持和建议：
- 文档：https://chaomixian.github.io/vFlow-Docs/
- 问题反馈：https://github.com/ChaoMixian/vFlow/issues
