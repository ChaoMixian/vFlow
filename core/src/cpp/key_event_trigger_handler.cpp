#include <iostream>
#include <string>
#include <chrono>
#include <vector>
#include <fcntl.h>
#include <unistd.h>
#include <linux/input.h>
#include <poll.h>
#include <getopt.h>
#include <map>
#include <dirent.h>
#include <cstring>
#include <cstdlib>
#include <sys/stat.h>
#include <sys/types.h>
#include <signal.h>
#include <errno.h>

using namespace std;

// 全局标志用于信号处理
volatile sig_atomic_t running = 1;

// 信号处理函数
void signal_handler(int signum) {
    running = 0;
}

// 守护进程化
void daemonize() {
    pid_t pid = fork();
    if (pid < 0) {
        perror("fork failed");
        exit(EXIT_FAILURE);
    }
    if (pid > 0) {
        // 父进程退出
        exit(EXIT_SUCCESS);
    }

    // 子进程继续
    if (setsid() < 0) {
        perror("setsid failed");
        exit(EXIT_FAILURE);
    }

    // 第二次 fork，确保不是会话首领
    pid = fork();
    if (pid < 0) {
        perror("second fork failed");
        exit(EXIT_FAILURE);
    }
    if (pid > 0) {
        exit(EXIT_SUCCESS);
    }

    // 设置文件权限掩码
    umask(0);

    // 关闭所有打开的文件描述符
    for (int fd = sysconf(_SC_OPEN_MAX); fd >= 0; fd--) {
        close(fd);
    }

    // 打开 /dev/null 作为 stdin, stdout, stderr
    int fd = open("/dev/null", O_RDWR);
    if (fd != -1) {
        dup2(fd, STDIN_FILENO);
        dup2(fd, STDOUT_FILENO);
        dup2(fd, STDERR_FILENO);
        if (fd > STDERR_FILENO) {
            close(fd);
        }
    }

    // 忽略信号
    signal(SIGCHLD, SIG_IGN);
    signal(SIGHUP, SIG_IGN);
}

/**
 * KeyState 结构体
 * 记录单个按键的手势识别状态
 */
struct KeyState {
    int state = 0;           // 0: 空闲, 1: 已按下, 2: 已抬起(等待双击)
    long long down_time = 0; // 按下时的时间戳
    long long up_time = 0;   // 抬起时的时间戳
};

/**
 * GestureDetector 类
 * 负责管理多个输入设备并识别各按键的手势
 */
class GestureDetector {
private:
    struct MonitorType {
        bool click = false;
        bool double_click = false;
        bool long_press = false;
        bool short_press = false;
        bool slider = false;
        bool test_mode = false;
    } m_type;

    vector<int> device_fds;
    vector<string> device_paths;
    int target_key_code = -1; // -1 表示监听所有按键
    string package_name;      // Android 包名
    int last_ringer_mode = -1;

    // 为每个按键维护独立的状态机
    map<int, KeyState> key_states;

    // 时间阈值 (毫秒)
    const int HOLD_MS = 250;
    const int CLICK_MS = 200;
    const int DOUBLE_MS = 250;

    /**
     * 获取系统单调时间（毫秒）
     */
    long long getNowMs() {
        using namespace chrono;
        return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
    }

    int getRingerMode() {
        FILE* fp = popen("settings get system ringer_mode 2>/dev/null", "r");
        if (fp == nullptr) {
            return -1;
        }

        char buffer[64];
        string output;
        while (fgets(buffer, sizeof(buffer), fp) != nullptr) {
            output += buffer;
        }
        pclose(fp);

        try {
            return stoi(output);
        } catch (...) {
            return -1;
        }
    }

    void sendTestBroadcast(const string& devicePath, const string& action, int keyCode) {
        string command = "am broadcast -a " + package_name + ".KEY_TEST_EVENT "
                        "-p " + package_name + " "
                        "--es device_path " + shellEscape(devicePath) + " "
                        "--es gesture_type " + shellEscape(action) + " "
                        "--ei key_code " + to_string(keyCode);

        int result = system(command.c_str());
        if (result == -1) {
            cerr << "[ERROR] test broadcast failed: " << command << endl;
        }
    }

    string shellEscape(const string& value) {
        string escaped = "'";
        for (char c : value) {
            if (c == '\'') {
                escaped += "'\\''";
            } else {
                escaped += c;
            }
        }
        escaped += "'";
        return escaped;
    }

