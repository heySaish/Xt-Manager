#!/bin/sh

# Xt Manager Universal Archive Helper
# Alpine PRoot
#
# Usage:
#   xt-arc.sh extract ARCHIVE OUTPUT_DIR
#   xt-arc.sh compress FORMAT OUTPUT ARCHIVE_INPUT...
#
# Supported:
#   zip, 7z, tar, tar.gz/tgz, tar.bz2/tbz2, tar.xz/txz,
#   tar.zst/tzst, gz, bz2, xz, zst

set -eu

export PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin:${PATH:-}"

PROG="xt-arc"

log() {
    printf '[%s] %s\n' "$PROG" "$*" >&2
}

die() {
    printf '[%s] ERROR: %s\n' "$PROG" "$*" >&2
    exit 1
}

need() {
    command -v "$1" >/dev/null 2>&1 || die "Required tool not found: $1"
}

progress_pipe() {
    # pv output numeric (0..100) ko stderr par rakho, taaki Kotlin directly progress update kar sake.
    if command -v pv >/dev/null 2>&1; then
        pv -n
    else
        cat
    fi
}

detect_format() {
    file="$1"
    name=$(basename "$file")
    lower=$(printf '%s' "$name" | tr '[:upper:]' '[:lower:]')

    case "$lower" in
        *.tar.gz|*.tgz)
            printf '%s\n' "tar.gz"
            ;;
        *.tar.bz2|*.tbz|*.tbz2)
            printf '%s\n' "tar.bz2"
            ;;
        *.tar.xz|*.txz)
            printf '%s\n' "tar.xz"
            ;;
        *.tar.zst|*.tzst)
            printf '%s\n' "tar.zst"
            ;;
        *.tar)
            printf '%s\n' "tar"
            ;;
        *.7z)
            printf '%s\n' "7z"
            ;;
        *.zip)
            printf '%s\n' "zip"
            ;;
        *.gz)
            printf '%s\n' "gz"
            ;;
        *.bz2)
            printf '%s\n' "bz2"
            ;;
        *.xz)
            printf '%s\n' "xz"
            ;;
        *.zst)
            printf '%s\n' "zst"
            ;;
        *)
            # Extension unknown, magic-based detection.
            need file

            mime=$(file -b --mime-type "$file" 2>/dev/null || true)

            case "$mime" in
                application/zip)
                    printf '%s\n' "zip"
                    ;;
                application/x-7z-compressed)
                    printf '%s\n' "7z"
                    ;;
                application/x-tar)
                    printf '%s\n' "tar"
                    ;;
                application/gzip)
                    printf '%s\n' "gz"
                    ;;
                application/x-bzip2)
                    printf '%s\n' "bz2"
                    ;;
                application/x-xz)
                    printf '%s\n' "xz"
                    ;;
                application/zstd)
                    printf '%s\n' "zst"
                    ;;
                *)
                    die "Unknown archive format: $file"
                    ;;
            esac
            ;;
    esac
}

