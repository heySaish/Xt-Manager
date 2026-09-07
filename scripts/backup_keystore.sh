#!/bin/sh
# Backup or restore release keystore to/from external SD card storage (/sdcard/Xt-Manager-Keystore/)

KEYSTORE_DIR="/sdcard/Xt-Manager-Keystore"
mkdir -p "$KEYSTORE_DIR" 2>/dev/null

if [ -f "app/release.keystore" ]; then
    cp "app/release.keystore" "$KEYSTORE_DIR/release.keystore"
    echo "✅ Keystore backed up to $KEYSTORE_DIR/release.keystore"
elif [ -f "$KEYSTORE_DIR/release.keystore" ]; then
    cp "$KEYSTORE_DIR/release.keystore" "app/release.keystore"
    echo "✅ Keystore restored from $KEYSTORE_DIR/release.keystore to app/release.keystore"
else
    echo "ℹ️ No release.keystore found in app/ or $KEYSTORE_DIR"
fi
