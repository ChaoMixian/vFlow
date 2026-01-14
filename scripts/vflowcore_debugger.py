#!/usr/bin/env python3
"""
vFlowCore 调试工具
使用 tkinter GUI 与 vFlowCore 进行通信调试
"""

import tkinter as tk
from tkinter import ttk, scrolledtext, messagebox
import socket
import json
import threading
from typing import Dict, Any, Optional

class VFlowCoreDebugger:
    def __init__(self, root):
        self.root = root
        self.root.title("vFlowCore 调试工具")
        self.root.geometry("900x700")

        # 连接配置
        self.host = "127.0.0.1"
        self.port = 19999
        self.socket: Optional[socket.socket] = None
        self.connected = False

        self.setup_ui()

    def setup_ui(self):
        # 顶部连接控制区
        connection_frame = ttk.LabelFrame(self.root, text="连接配置", padding=10)
        connection_frame.pack(fill=tk.X, padx=10, pady=5)

        ttk.Label(connection_frame, text="主机:").grid(row=0, column=0, sticky=tk.W, padx=5)
        self.host_entry = ttk.Entry(connection_frame, width=20)
        self.host_entry.insert(0, self.host)
        self.host_entry.grid(row=0, column=1, padx=5)

        ttk.Label(connection_frame, text="端口:").grid(row=0, column=2, sticky=tk.W, padx=5)
        self.port_entry = ttk.Entry(connection_frame, width=10)
        self.port_entry.insert(0, str(self.port))
        self.port_entry.grid(row=0, column=3, padx=5)

        self.connect_btn = ttk.Button(connection_frame, text="连接", command=self.toggle_connection)
        self.connect_btn.grid(row=0, column=4, padx=10)

        self.status_label = ttk.Label(connection_frame, text="未连接", foreground="red")
        self.status_label.grid(row=0, column=5, padx=10)

        # 主要内容区 - 使用 PanedWindow 分割
        paned = ttk.PanedWindow(self.root, orient=tk.HORIZONTAL)
        paned.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)

        # 左侧：请求构建区
        left_frame = ttk.LabelFrame(paned, text="请求构建", padding=10)
        paned.add(left_frame, weight=1)

        # Target 选择
        ttk.Label(left_frame, text="Target:").grid(row=0, column=0, sticky=tk.W, pady=5)
        self.target_var = tk.StringVar(value="system")
        target_combo = ttk.Combobox(left_frame, textvariable=self.target_var, width=20, state="readonly")
        target_combo['values'] = ("system", "clipboard", "input", "wifi", "bluetooth_manager", "power", "activity")
        target_combo.grid(row=0, column=1, sticky=tk.EW, pady=5)
        target_combo.bind("<<ComboboxSelected>>", self.on_target_changed)

        # Method 选择
        ttk.Label(left_frame, text="Method:").grid(row=1, column=0, sticky=tk.W, pady=5)
        self.method_var = tk.StringVar(value="ping")
        self.method_combo = ttk.Combobox(left_frame, textvariable=self.method_var, width=20, state="readonly")
        self.method_combo.grid(row=1, column=1, sticky=tk.EW, pady=5)
        self.method_combo.bind("<<ComboboxSelected>>", self.on_method_changed)

        # 参数编辑区
        ttk.Label(left_frame, text="参数 (JSON):").grid(row=2, column=0, sticky=tk.NW, pady=5)
        self.params_text = scrolledtext.ScrolledText(left_frame, width=30, height=15)
        self.params_text.grid(row=3, column=0, columnspan=2, sticky=tk.NSEW, pady=5)
        self.params_text.insert("1.0", "{}")

        # 快捷按钮区
        button_frame = ttk.Frame(left_frame)
        button_frame.grid(row=4, column=0, columnspan=2, pady=10)

        ttk.Button(button_frame, text="格式化 JSON", command=self.format_params).pack(side=tk.LEFT, padx=5)
        ttk.Button(button_frame, text="清空", command=self.clear_params).pack(side=tk.LEFT, padx=5)
        ttk.Button(button_frame, text="发送请求", command=self.send_request).pack(side=tk.LEFT, padx=5)
        ttk.Button(button_frame, text="🧪 自动测试", command=self.run_auto_test).pack(side=tk.LEFT, padx=5)

        # 预设请求区
        preset_frame = ttk.LabelFrame(left_frame, text="快捷操作", padding=5)
        preset_frame.grid(row=5, column=0, columnspan=2, sticky=tk.EW, pady=10)

        self.create_preset_buttons(preset_frame)

        # 右侧：响应显示区
        right_frame = ttk.LabelFrame(paned, text="响应", padding=10)
        paned.add(right_frame, weight=1)

        self.response_text = scrolledtext.ScrolledText(right_frame, width=40, height=20)
        self.response_text.pack(fill=tk.BOTH, expand=True)

        # 底部日志区
        log_frame = ttk.LabelFrame(self.root, text="日志", padding=10)
        log_frame.pack(fill=tk.X, padx=10, pady=5)

        self.log_text = scrolledtext.ScrolledText(log_frame, height=6)
        self.log_text.pack(fill=tk.BOTH)

        # 初始化
        self.on_target_changed(None)
        self.log("调试工具已启动")

    def create_preset_buttons(self, parent):
        """根据当前 target 创建快捷按钮"""
        self.preset_buttons = {}

        presets = {
            "system": [
                ("Ping", {"method": "ping"}),
                ("退出 Core", {"method": "exit"}),
            ],
            "clipboard": [
                ("获取剪贴板", {"method": "getClipboard"}),
                ("设置剪贴板", {"method": "setClipboard", "params": {"text": "Hello from vFlowCore"}}),
            ],
            "input": [
                ("点击", {"method": "tap", "params": {"x": 500, "y": 500}}),
                ("滑动", {"method": "swipe", "params": {"x1": 500, "y1": 500, "x2": 500, "y2": 1000, "duration": 300}}),
                ("输入文本", {"method": "inputText", "params": {"text": "test"}}),
            ],
            "wifi": [
                ("开启 WiFi", {"method": "setWifiEnabled", "params": {"enabled": True}}),
                ("关闭 WiFi", {"method": "setWifiEnabled", "params": {"enabled": False}}),
            ],
            "bluetooth_manager": [
                ("开启蓝牙", {"method": "setBluetoothEnabled", "params": {"enabled": True}}),
                ("关闭蓝牙", {"method": "setBluetoothEnabled", "params": {"enabled": False}}),
            ],
            "power": [
                ("唤醒屏幕", {"method": "wakeUp"}),
                ("关闭屏幕", {"method": "goToSleep"}),
            ],
            "activity": [
                ("强制停止应用", {"method": "forceStopPackage", "params": {"package": "com.example.app"}}),
            ],
        }

        for i, (text, data) in enumerate(presets.get(self.target_var.get(), [])):
            btn = ttk.Button(parent, text=text, command=lambda d=data: self.apply_preset(d))
            btn.grid(row=i // 2, column=i % 2, sticky=tk.EW, padx=2, pady=2)
            self.preset_buttons[text] = btn

    def update_preset_buttons(self):
        """更新快捷按钮"""
        for widget in self.preset_buttons.values():
            widget.destroy()
        self.create_preset_buttons(self.master.children["!panedwindow"].children["!labelframe"].children["!labelframe2"])

    def on_target_changed(self, event):
        """Target 改变时更新可用方法"""
        target = self.target_var.get()

        methods = {
            "system": ["ping", "exit"],
            "clipboard": ["getClipboard", "setClipboard"],
            "input": ["tap", "swipe", "key", "inputText"],
            "wifi": ["setWifiEnabled"],
            "bluetooth_manager": ["setBluetoothEnabled"],
            "power": ["wakeUp", "goToSleep"],
            "activity": ["forceStopPackage"],
        }

        self.method_combo['values'] = methods.get(target, [])
        if methods.get(target):
            self.method_var.set(methods[target][0])
            # 自动填充第一个方法的参数
            self.auto_fill_params()

        # 重新创建快捷按钮
        for widget in self.preset_buttons.values():
            widget.grid_forget()
            widget.destroy()

        # 获取快捷操作 Frame
        left_frame = self.root.winfo_children()[1].winfo_children()[0]  # PanedWindow -> left_frame
        preset_frame = None
        for child in left_frame.winfo_children():
            if isinstance(child, ttk.LabelFrame) and "快捷操作" in str(child):
                preset_frame = child
                break

        if preset_frame:
            for widget in preset_frame.winfo_children():
                widget.destroy()

            presets = {
                "system": [("Ping", "ping"), ("退出 Core", "exit")],
                "clipboard": [("获取剪贴板", "getClipboard"), ("设置剪贴板", "setClipboard")],
                "input": [("点击", "tap"), ("滑动", "swipe"), ("输入文本", "inputText")],
                "wifi": [("开启 WiFi", "setWifiEnabled"), ("关闭 WiFi", "setWifiEnabled")],
                "bluetooth_manager": [("开启蓝牙", "setBluetoothEnabled"), ("关闭蓝牙", "setBluetoothEnabled")],
                "power": [("唤醒屏幕", "wakeUp"), ("关闭屏幕", "goToSleep")],
                "activity": [("强制停止应用", "forceStopPackage")],
            }

            for i, (text, method) in enumerate(presets.get(target, [])):
                btn = ttk.Button(preset_frame, text=text,
                              command=lambda m=method, t=text: self.quick_action(t, m))
                btn.grid(row=i // 2, column=i % 2, sticky=tk.EW, padx=2, pady=2)

    def on_method_changed(self, event):
        """Method 改变时自动填充测试样例参数"""
        self.auto_fill_params()

    def auto_fill_params(self):
        """根据当前 target 和 method 自动填充测试样例参数"""
        target = self.target_var.get()
        method = self.method_var.get()

        # 定义每个方法的测试样例参数
        example_params = {
            "system": {
                "ping": {},
                "exit": {},
            },
            "clipboard": {
                "getClipboard": {},
                "setClipboard": {"text": "Hello from vFlowCore Debugger"},
            },
            "input": {
                "tap": {"x": 500, "y": 500},
                "swipe": {"x1": 500, "y1": 500, "x2": 500, "y2": 1000, "duration": 300},
                "key": {"code": 4},  # BACK 键
                "inputText": {"text": "test"},
            },
            "wifi": {
                "setWifiEnabled": {"enabled": True},
            },
            "bluetooth_manager": {
                "setBluetoothEnabled": {"enabled": True},
            },
            "power": {
                "wakeUp": {},
                "goToSleep": {},
            },
            "activity": {
                "forceStopPackage": {"package": "com.example.app"},
            },
        }

        # 获取样例参数
        params = example_params.get(target, {}).get(method, {})

        # 填充到参数编辑区
        self.params_text.delete("1.0", tk.END)
        self.params_text.insert("1.0", json.dumps(params, indent=2, ensure_ascii=False))

    def quick_action(self, text, method):
        """快捷操作"""
        params = {}

        # 根据操作类型设置默认参数
        if text == "设置剪贴板":
            params = {"text": "Hello from vFlowCore Debugger"}
        elif text == "点击":
            params = {"x": 500, "y": 500}
        elif text == "滑动":
            params = {"x1": 500, "y1": 500, "x2": 500, "y2": 1000, "duration": 300}
        elif text == "输入文本":
            params = {"text": "test"}
        elif "开启" in text or "关闭" in text:
            params = {"enabled": "开启" in text}
        elif text == "强制停止应用":
            params = {"package": "com.example.app"}

        self.params_text.delete("1.0", tk.END)
        self.params_text.insert("1.0", json.dumps(params, indent=2, ensure_ascii=False))
        self.send_request()

    def apply_preset(self, data):
        """应用预设"""
        self.method_var.set(data["method"])
        if "params" in data:
            self.params_text.delete("1.0", tk.END)
            self.params_text.insert("1.0", json.dumps(data["params"], indent=2, ensure_ascii=False))
        else:
            self.params_text.delete("1.0", tk.END)
            self.params_text.insert("1.0", "{}")

        self.send_request()

    def toggle_connection(self):
        """切换连接状态"""
        if self.connected:
            self.disconnect()
        else:
            self.connect()

    def connect(self):
        """连接到 vFlowCore"""
        try:
            self.host = self.host_entry.get()
            self.port = int(self.port_entry.get())

            self.socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            # 设置连接超时为 5 秒，但不设置读写超时（避免长时间无操作断开）
            self.socket.settimeout(5)
            self.socket.connect((self.host, self.port))
            # 连接成功后移除超时，保持长连接
            self.socket.settimeout(None)

            self.connected = True
            self.connect_btn.config(text="断开")
            self.status_label.config(text="已连接", foreground="green")
            self.log(f"已连接到 {self.host}:{self.port}")

            # 自动 ping 测试
            self.send_ping()
        except Exception as e:
            messagebox.showerror("连接失败", f"无法连接到 vFlowCore:\n{e}")
            self.log(f"连接失败: {e}")

    def disconnect(self):
        """断开连接"""
        if self.socket:
            try:
                self.socket.close()
            except:
                pass
            self.socket = None

        self.connected = False
        self.connect_btn.config(text="连接")
        self.status_label.config(text="未连接", foreground="red")
        self.log("已断开连接")

    def send_ping(self):
        """发送 ping 测试"""
        req = {"target": "system", "method": "ping"}
        self.send_request_raw(req)

    def send_request(self):
        """发送请求"""
        try:
            params_str = self.params_text.get("1.0", tk.END).strip()
            params = json.loads(params_str) if params_str else {}

            req = {
                "target": self.target_var.get(),
                "method": self.method_var.get(),
                "params": params
            }

            self.send_request_raw(req)
        except json.JSONDecodeError as e:
            messagebox.showerror("JSON 错误", f"参数 JSON 格式错误:\n{e}")
        except Exception as e:
            messagebox.showerror("错误", f"发送请求失败:\n{e}")

    def send_request_raw(self, req: Dict[str, Any]):
        """发送原始请求"""
        if not self.connected or not self.socket:
            messagebox.showwarning("未连接", "请先连接到 vFlowCore")
            return

        try:
            # 发送请求
            req_str = json.dumps(req) + "\n"
            self.socket.sendall(req_str.encode('utf-8'))

            self.log(f"发送: {req_str.strip()}")

            # 接收响应
            response = self.socket.recv(4096).decode('utf-8').strip()
            self.log(f"接收: {response}")

            # 显示响应
            self.response_text.delete("1.0", tk.END)
            try:
                response_json = json.loads(response)
                self.response_text.insert("1.0", json.dumps(response_json, indent=2, ensure_ascii=False))
            except:
                self.response_text.insert("1.0", response)

        except BrokenPipeError:
            # 连接已断开，尝试重连
            self.log("⚠️ 连接已断开，尝试重连...")
            self.disconnect()

            # 自动重连
            try:
                self.connect()
                if self.connected:
                    self.log("✅ 重连成功，重新发送请求")
                    # 重新发送请求
                    self.send_request_raw(req)
            except Exception as e:
                self.log(f"❌ 重连失败: {e}")
                messagebox.showerror("连接断开", f"连接已断开且重连失败:\n{e}")

        except Exception as e:
            self.log(f"通信错误: {e}")
            messagebox.showerror("通信错误", f"与 vFlowCore 通信失败:\n{e}")

    def format_params(self):
        """格式化参数 JSON"""
        try:
            params_str = self.params_text.get("1.0", tk.END).strip()
            if params_str:
                params = json.loads(params_str)
                formatted = json.dumps(params, indent=2, ensure_ascii=False)
                self.params_text.delete("1.0", tk.END)
                self.params_text.insert("1.0", formatted)
        except json.JSONDecodeError as e:
            messagebox.showerror("格式化失败", f"JSON 格式错误:\n{e}")

    def clear_params(self):
        """清空参数"""
        self.params_text.delete("1.0", tk.END)
        self.params_text.insert("1.0", "{}")

    def log(self, message: str):
        """添加日志"""
        self.log_text.insert(tk.END, f"[{self.get_timestamp()}] {message}\n")
        self.log_text.see(tk.END)

    @staticmethod
    def get_timestamp():
        """获取时间戳"""
        import datetime
        return datetime.datetime.now().strftime("%H:%M:%S")

    def get_test_cases(self):
        """获取所有测试用例"""
        return {
            "safe": [  # 安全测试（无副作用）
                ("system", "ping", {}, "Ping 测试"),
                ("clipboard", "getClipboard", {}, "获取剪贴板"),
            ],
            "destructive": [  # 有副作用的测试
                ("clipboard", "setClipboard", {"text": "Auto Test from vFlowCore Debugger"}, "设置剪贴板"),
                ("wifi", "setWifiEnabled", {"enabled": True}, "开启 WiFi"),
                ("wifi", "setWifiEnabled", {"enabled": False}, "关闭 WiFi"),
                ("bluetooth_manager", "setBluetoothEnabled", {"enabled": True}, "开启蓝牙"),
                ("bluetooth_manager", "setBluetoothEnabled", {"enabled": False}, "关闭蓝牙"),
                ("power", "wakeUp", {}, "唤醒屏幕"),
                ("power", "goToSleep", {}, "关闭屏幕"),
            ],
            "dangerous": [  # 危险测试（会杀死应用或影响系统）
                ("input", "tap", {"x": 500, "y": 500}, "点击屏幕"),
                ("input", "swipe", {"x1": 500, "y1": 500, "x2": 500, "y2": 1000, "duration": 300}, "滑动屏幕"),
                ("input", "inputText", {"text": "test"}, "输入文本"),
                ("activity", "forceStopPackage", {"package": "com.chaomixian.vflow"}, "强制停止应用"),
            ]
        }

    def run_auto_test(self):
        """运行自动测试"""
        if not self.connected:
            messagebox.showwarning("未连接", "请先连接到 vFlowCore")
            return

        # 创建自定义对话框
        dialog = tk.Toplevel(self.root)
        dialog.title("选择测试范围")
        dialog.geometry("600x450")
        dialog.transient(self.root)
        dialog.grab_set()

        # 居中显示
        dialog.update_idletasks()
        width = 600
        height = 450
        x = self.root.winfo_x() + (self.root.winfo_width() - width) // 2
        y = self.root.winfo_y() + (self.root.winfo_height() - height) // 2
        dialog.geometry(f"{width}x{height}+{x}+{y}")

        # 标题
        tk.Label(dialog, text="🧪 选择测试范围", font=("Arial", 14, "bold")).pack(pady=15)

        # 说明文本
        info_frame = tk.Frame(dialog)
        info_frame.pack(pady=10, padx=20, fill=tk.BOTH, expand=True)

        test_options = [
            ("🟢 安全测试", "safe", "2 个测试 • 无副作用\n• ping 测试\n• 获取剪贴板"),
            ("🟡 常规测试", "regular", "9 个测试 • 有副作用\n• 开关 WiFi、蓝牙、电源\n• 修改剪贴板内容"),
            ("🔴 完整测试", "full", "13 个测试 • 包括危险操作\n• 点击屏幕、滑动\n• 输入文本、杀死应用")
        ]

        selected_option = tk.StringVar(value="safe")

        for i, (label_text, value, desc) in enumerate(test_options):
            frame = tk.Frame(info_frame, relief=tk.RIDGE, borderwidth=2, padx=15, pady=10)
            frame.pack(fill=tk.X, pady=8)

            rb = tk.Radiobutton(frame, text=label_text, variable=selected_option, value=value,
                               font=("Arial", 11, "bold"))
            rb.pack(anchor=tk.W)

            desc_label = tk.Label(frame, text=desc, justify=tk.LEFT, font=("Arial", 9))
            desc_label.pack(anchor=tk.W, padx=25)

        # 按钮区
        button_frame = tk.Frame(dialog)
        button_frame.pack(pady=20)

        result = {"choice": None}

        def on_ok():
            result["choice"] = selected_option.get()
            dialog.destroy()

        def on_cancel():
            result["choice"] = None
            dialog.destroy()

        tk.Button(button_frame, text="开始测试", command=on_ok, width=12,
                 font=("Arial", 11, "bold"), padx=20, pady=8).pack(side=tk.LEFT, padx=10)
        tk.Button(button_frame, text="取消", command=on_cancel, width=12,
                 font=("Arial", 11), padx=20, pady=8).pack(side=tk.LEFT, padx=10)

        # 等待对话框关闭
        self.root.wait_window(dialog)

        # 根据选择获取测试用例
        choice = result["choice"]
        if choice is None:
            return
        elif choice == "safe":
            test_cases = self.get_test_cases()["safe"]
        elif choice == "regular":
            test_cases = self.get_test_cases()["safe"] + self.get_test_cases()["destructive"]
        else:  # full
            test_cases = (self.get_test_cases()["safe"] +
                         self.get_test_cases()["destructive"] +
                         self.get_test_cases()["dangerous"])

        # 清空响应区并显示测试开始
        self.response_text.delete("1.0", tk.END)
        self.log("=" * 60)
        self.log(f"🧪 开始自动测试 - 共 {len(test_cases)} 个测试用例")
        self.log("=" * 60)

        # 运行测试
        results = {
            "passed": 0,
            "failed": 0,
            "skipped": 0,
            "details": []
        }

        for i, (target, method, params, description) in enumerate(test_cases, 1):
            self.log(f"\n[{i}/{len(test_cases)}] 测试: {description}")
            self.log(f"  Target: {target}, Method: {method}")

            # 构建请求
            req = {
                "target": target,
                "method": method,
                "params": params
            }

            try:
                # 发送请求
                req_str = json.dumps(req) + "\n"
                self.socket.sendall(req_str.encode('utf-8'))

                # 接收响应
                response = self.socket.recv(4096).decode('utf-8').strip()
                response_json = json.loads(response)

                # 判断测试结果
                success = response_json.get("success", False)

                if success:
                    results["passed"] += 1
                    self.log(f"  ✅ 通过 - {response}")
                    results["details"].append({
                        "name": description,
                        "status": "✅ 通过",
                        "response": response_json
                    })
                else:
                    results["failed"] += 1
                    error_msg = response_json.get("error", "Unknown error")
                    self.log(f"  ❌ 失败 - {error_msg}")
                    results["details"].append({
                        "name": description,
                        "status": "❌ 失败",
                        "error": error_msg,
                        "response": response_json
                    })

                # 在响应区显示实时结果
                self.response_text.delete("1.0", tk.END)
                self.response_text.insert("1.0", f"正在测试: [{i}/{len(test_cases)}] {description}\n\n")
                self.response_text.insert(tk.END, json.dumps(response_json, indent=2, ensure_ascii=False))

            except Exception as e:
                results["failed"] += 1
                self.log(f"  ❌ 异常 - {e}")
                results["details"].append({
                    "name": description,
                    "status": "❌ 异常",
                    "error": str(e)
                })

            # 短暂延迟，避免请求过快
            self.root.update()
            import time
            time.sleep(0.2)

        # 显示测试报告
        self.log("\n" + "=" * 60)
        self.log("📊 测试报告")
        self.log("=" * 60)
        self.log(f"总计: {len(test_cases)} 个测试")
        self.log(f"通过: {results['passed']} 个 ✅")
        self.log(f"失败: {results['failed']} 个 ❌")
        self.log(f"跳过: {results['skipped']} 个 ⏭️")
        self.log(f"成功率: {results['passed'] / len(test_cases) * 100:.1f}%")
        self.log("=" * 60)

        # 在响应区显示完整报告
        self.response_text.delete("1.0", tk.END)
        report = ["🧪 vFlowCore 自动测试报告", "=" * 40, ""]
        report.append(f"测试时间: {self.get_timestamp()}")
        report.append(f"总计: {len(test_cases)} 个测试")
        report.append(f"通过: {results['passed']} 个 ✅")
        report.append(f"失败: {results['failed']} 个 ❌")
        report.append(f"成功率: {results['passed'] / len(test_cases) * 100:.1f}%")
        report.append("")
        report.append("详细结果:")
        report.append("-" * 40)

        for detail in results["details"]:
            report.append(f"\n{detail['status']} {detail['name']}")
            if "error" in detail:
                report.append(f"  错误: {detail['error']}")
            if "response" in detail:
                report.append(f"  响应: {json.dumps(detail['response'], ensure_ascii=False)}")

        report.append("\n" + "=" * 40)

        self.response_text.insert("1.0", "\n".join(report))

        # 弹窗显示总结
        if results["failed"] == 0:
            messagebox.showinfo("测试完成", f"🎉 全部通过！\n\n{results['passed']}/{len(test_cases)} 个测试通过")
        else:
            messagebox.showwarning(
                "测试完成",
                f"⚠️ 部分测试失败\n\n"
                f"通过: {results['passed']} 个\n"
                f"失败: {results['failed']} 个\n"
                f"成功率: {results['passed'] / len(test_cases) * 100:.1f}%"
            )

def main():
    root = tk.Tk()
    app = VFlowCoreDebugger(root)
    root.mainloop()

if __name__ == "__main__":
    main()
