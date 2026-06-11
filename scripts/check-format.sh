#!/usr/bin/env bash
# vim:ts=4:sts=4:sw=4:et
# shellcheck disable=SC2317,SC2329

# SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
# SPDX-License-Identifier: Apache-2.0
#
# Contact Magic Lane at <info@magiclane.com> for SDK licensing options.

set -eEuo pipefail

declare -r COLOR_RESET="\033[0m"
declare -r COLOR_RED="\033[31;1m"
declare -r COLOR_GREEN="\033[32;1m"
declare -r COLOR_YELLOW="\033[33;1m"
declare -r COLOR_BLUE="\033[34;1m"
declare -r COLOR_CYAN="\033[36;1m"

function log_timestamp()
{
    date "+%Y-%m-%d %H:%M:%S"
}

function log_info()
{
    printf '%b\n' "${COLOR_CYAN}[$(log_timestamp)] [INFO]${COLOR_RESET} $*"
}

function log_success()
{
    printf '%b\n' "${COLOR_GREEN}[$(log_timestamp)] [SUCCESS]${COLOR_RESET} $*"
}

function log_warning()
{
    printf '%b\n' "${COLOR_YELLOW}[$(log_timestamp)] [WARNING]${COLOR_RESET} $*"
}

function log_error()
{
    printf '%b\n' "${COLOR_RED}[$(log_timestamp)] [ERROR]${COLOR_RESET} $*" >&2
}

function log_step()
{
    printf '\n%b\n\n' "${COLOR_BLUE}[$(log_timestamp)] [STEP]${COLOR_RESET} $*"
}

function check_cmd()
{
    command -v "${1}" >/dev/null 2>&1
}

function is_mac()
{
    local OS_NAME
    OS_NAME="$(uname | tr '[:upper:]' '[:lower:]')"
    [[ "${OS_NAME}" =~ darwin ]]
}

function setup_mac_deps()
{
    is_mac || return 0

    if ! check_cmd brew; then
        log_error "Missing Homebrew. Run:"
        # shellcheck disable=SC2016
        log_error '$ bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"'
        exit 1
    fi

    local BREW_PREFIX
    BREW_PREFIX="$(brew --prefix)"

    # package:path_suffix
    local -a DEPS=(
        "grep:grep/libexec/gnubin"
        "coreutils:coreutils/libexec/gnubin"
        "findutils:findutils/libexec/gnubin"
    )

    local DEP PKG PATH_SUFFIX
    for DEP in "${DEPS[@]}"; do
        PKG="${DEP%%:*}"
        PATH_SUFFIX="${DEP##*:}"

        if ! brew ls --versions "${PKG}" > /dev/null 2>&1; then
            log_error "Missing ${PKG}. Run 'brew install ${PKG}'"
            exit 1
        fi

        export PATH="${BREW_PREFIX}/opt/${PATH_SUFFIX}:${PATH}"
    done
}

GRADLE_WRAPPER=""
PROJECT_DIR=""
FORMAT_OUTPUT=""

function dist_clean()
{
    if [[ -n "${PROJECT_DIR}" ]] && [[ -d "${PROJECT_DIR}/.gradle" ]]; then
        find "${PROJECT_DIR}" -type d -name "build" -exec rm -rf {} + 2>/dev/null || true
        find "${PROJECT_DIR}" -type d -name ".gradle" -exec rm -rf {} + 2>/dev/null || true
        find "${PROJECT_DIR}" -type d -name ".idea" -exec rm -rf {} + 2>/dev/null || true
        find "${PROJECT_DIR}" -type d -name ".kotlin" -exec rm -rf {} + 2>/dev/null || true
        find "${PROJECT_DIR}" -type f -name "local.properties" -exec rm {} + 2>/dev/null || true
    fi
}

function ctrl_c()
{
    exit 1
}
trap ctrl_c INT TERM

