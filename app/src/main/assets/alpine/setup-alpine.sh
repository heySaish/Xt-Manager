#!/bin/sh

set -e

echo "[1/4] Setting DNS..."
printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > /etc/resolv.conf

echo "[2/4] Adding Alpine Edge repositories..."

cat >> /etc/apk/repositories <<'EOF'
https://dl-cdn.alpinelinux.org/alpine/edge/main
https://dl-cdn.alpinelinux.org/alpine/edge/community
EOF

echo "[3/4] Updating APK indexes..."
apk update || true

echo "[4/4] Installing Bash..."
apk add bash || true

echo
echo "================================"
echo " Alpine setup completed! 🐧"
echo "================================"
echo
if command -v bash >/dev/null 2>&1; then
    echo "Bash: $(bash --version | head -n 1)"
else
    echo "Fallback: Using /bin/sh"
fi
