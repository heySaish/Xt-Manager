export LD_LIBRARY_PATH=$PREFIX

mkdir -p "$PREFIX/tmp"
mkdir -p "$PREFIX/alpine/tmp"
mkdir -p "$PREFIX/public"

export PROOT_TMP_DIR=$PREFIX/tmp

if [ "$FDROID" = "true" ]; then

    if [ -f "$PREFIX/libproot.so" ]; then
        export PROOT_LOADER="$PREFIX/libproot.so"
    fi

    if [ -f "$PREFIX/libproot32.so" ]; then
        export PROOT_LOADER32="$PREFIX/libproot32.so"
    fi

    export PROOT="$PREFIX/libproot-xed.so"
    for f in "$PREFIX"/*.so "$PREFIX"/*.sh; do
        [ -f "$f" ] && [ ! -L "$f" ] && chmod +x "$f" 2>/dev/null || true
    done
else
    if [ -f "$NATIVE_DIR/libproot.so" ]; then
        export PROOT_LOADER="$NATIVE_DIR/libproot.so"
    fi

    if [ -f "$NATIVE_DIR/libproot32.so" ]; then
        export PROOT_LOADER32="$NATIVE_DIR/libproot32.so"
    fi

    export PROOT="$NATIVE_DIR/libproot-xed.so"
fi

# Ensure libtalloc.so.2 link is created for the linker
if [ -e "$PREFIX/libtalloc.so.2" ] || [ -L "$PREFIX/libtalloc.so.2" ]; then
    rm -f "$PREFIX/libtalloc.so.2"
fi

if [ -f "$PREFIX/libtalloc.so" ]; then
    ln -s "$PREFIX/libtalloc.so" "$PREFIX/libtalloc.so.2" 2>/dev/null || true
elif [ -f "$NATIVE_DIR/libtalloc.so" ]; then
    ln -s "$NATIVE_DIR/libtalloc.so" "$PREFIX/libtalloc.so.2" 2>/dev/null || true
fi

ARGS="--kill-on-exit"

for system_mnt in /apex /odm /product /system /system_ext /vendor /linkerconfig/ld.config.txt /linkerconfig/com.android.art/ld.config.txt /plat_property_contexts /property_contexts; do
 if [ -e "$system_mnt" ]; then
  system_mnt=$(readlink -f "$system_mnt" 2>/dev/null || echo "$system_mnt")
  ARGS="$ARGS -b ${system_mnt}"
 fi
done

unset system_mnt

ARGS="$ARGS -b /sdcard"
ARGS="$ARGS -b /storage"
ARGS="$ARGS -b /dev"
ARGS="$ARGS -b /data"
ARGS="$ARGS -b /dev/urandom:/dev/random"
ARGS="$ARGS -b /proc"
ARGS="$ARGS -b /sys"
ARGS="$ARGS -b $PREFIX"
ARGS="$ARGS -b $NATIVE_DIR"
ARGS="$ARGS -b $PREFIX/public:/public"
ARGS="$ARGS -b $PREFIX/public:/home"
ARGS="$ARGS -b $PREFIX/public:/root"
if [ -n "$INITIAL_CWD" ] && [ -d "$INITIAL_CWD" ]; then
    ARGS="$ARGS -w $INITIAL_CWD"
else
    ARGS="$ARGS -w /public"
fi
ARGS="$ARGS -b $PREFIX/alpine/tmp:/dev/shm"


if [ -e "/proc/self/fd" ]; then
  ARGS="$ARGS -b /proc/self/fd:/dev/fd"
fi

if [ -e "/proc/self/fd/0" ]; then
  ARGS="$ARGS -b /proc/self/fd/0:/dev/stdin"
fi

if [ -e "/proc/self/fd/1" ]; then
  ARGS="$ARGS -b /proc/self/fd/1:/dev/stdout"
fi

if [ -e "/proc/self/fd/2" ]; then
  ARGS="$ARGS -b /proc/self/fd/2:/dev/stderr"
fi


ARGS="$ARGS -r $PREFIX/alpine"
ARGS="$ARGS -0"
ARGS="$ARGS --link2symlink"
ARGS="$ARGS --sysvipc"
ARGS="$ARGS -L"


FAILSAFE=false
INSTALLING=false

for arg in "$@"; do
    case "$arg" in
        --failsafe)
            FAILSAFE=true
            ;;
        --installing)
            INSTALLING=true
            ;;
    esac
done

if [ "$FAILSAFE" = true ] && [ "$INSTALLING" != true ]; then
    echo "$$" > "$PREFIX/pid"

    LINKER="/system/bin/linker64"
    ARCH="$(uname -m)"
    if [ "$ARCH" != "aarch64" ] && [ "$ARCH" != "x86_64" ]; then
        LINKER="/system/bin/linker"
    fi

    exec "$PROOT" $ARGS /bin/sh
else
    exec "$PROOT" $ARGS /bin/sh "$PREFIX/init-alpine.sh" "$@"
fi