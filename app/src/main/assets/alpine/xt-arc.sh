#!/bin/sh
# xt-arc.sh - Universal Archive Helper Script for Xt-Manager Alpine PRoot
set -e

ACTION="$1"
shift

if [ "$ACTION" = "compress" ]; then
    FORMAT="$1"
    OUTPUT="$2"
    shift 2
    SOURCES="$@"

    if [ -z "$FORMAT" ] || [ -z "$OUTPUT" ] || [ -z "$SOURCES" ]; then
        echo "Usage: xt-arc.sh compress <format> <output_archive> <sources...>"
        exit 1
    fi

    mkdir -p "$(dirname "$OUTPUT")"
    FIRST_SRC="$(echo "$SOURCES" | awk '{print $1}')"
    PARENT_DIR="$(dirname "$FIRST_SRC")"
    
    REL_SOURCES=""
    for s in $SOURCES; do
        REL_SOURCES="$REL_SOURCES $(basename "$s")"
    done

    echo "⚡ [xt-arc] Compressing format: $FORMAT -> $OUTPUT"
    case "$FORMAT" in
        "zip"|"ZIP")
            if command -v 7z >/dev/null 2>&1; then
                7z a -tzip -bsp1 -y -mx=5 -mmt=on "$OUTPUT" $SOURCES
            elif command -v 7za >/dev/null 2>&1; then
                7za a -tzip -bsp1 -y -mx=5 -mmt=on "$OUTPUT" $SOURCES
            elif command -v zip >/dev/null 2>&1; then
                zip -r -5 "$OUTPUT" $SOURCES
            else
                tar -czf "$OUTPUT" -C "$PARENT_DIR" $REL_SOURCES
            fi
            ;;
        "7z"|"SEVEN_Z"|"7Z")
            if command -v 7z >/dev/null 2>&1; then
                7z a -t7z -bsp1 -y -mx=5 -mmt=on "$OUTPUT" $SOURCES
            elif command -v 7za >/dev/null 2>&1; then
                7za a -t7z -bsp1 -y -mx=5 -mmt=on "$OUTPUT" $SOURCES
            else
                echo "Error: 7z binary not found in Alpine environment"
                exit 1
            fi
            ;;
        "tar.gz"|"TAR_GZ"|"tgz")
            tar -czf "$OUTPUT" -C "$PARENT_DIR" $REL_SOURCES
            ;;
        "tar.xz"|"TAR_XZ"|"txz")
            tar -cJf "$OUTPUT" -C "$PARENT_DIR" $REL_SOURCES
            ;;
        *) # Default TAR
            tar -cf "$OUTPUT" -C "$PARENT_DIR" $REL_SOURCES
            ;;
    esac
    echo "✅ [xt-arc] Compression completed successfully!"

elif [ "$ACTION" = "extract" ]; then
    ARCHIVE="$1"
    DEST_DIR="$2"
    PASSWORD="$3"

    if [ -z "$ARCHIVE" ] || [ -z "$DEST_DIR" ]; then
        echo "Usage: xt-arc.sh extract <archive> <destination_dir> [password]"
        exit 1
    fi

    mkdir -p "$DEST_DIR"
    echo "⚡ [xt-arc] Extracting $ARCHIVE -> $DEST_DIR"

    LOWER_ARCHIVE="$(echo "$ARCHIVE" | tr '[:upper:]' '[:lower:]')"
    case "$LOWER_ARCHIVE" in
        *.7z|*.rar)
            PASS_FLAG=""
            [ -n "$PASSWORD" ] && PASS_FLAG="-p$PASSWORD"
            if command -v 7z >/dev/null 2>&1; then
                7z x "$ARCHIVE" "-o$DEST_DIR" -bsp1 -y -mmt=on $PASS_FLAG
            elif command -v 7za >/dev/null 2>&1; then
                7za x "$ARCHIVE" "-o$DEST_DIR" -bsp1 -y -mmt=on $PASS_FLAG
            else
                echo "Error: 7z binary not found in Alpine environment"
                exit 1
            fi
            ;;
        *.zip|*.apk|*.jar)
            PASS_FLAG=""
            [ -n "$PASSWORD" ] && PASS_FLAG="-p$PASSWORD"
            if command -v 7z >/dev/null 2>&1; then
                7z x "$ARCHIVE" "-o$DEST_DIR" -bsp1 -y -mmt=on $PASS_FLAG
            elif command -v unzip >/dev/null 2>&1; then
                unzip -o "$ARCHIVE" -d "$DEST_DIR" $PASS_FLAG
            else
                tar --no-same-owner --no-same-permissions -xf "$ARCHIVE" -C "$DEST_DIR"
            fi
            ;;
        *) # TAR, TAR.GZ, TAR.XZ, etc.
            tar --no-same-owner --no-same-permissions -xf "$ARCHIVE" -C "$DEST_DIR"
            ;;
    esac
    echo "✅ [xt-arc] Extraction completed successfully!"

else
    echo "Usage: xt-arc.sh [compress|extract] ..."
    exit 1
fi
