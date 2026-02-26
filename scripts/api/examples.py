#!/usr/bin/env python3
"""
vFlow API 快速示例脚本
展示常用的API操作

用法：
    python examples.py --url http://192.168.1.100:8080 --token YOUR_TOKEN
"""

import argparse
import json
import requests
import sys
from typing import Dict, Any


class VFlowExamples:
    """vFlow API示例"""

    def __init__(self, base_url: str, token: str):
        self.base_url = base_url.rstrip('/')
        self.token = token
        self.headers = {
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json'
        }

    def _print(self, title: str):
        """打印标题"""
        print(f"\n{'='*70}")
        print(f"  {title}")
        print('='*70)

    def example_1_list_workflows(self):
        """示例1: 获取所有工作流"""
        self._print("示例1: 获取所有工作流")

        response = requests.get(
            f"{self.base_url}/api/v1/workflows",
            headers=self.headers
        )
        data = response.json()

        if data['code'] == 0:
            workflows = data['data']['workflows']
            print(f"✅ 找到 {len(workflows)} 个工作流:\n")
            for wf in workflows[:5]:  # 只显示前5个
                print(f"  • {wf['name']}")
                print(f"    ID: {wf['id']}")
                print(f"    描述: {wf.get('description', '无')}")
                print(f"    启用: {'是' if wf['isEnabled'] else '否'}")
                print(f"    步骤数: {wf['stepCount']}")
                print()
            if len(workflows) > 5:
                print(f"  ... 还有 {len(workflows) - 5} 个工作流")
        else:
            print(f"❌ 错误: {data['message']}")

    def example_2_get_workflow_detail(self, workflow_id: str):
        """示例2: 获取工作流详情"""
        self._print(f"示例2: 获取工作流详情 (ID: {workflow_id})")

        response = requests.get(
            f"{self.base_url}/api/v1/workflows/{workflow_id}",
            headers=self.headers
        )
        data = response.json()

        if data['code'] == 0:
            workflow = data['data']
            print(f"✅ 工作流详情:\n")
            print(f"  名称: {workflow['name']}")
            print(f"  描述: {workflow.get('description', '无')}")
            print(f"  状态: {'启用' if workflow['isEnabled'] else '禁用'}")
            print(f"  版本: {workflow.get('version', '1.0.0')}")
            print(f"  最后修改: {workflow.get('modifiedAt', 0)}")

            # 显示工作流步骤/流程
            steps = workflow.get('steps', [])
            print(f"\n  📋 流程步骤 ({len(steps)}个):\n")
            for i, step in enumerate(steps):
                module_id = step.get('moduleId', 'unknown')
                params = step.get('parameters', {})
                indent = step.get('indentationLevel', 0)

                # 缩进显示层级
                indent_str = "    " * indent
                print(f"  {indent_str}步骤 {i+1}: {module_id}")

                # 显示关键参数
                if params:
                    # 只显示前几个参数
                    param_keys = list(params.keys())[:3]
                    for key in param_keys:
                        val = params[key]
                        # 截断太长的值
                        val_str = str(val)[:50] + "..." if len(str(val)) > 50 else str(val)
                        print(f"  {indent_str}  - {key}: {val_str}")
                    if len(params) > 3:
                        print(f"  {indent_str}  ... 还有 {len(params)-3} 个参数")

            # 显示标签
            tags = workflow.get('tags', [])
            if tags:
                print(f"\n  🏷️ 标签: {', '.join(tags)}")

            return workflow
        else:
            print(f"❌ 错误: {data['message']}")
            return None

    def example_3_create_workflow(self):
        """示例3: 创建新工作流"""
        self._print("示例3: 创建新工作流")

        # 创建包含实际步骤的工作流
        workflow_data = {
            "name": "Python测试工作流",
            "description": "通过Python API创建的测试工作流",
            "isEnabled": False,
            "steps": [
                {
                    "id": "step-1",
                    "moduleId": "vflow.trigger.manual",
                    "parameters": {},
                    "indentationLevel": 0
                },
                {
                    "id": "step-2",
                    "moduleId": "vflow.device.click",
                    "parameters": {
                        "target": "200,200"
                    },
                    "indentationLevel": 0
                },
                {
                    "id": "step-3",
                    "moduleId": "vflow.interaction.input_text",
                    "parameters": {
                        "text": "Hello vFlow"
                    },
                    "indentationLevel": 0
                }
            ]
        }

        response = requests.post(
            f"{self.base_url}/api/v1/workflows",
            headers=self.headers,
            json=workflow_data
        )
        data = response.json()

        if data['code'] == 0:
            workflow_id = data['data']['id']
            print(f"✅ 工作流创建成功!")
            print(f"  ID: {workflow_id}")
            print(f"  步骤数: {len(workflow_data['steps'])}")
            print(f"  步骤1: 手动触发器 (vflow.trigger.manual)")
            print(f"  步骤2: 点击元素 (vflow.device.click)")
            print(f"  步骤3: 输入文本 (vflow.interaction.input_text)")
            return workflow_id
        else:
            print(f"❌ 创建失败: {data['message']}")
            return None

    def example_4_execute_workflow(self, workflow_id: str):
        """示例4: 执行工作流"""
        self._print(f"示例4: 执行工作流 (ID: {workflow_id})")

        execute_data = {
            "async": True
        }

        response = requests.post(
            f"{self.base_url}/api/v1/workflows/{workflow_id}/execute",
            headers=self.headers,
            json=execute_data
        )
        data = response.json()

        if data['code'] == 0:
            exec_data = data['data']
            print(f"✅ 工作流开始执行!")
            print(f"  执行ID: {exec_data['execution_id']}")
            print(f"  状态: {exec_data['status']}")
            return exec_data['execution_id']
        else:
            print(f"❌ 执行失败: {data['message']}")
            return None

    def example_5_check_execution_status(self, execution_id: str):
        """示例5: 检查执行状态"""
        self._print(f"示例5: 检查执行状态 (ID: {execution_id})")

        import time
        time.sleep(1)  # 等待一秒

        response = requests.get(
            f"{self.base_url}/api/v1/executions/{execution_id}",
            headers=self.headers
        )
        data = response.json()

        if data['code'] == 0:
            exec_data = data['data']
            print(f"✅ 执行状态:")
            print(f"  状态: {exec_data['status']}")
            print(f"  当前步骤: {exec_data.get('currentStepIndex', 0)}/{exec_data.get('totalSteps', 0)}")
            print(f"  开始时间: {exec_data.get('startedAt', 0)}")
            if exec_data.get('completedAt'):
                print(f"  完成时间: {exec_data['completedAt']}")
                print(f"  耗时: {exec_data.get('duration', 0)}ms")
        else:
            print(f"❌ 错误: {data['message']}")

    def example_6_list_modules(self):
        """示例6: 获取所有模块"""
        self._print("示例6: 获取所有模块")

        response = requests.get(
            f"{self.base_url}/api/v1/modules",
            headers=self.headers
        )
        data = response.json()

        if data['code'] == 0:
            modules = data['data']['modules']
            print(f"✅ 找到 {len(modules)} 个模块\n")

            # 按分类统计
            categories = {}
            for module in modules:
                cat = module['metadata']['category']
                categories[cat] = categories.get(cat, 0) + 1

            for cat, count in categories.items():
                print(f"  {cat}: {count}个模块")
        else:
            print(f"❌ 错误: {data['message']}")

    def example_6b_get_module_detail(self, module_id: str):
        """示例6b: 获取模块详情"""
        self._print(f"示例6b: 获取模块详情 (ID: {module_id})")

        response = requests.get(
            f"{self.base_url}/api/v1/modules/{module_id}",
            headers=self.headers
        )
        data = response.json()

        if data['code'] == 0:
            module = data['data']
            print(f"✅ 模块详情:\n")
            print(f"  ID: {module['id']}")
            print(f"  名称: {module['metadata']['name']}")
            print(f"  分类: {module['metadata']['category']}")
            print(f"  描述: {module['metadata']['description']}")
            print(f"  块类型: {module['blockBehavior']['blockType']}")

            # 显示输入参数
            inputs = module.get('inputs', [])
            print(f"\n  📥 输入参数 ({len(inputs)}个):")
            for inp in inputs:
                required = "必填" if inp.get('required', False) else "可选"
                print(f"    - {inp['id']} ({inp['type']}) [{required}]")
                print(f"      标签: {inp['label']}")
                if inp.get('options'):
                    print(f"      选项: {', '.join(inp['options'])}")

            # 显示输出参数
            outputs = module.get('outputs', [])
            print(f"\n  📤 输出参数 ({len(outputs)}个):")
            for out in outputs:
                print(f"    - {out['id']} ({out['type']})")
                print(f"      标签: {out['label']}")

            return module
        else:
            print(f"❌ 错误: {data['message']}")
            return None

    def example_6c_get_module_input_schema(self, module_id: str):
        """示例6c: 获取模块输入Schema (用于动态表单生成)"""
        self._print(f"示例6c: 获取模块输入Schema (ID: {module_id})")

        response = requests.get(
            f"{self.base_url}/api/v1/modules/{module_id}/input-schema",
            headers=self.headers
        )
        data = response.json()

        if data['code'] == 0:
            schema = data['data']['schema']
            print(f"✅ 模块输入Schema:\n")
            print(f"  字段数: {len(schema)}\n")

            for field in schema:
                print(f"  📝 {field['key']}")
                print(f"     类型: {field['type']}")
                print(f"     标签: {field['label']}")
                if field.get('required'):
                    print(f"     必填: 是")
                if field.get('options'):
                    print(f"     选项: {field['options']}")
                if field.get('defaultValue'):
                    print(f"     默认值: {field['defaultValue']}")
                if field.get('allowVariables'):
                    print(f"     允许变量: 是")
                print()

            return schema
        else:
            print(f"❌ 错误: {data['message']}")
            return None

    def example_7_get_system_info(self):
        """示例7: 获取系统信息"""
        self._print("示例7: 获取系统信息")

        response = requests.get(
            f"{self.base_url}/api/v1/system/info",
            headers=self.headers
        )
        data = response.json()

        if data['code'] == 0:
            sys_info = data['data']
            print(f"✅ 系统信息:\n")
            print(f"  设备: {sys_info['device']['brand']} {sys_info['device']['model']}")
            print(f"  Android: {sys_info['device']['androidVersion']} (API {sys_info['device']['apiLevel']})")
            print(f"  服务器版本: {sys_info['server']['version']}")
            print(f"  运行时间: {sys_info['server']['uptime']/1000/60:.1f}分钟")
        else:
            print(f"❌ 错误: {data['message']}")

    def example_8_search_workflow(self, keyword: str):
        """示例8: 搜索工作流"""
        self._print(f"示例8: 搜索工作流 (关键词: {keyword})")

        response = requests.get(
            f"{self.base_url}/api/v1/workflows",
            headers=self.headers,
            params={'search': keyword}
        )
        data = response.json()

        if data['code'] == 0:
            workflows = data['data']['workflows']
            print(f"✅ 找到 {len(workflows)} 个匹配的工作流:\n")
            for wf in workflows:
                print(f"  • {wf['name']} (ID: {wf['id']})")
        else:
            print(f"❌ 错误: {data['message']}")

    def example_9_get_magic_variables(self, workflow_id: str):
        """示例9: 获取工作流的魔法变量"""
        self._print(f"示例9: 获取工作流魔法变量 (ID: {workflow_id})")

        response = requests.get(
            f"{self.base_url}/api/v1/workflows/{workflow_id}/magic-variables",
            headers=self.headers
        )
        data = response.json()

        if data['code'] == 0:
            result = data['data']

            # 显示步骤变量
            magic_vars = result.get('magicVariables', [])
            print(f"✅ 魔法变量:\n")
            print(f"  📊 步骤输出变量 ({len(magic_vars)}个):")
            for var in magic_vars[:10]:
                print(f"    - {var['key']}")
                print(f"      标签: {var.get('label', 'N/A')}")
                print(f"      类型: {var.get('type', 'any')}")
                print(f"      步骤: {var.get('stepName', 'N/A')}")

            # 显示系统变量
            sys_vars = result.get('systemVariables', [])
            print(f"\n  ⚙️ 系统变量 ({len(sys_vars)}个):")
            for var in sys_vars:
                print(f"    - {var['key']}")
                print(f"      标签: {var.get('label', 'N/A')}")
                print(f"      描述: {var.get('description', 'N/A')}")
        else:
            print(f"❌ 错误: {data['message']}")


