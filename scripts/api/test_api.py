#!/usr/bin/env python3
"""
vFlow API 测试脚本
测试所有远程API端点

用法：
    python test_api.py --url http://192.168.1.100:8080 --token YOUR_TOKEN

依赖：
    pip install requests websocket-client
"""

import argparse
import json
import requests
import time
import sys
from typing import Optional, Dict, Any, List
from datetime import datetime


class VFlowAPIClient:
    """vFlow API客户端"""

    def __init__(self, base_url: str, token: str):
        self.base_url = base_url.rstrip('/')
        self.token = token
        self.session = requests.Session()
        self.session.headers.update({
            'Authorization': f'Bearer {token}',
            'Content-Type': 'application/json'
        })

    def _request(self, method: str, path: str, **kwargs) -> Dict[str, Any]:
        """发送HTTP请求"""
        url = f"{self.base_url}{path}"
        try:
            response = self.session.request(method, url, **kwargs)
            response.raise_for_status()
            return response.json()
        except requests.exceptions.RequestException as e:
            print(f"❌ 请求失败: {e}")
            if hasattr(e.response, 'text'):
                print(f"   响应: {e.response.text}")
            return None

    def get(self, path: str, params: Dict = None) -> Dict[str, Any]:
        """GET请求"""
        return self._request('GET', path, params=params)

    def post(self, path: str, data: Dict = None) -> Dict[str, Any]:
        """POST请求"""
        return self._request('POST', path, json=data)

    def put(self, path: str, data: Dict = None) -> Dict[str, Any]:
        """PUT请求"""
        return self._request('PUT', path, json=data)

    def delete(self, path: str) -> Dict[str, Any]:
        """DELETE请求"""
        return self._request('DELETE', path)

    def check_success(self, response: Dict[str, Any]) -> bool:
        """检查响应是否成功"""
        if response is None:
            return False
        return response.get('code') == 0


