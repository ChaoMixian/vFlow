# vFlow API 测试工具使用指南

## 📁 文件说明

`scripts/` 目录包含以下测试工具：

### 1. `test_api.py` - 完整API测试套件
自动化测试所有API端点，生成测试报告。

**功能**:
- ✅ 测试20+个API端点
- ✅ 自动生成测试报告
- ✅ 支持分类测试（health/workflows/executions/modules）
- ✅ 自动清理测试数据

**用法**:
```bash
# 完整测试
python scripts/test_api.py --url http://192.168.1.100:8080 --token YOUR_TOKEN

# 分类测试
python scripts/test_api.py --url http://192.168.1.100:8080 --token YOUR_TOKEN --test health
python scripts/test_api.py --url http://192.168.1.100:8080 --token YOUR_TOKEN --test workflows
python scripts/test_api.py --url http://192.168.1.100:8080 --token YOUR_TOKEN --test executions
python scripts/test_api.py --url http://192.168.1.100:8080 --token YOUR_TOKEN --test modules
```

### 2. `examples.py` - API使用示例
展示常用API操作的示例代码。

**功能**:
- ✅ 8个实用示例
- ✅ 完整的代码示例
- ✅ 可以作为代码模板使用

**用法**:
```bash
# 运行所有示例
python scripts/examples.py --url http://192.168.1.100:8080 --token YOUR_TOKEN

# 运行特定示例
python scripts/examples.py --url http://192.168.1.100:8080 --token YOUR_TOKEN --example 1
python scripts/examples.py --url http://192.168.1.100:8080 --token YOUR_TOKEN --example 2
```

**示例列表**:
1. 获取所有工作流
2. 获取工作流详情
3. 创建新工作流
4. 执行工作流
5. 检查执行状态
6. 获取所有模块
7. 获取系统信息
8. 搜索工作流

### 3. `quick_test.sh` - Linux/Mac快速测试
一键快速验证API是否正常工作。

**用法**:
```bash
# 设置环境变量
export API_URL=http://192.168.1.100:8080
export API_TOKEN=your-token-here

# 运行测试
./scripts/quick_test.sh

# 或一行命令
API_URL=http://192.168.1.100:8080 API_TOKEN=your-token ./scripts/quick_test.sh
```

### 4. `quick_test.bat` - Windows快速测试
Windows批处理版本。

**用法**:
```cmd
REM 设置环境变量
set API_URL=http://192.168.1.100:8080
set API_TOKEN=your-token-here

REM 运行测试
quick_test.bat
```

## 🚀 快速开始

### 步骤1: 获取访问令牌

1. 在vFlow应用中：**设置** → **远程API**
2. 点击**生成令牌**
3. 输入设备ID（例如：`python-tester`）
4. **立即复制** Token（只显示一次！）

### 步骤2: 运行测试

**Linux/Mac**:
```bash
export API_URL=http://192.168.1.100:8080
export API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
./scripts/quick_test.sh
```

**Windows**:
```cmd
set API_URL=http://192.168.1.100:8080
set API_TOKEN=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
quick_test.bat
```

**Python (跨平台)**:
```bash
python scripts/test_api.py --url http://192.168.1.100:8080 --token YOUR_TOKEN
```

### 步骤3: 查看示例代码

```bash
python scripts/examples.py --url http://192.168.1.100:8080 --token YOUR_TOKEN
```

## 📝 示例代码

### Python示例：获取并执行工作流

```python
import requests

BASE_URL = "http://192.168.1.100:8080"
TOKEN = "your-token-here"
HEADERS = {
    "Authorization": f"Bearer {TOKEN}",
    "Content-Type": "application/json"
}

# 1. 获取所有工作流
response = requests.get(
    f"{BASE_URL}/api/v1/workflows",
    headers=HEADERS
)
workflows = response.json()['data']['workflows']

# 2. 选择第一个工作流
workflow_id = workflows[0]['id']

# 3. 执行工作流
response = requests.post(
    f"{BASE_URL}/api/v1/workflows/{workflow_id}/execute",
    headers=HEADERS,
    json={"async": True}
)
execution_id = response.json()['data']['executionId']

# 4. 获取执行状态
response = requests.get(
    f"{BASE_URL}/api/v1/executions/{execution_id}",
    headers=HEADERS
)
status = response.json()['data']['status']

print(f"工作流执行状态: {status}")
```

