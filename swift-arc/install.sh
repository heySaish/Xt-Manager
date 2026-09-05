#!/usr/bin/env bash
set -e

echo -e "\033[38;5;51m\033[1m⚡ Installing SWIFT-ARC System...\033[0m"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SWIFT_SRC="$SCRIPT_DIR/swift-arc"

chmod +x "$SWIFT_SRC"

# Determine target binary directory
TARGET_DIR=""
if [ -n "$PREFIX" ] && [ -d "$PREFIX/bin" ]; then
    TARGET_DIR="$PREFIX/bin"
elif [ -d "$HOME/.local/bin" ]; then
    TARGET_DIR="$HOME/.local/bin"
elif [ -w "/usr/local/bin" ]; then
    TARGET_DIR="/usr/local/bin"
else
    TARGET_DIR="$HOME/bin"
    mkdir -p "$TARGET_DIR"
fi

echo -e "\033[38;5;245m↳ Target binary directory: \033[38;5;84m$TARGET_DIR\033[0m"

# Create symlinks or copy executables
cp "$SWIFT_SRC" "$TARGET_DIR/swift-arc"
cp "$SWIFT_SRC" "$TARGET_DIR/extract"
cp "$SWIFT_SRC" "$TARGET_DIR/compress"
cp "$SWIFT_SRC" "$TARGET_DIR/arc"

chmod +x "$TARGET_DIR/swift-arc" "$TARGET_DIR/extract" "$TARGET_DIR/compress" "$TARGET_DIR/arc"

# Setup shell aliases in bashrc / zshrc
SHELL_RC=""
if [ -f "$HOME/.bashrc" ]; then
    SHELL_RC="$HOME/.bashrc"
elif [ -f "$HOME/.zshrc" ]; then
    SHELL_RC="$HOME/.zshrc"
fi

if [ -n "$SHELL_RC" ]; then
    if ! grep -q "SWIFT-ARC ALIASES" "$SHELL_RC"; then
        cat << 'EOF' >> "$SHELL_RC"

# --- SWIFT-ARC ALIASES ---
alias ex='extract'
alias cm='compress'
alias pack='compress'
alias unpack='extract'
# -------------------------
EOF
        echo -e "\033[38;5;245m↳ Added shell aliases (ex, cm, pack, unpack) to \033[38;5;220m$SHELL_RC\033[0m"
    fi
fi

echo -e "\n\033[38;5;84m\033[1m✅ Installation Successful!\033[0m"
echo -e "\033[38;5;245mYou can now use these quick commands anywhere in your terminal:\033[0m"
echo -e "  \033[38;5;51mextract file.zip\033[0m        - Extract any archive instantly (auto-detects zip, 7z, tar.gz, etc.)"
echo -e "  \033[38;5;51mcompress folder/\033[0m        - Fast multi-threaded compression"
echo -e "  \033[38;5;51marc info archive.7z\033[0m     - View archive information & preview files"
echo -e "  \033[38;5;51marc --help\033[0m              - Show command help"