extract() {
    archive="$1"
    output="$2"

    [ -f "$archive" ] || die "Archive does not exist: $archive"

    mkdir -p "$output"

    format=$(detect_format "$archive")

    log "FORMAT=$format"
    log "INPUT=$archive"
    log "OUTPUT=$output"

    case "$format" in

        zip)
            need unzip

            size=$(wc -c < "$archive" | tr -d ' ')

            log "ENGINE=unzip"
            log "SIZE=$size"

            if command -v pv >/dev/null 2>&1; then
                pv -n "$archive" > "$output/.xt-archive.tmp.zip"
                unzip -o "$output/.xt-archive.tmp.zip" -d "$output"
                rm -f "$output/.xt-archive.tmp.zip"
            else
                unzip -o "$archive" -d "$output"
            fi
            ;;

        7z)
            need 7z

            log "ENGINE=7z"

            7z x \
                -y \
                -aoa \
                -mmt=on \
                "-o$output" \
                "$archive"
            ;;

        tar)
            need tar

            log "ENGINE=tar"

            if command -v pv >/dev/null 2>&1; then
                pv -n "$archive" | tar --no-same-owner -xf - -C "$output"
            else
                tar --no-same-owner -xf "$archive" -C "$output"
            fi
            ;;

        tar.gz)
            need tar
            need gzip

            log "ENGINE=tar+gzip"

            if command -v pv >/dev/null 2>&1; then
                pv -n "$archive" | gzip -dc | tar --no-same-owner -xf - -C "$output"
            else
                tar --no-same-owner -xzf "$archive" -C "$output"
            fi
            ;;

        tar.bz2)
            need tar
            need bzip2

            log "ENGINE=tar+bzip2"

            if command -v pv >/dev/null 2>&1; then
                pv -n "$archive" | bzip2 -dc | tar --no-same-owner -xf - -C "$output"
            else
                tar --no-same-owner -xjf "$archive" -C "$output"
            fi
            ;;

        tar.xz)
            need tar
            need xz

            log "ENGINE=tar+xz"

            if command -v pv >/dev/null 2>&1; then
                pv -n "$archive" | xz -dc -T0 | tar --no-same-owner -xf - -C "$output"
            else
                tar --no-same-owner -xJf "$archive" -C "$output"
            fi
            ;;

        tar.zst)
            need tar
            need zstd

            log "ENGINE=tar+zstd"

            if command -v pv >/dev/null 2>&1; then
                pv -n "$archive" | zstd -dc -T0 | tar --no-same-owner -xf - -C "$output"
            else
                zstd -dc -T0 "$archive" | tar --no-same-owner -xf - -C "$output"
            fi
            ;;

        gz)
            need gzip

            output_file="$output/$(basename "$archive" .gz)"

            log "ENGINE=gzip"
            log "OUTPUT_FILE=$output_file"

            if command -v pv >/dev/null 2>&1; then
                pv -n "$archive" | gzip -dc > "$output_file"
            else
                gzip -dc "$archive" > "$output_file"
            fi
            ;;

        bz2)
            need bzip2

            output_file="$output/$(basename "$archive" .bz2)"

            log "ENGINE=bzip2"
            log "OUTPUT_FILE=$output_file"

            if command -v pv >/dev/null 2>&1; then
                pv -n "$archive" | bzip2 -dc > "$output_file"
            else
                bzip2 -dc "$archive" > "$output_file"
            fi
            ;;

        xz)
            need xz

            output_file="$output/$(basename "$archive" .xz)"

            log "ENGINE=xz"
            log "OUTPUT_FILE=$output_file"

            if command -v pv >/dev/null 2>&1; then
                pv -n "$archive" | xz -dc -T0 > "$output_file"
            else
                xz -dc -T0 "$archive" > "$output_file"
            fi
            ;;

        zst)
            need zstd

            output_file="$output/$(basename "$archive" .zst)"

            log "ENGINE=zstd"
            log "OUTPUT_FILE=$output_file"

            if command -v pv >/dev/null 2>&1; then
                pv -n "$archive" | zstd -dc -T0 > "$output_file"
            else
                zstd -dc -T0 "$archive" > "$output_file"
            fi
            ;;

        *)
            die "Extraction not implemented for: $format"
            ;;
    esac

    log "STATUS=success"
}

compress() {
    format="$1"
    output="$2"
    shift 2

    [ "$#" -gt 0 ] || die "No input files/directories specified"

    mkdir -p "$(dirname "$output")"

    case "$format" in

        zip)
            need zip

            log "ENGINE=zip"
            zip -r "$output" "$@"
            ;;

        7z)
            need 7z

            log "ENGINE=7z"
            7z a \
                -y \
                -mmt=on \
                "$output" \
                "$@"
            ;;

        tar)
            need tar

            log "ENGINE=tar"
            tar -cf "$output" "$@"
            ;;

        tar.gz|tgz)
            need tar
            need gzip

            log "ENGINE=tar+gzip"
            tar -cf - "$@" |
                gzip |
                progress_pipe > "$output"
            ;;

        tar.bz2|tbz2)
            need tar
            need bzip2

            log "ENGINE=tar+bzip2"
            tar -cf - "$@" |
                bzip2 |
                progress_pipe > "$output"
            ;;

        tar.xz|txz)
            need tar
            need xz

            log "ENGINE=tar+xz"
            tar -cf - "$@" |
                xz -T0 |
                progress_pipe > "$output"
            ;;

        tar.zst|tzst)
            need tar
            need zstd

            log "ENGINE=tar+zstd"
            tar -cf - "$@" |
                zstd -T0 |
                progress_pipe > "$output"
            ;;

        *)
            die "Unsupported compression format: $format"
            ;;
    esac

    log "STATUS=success"
}

usage() {
    cat >&2 <<EOF
Xt Manager Archive Helper

Extract:
  xt-arc.sh extract ARCHIVE OUTPUT_DIR

Compress:
  xt-arc.sh compress FORMAT OUTPUT INPUT...

Examples:
  xt-arc.sh extract file.zip /tmp/out
  xt-arc.sh extract file.tar.xz /tmp/out

  xt-arc.sh compress zip archive.zip file1 file2 folder
  xt-arc.sh compress 7z archive.7z folder
  xt-arc.sh compress tar.xz archive.tar.xz folder
EOF
    exit 2
}

[ "$#" -ge 1 ] || usage

operation="$1"
shift

case "$operation" in
    extract)
        [ "$#" -eq 2 ] || usage
        extract "$1" "$2"
        ;;

    compress)
        [ "$#" -ge 3 ] || usage
        compress "$@"
        ;;

    *)
        die "Unknown operation: $operation"
        ;;
esac
