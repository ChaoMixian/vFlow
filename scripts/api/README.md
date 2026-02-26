# vFlow API 测试脚本

## 🚀 快速开始

### 1. 获取Token
在vFlow应用中：**设置** → **远程API** → **生成令牌**

### 2. 选择测试工具

| 工具 | 描述 | 适用平台 |
|------|------|----------|
| `quick_test.sh` | 快速测试 (推荐) | Linux/Mac |
| `quick_test.bat` | 快速测试 | Windows |
| `test_api.py` | 完整API测试 | 所有平台 |
| `examples.py` | API使用示例 | 所有平台 |

### 3. 运行测试

**Linux/Mac**:
```bash
export API_URL=http://YOUR_PHONE_IP:8080
export API_TOKEN=your-token
./quick_test.sh
```

**Windows**:
```cmd
set API_URL=http://YOUR_PHONE_IP:8080
set API_TOKEN=your-token
quick_test.bat
```

**Python** (跨平台):
```bash
pip install requests
python test_api.py --url $API_URL --token $API_TOKEN
```

## 📖 详细文档

- **中文指南**: [README_CN.md](README_CN.md)
- **测试脚本**: [test_api.py](test_api.py)
- **示例代码**: [examples.py](examples.py)

## 📋 测试覆盖

- ✅ 健康检查
- ✅ 系统信息
- ✅ 工作流管理（CRUD）
- ✅ 工作流执行
- ✅ 执行日志
- ✅ 模块查询
- ✅ 变量管理
- ✅ 文件夹操作
- ✅ 导入导出

## 🔧 依赖安装

```bash
pip install requests websocket-client
```