def main():
    parser = argparse.ArgumentParser(description='vFlow API使用示例')
    parser.add_argument('--url', required=True, help='API服务器地址')
    parser.add_argument('--token', required=True, help='访问令牌')
    parser.add_argument('--example', type=int, choices=range(1, 10),
                       help='运行特定示例 (1-9), 不指定则运行所有示例')

    args = parser.parse_args()

    examples = VFlowExamples(args.url, args.token)

    if args.example:
        # 运行特定示例
        example_map = {
            1: examples.example_1_list_workflows,
            2: lambda: examples.example_2_get_workflow_detail("test-id"),
            3: examples.example_3_create_workflow,
            4: lambda: examples.example_4_execute_workflow("test-id"),
            5: lambda: examples.example_5_check_execution_status("test-id"),
            6: examples.example_6_list_modules,
            7: examples.example_7_get_system_info,
            8: lambda: examples.example_8_search_workflow("test"),
            9: lambda: examples.example_9_get_magic_variables("test-id")
        }
        example_map[args.example]()
    else:
        # 运行所有示例
        examples.example_1_list_workflows()

        # 获取第一个工作流ID用于后续示例
        response = requests.get(
            f"{args.url}/api/v1/workflows",
            headers=examples.headers
        )
        data = response.json()
        if data['code'] == 0 and data['data']['workflows']:
            first_workflow_id = data['data']['workflows'][0]['id']

            examples.example_2_get_workflow_detail(first_workflow_id)
            examples.example_9_get_magic_variables(first_workflow_id)
            new_id = examples.example_3_create_workflow()
            examples.example_6_list_modules()

            # 获取一个复杂的模块（跳过触发器）
            module_response = requests.get(
                f"{args.url}/api/v1/modules",
                headers=examples.headers
            )
            module_data = module_response.json()
            if module_data['code'] == 0 and module_data['data']['modules']:
                modules = module_data['data']['modules']
                first_module_id = None

                # 跳过触发器模块
                for m in modules:
                    cat = m['metadata']['category']
                    if '触发器' not in cat:
                        first_module_id = m['id']
                        break

                # 再没有就用第一个
                if not first_module_id and modules:
                    first_module_id = modules[0]['id']

                if first_module_id:
                    print(f"\n📌 使用示例模块: {first_module_id}")
                    examples.example_6b_get_module_detail(first_module_id)
                    examples.example_6c_get_module_input_schema(first_module_id)

            examples.example_7_get_system_info()

            # 如果创建了新工作流，执行它
            if new_id:
                exec_id = examples.example_4_execute_workflow(new_id)
                if exec_id:
                    examples.example_5_check_execution_status(exec_id)
        else:
            print("⚠️  没有可用的工作流，跳过需要工作流ID的示例")


if __name__ == '__main__':
    main()
