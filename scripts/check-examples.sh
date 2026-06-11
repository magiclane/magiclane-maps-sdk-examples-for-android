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
        "gnu-sed:gnu-sed/libexec/gnubin"
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

    if [[ ${EXIT_CODE} -eq 0 ]]; then
        log_success "All checks passed"
    else
        log_error "Some checks failed (exit code ${EXIT_CODE})"
    fi

    printf '\n'
    log_info "Bye-Bye"

    exit "${EXIT_CODE}"
}
trap on_exit EXIT

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(dirname "${SCRIPT_DIR}")"

setup_mac_deps

mapfile -t EXAMPLE_PROJECTS < <(
    find "${PROJECT_DIR}" -maxdepth 1 -type d -exec [ -d "{}/app/libs" ] \; -print 2>/dev/null | sort
)

if ((${#EXAMPLE_PROJECTS[@]} == 0)); then
    log_error "No examples found under ${PROJECT_DIR}"
    exit 1
fi

function check_package_names()
{
    local RC=0
    local EXAMPLE_NAME BUILD_GRADLE BASE_PACKAGE PACKAGE
    local MAIN_SRC ANDROID_TEST_SRC TEST_SRC MANIFEST
    local FILE

    # Associative array to track base package names across all examples
    declare -A ALL_PACKAGES

    for I in "${!EXAMPLE_PROJECTS[@]}"; do
        EXAMPLE_NAME="$(basename "${EXAMPLE_PROJECTS[I]}")"
        log_info "Check '${EXAMPLE_NAME}' for package consistency..."

        # Get base package from build.gradle.kts namespace (required)
        BUILD_GRADLE="${EXAMPLE_PROJECTS[I]}/app/build.gradle.kts"
        BASE_PACKAGE=""

        if [[ -f "${BUILD_GRADLE}" ]]; then
            BASE_PACKAGE="$(sed -nE 's/^[[:space:]]*namespace[[:space:]]*=[[:space:]]*"([^"]+)".*/\1/p' "${BUILD_GRADLE}" | head -n1)"

            if [[ -z "${BASE_PACKAGE}" ]]; then
                log_error "'${EXAMPLE_NAME}' build.gradle.kts is missing namespace"
                RC=1
                continue
            fi

            # Check namespace has correct prefix
            case "${BASE_PACKAGE}" in
                com.magiclane.sdk.examples.*) : ;;
                *)
                    log_error "'${EXAMPLE_NAME}' build.gradle.kts has wrong namespace: '${BASE_PACKAGE}'"
                    log_error "Expected prefix: 'com.magiclane.sdk.examples.'"
                    RC=1
                    ;;
            esac
        else
            log_error "'${EXAMPLE_NAME}' is missing build.gradle.kts"
            RC=1
            continue
        fi

        # Check for duplicate namespaces across examples
        if [[ -n "${ALL_PACKAGES["${BASE_PACKAGE}"]+x}" ]]; then
            log_error "Duplicate namespace '${BASE_PACKAGE}' found in:"
            log_error "  - ${ALL_PACKAGES["${BASE_PACKAGE}"]}"
            log_error "  - ${EXAMPLE_NAME}"
            RC=1
        else
            ALL_PACKAGES["${BASE_PACKAGE}"]="${EXAMPLE_NAME}"
        fi

        # Find all Kotlin source directories
        MAIN_SRC="${EXAMPLE_PROJECTS[I]}/app/src/main/kotlin"
        ANDROID_TEST_SRC="${EXAMPLE_PROJECTS[I]}/app/src/androidTest/kotlin"
        TEST_SRC="${EXAMPLE_PROJECTS[I]}/app/src/test/kotlin"

        # Check main sources
        if [[ -d "${MAIN_SRC}" ]]; then
            while IFS= read -r FILE; do
                PACKAGE="$(sed -nE 's/^[[:space:]]*package[[:space:]]+([^[:space:]]+).*/\1/p' "${FILE}" | head -n1)"

                if [[ -z "${PACKAGE}" ]]; then
                    log_error "'${EXAMPLE_NAME}' file missing package declaration: ${FILE}"
                    RC=1
                    continue
                fi

                # Check if package starts with com.magiclane.sdk.examples.
                case "${PACKAGE}" in
                    com.magiclane.sdk.examples.*) : ;;
                    *)
                        log_error "'${EXAMPLE_NAME}' main source has wrong package: '${PACKAGE}'"
                        log_error "Expected prefix: 'com.magiclane.sdk.examples.'"
                        RC=1
                        ;;
                esac

                # Check package matches namespace (using case for safety)
                case "${PACKAGE}" in
                    "${BASE_PACKAGE}"|"${BASE_PACKAGE}".*) : ;;
                    *)
                        log_error "'${EXAMPLE_NAME}' main package doesn't match namespace:"
                        log_error "  Namespace: ${BASE_PACKAGE}"
                        log_error "  Found: ${PACKAGE}"
                        RC=1
                        ;;
                esac
            done < <(find "${MAIN_SRC}" -type f -name "*.kt" 2>/dev/null | sort)
        fi

        # Check androidTest sources
        if [[ -d "${ANDROID_TEST_SRC}" ]]; then
            while IFS= read -r FILE; do
                PACKAGE="$(sed -nE 's/^[[:space:]]*package[[:space:]]+([^[:space:]]+).*/\1/p' "${FILE}" | head -n1)"

                if [[ -z "${PACKAGE}" ]]; then
                    log_error "'${EXAMPLE_NAME}' file missing package declaration: ${FILE}"
                    RC=1
                    continue
                fi

                case "${PACKAGE}" in
                    com.magiclane.sdk.examples.*) : ;;
                    *)
                        log_error "'${EXAMPLE_NAME}' androidTest source has wrong package: '${PACKAGE}'"
                        log_error "Expected prefix: 'com.magiclane.sdk.examples.'"
                        RC=1
                        ;;
                esac

                case "${PACKAGE}" in
                    "${BASE_PACKAGE}"|"${BASE_PACKAGE}".*) : ;;
                    *)
                        log_error "'${EXAMPLE_NAME}' androidTest package doesn't match namespace:"
                        log_error "  Namespace: ${BASE_PACKAGE}"
                        log_error "  Found: ${PACKAGE}"
                        RC=1
                        ;;
                esac
            done < <(find "${ANDROID_TEST_SRC}" -type f -name "*.kt" 2>/dev/null | sort)
        fi

        # Check test sources
        if [[ -d "${TEST_SRC}" ]]; then
            while IFS= read -r FILE; do
                PACKAGE="$(sed -nE 's/^[[:space:]]*package[[:space:]]+([^[:space:]]+).*/\1/p' "${FILE}" | head -n1)"

                if [[ -z "${PACKAGE}" ]]; then
                    log_error "'${EXAMPLE_NAME}' file missing package declaration: ${FILE}"
                    RC=1
                    continue
                fi

                case "${PACKAGE}" in
                    com.magiclane.sdk.examples.*) : ;;
                    *)
                        log_error "'${EXAMPLE_NAME}' test source has wrong package: '${PACKAGE}'"
                        log_error "Expected prefix: 'com.magiclane.sdk.examples.'"
                        RC=1
                        ;;
                esac

                case "${PACKAGE}" in
                    "${BASE_PACKAGE}"|"${BASE_PACKAGE}".*) : ;;
                    *)
                        log_error "'${EXAMPLE_NAME}' test package doesn't match namespace:"
                        log_error "  Namespace: ${BASE_PACKAGE}"
                        log_error "  Found: ${PACKAGE}"
                        RC=1
                        ;;
                esac
            done < <(find "${TEST_SRC}" -type f -name "*.kt" 2>/dev/null | sort)
        fi

        # Check AndroidManifest.xml has NO package or namespace
        MANIFEST="${EXAMPLE_PROJECTS[I]}/app/src/main/AndroidManifest.xml"
        if [[ -f "${MANIFEST}" ]]; then
            if grep -qE '(package|namespace)[[:space:]]*=' "${MANIFEST}" 2>/dev/null; then
                log_error "'${EXAMPLE_NAME}' AndroidManifest.xml should not contain package or namespace attribute"
                RC=1
            fi
        fi
    done

    if [[ ${RC} -eq 1 ]]; then
        log_error "Package name issues found. Please fix"
    fi

    return ${RC}
}

