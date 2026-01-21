#!/usr/bin/env python3
"""
HTTP 测试服务器 - 用于测试 HttpRequestModule
监听 8000 端口，打印所有请求的详细信息，支持保存上传的文件
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
import json
from urllib.parse import urlparse, parse_qs
import sys
import os
from datetime import datetime
import re

colors = {
    'reset': '\033[0m',
    'bold': '\033[1m',
    'cyan': '\033[96m',
    'green': '\033[92m',
    'yellow': '\033[93m',
    'blue': '\033[94m',
    'magenta': '\033[95m',
    'red': '\033[91m',
}

# 创建上传目录
UPLOAD_DIR = "uploads"
if not os.path.exists(UPLOAD_DIR):
    os.makedirs(UPLOAD_DIR)

def color_print(color, text):
    print(f"{colors.get(color, '')}{text}{colors['reset']}")

def parse_multipart_data(content_type, body):
    """解析 multipart/form-data 数据"""
    # 从 Content-Type 头中提取 boundary
    match = re.search(r'boundary=([^;]+)', content_type)
    if not match:
        return {}, []

    boundary = '--' + match.group(1)

    parts = body.split(boundary.encode())
    fields = {}
    files = []

    for i, part in enumerate(parts):
        if i == 0 or i == len(parts) - 1:  # 跳过开头和结尾的空部分
            continue

        # 分离头部和内容
        if b'\r\n\r\n' not in part:
            continue

        headers, content = part.split(b'\r\n\r\n', 1)
        headers = headers.decode('utf-8', errors='ignore')
        content = content.rstrip(b'\r\n')

        # 解析 Content-Disposition
        disp_match = re.search(r'name="([^"]+)"', headers)
        if not disp_match:
            continue

        field_name = disp_match.group(1)

        # 检查是否有文件名
        filename_match = re.search(r'filename="([^"]+)"', headers)

        if filename_match:
            # 这是一个文件
            filename = filename_match.group(1)
            content_type_match = re.search(r'Content-Type: ([^\r\n]+)', headers)
            content_type = content_type_match.group(1) if content_type_match else 'application/octet-stream'

            files.append({
                'field_name': field_name,
                'filename': filename,
                'content_type': content_type,
                'data': content,
                'size': len(content)
            })
        else:
            # 这是一个普通字段
            try:
                value = content.decode('utf-8')
                fields[field_name] = value
            except:
                fields[field_name] = str(content)

    return fields, files

def save_uploaded_file(file_info):
    """保存上传的文件"""
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S_%f")
    filename = file_info['filename']
    field_name = file_info['field_name']

    # 生成安全的文件名：时间戳_字段名_原始文件名
    # 例如：20250122_143020_123456_file0_photo.jpg
    name_only, ext = os.path.splitext(filename)
    safe_filename = f"{timestamp}_{field_name}{ext}"
    filepath = os.path.join(UPLOAD_DIR, safe_filename)

    # 写入文件
    with open(filepath, 'wb') as f:
        f.write(file_info['data'])

    return filepath

class TestRequestHandler(BaseHTTPRequestHandler):
    def log_request_details(self):
        """打印请求的完整细节（不包含请求体）"""
        print("\n" + "=" * 80)
        color_print('bold', f"📨 收到请求")
        print("=" * 80)

        # 请求行
        color_print('cyan', f"📍 方法: {self.command}")
        color_print('cyan', f"📍 路径: {self.path}")

        # 解析 URL 和查询参数
        parsed = urlparse(self.path)
        if parsed.query:
            query_params = parse_qs(parsed.query)
            color_print('yellow', "\n🔍 查询参数:")
            for key, values in query_params.items():
                for value in values:
                    color_print('yellow', f"   {key} = {value}")

        # 请求头
        color_print('green', "\n📋 请求头:")
        for header, value in self.headers.items():
            color_print('green', f"   {header}: {value}")

    def log_request_details_with_body(self, body):
        """打印请求的完整细节（包含请求体）"""
        self.log_request_details()

        content_length = len(body)
        if content_length > 0:
            content_type = self.headers.get('Content-Type', '')

            color_print('magenta', f"\n📦 请求体 ({content_length} 字节, Content-Type: {content_type}):")

            # 处理 multipart/form-data
            if 'multipart/form-data' in content_type:
                fields, files = parse_multipart_data(content_type, body)

                if fields:
                    color_print('blue', "\n📝 表单字段:")
                    for key, value in fields.items():
                        color_print('blue', f"   {key} = {value}")

                if files:
                    color_print('bold', "\n📁 上传的文件:")
                    for file_info in files:
                        color_print('bold', f"   字段名: {file_info['field_name']}")
                        color_print('yellow', f"   文件名: {file_info['filename']}")
                        color_print('yellow', f"   类型: {file_info['content_type']}")
                        color_print('yellow', f"   大小: {file_info['size']} 字节")

                        # 保存文件
                        saved_path = save_uploaded_file(file_info)
                        color_print('green', f"   ✓ 已保存到: {saved_path}")
                        print()
                else:
                    color_print('red', "   未检测到文件")

            elif 'application/json' in content_type:
                try:
                    json_data = json.loads(body.decode('utf-8'))
                    color_print('magenta', json.dumps(json_data, ensure_ascii=False, indent=2))
                except:
                    color_print('magenta', body.decode('utf-8'))
            elif 'application/x-www-form-urlencoded' in content_type:
                decoded = body.decode('utf-8')
                color_print('magenta', decoded)
                # 尝试解析为键值对
                try:
                    params = parse_qs(decoded)
                    color_print('magenta', "\n解析后的表单数据:")
                    for key, values in params.items():
                        for value in values:
                            color_print('magenta', f"   {key} = {value}")
                except:
                    pass
            else:
                # 其他类型，尝试显示文本
                try:
                    text = body.decode('utf-8')
                    if len(text) > 500:
                        color_print('magenta', text[:500] + "... (已截断)")
                    else:
                        color_print('magenta', text)
                except:
                    color_print('red', "[二进制数据，无法显示]")

        print("\n" + "=" * 80 + "\n")

    def do_GET(self):
        self.log_request_details()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        response = {
            "status": "success",
            "message": "GET request received",
            "path": self.path
        }
        self.wfile.write(json.dumps(response, ensure_ascii=False).encode('utf-8'))

    def do_POST(self):
        # 先读取请求体（在 log_request_details 之前）
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length) if content_length > 0 else b''

        # 传递请求体给日志方法
        self.log_request_details_with_body(body)

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()

        response = {
            "status": "success",
            "message": "POST request received",
            "path": self.path
        }
        self.wfile.write(json.dumps(response, ensure_ascii=False).encode('utf-8'))

    def do_PUT(self):
        # 先读取请求体
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length) if content_length > 0 else b''

        self.log_request_details_with_body(body)

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()

        response = {
            "status": "success",
            "message": "PUT request received",
            "path": self.path
        }
        self.wfile.write(json.dumps(response, ensure_ascii=False).encode('utf-8'))

    def do_DELETE(self):
        self.log_request_details()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        response = {
            "status": "success",
            "message": "DELETE request received",
            "path": self.path
        }
        self.wfile.write(json.dumps(response, ensure_ascii=False).encode('utf-8'))

    def do_PATCH(self):
        # 先读取请求体
        content_length = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_length) if content_length > 0 else b''

        self.log_request_details_with_body(body)

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()

        response = {
            "status": "success",
            "message": "PATCH request received",
            "path": self.path
        }
        self.wfile.write(json.dumps(response, ensure_ascii=False).encode('utf-8'))

    def log_message(self, format, *args):
        """禁用默认的日志输出"""
        pass

def run_server(port=8000):
    server_address = ('', port)
    httpd = HTTPServer(server_address, TestRequestHandler)

    color_print('bold', f"\n🚀 HTTP 测试服务器启动")
    color_print('green', f"✓ 监听端口: {port}")
    color_print('green', f"✓ 访问地址: http://localhost:{port}")
    color_print('green', f"✓ 文件保存目录: {os.path.abspath(UPLOAD_DIR)}")
    color_print('yellow', "\n提示: 按 Ctrl+C 停止服务器\n")

    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        color_print('red', "\n\n👋 服务器已停止")
        sys.exit(0)

if __name__ == '__main__':
    run_server()