    /**
     * 发送 Intent 到 Android
     * 调用 am start-service 发送广播
     */
    void sendIntent(const string& devicePath, const string& gestureType, int keyCode) {
        string command = "am start-service -n " + package_name + "/com.chaomixian.vflow.services.TriggerService "
                        "-a com.chaomixian.vflow.KEY_EVENT_RECEIVED "
                        "--es device " + devicePath + " "
                        "--es gesture_type " + gestureType + " "
                        "--ei key_code " + to_string(keyCode);

        // 输出到 stderr 以便调试
        cerr << "[TRACE] Sending intent: " << command << endl;

        // 使用 system 后台执行，不阻塞主循环
        int result = system(command.c_str());

        // 检查执行结果
        if (result == -1) {
            cerr << "[ERROR] system() failed for command: " << command << endl;
        } else {
            cerr << "[SUCCESS] Intent sent, gesture=" << gestureType << ", key=" << keyCode << endl;
        }
    }

    /**
     * 输出手势事件（通过 Intent 发送到 Android）
     */
    void emitGesture(const string& devicePath, const string& gesture, int code) {
        sendIntent(devicePath, gesture, code);
    }

    /**
     * 处理特定按键的超时逻辑
     */
    void handleTimeout(const string& devicePath, int code, KeyState& ks) {
        long long now = getNowMs();
        if (ks.state == 1) {
            if (now - ks.down_time >= HOLD_MS) {
                if (m_type.long_press) emitGesture(devicePath, "long_press", code);
                ks.state = 0;
            }
        } else if (ks.state == 2) {
            if (now - ks.up_time >= CLICK_MS) {
                if (m_type.click) emitGesture(devicePath, "click", code);
                ks.state = 0;
            }
        }
    }

public:
    GestureDetector(int keyCode, bool c, bool d, bool l, const string& pkg)
            : target_key_code(keyCode), package_name(pkg) {
        m_type.click = c;
        m_type.double_click = d;
        m_type.long_press = l;
        last_ringer_mode = getRingerMode();
    }

    void enableShortPress() {
        m_type.short_press = true;
    }

    void enableSlider() {
        m_type.slider = true;
    }

    void enableTestMode() {
        m_type.test_mode = true;
    }

    ~GestureDetector() {
        for (int fd : device_fds) {
            if (fd >= 0) close(fd);
        }
    }

    /**
     * 添加要监听的设备路径
     */
    void addDevice(const string& path) {
        int fd = open(path.c_str(), O_RDONLY | O_NONBLOCK);
        if (fd >= 0) {
            device_fds.push_back(fd);
            device_paths.push_back(path);
        } else {
            // 权限不足在安卓上很常见，这里保持静默或输出
            // perror(("无法打开设备: " + path).c_str());
        }
    }