### cURL示例：创建工作流

```bash
curl -X POST "http://192.168.1.100:8080/api/v1/workflows" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "My Workflow",
    "description": "Test workflow",
    "steps": [],
    "isEnabled": false
  }'
```

### JavaScript示例：获取工作流列表

```javascript
const BASE_URL = 'http://192.168.1.100:8080';
const TOKEN = 'your-token-here';

fetch(`${BASE_URL}/api/v1/workflows`, {
  headers: {
    'Authorization': `Bearer ${TOKEN}`,
    'Content-Type': 'application/json'
  }
})
  .then(response => response.json())
  .then(data => {
    if (data.code === 0) {
      console.log(`找到 ${data.data.workflows.length} 个工作流`);
      data.data.workflows.forEach(wf => {
        console.log(`  • ${wf.name} (${wf.id})`);
      });
    }
  });
```

## 🧪 高级用法

### 自定义测试脚本

```python
from scripts.test_api import VFlowAPIClient

# 创建客户端
client = VFlowAPIClient(
    base_url="http://192.168.1.100:8080",
    token="your-token"
)

# 获取工作流
workflows = client.get('/api/v1/workflows')['data']['workflows']

# 筛选启用的Trigger工作流
triggers = [wf for wf in workflows if wf['isEnabled']]
print(f"找到 {len(triggers)} 个触发器工作流")

# 批量导出
for wf in triggers[:5]:  # 只导出前5个
    export = client.get(f"/api/v1/workflows/{wf['id']}/export")
    print(f"导出: {wf['name']}")
```

### 性能测试

```python
import time

client = VFlowAPIClient(url, token)

# 测试响应时间
start = time.time()
response = client.get('/api/v1/workflows')
duration = time.time() - start

print(f"响应时间: {duration*1000:.2f}ms")

# 并发测试
import threading

def test_worker():
    client = VFlowAPIClient(url, token)
    return client.get('/api/v1/system/health')

threads = []
start = time.time()
for i in range(10):
    t = threading.Thread(target=test_worker)
    threads.append(t)
    t.start()

for t in threads:
    t.join()

duration = time.time() - start
print(f"10个并发请求耗时: {duration:.2f}秒")
```

### 错误处理

```python
from scripts.test_api import VFlowAPIClient

client = VFlowAPIClient(url, token)

# 获取工作流（带错误处理）
response = client.get('/api/v1/workflows')

if response and response.get('code') == 0:
    workflows = response['data']['workflows']
    print(f"成功获取 {len(workflows)} 个工作流")
else:
    error = response.get('message', 'Unknown error')
    print(f"获取失败: {error}")
```

## 🐛 故障排查

### 问题1: 连接被拒绝

**错误**: `Connection refused`

**解决方案**:
1. 检查API服务器是否已启动（在设置中查看）
2. 检查端口号是否正确（默认8080）
3. 检查防火墙设置

### 问题2: 401 Unauthorized

**错误**: `401 Unauthorized`

**解决方案**:
1. Token可能已过期（有效期1小时）
2. 重新生成Token
3. 检查Token是否完整复制

### 问题3: ClassNotFoundException

**错误**: 启动API设置Activity崩溃

**解决方案**:
1. 重新安装APK（已修复）
2. 确保使用最新版本

### 问题4: 模块未找到

**错误**: `404 Not Found`

**解决方案**:
1. 检查API路径是否正确
2. 确认API版本为v1
3. 查看API文档确认端点存在

## 📚 更多资源

- **完整API文档**: `docs/api/API.md`

## 🤝 贡献

如果发现问题或有改进建议，欢迎提交Issue或Pull Request！