function check_secrets()
{
    local RC=0
    local EXAMPLE_NAME
    local -a MATCHES

    for I in "${!EXAMPLE_PROJECTS[@]}"; do
        EXAMPLE_NAME="$(basename "${EXAMPLE_PROJECTS[I]}")"
        log_info "Check '${EXAMPLE_NAME}' for secrets..."

        # Check for hardcoded JWT tokens in various file types
        MATCHES=()
        mapfile -t MATCHES < <(grep -r -l -E '"eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}"' \
            "${EXAMPLE_PROJECTS[I]}" \
            --include='*.kt' \
            --include='*.kts' \
            --include='*.java' \
            --include='*.xml' \
            --include='*.json' \
            --include='*.properties' \
            --exclude-dir=build \
            --exclude-dir=.gradle \
            --exclude-dir=.idea \
            --binary-files=without-match 2>/dev/null || true)

        if ((${#MATCHES[@]} > 0)); then
            log_error "'${EXAMPLE_NAME}' may contain hardcoded JWT token:"
            printf '    @ %s\n' "${MATCHES[@]}"
            RC=1
        fi
    done

    if [[ ${RC} -eq 1 ]]; then
        log_error "Secrets found. Please check"
    fi

    return ${RC}
}

function check_license()
{
    local RC=0
    local EXAMPLE_NAME
    local -a SOURCES_WITH_MISSING_SPDX
    local -a SOURCES
    local FILE
    local MISSING_COPYRIGHT MISSING_LICENSE

    local FILE_EXCEPTIONS='(^|/)BuildConfig\.kt$'
    local DIR_EXCEPTIONS='(^|/)MPChartLib(/|$)'

    for I in "${!EXAMPLE_PROJECTS[@]}"; do
        EXAMPLE_NAME="$(basename "${EXAMPLE_PROJECTS[I]}")"
        log_info "Check '${EXAMPLE_NAME}' for license..."

        SOURCES_WITH_MISSING_SPDX=()
        SOURCES=()

        pushd "${EXAMPLE_PROJECTS[I]}" > /dev/null || continue

        # git ls-files excludes build outputs; find fallback excludes them explicitly
        mapfile -t SOURCES < <(
            git ls-files -- '*.kt' '*.java' '*.sh' 2>/dev/null || \
            find . -type f \( -name "*.kt" -o -name "*.java" -o -name "*.sh" \) \
                -not -path './build/*' \
                -not -path './.gradle/*' \
                -not -path './.idea/*' \
                2>/dev/null
        )

        while IFS= read -r FILE; do
            [[ -z "${FILE}" ]] && continue

            MISSING_COPYRIGHT=false
            MISSING_LICENSE=false

            # Match comment styles: //, #, /*, or * (multi-line comment continuation)
            if ! grep -qE '^[[:space:]]*(//|#|/\*|\*)[[:space:]]*SPDX-FileCopyrightText:' "${FILE}" 2>/dev/null; then
                MISSING_COPYRIGHT=true
            fi

            if ! grep -qE '^[[:space:]]*(//|#|/\*|\*)[[:space:]]*SPDX-License-Identifier:' "${FILE}" 2>/dev/null; then
                MISSING_LICENSE=true
            fi

            if "${MISSING_COPYRIGHT}" || "${MISSING_LICENSE}"; then
                local REASON=""
                if "${MISSING_COPYRIGHT}" && "${MISSING_LICENSE}"; then
                    REASON="missing both SPDX headers"
                elif "${MISSING_COPYRIGHT}"; then
                    REASON="missing SPDX-FileCopyrightText"
                else
                    REASON="missing SPDX-License-Identifier"
                fi
                SOURCES_WITH_MISSING_SPDX+=("${EXAMPLE_PROJECTS[I]}/${FILE#./} (${REASON})")
            fi
        done < <(printf '%s\n' "${SOURCES[@]}" | sort -u | grep -vE "${FILE_EXCEPTIONS}" | grep -vE "${DIR_EXCEPTIONS}")

        popd > /dev/null || true

        if ((${#SOURCES_WITH_MISSING_SPDX[@]} > 0)); then
            log_error "Following files have SPDX header issues in '${EXAMPLE_NAME}':"
            printf '    @ %s\n' "${SOURCES_WITH_MISSING_SPDX[@]}"
            printf '\n'
            RC=1
        fi
    done

    if [[ ${RC} -eq 1 ]]; then
        log_error "Missing license/copyright identifiers. Please check"
    fi

    return ${RC}
}

# =============================================================================
# Main
# =============================================================================

RC=0

log_step "Checking package names..."
check_package_names || RC=1

log_step "Checking secrets..."
check_secrets || RC=1

log_step "Checking licenses..."
check_license || RC=1

exit "${RC}"
