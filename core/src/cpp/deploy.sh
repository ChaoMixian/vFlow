#!/bin/bash
# vflow_shell_exec 部署脚本
# 将编译好的二进制文件复制到 app/assets 目录

set -e  # 遇到错误立即退出

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
echo_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
echo_error() { echo -e "${RED}[ERROR]${NC} $1"; }
echo_step() { echo -e "${BLUE}[STEP]${NC} $1"; }

# 获取脚本所在目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_DIR="$SCRIPT_DIR/build"
ASSETS_DIR="$SCRIPT_DIR/../../../app/src/main/assets"
TARGET_DIR="$ASSETS_DIR/vflow_shell_exec"

# 支持的架构列表
ABIS=("armeabi-v7a" "arm64-v8a" "x86" "x86_64")

# 检查源文件是否存在
check_sources() {
    echo_step "Checking build outputs..."
    local missing=0

    for ABI in "${ABIS[@]}"; do
        local SOURCE="$BUILD_DIR/$ABI/vflow_shell_exec"
        if [ ! -f "$SOURCE" ]; then
            echo_error "  ✗ Missing: $SOURCE"
            ((missing++))
        else
            local SIZE=$(ls -lh "$SOURCE" | awk '{print $5}')
            echo_info "  ✓ Found: $ABI ($SIZE)"
        fi
    done

    if [ $missing -gt 0 ]; then
        echo_error ""
        echo_error "Missing $missing architecture(s). Please run ./build.sh first."
        exit 1
    fi

    echo ""
}

# 部署到 assets
deploy_to_assets() {
    echo_step "Deploying to assets directory..."
    echo ""

    # 创建目标目录
    mkdir -p "$TARGET_DIR"

    for ABI in "${ABIS[@]}"; do
        local SOURCE="$BUILD_DIR/$ABI/vflow_shell_exec"
        local TARGET="$TARGET_DIR/$ABI/vflow_shell_exec"

        # 创建 ABI 子目录
        mkdir -p "$TARGET_DIR/$ABI"

        # 复制文件
        echo_info "Deploying $ABI..."
        cp "$SOURCE" "$TARGET"

        # 验证复制
        if [ -f "$TARGET" ]; then
            local SIZE=$(ls -lh "$TARGET" | awk '{print $5}')
            echo_info "  ✓ Copied to: $TARGET ($SIZE)"
        else
            echo_error "  ✗ Failed to copy"
            exit 1
        fi
    done

    echo ""
}

# 生成 SHA256 校验和（可选）
generate_checksums() {
    echo_step "Generating SHA256 checksums..."

    local CHECKSUM_FILE="$TARGET_DIR/checksums.sha256"

    if command -v shasum &> /dev/null; then
        # macOS
        (
            cd "$TARGET_DIR"
            for ABI in "${ABIS[@]}"; do
                shasum -a 256 "$ABI/vflow_shell_exec"
            done
        ) > "$CHECKSUM_FILE"
        echo_info "  ✓ Checksums saved to: $CHECKSUM_FILE"
    elif command -v sha256sum &> /dev/null; then
        # Linux
        (
            cd "$TARGET_DIR"
            for ABI in "${ABIS[@]}"; do
                sha256sum "$ABI/vflow_shell_exec"
            done
        ) > "$CHECKSUM_FILE"
        echo_info "  ✓ Checksums saved to: $CHECKSUM_FILE"
    else
        echo_warn "  ⚠ Neither shasum nor sha256sum available, skipping checksums"
    fi

    echo ""
}

# 显示部署摘要
show_summary() {
    echo_step "Deployment Summary"
    echo ""
    echo_info "Target directory: $TARGET_DIR"
    echo ""

    echo_info "Deployed files:"
    for ABI in "${ABIS[@]}"; do
        local TARGET="$TARGET_DIR/$ABI/vflow_shell_exec"
        if [ -f "$TARGET" ]; then
            local SIZE=$(ls -lh "$TARGET" | awk '{print $5}')
            echo_info "  ✓ $ABI/vflow_shell_exec ($SIZE)"
        fi
    done

    # 显示 checksums（如果存在）
    if [ -f "$TARGET_DIR/checksums.sha256" ]; then
        echo_info "  ✓ checksums.sha256"
    fi

    echo ""
    echo_info "Total size:"
    local TOTAL_SIZE=$(du -sh "$TARGET_DIR" | awk '{print $1}')
    echo_info "  $TOTAL_SIZE"
    echo ""

    echo_info "Next steps:"
    echo_info "  1. Build and install the app"
    echo_info "  2. vflow_shell_exec will be deployed at runtime to:"
    echo_info "     /data/data/com.chaomixian.vflow/files/bin/vflow_shell_exec"
    echo ""
}

# 主函数
main() {
    echo_info "========================================="
    echo_info "vflow_shell_exec Deployment Script"
    echo_info "========================================="
    echo ""

    # 检查源文件
    check_sources

    # 部署到 assets
    deploy_to_assets

    # 生成校验和
    generate_checksums

    # 显示摘要
    show_summary

    echo_info "========================================="
    echo_info "Deployment completed successfully!"
    echo_info "========================================="
}

# 执行主函数
main "$@"