function on_error()
{
    local EXIT_CODE="${1:-1}"
    local LINE="${2:-unknown}"
    local COMMAND="${3:-unknown}"

    log_error "Command failed at line ${LINE}: ${COMMAND}"
    log_error "Exit code: ${EXIT_CODE}"

    if [[ ${#FUNCNAME[@]} -gt 2 ]]; then
        log_error "Call stack:"
        for ((I = 1; I < ${#FUNCNAME[@]} - 1; I++)); do
            log_error "  ${FUNCNAME[I]}() at ${BASH_SOURCE[I]}:${BASH_LINENO[I - 1]}"
        done
    fi
}
trap 'on_error "$?" "${LINENO}" "${BASH_COMMAND}"' ERR

function on_exit()
{
    local EXIT_CODE=$?
    set +e

    dist_clean

    if [[ -n "${FORMAT_OUTPUT}" ]] && [[ -f "${FORMAT_OUTPUT}" ]]; then
        rm -f "${FORMAT_OUTPUT}"
    fi

    if [[ -n "${GRADLE_WRAPPER}" ]]; then
        "${GRADLE_WRAPPER}" --stop || true
    fi

    if [[ ${EXIT_CODE} -eq 0 ]]; then
        log_success "Format completed successfully"
    else
        log_error "Format failed (exit code ${EXIT_CODE})"
    fi

    printf '\n'
    log_info "Bye-Bye"

    exit "${EXIT_CODE}"
}
trap on_exit EXIT

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "${SCRIPT_DIR}")"

setup_mac_deps

log_step "Checking prerequisites..."

if [[ -z "${ANDROID_SDK_ROOT:-}" ]]; then
    log_error "ANDROID_SDK_ROOT not set. Please export ANDROID_SDK_ROOT env. variable"
    exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]]; then
    log_error "JAVA_HOME not set. Please export JAVA_HOME env. variable"
    exit 1
fi

if [[ ! -x "${JAVA_HOME}/bin/java" ]]; then
    log_error "JAVA_HOME does not contain a runnable java binary: ${JAVA_HOME}/bin/java"
    exit 1
fi

JAVA_VERSION_LINE="$("${JAVA_HOME}/bin/java" -version 2>&1 | head -n 1)"
if ! printf '%s\n' "${JAVA_VERSION_LINE}" | grep -Eq 'version "((17|21)(\.|"))'; then
    log_error "Wrong Java version. Need 17 or 21. Found: ${JAVA_VERSION_LINE}"
    exit 1
fi

GRADLE_WRAPPER="$(find "${PROJECT_DIR}" -maxdepth 1 -type f -executable -name gradlew -print -quit)"
if [[ -z "${GRADLE_WRAPPER}" ]]; then
    log_error "gradlew not found in ${PROJECT_DIR}"
    exit 1
fi

GRADLE_OPTS="-Xms4g -Xmx4g"
GRADLE_OPTS="${GRADLE_OPTS} -Dorg.gradle.daemon=false"
GRADLE_OPTS="${GRADLE_OPTS} -Dfile.encoding=UTF-8"
export GRADLE_OPTS

pushd "${PROJECT_DIR}" &> /dev/null

log_step "Cleaning build artifacts..."
dist_clean

log_step "Running 'formatAll' Gradle task..."

FORMAT_OUTPUT="$(mktemp)"

set +e
"${GRADLE_WRAPPER}" --no-parallel --no-watch-fs --no-build-cache --warning-mode all --console=plain formatAll 2>&1 | tee "${FORMAT_OUTPUT}"
FORMAT_EXIT_CODE=${PIPESTATUS[0]}
set -e

popd &> /dev/null

log_step "Checking results..."

KTLINT_ERRORS=0
if grep -qE "\(cannot be auto-corrected\)" "${FORMAT_OUTPUT}"; then
    log_error "Found ktlint issues that cannot be auto-corrected:"
    grep -E "\.kt:[0-9]+:[0-9]+.*\(cannot be auto-corrected\)" "${FORMAT_OUTPUT}" | sort -u | while read -r LINE; do
        log_error "  ${LINE}"
    done
    KTLINT_ERRORS=1
fi

if [[ ${FORMAT_EXIT_CODE} -ne 0 ]]; then
    log_error "Format task failed with exit code ${FORMAT_EXIT_CODE}"
    exit 1
fi

if [[ ${KTLINT_ERRORS} -ne 0 ]]; then
    log_error "Format completed but some issues require manual fixes"
    exit 1
fi

exit 0