class APITester:
    """API测试器"""

    def __init__(self, client: VFlowAPIClient):
        self.client = client
        self.test_results = []
        self.workflow_id = None
        self.execution_id = None

    def log_test(self, name: str, success: bool, details: str = ""):
        """记录测试结果"""
        status = "✅ PASS" if success else "❌ FAIL"
        self.test_results.append({
            'name': name,
            'success': success,
            'details': details
        })
        print(f"{status} - {name}")
        if details:
            print(f"     {details}")

    def test_health_check(self):
        """测试健康检查"""
        print("\n" + "="*60)
        print("🏥 测试健康检查")
        print("="*60)
        response = self.client.get('/api/v1/system/health')
        if self.client.check_success(response):
            data = response.get('data', {})
            self.log_test("健康检查", True, f"状态: {data.get('status')}, 版本: {data.get('version')}")
        else:
            self.log_test("健康检查", False)

    def test_system_info(self):
        """测试系统信息"""
        print("\n" + "="*60)
        print("📱 测试系统信息")
        print("="*60)
        response = self.client.get('/api/v1/system/info')
        if self.client.check_success(response):
            data = response.get('data', {})
            device = data.get('device', {})
            self.log_test("获取系统信息", True,
                f"设备: {device.get('brand')} {device.get('model')}, "
                f"Android {device.get('androidVersion')}")
        else:
            self.log_test("获取系统信息", False)

    def test_system_stats(self):
        """测试系统统计"""
        print("\n" + "="*60)
        print("📊 测试系统统计")
        print("="*60)
        response = self.client.get('/api/v1/system/stats')
        if self.client.check_success(response):
            data = response.get('data', {})
            self.log_test("获取系统统计", True,
                f"工作流: {data.get('workflowCount')}个, "
                f"执行: {data.get('totalExecutions')}次")
        else:
            self.log_test("获取系统统计", False)

    def test_list_workflows(self):
        """测试获取工作流列表"""
        print("\n" + "="*60)
        print("📋 测试工作流列表")
        print("="*60)
        response = self.client.get('/api/v1/workflows')
        if self.client.check_success(response):
            data = response.get('data', {})
            workflows = data.get('workflows', [])
            self.log_test("获取工作流列表", True, f"找到 {len(workflows)} 个工作流")
            if workflows:
                self.workflow_id = workflows[0]['id']
                print(f"     测试工作流ID: {self.workflow_id}")
        else:
            self.log_test("获取工作流列表", False)

    def test_get_workflow(self):
        """测试获取工作流详情"""
        if not self.workflow_id:
            self.log_test("获取工作流详情", False, "没有可用的工作流ID")
            return

        print("\n" + "="*60)
        print("📄 测试工作流详情")
        print("="*60)
        response = self.client.get(f'/api/v1/workflows/{self.workflow_id}')
        if self.client.check_success(response):
            data = response.get('data', {})
            self.log_test("获取工作流详情", True,
                f"工作流: {data.get('name')}, 步骤数: {len(data.get('steps', []))}")
        else:
            self.log_test("获取工作流详情", False)

    def test_create_workflow(self):
        """测试创建工作流"""
        print("\n" + "="*60)
        print("➕ 测试创建工作流")
        print("="*60)

        timestamp = int(time.time() * 1000)
        workflow_data = {
            "name": f"API测试工作流_{timestamp}",
            "description": "通过API创建的测试工作流",
            "steps": [],
            "isEnabled": False
        }

        response = self.client.post('/api/v1/workflows', data=workflow_data)
        if self.client.check_success(response):
            data = response.get('data', {})
            self.workflow_id = data.get('id')
            self.log_test("创建工作流", True, f"工作流ID: {self.workflow_id}")
        else:
            self.log_test("创建工作流", False)

    def test_update_workflow(self):
        """测试更新工作流"""
        if not self.workflow_id:
            self.log_test("更新工作流", False, "没有可用的工作流ID")
            return

        print("\n" + "="*60)
        print("✏️ 测试更新工作流")
        print("="*60)

        update_data = {
            "description": f"更新于 {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}"
        }

        response = self.client.put(f'/api/v1/workflows/{self.workflow_id}', data=update_data)
        if self.client.check_success(response):
            self.log_test("更新工作流", True)
        else:
            self.log_test("更新工作流", False)

    def test_duplicate_workflow(self):
        """测试复制工作流"""
        if not self.workflow_id:
            self.log_test("复制工作流", False, "没有可用的工作流ID")
            return

        print("\n" + "="*60)
        print("📋 测试复制工作流")
        print("="*60)

        response = self.client.post(f'/api/v1/workflows/{self.workflow_id}/duplicate')
        if self.client.check_success(response):
            data = response.get('data', {})
            self.log_test("复制工作流", True, f"新工作流ID: {data.get('newWorkflowId')}")
        else:
            self.log_test("复制工作流", False)

    def test_execute_workflow(self):
        """测试执行工作流"""
        if not self.workflow_id:
            self.log_test("执行工作流", False, "没有可用的工作流ID")
            return

        print("\n" + "="*60)
        print("▶️ 测试执行工作流")
        print("="*60)

        execute_data = {
            "async": True
        }

        response = self.client.post(f'/api/v1/workflows/{self.workflow_id}/execute', data=execute_data)
        if self.client.check_success(response):
            data = response.get('data', {})
            self.execution_id = data.get('execution_id')
            status = data.get('status')
            self.log_test("执行工作流", True, f"执行ID: {self.execution_id}, 状态: {status}")
        else:
            self.log_test("执行工作流", False)

    def test_get_execution_status(self):
        """测试获取执行状态"""
        if not self.execution_id:
            self.log_test("获取执行状态", False, "没有可用的执行ID")
            return

        print("\n" + "="*60)
        print("📊 测试执行状态")
        print("="*60)

        # 等待一下让执行完成
        time.sleep(1)

        response = self.client.get(f'/api/v1/executions/{self.execution_id}')
        if self.client.check_success(response):
            data = response.get('data', {})
            self.log_test("获取执行状态", True, f"状态: {data.get('status')}")
        else:
            self.log_test("获取执行状态", False)

    def test_get_execution_logs(self):
        """测试获取执行日志"""
        if not self.execution_id:
            self.log_test("获取执行日志", False, "没有可用的执行ID")
            return

        print("\n" + "="*60)
        print("📝 测试执行日志")
        print("="*60)

        response = self.client.get(f'/api/v1/executions/{self.execution_id}/logs')
        if self.client.check_success(response):
            data = response.get('data', {})
            logs = data.get('logs', [])
            self.log_test("获取执行日志", True, f"日志条数: {len(logs)}")
        else:
            self.log_test("获取执行日志", False)

    def test_list_executions(self):
        """测试获取执行列表"""
        print("\n" + "="*60)
        print("📜 测试执行列表")
        print("="*60)

        response = self.client.get('/api/v1/executions', params={'limit': 10})
        if self.client.check_success(response):
            data = response.get('data', {})
            executions = data.get('executions', [])
            self.log_test("获取执行列表", True, f"执行记录: {len(executions)}条")
        else:
            self.log_test("获取执行列表", False)

    def test_list_modules(self):
        """测试获取模块列表"""
        print("\n" + "="*60)
        print("🧩 测试模块列表")
        print("="*60)

        response = self.client.get('/api/v1/modules')
        if self.client.check_success(response):
            data = response.get('data', {})
            modules = data.get('modules', [])
            self.log_test("获取模块列表", True, f"模块数: {len(modules)}个")
        else:
            self.log_test("获取模块列表", False)

    def test_module_categories(self):
        """测试获取模块分类"""
        print("\n" + "="*60)
        print("📂 测试模块分类")
        print("="*60)

        response = self.client.get('/api/v1/modules/categories')
        if self.client.check_success(response):
            data = response.get('data', {})
            categories = data.get('categories', [])
            self.log_test("获取模块分类", True, f"分类数: {len(categories)}个")
            for cat in categories:
                print(f"     - {cat.get('name')} ({cat.get('id')})")
        else:
            self.log_test("获取模块分类", False)

    def test_get_folder_detail(self):
        """测试获取文件夹详情"""
        print("\n" + "="*60)
        print("📁 测试文件夹详情")
        print("="*60)

        # 先获取文件夹列表
        response = self.client.get('/api/v1/folders')
        if self.client.check_success(response):
            data = response.get('data', {})
            folders = data.get('folders', [])
            if folders:
                folder_id = folders[0]['id']
                # 获取文件夹详情
                detail_response = self.client.get(f'/api/v1/folders/{folder_id}')
                if self.client.check_success(detail_response):
                    detail = detail_response.get('data', {})
                    self.log_test("获取文件夹详情", True,
                        f"文件夹: {detail.get('name')}, 工作流: {detail.get('workflowCount')}个")
                else:
                    self.log_test("获取文件夹详情", False)
            else:
                self.log_test("获取文件夹详情", True, "没有文件夹，跳过测试")
        else:
            self.log_test("获取文件夹详情", False)

    def test_list_folders(self):
        """测试获取文件夹列表"""
        print("\n" + "="*60)
        print("📁 测试文件夹列表")
        print("="*60)

        response = self.client.get('/api/v1/folders')
        if self.client.check_success(response):
            data = response.get('data', {})
            folders = data.get('folders', [])
            self.log_test("获取文件夹列表", True, f"文件夹数: {len(folders)}个")
        else:
            self.log_test("获取文件夹列表", False)

    def test_export_workflow(self):
        """测试导出工作流"""
        if not self.workflow_id:
            self.log_test("导出工作流", False, "没有可用的工作流ID")
            return

        print("\n" + "="*60)
        print("📤 测试导出工作流")
        print("="*60)

        response = self.client.get(f'/api/v1/workflows/{self.workflow_id}/export')
        if self.client.check_success(response):
            self.log_test("导出工作流", True)
        else:
            self.log_test("导出工作流", False)

    def run_all_tests(self):
        """运行所有测试"""
        print("\n" + "🚀 "*60)
        print("vFlow API 测试开始")
        print("🚀 "*60)

        start_time = time.time()

        # 基础测试
        self.test_health_check()
        self.test_system_info()
        self.test_system_stats()

        # 工作流测试
        self.test_list_workflows()
        self.test_create_workflow()  # 这会设置workflow_id
        self.test_get_workflow()
        self.test_update_workflow()
        self.test_duplicate_workflow()

        # 执行测试
        self.test_execute_workflow()  # 这会设置execution_id
        self.test_get_execution_status()
        self.test_get_execution_logs()
        self.test_list_executions()

        # 模块测试
        self.test_module_categories()
        self.test_list_modules()

        # 其他测试
        self.test_list_folders()
        self.test_get_folder_detail()
        self.test_export_workflow()

        end_time = time.time()
        duration = end_time - start_time

        # 打印测试结果汇总
        self.print_summary(duration)

        # 清理：删除测试创建的工作流
        if self.workflow_id and "API测试工作流" in self.workflow_id:
            print("\n" + "="*60)
            print("🧹 清理测试数据")
            print("="*60)
            response = self.client.delete(f'/api/v1/workflows/{self.workflow_id}')
            if self.client.check_success(response):
                print("✅ 已删除测试工作流")
            else:
                print("❌ 删除测试工作流失败")

    def print_summary(self, duration: float):
        """打印测试结果汇总"""
        print("\n" + "="*60)
        print("📊 测试结果汇总")
        print("="*60)

        total = len(self.test_results)
        passed = sum(1 for r in self.test_results if r['success'])
        failed = total - passed

        print(f"总测试数: {total}")
        print(f"通过: {passed}")
        print(f"失败: {failed}")
        print(f"成功率: {passed/total*100:.1f}%")
        print(f"耗时: {duration:.2f}秒")

        if failed > 0:
            print("\n❌ 失败的测试:")
            for result in self.test_results:
                if not result['success']:
                    print(f"  • {result['name']}: {result['details']}")

        print("\n" + "✨"*30)
        if failed == 0:
            print("🎉 所有测试通过！")
        else:
            print(f"⚠️  {failed}个测试失败")
        print("✨"*30)


