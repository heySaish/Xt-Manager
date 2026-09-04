export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin:$PREFIX/local/bin
export PS1="\[\e[38;5;46m\]\u\[\033[39m\]@localhost \[\033[39m\]\w \[\033[0m\]\\$ "
export HOME=/public
export TERM=xterm-256color

INSTALLING=false
FAILSAFE=false

# Parse internal flags
while [ $# -gt 0 ]; do
    case "$1" in
        --installing)
            INSTALLING=true
            shift
            ;;
        --failsafe)
            FAILSAFE=true
            shift
            ;;
        --)
            shift
            break
            ;;
        *)
            break
            ;;
    esac
done

# If a command was supplied, execute it and exit
# without it Executor will break
if [ "$INSTALLING" != true ] && [ $# -gt 0 ] && [ "${1#--}" = "$1" ]; then
    exec "$@"
fi

if [ ! -f /linkerconfig/ld.config.txt ]; then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi


if [ "$INSTALLING" = true ]; then
    echo "Configuring timezone..."

    if [ -n "$ANDROID_TZ" ] && [ -f "/usr/share/zoneinfo/$ANDROID_TZ" ]; then
        ln -sf "/usr/share/zoneinfo/$ANDROID_TZ" /etc/localtime
        echo "$ANDROID_TZ" > /etc/timezone
        echo "Timezone set to: $ANDROID_TZ"
    else
        echo "Failed to detect timezone"
    fi

    mkdir -p "$PREFIX/.configured"

    if [ ! -f "$HOME/.bashrc" ]; then
       touch "$HOME/.bashrc" && chmod 644 "$HOME/.bashrc"
    fi

    echo "Installation completed."
    exit 0
fi



    echo "$$" > "$PREFIX/pid"

    if [ ! -e "$PREFIX/alpine/etc/xtmanager_motd" ]; then
        cat <<'EOF' > "$PREFIX/alpine/etc/xtmanager_motd"
\e[1;34mWelcome to Alpine Linux (Xt-Manager)\e[0m

  * Packages:  \e[32mapk add <package>\e[0m
  * Setup:     \e[32mxt-setup-alpine\e[0m
  * Workspace: \e[33m/public\e[0m (~)
  * Storage:   \e[33m/sdcard\e[0m

EOF
    fi

    # Create xtmanager CLI tool
    if [ ! -e "$PREFIX/alpine/usr/local/bin/xtmanager" ]; then
        mkdir -p "$PREFIX/alpine/usr/local/bin"
        cat <<'XTMANAGER_CLI' > "$PREFIX/alpine/usr/local/bin/xtmanager"
#!/bin/bash
# xtmanager - Open files/folders in Xt-Manager
# Uses OSC escape sequences to communicate with the terminal

usage() {
    echo "Usage: xtmanager [file/folder...]"
    echo ""
    echo "Open files or folders in Xt-Manager."
    echo ""
    echo "Examples:"
    echo "  xtmanager file.txt      # Open a file"
    echo "  xtmanager .             # Open current folder"
    echo "  xtmanager ~/project     # Open a folder"
    echo "  xtmanager -h, --help    # Show this help"
}

get_abs_path() {
    local path="$1"
    local abs_path=""

    if command -v realpath >/dev/null 2>&1; then
        abs_path=$(realpath -- "$path" 2>/dev/null)
    fi

    if [[ -z "$abs_path" ]]; then
        if [[ -d "$path" ]]; then
            abs_path=$(cd -- "$path" 2>/dev/null && pwd -P)
        elif [[ -e "$path" ]]; then
            local dir_name file_name
            dir_name=$(dirname -- "$path")
            file_name=$(basename -- "$path")
            abs_path="$(cd -- "$dir_name" 2>/dev/null && pwd -P)/$file_name"
        elif [[ "$path" == /* ]]; then
            abs_path="$path"
        else
            abs_path="$PWD/$path"
        fi
    fi

    echo "$abs_path"
}

open_in_xtmanager() {
    local path=$(get_abs_path "$1")
    local type="file"
    [[ -d "$path" ]] && type="folder"

    # Send OSC 7777 escape sequence: \e]7777;cmd;type;path\a
    printf '\e]7777;open;%s;%s\a' "$type" "$path"
}

if [[ $# -eq 0 ]]; then
    open_in_xtmanager "."
    exit 0
fi

for arg in "$@"; do
    case "$arg" in
        -h|--help)
            usage
            exit 0
            ;;
        *)
            if [[ -e "$arg" ]]; then
                open_in_xtmanager "$arg"
            else
                echo "Error: '$arg' does not exist" >&2
                exit 1
            fi
            ;;
    esac
done
XTMANAGER_CLI
        chmod +x "$PREFIX/alpine/usr/local/bin/xtmanager"
    fi

    # Create xt-setup-alpine CLI tool
    if [ ! -e "$PREFIX/alpine/usr/local/bin/xt-setup-alpine" ]; then
        mkdir -p "$PREFIX/alpine/usr/local/bin"
        cat <<'SETUP_CLI' > "$PREFIX/alpine/usr/local/bin/xt-setup-alpine"
#!/bin/sh
# xt-setup-alpine - Manual setup helper for Alpine Linux environment

echo -e "\e[34;1m[*] \e[0mSetting up Alpine Linux environment...\e[0m"

required_packages="bash command-not-found tzdata wget"
missing_packages=""

for pkg in $required_packages; do
    if ! apk info -e "$pkg" >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done

if [ -n "$missing_packages" ]; then
    echo -e "\e[34;1m[*] \e[0mUpdating package lists and installing essential packages:$missing_packages...\e[0m"
    apk update
    apk add $missing_packages
    if [ $? -eq 0 ]; then
        echo -e "\e[32;1m[+] \e[0mSuccessfully installed essential packages!\e[0m"
    else
        echo -e "\e[31;1m[-] \e[0mFailed to install some packages. Check your internet connection.\e[0m"
    fi
else
    echo -e "\e[32;1m[+] \e[0mAll essential packages ($required_packages) are already installed!\e[0m"
fi

if [ -n "$ANDROID_TZ" ] && [ -f "/usr/share/zoneinfo/$ANDROID_TZ" ]; then
    ln -sf "/usr/share/zoneinfo/$ANDROID_TZ" /etc/localtime 2>/dev/null
    echo "$ANDROID_TZ" > /etc/timezone 2>/dev/null
    echo -e "\e[32;1m[+] \e[0mTimezone configured: $ANDROID_TZ"
fi

echo -e "\e[32;1m[+] \e[0mXt-Manager Alpine setup complete!\e[0m"
SETUP_CLI
        chmod +x "$PREFIX/alpine/usr/local/bin/xt-setup-alpine"
    fi

    # Create initrc if it doesn't exist
    #initrc runs in bash so we can use bash features
if [ ! -e "$PREFIX/alpine/initrc" ]; then
    cat <<'EOF' > "$PREFIX/alpine/initrc"
# Source rc files if they exist

if [ -f "/etc/profile" ]; then
    source "/etc/profile"
fi

# Environment setup
export PATH=$PATH:/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin

export HOME=/public
export TERM=xterm-256color
SHELL=/bin/bash
export PIP_BREAK_SYSTEM_PACKAGES=1
export PS1='\[\033[1;32m\]alpine\[\033[0m\]:\[\033[1;34m\]\w\[\033[0m\]\$ '

# Display MOTD if available
if [ -s /etc/xtmanager_motd ]; then
    echo -e "$(cat /etc/xtmanager_motd)"
fi

# Replicate behaviour of termux
alias clear='reset'

if [ -f "$HOME/.bashrc" ]; then
    source "$HOME/.bashrc"
fi

EOF
fi


chmod +x "$PREFIX/alpine/initrc"

# First-time terminal open script execution
if [ ! -f "$PREFIX/.first_boot_script_run" ]; then
    if [ -f "$PREFIX/setup-alpine.sh" ]; then
        /bin/sh "$PREFIX/setup-alpine.sh" 2>/dev/null || true
    elif [ -f /setup-alpine.sh ]; then
        /bin/sh /setup-alpine.sh 2>/dev/null || true
    fi
    touch "$PREFIX/.first_boot_script_run" 2>/dev/null || true
fi

# Shell Login System: Bash if installed, otherwise fallback to /bin/sh
if [ -x /bin/bash ]; then
    exec /bin/bash --rcfile /initrc -i
elif [ -x /usr/bin/bash ]; then
    exec /usr/bin/bash --rcfile /initrc -i
elif [ -x "$PREFIX/alpine/bin/bash" ]; then
    exec "$PREFIX/alpine/bin/bash" --rcfile /initrc -i
elif [ -x "$PREFIX/alpine/usr/bin/bash" ]; then
    exec "$PREFIX/alpine/usr/bin/bash" --rcfile /initrc -i
else
    exec /bin/sh -i
fi
