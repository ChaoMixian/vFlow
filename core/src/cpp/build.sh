#!/bin/bash
# vflow_shell_exec 多架构构建脚本
# 支持架构：armeabi-v7a, arm64-v8a, x86, x86_64

set -e  # 遇到错误立即退出

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo_info() { echo -e "${GREEN}[INFO]${NC} $1" >&2; }
echo_warn() { echo -e "${YELLOW}[WARN]${NC} $1" >&2; }
echo_error() { echo -e "${RED}[ERROR]${NC} $1" >&2; }

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"
SOURCE_FILE="$SCRIPT_DIR/shell_launcher.cpp"

# 检测 NDK 路径
detect_ndk() {
    local NDK=""

    # 1. 从 local.properties 读取 sdk.dir
    if [ -f "$SCRIPT_DIR/../../../../local.properties" ]; then
        local SDK_DIR=$(grep "^sdk.dir=" "$SCRIPT_DIR/../../../../local.properties" | cut -d'=' -f2)
        if [ -n "$SDK_DIR" ] && [ -d "$SDK_DIR/ndk" ]; then
            # 查找 NDK（可能有多个版本），排除 ndk 目录本身
            NDK=$(find "$SDK_DIR/ndk" -maxdepth 1 -type d -name "[0-9]*" | sort -V | tail -n 1)
            if [ -n "$NDK" ]; then
                echo_info "Found NDK via local.properties: $NDK"
                echo "$NDK"
                return
            fi
        fi
    fi

    # 2. 检查 ANDROID_NDK_HOME 环境变量
    if [ -n "$ANDROID_NDK_HOME" ] && [ -d "$ANDROID_NDK_HOME" ]; then
        echo_info "Found NDK via ANDROID_NDK_HOME: $ANDROID_NDK_HOME"
        echo "$ANDROID_NDK_HOME"
        return
    fi

    # 3. 检查 ANDROID_SDK_ROOT/ndk
    if [ -n "$ANDROID_SDK_ROOT" ] && [ -d "$ANDROID_SDK_ROOT/ndk" ]; then
        NDK=$(find "$ANDROID_SDK_ROOT/ndk" -maxdepth 1 -type d -name "[0-9]*" | sort -V | tail -n 1)
        if [ -n "$NDK" ]; then
            echo_info "Found NDK via ANDROID_SDK_ROOT: $NDK"
            echo "$NDK"
            return
        fi
    fi

    # 4. 检查常见的默认路径
    local DEFAULT_NDK_PATHS=(
        "$HOME/Library/Android/sdk/ndk"
        "$HOME/Android/Sdk/ndk"
    )

    for path in "${DEFAULT_NDK_PATHS[@]}"; do
        if [ -d "$path" ]; then
            NDK=$(find "$path" -maxdepth 1 -type d -name "[0-9]*" | sort -V | tail -n 1)
            if [ -n "$NDK" ]; then
                echo_info "Found NDK via default path: $NDK"
                echo "$NDK"
                return
            fi
        fi
    done

    echo_error "NDK not found!"
    echo_error "Please set ANDROID_NDK_HOME or ensure SDK is properly installed"
    echo ""
    echo_error "Install NDK:"
    echo_error "  1. Via Android Studio: Preferences → Appearance & Behavior → System Settings → Android SDK → SDK Tools → NDK (Side by side)"
    echo_error "  2. Or manually: https://developer.android.com/ndk/downloads"
    exit 1
}

# 检测主机架构
detect_host() {
    local OS=$(uname -s)

    case "$OS" in
        Darwin)
            # NDK toolchain 在 macOS 上只有 darwin-x86_64
            echo "darwin-x86_64"
            ;;
        Linux)
            echo "linux-x86_64"
            ;;
        *)
            echo_error "Unsupported host OS: $OS"
            exit 1
            ;;
    esac
}

# 主函数
main() {
    echo_info "========================================="
    echo_info "vflow_shell_exec Multi-Arch Build Script"
    echo_info "========================================="
    echo ""

    # 检查源文件
    if [ ! -f "$SOURCE_FILE" ]; then
        echo_error "Source file not found: $SOURCE_FILE"
        exit 1
    fi

    # 检测 NDK
    NDK=$(detect_ndk)
    HOST=$(detect_host)

    # 检查 NDK 工具链
    TOOLCHAIN="$NDK/toolchains/llvm/prebuilt/$HOST"
    if [ ! -d "$TOOLCHAIN" ]; then
        echo_error "Toolchain not found: $TOOLCHAIN"
        exit 1
    fi

    echo_info "Toolchain: $TOOLCHAIN"
    echo ""

    # 创建构建目录
    mkdir -p "$BUILD_DIR"

    # 编译选项
    CFLAGS="-static-libgcc -static-libstdc++ -pie -fPIE -O2 -s"
    LDFLAGS="-llog -landroid"

    # 定义架构列表（不使用关联数组，因为 bash 3.2 不支持）
    ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

    # 函数：获取架构对应的编译器
    get_compiler() {
        case "$1" in
            armeabi-v7a) echo "armv7a-linux-androideabi21-clang" ;;
            arm64-v8a) echo "aarch64-linux-android21-clang" ;;
            x86) echo "i686-linux-android21-clang" ;;
            x86_64) echo "x86_64-linux-android21-clang" ;;
            *) echo "" ;;
        esac
    }

    # 编译各个架构
    echo_info "Building vflow_shell_exec for all architectures..."
    echo ""

    for ABI in "${ABIS[@]}"; do
        CC=$(get_compiler "$ABI")
        OUTPUT="$BUILD_DIR/$ABI/vflow_shell_exec"

        echo_info "Building $ABI..."
        echo "  Compiler: $CC"
        echo "  Output:   $OUTPUT"

        # 创建 ABI 目录
        mkdir -p "$BUILD_DIR/$ABI"

        # 编译
        if "$TOOLCHAIN/bin/$CC" $CFLAGS "$SOURCE_FILE" $LDFLAGS -o "$OUTPUT"; then
            # 检查是否编译成功
            if [ -f "$OUTPUT" ]; then
                SIZE=$(ls -lh "$OUTPUT" | awk '{print $5}')
                echo_info "  ✓ Success ($SIZE)"
            else
                echo_error "  ✗ Failed (output not found)"
                exit 1
            fi
        else
            echo_error "  ✗ Failed (compilation error)"
            exit 1
        fi
        echo ""
    done

    # 总结
    echo_info "========================================="
    echo_info "Build completed successfully!"
    echo_info "========================================="
    echo ""
    echo_info "Output directory: $BUILD_DIR"
    echo ""

    # 列出编译结果
    for ABI in "${ABIS[@]}"; do
        OUTPUT="$BUILD_DIR/$ABI/vflow_shell_exec"
        if [ -f "$OUTPUT" ]; then
            SIZE=$(ls -lh "$OUTPUT" | awk '{print $5}')
            echo_info "  $ABI: $SIZE"
        fi
    done

    echo ""
    echo_info "Next step: Run ./deploy.sh to copy binaries to assets"
}

# 执行主函数
main "$@"
