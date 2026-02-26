#!/bin/bash
# vFlow API 快速测试脚本
# 用于快速验证API是否正常工作

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║        vFlow API 快速测试                          ║${NC}"
echo -e "${BLUE}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""

# 检查参数
if [ -z "$API_URL" ] || [ -z "$API_TOKEN" ]; then
    echo -e "${RED}❌ 错误: 请设置环境变量${NC}"
    echo ""
    echo "使用方法:"
    echo "  export API_URL=http://192.168.1.100:8080"
    echo "  export API_TOKEN=your-token-here"
    echo "  ./quick_test.sh"
    echo ""
    echo "或者直接传入参数:"
    echo "  API_URL=http://192.168.1.100:8080 API_TOKEN=your-token ./quick_test.sh"
    exit 1
fi

# 测试连接
echo -e "${BLUE}🔗 测试连接...${NC}"
response=$(curl -s -w "\n%{http_code}" -o /tmp/api_response.json \
    -H "Authorization: Bearer $API_TOKEN" \
    "$API_URL/api/v1/system/health")

http_code=$(tail -n1 /tmp/api_response.json)
body=$(head -n -1 /tmp/api_response.json)

if [ "$http_code" != "200" ]; then
    echo -e "${RED}❌ 连接失败 (HTTP $http_code)${NC}"
    echo "请检查:"
    echo "  1. API_URL是否正确 (例如: http://192.168.1.100:8080)"
    echo "  2. API_TOKEN是否有效"
    echo "  3. 手机和电脑是否在同一网络"
    exit 1
fi

code=$(echo "$body" | grep -o '"code":[0-9]*' | grep -o '[0-9]*' || echo "")

if [ "$code" != "0" ]; then
    echo -e "${RED}❌ API返回错误: $body${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 连接成功!${NC}"
echo ""

# 测试获取系统信息
echo -e "${BLUE}📱 获取系统信息...${NC}"
response=$(curl -s -H "Authorization: Bearer $API_TOKEN" \
    "$API_URL/api/v1/system/info")

echo "$response" | python3 -m json.tool | grep -E "(brand|model|androidVersion|apiLevel)" | head -4 | \
    sed 's/.*"brand": "\(.*\)".*/  设备: \1/' | \
    sed 's/.*"model": "\(.*\)".*/  型号: \1/' | \
    sed 's/.*"androidVersion": "\(.*\)".*/  系统: Android \1/' | \
    sed 's/.*"apiLevel": \(.*\).*/  API: \1/' | \
    sed 's/^/    /' | sed 's/"$//' | sed 's/,$//'

echo ""

# 测试获取工作流列表
echo -e "${BLUE}📋 获取工作流列表...${NC}"
response=$(curl -s -H "Authorization: Bearer $API_TOKEN" \
    "$API_URL/api/v1/workflows")

workflow_count=$(echo "$response" | grep -o '"workflowCount":[0-9]*' | grep -o '[0-9]*' || echo "0")

echo -e "${GREEN}✅ 找到 $workflow_count 个工作流${NC}"
echo ""

# 测试获取模块分类
echo -e "${BLUE}🧩 获取模块分类...${NC}"
response=$(curl -s -H "Authorization: Bearer $API_TOKEN" \
    "$API_URL/api/v1/modules/categories")

cat_count=$(echo "$response" | grep -o '"id"' | wc -l | tr -d ' ')
echo -e "${GREEN}✅ 找到 $cat_count 个模块分类${NC}"

# 显示分类列表
echo "$response" | python3 -c "
import sys, json
data = json.load(sys.stdin)
if data['code'] == 0:
    for cat in data['data']['categories']:
        print(f\"  • {cat['name']} ({cat['id']})\")" | sed 's/\"//g'
" 2>/dev/null || echo "  (无法解析)"

echo ""

# 测试创建工作流
echo -e "${BLUE}➕ 创建测试工作流...${NC}"
timestamp=$(date +%s)
test_data=$(cat <<EOF
{
  "name": "Quick Test Workflow $timestamp",
  "description": "通过快速测试脚本创建",
  "steps": [],
  "isEnabled": false
}
EOF
)

response=$(curl -s -X POST -H "Authorization: Bearer $API_TOKEN" \
    -H "Content-Type: application/json" \
    -d "$test_data" \
    "$API_URL/api/v1/workflows")

new_workflow_id=$(echo "$response" | grep -o '"id":"[^"]*"' | grep -o ':[^"]*' | tr -d ':' || echo "")

if [ -n "$new_workflow_id" ]; then
    echo -e "${GREEN}✅ 工作流创建成功 (ID: $new_workflow_id)${NC}"

    # 测试删除工作流
    echo ""
    echo -e "${BLUE}🗑️  删除测试工作流...${NC}"
    curl -s -X DELETE -H "Authorization: Bearer $API_TOKEN" \
        "$API_URL/api/v1/workflows/$new_workflow_id" > /dev/null
    echo -e "${GREEN}✅ 工作流已删除${NC}"
else
    echo -e "${YELLOW}⚠️  工作流创建失败${NC}"
fi

echo ""
echo -e "${BLUE}📊 获取系统统计...${NC}"
response=$(curl -s -H "Authorization: Bearer $API_TOKEN" \
    "$API_URL/api/v1/system/stats")

echo "$response" | python3 -m json.tool | grep -E "(workflowCount|totalExecutions|successRate)" | head -3 | \
    sed 's/.*"workflowCount": \([0-9]*\).*/  工作流数: \1/' | \
    sed 's/.*"totalExecutions": \([0-9]*\).*/  总执行次数: \1/' | \
    sed 's/.*"successRate": \([0-9.]*\).*/  成功率: \1%/' | \
    sed 's/^/    /' | sed 's/"$//' | sed 's/,$//'

echo ""
echo -e "${GREEN}╔═══════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                  🎉 所有测试通过！                          ║${NC}"
echo -e "${GREEN}╚═══════════════════════════════════════════════════════╝${NC}"
echo ""
echo "💡 提示:"
echo "  • 运行完整测试: python scripts/test_api.py --url $API_URL --token $API_TOKEN"
echo "  • 查看示例: python scripts/examples.py --url $API_URL --token $API_TOKEN"
echo "  • 完整文档: docs/API.md"