    /**
     * 运行监听主循环
     */
    void run() {
        if (device_fds.empty()) {
            cerr << "错误: 没有找到可读的设备节点，请检查 root 权限。" << endl;
            return;
        }

        cout << "正在监听 " << device_fds.size() << " 个设备..." << endl;
        if (target_key_code != -1) cout << "过滤按键: " << target_key_code << endl;

        vector<pollfd> pfds;
        for (int fd : device_fds) {
            pfds.push_back({ fd, POLLIN, 0 });
        }

        input_event ev;
        while (running) {
            // 动态计算 poll 超时
            int min_timeout = 100;
            long long now = getNowMs();
            for (auto& pair : key_states) {
                KeyState& ks = pair.second;
                if (ks.state == 1) {
                    min_timeout = min(min_timeout, (int)max(0LL, HOLD_MS - (now - ks.down_time)));
                } else if (ks.state == 2) {
                    min_timeout = min(min_timeout, (int)max(0LL, CLICK_MS - (now - ks.up_time)));
                }
            }

            int ret = poll(pfds.data(), pfds.size(), min_timeout);

            // 1. 处理超时逻辑
            if (ret <= 0) {
                for (auto it = key_states.begin(); it != key_states.end(); ) {
                    const string& devicePath = device_paths.empty() ? string("/dev/input/unknown") : device_paths.front();
                    handleTimeout(devicePath, it->first, it->second);
                    if (it->second.state == 0) it = key_states.erase(it);
                    else ++it;
                }
            }

            // 2. 处理按键输入逻辑
            if (ret > 0) {
                for (size_t index = 0; index < pfds.size(); ++index) {
                    auto& pfd = pfds[index];
                    const string& devicePath = device_paths[index];
                    if (pfd.revents & POLLIN) {
                        while (read(pfd.fd, &ev, sizeof(ev)) > 0) {
                            if (ev.type != EV_KEY) continue;
                            if (target_key_code != -1 && ev.code != target_key_code) continue;

                            int code = ev.code;
                            KeyState& ks = key_states[code];
                            now = getNowMs();

                            if (ev.value == 1) { // Pressed
                                if (m_type.test_mode) {
                                    sendTestBroadcast(devicePath, "DOWN", code);
                                }
                                if (m_type.short_press) {
                                    emitGesture(devicePath, "short_press", code);
                                }
                                if (ks.state == 0) {
                                    ks.down_time = now;
                                    ks.state = 1;
                                } else if (ks.state == 2) {
                                    if (now - ks.up_time <= DOUBLE_MS) {
                                        if (m_type.double_click) emitGesture(devicePath, "double_click", code);
                                        ks.state = 0;
                                    } else {
                                        if (m_type.click) emitGesture(devicePath, "click", code);
                                        ks.down_time = now;
                                        ks.state = 1;
                                    }
                                }
                            } else if (ev.value == 0) { // Released
                                if (m_type.test_mode) {
                                    sendTestBroadcast(devicePath, "UP", code);
                                }
                                if (m_type.slider) {
                                    usleep(100 * 1000);
                                    int new_ringer_mode = getRingerMode();
                                    if (last_ringer_mode != -1 && new_ringer_mode != -1 && new_ringer_mode != last_ringer_mode) {
                                        if (new_ringer_mode > last_ringer_mode) {
                                            emitGesture(devicePath, "swipe_up", code);
                                        } else {
                                            emitGesture(devicePath, "swipe_down", code);
                                        }
                                    }
                                    if (new_ringer_mode != -1) {
                                        last_ringer_mode = new_ringer_mode;
                                    }
                                }
                                if (ks.state == 1) {
                                    if (now - ks.down_time < HOLD_MS) {
                                        ks.up_time = now;
                                        ks.state = 2;
                                    } else {
                                        ks.state = 0;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
};

/**
 * 适配 Android：使用 dirent 手动扫描设备
 */
vector<string> scanDevices() {
    vector<string> devices;
    const char* dirname = "/dev/input";
    DIR* dir = opendir(dirname);
    if (dir == nullptr) {
        perror("无法打开 /dev/input");
        return devices;
    }

    struct dirent* entry;
    while ((entry = readdir(dir)) != nullptr) {
        // 筛选以 "event" 开头的文件
        if (strncmp(entry->d_name, "event", 5) == 0) {
            devices.push_back(string(dirname) + "/" + entry->d_name);
        }
    }
    closedir(dir);
    return devices;
}

int main(int argc, char* argv[]) {
    string path = "";
    string package_name = "com.chaomixian.vflow";  // 默认包名
    int target_key = -1;
    bool c = false, d = false, l = false, p = false, s = false, t = false;
    enum {
        OPT_PRESS = 1000,
        OPT_SLIDER = 1001,
        OPT_TEST = 1002
    };

    static struct option long_options[] = {
            {"device",  required_argument, 0, 'p'},
            {"key",     required_argument, 0, 'k'},
            {"package", required_argument, 0, 'n'},
            {"click",   no_argument,       0, 'c'},
            {"double",  no_argument,       0, 'd'},
            {"hold",    no_argument,       0, 'h'},
            {"press",   no_argument,       0, OPT_PRESS},
            {"slider",  no_argument,       0, OPT_SLIDER},
            {"test",    no_argument,       0, OPT_TEST},
            {"all",     no_argument,       0, 'a'},
            {0, 0, 0, 0}
    };

    int opt;
    while ((opt = getopt_long(argc, argv, "p:k:n:cdha", long_options, nullptr)) != -1) {
        switch (opt) {
            case 'p': path = optarg; break;
            case 'k':
                try { target_key = stoi(optarg, nullptr, 0); }
                catch (...) { cerr << "键值格式错误" << endl; return 1; }
                break;
            case 'n': package_name = optarg; break;
            case 'c': c = true; break;
            case 'd': d = true; break;
            case 'h': l = true; break;
            case OPT_PRESS: p = true; break;
            case OPT_SLIDER: s = true; break;
            case OPT_TEST: t = true; break;
            case 'a': c = d = l = true; break;
            default: return 1;
        }
    }

    if (!c && !d && !l && !p && !s) c = d = l = true;

    // 设置信号处理
    signal(SIGTERM, signal_handler);
    signal(SIGINT, signal_handler);

    // 守护进程化
    daemonize();

    GestureDetector detector(target_key, c, d, l, package_name);
    if (p) detector.enableShortPress();
    if (s) detector.enableSlider();
    if (t) detector.enableTestMode();

    if (!path.empty()) {
        detector.addDevice(path);
    } else {
        vector<string> all_devices = scanDevices();
        for (const auto& dev : all_devices) {
            detector.addDevice(dev);
        }
    }

    detector.run();
    return 0;
}