def main():
    parser = argparse.ArgumentParser(description='vFlow API测试脚本')
    parser.add_argument('--url', required=True, help='API服务器地址 (例如: http://192.168.1.100:8080)')
    parser.add_argument('--token', required=True, help='访问令牌')
    parser.add_argument('--test', choices=['all', 'health', 'workflows', 'executions', 'modules'],
                       default='all', help='要运行的测试 (默认: all)')

    args = parser.parse_args()

    # 创建客户端
    client = VFlowAPIClient(args.url, args.token)

    # 创建测试器
    tester = APITester(client)

    # 验证连接
    print("🔗 连接到服务器...")
    response = client.get('/api/v1/system/health')
    if not client.check_success(response):
        print("❌ 无法连接到API服务器，请检查:")
        print("   1. 服务器地址是否正确")
        print("   2. Token是否有效")
        print("   3. 手机和电脑是否在同一网络")
        sys.exit(1)

    print("✅ 连接成功！\n")

    # 运行测试
    if args.test == 'all':
        tester.run_all_tests()
    elif args.test == 'health':
        tester.test_health_check()
        tester.test_system_info()
        tester.test_system_stats()
    elif args.test == 'workflows':
        tester.test_list_workflows()
        tester.test_create_workflow()
        tester.test_get_workflow()
        tester.test_update_workflow()
    elif args.test == 'executions':
        tester.test_list_workflows()
        tester.test_execute_workflow()
        tester.test_get_execution_status()
        tester.test_get_execution_logs()
    elif args.test == 'modules':
        tester.test_module_categories()
        tester.test_list_modules()


if __name__ == '__main__':
    main()
