#!/usr/bin/env bash
# vim:ts=4:sts=4:sw=4:et

# SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
# SPDX-License-Identifier: Apache-2.0
#
# Contact Magic Lane at <info@magiclane.com> for SDK licensing options.

declare -r PROGNAME=${0##*/}

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
    echo -e "${COLOR_CYAN}[$(log_timestamp)] [INFO]${COLOR_RESET} $*"
}

function log_success()
{
    echo -e "${COLOR_GREEN}[$(log_timestamp)] [SUCCESS]${COLOR_RESET} $*"
}

function log_warning()
{
    echo -e "${COLOR_YELLOW}[$(log_timestamp)] [WARNING]${COLOR_RESET} $*"
}

function log_error()
{
    echo -e "${COLOR_RED}[$(log_timestamp)] [ERROR]${COLOR_RESET} $*" >&2
}

function log_step()
{
    echo ""
    echo ""
    echo -e "${COLOR_BLUE}[$(log_timestamp)] [STEP]${COLOR_RESET} $*"
    echo ""
    echo ""
}

GRADLE_WRAPPER=""
SDK_TEMP_DIR=""

function ctrl_c()
{
    exit 1
}
trap ctrl_c INT

function on_exit()
{
    if [ -n "${EXAMPLE_PROJECTS+x}" ]; then
        for EXAMPLE_PATH in ${EXAMPLE_PROJECTS}; do
            EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
            if [ -d "${EXAMPLE_PATH}"/build/app/reports ]; then
                mkdir -p "${MY_DIR}/_REPORTS/${EXAMPLE_NAME}"
                mv "${EXAMPLE_PATH}"/build/app/reports/* "${MY_DIR}/_REPORTS/${EXAMPLE_NAME}"/
            fi
            find "${EXAMPLE_PATH}" -type f -name "*.aar" -exec rm {} +
        done
    fi

    if [ -n "${MY_DIR+x}" ] && [ -d "${MY_DIR}/.gradle" ]; then
        find "${MY_DIR}" -type d -name "build" -exec rm -rf {} +
        find "${MY_DIR}" -type d -name ".gradle" -exec rm -rf {} +
        find "${MY_DIR}" -type d -name ".idea" -exec rm -rf {} +
        find "${MY_DIR}" -type d -name ".kotlin" -exec rm -rf {} +
        find "${MY_DIR}" -type f -name "local.properties" -exec rm {} +
    fi

    if [ -n "${GRADLE_WRAPPER}" ]; then
        "${GRADLE_WRAPPER}" --stop || true
    fi

    if [ -n "${SDK_TEMP_DIR}" ]; then
        rm -fr "${SDK_TEMP_DIR:?}"
    fi

    log_info "Build script completed"
}
trap 'on_exit' EXIT

function is_mac()
{
    local OS_NAME
    OS_NAME=$(uname | tr "[:upper:]" "[:lower:]")
    [[ ${OS_NAME} =~ darwin ]]
}

set -eEuo pipefail

SDK_ARCHIVE_PATH=""
LOCAL_MAVEN_REPOSITORY=""
API_TOKEN=""
RUN_UNIT_TESTS=false
RUN_INSTRUMENTED_TESTS=false
EMULATOR_HEADLESS=false
ANALYZE=false
FAIL_FAST=false

MY_DIR="$(cd "$(dirname "$0")" && pwd)"

if is_mac; then
    if [ ! -f "$(brew --prefix)/opt/gnu-getopt/bin/getopt" ]; then
        log_error "This script requires 'brew install gnu-getopt && brew link --force gnu-getopt'"
        exit 1
    fi

    PATH="$(brew --prefix)/opt/gnu-getopt/bin:${PATH}"
fi

function usage()
{
    echo -e "\033[32;1m
Usage: ${PROGNAME} [options] 

Options:
    [OPTIONAL] --sdk-archive=<path>
                    Set path to the Maps SDK for Android archive (.tar.bz2, .zip, or .aar).
                    If missing, SDK will be retrieved from Maven SDK Registry
    [OPTIONAL] --local-maven-repository=<path>
                    Set specific local Maven repository path to search for Maps SDK for Android.
                    If given, any other SDK path is ignored, including local SDK archive path

    [OPTIONAL] --api-token=<token>
                    Specify API token to be hardcoded into examples
    [OPTIONAL] --run-unit-tests
                    Run unit tests locally
    [OPTIONAL] --run-instrumented-tests
                    Run instrumented tests under Emulator
    [OPTIONAL] --emulator-headless
                    Emulator is used headless when running instrumented tests
    [OPTIONAL] --analyze
                    Analyze Kotlin code for all examples with Detekt and ktlint
    [OPTIONAL] --fail-fast
                    Exit on first error
\033[0m\n"
}

SHORTOPTS="h"
LONGOPTS_LIST=(
    "help"
    "sdk-archive:"
    "local-maven-repository:"
    "api-token:"
    "run-unit-tests"
    "run-instrumented-tests"
    "emulator-headless"
    "analyze"
    "fail-fast"
)

if ! PARSED_OPTIONS=$(getopt \
    -s bash \
    --options ${SHORTOPTS} \
    --longoptions "$(printf "%s," "${LONGOPTS_LIST[@]}")" \
    --name "${PROGNAME}" \
    -- "$@"); then
    usage
    exit 1
fi

eval set -- "${PARSED_OPTIONS}"
unset PARSED_OPTIONS

while true; do
    case "${1}" in
        -h|--help)
            usage
            exit 0
            ;;
        --sdk-archive)
            shift
            SDK_ARCHIVE_PATH="${1}"
            ;;
        --local-maven-repository)
            shift
            LOCAL_MAVEN_REPOSITORY="${1}"
            ;;
        --api-token)
            shift
            API_TOKEN="${1}"
            ;;
        --run-unit-tests)
            RUN_UNIT_TESTS=true
            ;;
        --run-instrumented-tests)
            RUN_INSTRUMENTED_TESTS=true
            ;;
        --emulator-headless)
            EMULATOR_HEADLESS=true
            ;;
        --analyze)
            ANALYZE=true
            ;;
        --fail-fast)
            FAIL_FAST=true
            ;;
        --)
            shift
            break
            ;;
        *)
            log_error "Internal error"
            exit 1
            ;;
    esac
    shift
done

log_info "Checking prerequisites..."

if [ -n "${LOCAL_MAVEN_REPOSITORY}" ]; then
    if [ ! -d "${LOCAL_MAVEN_REPOSITORY}"/com/magiclane ]; then
        log_error "Local Maven repository path is invalid"
        usage
        exit 1
    fi
else
    if [ -n "${SDK_ARCHIVE_PATH}" ] && [ ! -f "${SDK_ARCHIVE_PATH}" ]; then
        log_error "You must provide local path to SDK archive"
        usage
        exit 1
    fi
fi

if [ -z "${ANDROID_SDK_ROOT+x}" ]; then
    log_error "ANDROID_SDK_ROOT not set. Please export ANDROID_SDK_ROOT env. variable"
    exit 1
fi
if [ -z "${JAVA_HOME+x}" ]; then
    log_error "JAVA_HOME not set. Please export JAVA_HOME env. variable"
    exit 1
fi

JAVA_VERSION_STRING=$("${JAVA_HOME}"/bin/java -version 2>&1)
if ! echo "${JAVA_VERSION_STRING}" | grep -q 'version [ "]\(17\|21\)'; then
    log_error "Wrong Java version. Need 17 or 21. Found: ${JAVA_VERSION_STRING}"
    exit 1
fi

if [ -z "${LOCAL_MAVEN_REPOSITORY}" ] && [ -n "${SDK_ARCHIVE_PATH}" ]; then
    SDK_ARCHIVE_FILENAME="${SDK_ARCHIVE_PATH##*/}"
    SDK_AAR_PATH="${SDK_ARCHIVE_PATH}"
    if [[ ! "${SDK_ARCHIVE_FILENAME}" =~ (.tar.bz2|.aar|.zip)$ ]]; then
        log_error "Invalid SDK archive '${SDK_ARCHIVE_PATH}'"
        log_error "Supported formats: .tar.bz2, .zip, .aar"
        exit 1
    fi
    if [[ "${SDK_ARCHIVE_FILENAME}" =~ (.tar.bz2|.zip)$ ]]; then
        log_info "Extracting SDK archive..."
        SDK_TEMP_DIR="$(mktemp -d)"
        
        case "${SDK_ARCHIVE_PATH}" in
            *.tar.bz2)
                tar -xvf "${SDK_ARCHIVE_PATH}" --strip-components=1 -C "${SDK_TEMP_DIR}"
                ;;
            *.zip)
                if ! command -v unzip >/dev/null; then
                    log_error "unzip command not found. Please install unzip to extract .zip archives"
                    exit 2
                fi
                unzip -q "${SDK_ARCHIVE_PATH}" -d "${SDK_TEMP_DIR}"
                # Handle potential top-level directory in zip
                if [[ $(find "${SDK_TEMP_DIR}" -mindepth 1 -maxdepth 1 -type d | wc -l) -eq 1 ]]; then
                    TOP_DIR=$(find "${SDK_TEMP_DIR}" -mindepth 1 -maxdepth 1 -type d)
                    mv "${TOP_DIR}"/* "${SDK_TEMP_DIR}"/
                    rmdir "${TOP_DIR}"
                fi
                ;;
        esac
        
        SDK_AAR_PATH="$(find "${SDK_TEMP_DIR}" -maxdepth 2 -type f -iname "*.aar" | head -1)"
        log_success "SDK archive extracted successfully"
    fi
    if [ ! -f "${SDK_AAR_PATH}" ]; then
        log_error "Invalid aar path '${SDK_AAR_PATH}'"
        exit 1
    fi
fi

pushd "${MY_DIR}" &> /dev/null

# Find paths that contain an app module
EXAMPLE_PROJECTS=$(find "${MY_DIR}" -maxdepth 1 -type d -exec [ -d {}/app/libs ] \; -print -prune)

if [ -n "${SDK_ARCHIVE_PATH}" ]; then
    for EXAMPLE_PATH in ${EXAMPLE_PROJECTS}; do
        cp "${SDK_AAR_PATH}" "${EXAMPLE_PATH}/app/libs"
    done
fi

GRADLE_WRAPPER=$(find "${MY_DIR}" -maxdepth 1 -type f -executable -name gradlew -print -quit)
if [ -z "${GRADLE_WRAPPER}" ]; then
    log_error "gradlew not found in ${MY_DIR}"
    exit 1
fi

export GEM_TOKEN="${API_TOKEN}"
export GEM_SDK_LOCAL_MAVEN_PATH="${LOCAL_MAVEN_REPOSITORY}"

GRADLE_OPTS="-Xms8g -Xmx8g"
GRADLE_OPTS="${GRADLE_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
GRADLE_OPTS="${GRADLE_OPTS} -Dorg.gradle.daemon=false"
GRADLE_OPTS="${GRADLE_OPTS} -Dkotlin.incremental=false"
GRADLE_OPTS="${GRADLE_OPTS} -Dfile.encoding=UTF-8"
export GRADLE_OPTS

if "${FAIL_FAST}"; then
    for EXAMPLE_PATH in ${EXAMPLE_PROJECTS}; do
        EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
        log_step "Building example: ${EXAMPLE_NAME}"
        "${GRADLE_WRAPPER}" --no-parallel --no-watch-fs --stacktrace --warning-mode all ":${EXAMPLE_NAME}:app:assembleRelease"
        log_success "Build completed for ${EXAMPLE_NAME}"
    done
else
    log_step "Building all examples..."
    set +e
    "${GRADLE_WRAPPER}" --parallel --no-watch-fs --stacktrace --warning-mode all buildAll
    BUILD_EXIT_CODE=$?
    set -e
    
    if [ ${BUILD_EXIT_CODE} -ne 0 ]; then
        log_error "Build failed with exit code ${BUILD_EXIT_CODE}"
        exit 1
    fi
    log_success "All examples built successfully"
fi

rm -rf _APK
mkdir _APK

rm -rf _REPORTS
mkdir _REPORTS

for EXAMPLE_PATH in ${EXAMPLE_PROJECTS}; do
    EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
    if [ -f "${EXAMPLE_PATH}"/build/app/outputs/apk/release/app-release.apk ]; then
        mv "${EXAMPLE_PATH}"/build/app/outputs/apk/release/app-release.apk "_APK/${EXAMPLE_NAME}_app-release.apk"
    fi
done

if "${RUN_UNIT_TESTS}"; then
    log_step "Running unit tests from all examples..."
    set +e
    "${GRADLE_WRAPPER}" --no-parallel --no-watch-fs --warning-mode all runUnitTestsAll
    UNIT_TEST_EXIT_CODE=$?
    set -e
    
    if [ ${UNIT_TEST_EXIT_CODE} -ne 0 ]; then
        log_error "Unit tests failed with exit code ${UNIT_TEST_EXIT_CODE}"
    fi
    log_success "Unit tests completed"
fi

if "${RUN_INSTRUMENTED_TESTS}"; then
    [ -d "_REPORTS/androidTests" ] && rm -rf _REPORTS/androidTests

    log_step "Running instrumented tests from all examples..."
    GUI_OPTIONS=""
    if ! "${EMULATOR_HEADLESS}"; then
        GUI_OPTIONS="-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect --enable-display"
    fi

    for EXAMPLE_PATH in ${EXAMPLE_PROJECTS}; do
        EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
        set +e
        "${GRADLE_WRAPPER}" --no-parallel --no-watch-fs --no-configuration-cache --stacktrace --warning-mode all ":${EXAMPLE_NAME}:app:pixel_9api36googleDebugAndroidTest" ${GUI_OPTIONS}
        INSTRUMENTED_EXIT_CODE=$?
        set -e

        if [ ${INSTRUMENTED_EXIT_CODE} -ne 0 ]; then
            log_error "Instrumented tests failed for '${EXAMPLE_NAME}' with exit code ${INSTRUMENTED_EXIT_CODE}"
        fi
    done

    log_success "Instrumented tests completed"
fi

if ${ANALYZE}; then
	[ -d "_REPORTS/detekt" ] && rm -rf _REPORTS/detekt
	[ -d "_REPORTS/ktlint" ] && rm -rf _REPORTS/ktlint

    log_step "Analyzing Kotlin code for all examples..."
    set +e
    "${GRADLE_WRAPPER}" --no-parallel --no-watch-fs --warning-mode all checkAll
    ANALYZE_EXIT_CODE=$?
    set -e
    
    if [ ${ANALYZE_EXIT_CODE} -ne 0 ]; then
        log_error "Code analysis failed with exit code ${ANALYZE_EXIT_CODE}"
        exit 1
    fi
    log_success "Code analysis completed"
fi

"${GRADLE_WRAPPER}" --stop || true

popd &> /dev/null

exit 0
