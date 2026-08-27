#!/usr/bin/env bash
# vim:ts=4:sts=4:sw=4:et
# shellcheck disable=SC2317,SC2329

# SPDX-FileCopyrightText: 2021-2026 Magic Lane International B.V. <info@magiclane.com>
# SPDX-License-Identifier: Apache-2.0
#
# Contact Magic Lane at <info@magiclane.com> for SDK licensing options.

set -eEuo pipefail

declare -r PROGNAME="${0##*/}"

declare -r COLOR_RESET_DEFAULT="\033[0m"
declare -r COLOR_RED_DEFAULT="\033[31;1m"
declare -r COLOR_GREEN_DEFAULT="\033[32;1m"
declare -r COLOR_YELLOW_DEFAULT="\033[33;1m"
declare -r COLOR_BLUE_DEFAULT="\033[34;1m"
declare -r COLOR_CYAN_DEFAULT="\033[36;1m"

COLOR_RESET="${COLOR_RESET_DEFAULT}"
COLOR_RED="${COLOR_RED_DEFAULT}"
COLOR_GREEN="${COLOR_GREEN_DEFAULT}"
COLOR_YELLOW="${COLOR_YELLOW_DEFAULT}"
COLOR_BLUE="${COLOR_BLUE_DEFAULT}"
COLOR_CYAN="${COLOR_CYAN_DEFAULT}"

CONSOLE_MODE="auto"

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

function apply_console_mode()
{
    function _disable_colors()
    {
        COLOR_RESET=""
        COLOR_RED=""
        COLOR_GREEN=""
        COLOR_YELLOW=""
        COLOR_BLUE=""
        COLOR_CYAN=""
    }

    case "${CONSOLE_MODE}" in
        auto)
            if [[ ! -t 1 ]]; then
                _disable_colors
            fi
            ;;
        plain)
            _disable_colors
            ;;
        colored)
            :
            ;;
        verbose)
            set -x
            ;;
        *)
            log_error "Invalid --console value: '${CONSOLE_MODE}'. Allowed: auto, plain, colored, verbose"
            usage
            exit 1
            ;;
    esac
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

function is_ci()
{
    if [[ -n "${CI:-}" ]] && [[ "${CI}" != "false" ]] && [[ "${CI}" != "0" ]]; then
        return 0
    fi

    local -a CI_VARS=(
        GITHUB_ACTIONS
        GITLAB_CI
        JENKINS_URL
        TEAMCITY_VERSION
        BUILDKITE
        CIRCLECI
        TRAVIS
        APPVEYOR
        TF_BUILD
        BITBUCKET_BUILD_NUMBER
        DRONE
        SEMAPHORE
        CODEBUILD_BUILD_ID
    )

    local VAR
    for VAR in "${CI_VARS[@]}"; do
        [[ -n "${!VAR:-}" ]] && return 0
    done

    return 1
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
        "gnu-getopt:gnu-getopt/bin"
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

function contains_in_array()
{
    local NEEDLE="$1"
    shift

    local NEEDLE_LC
    NEEDLE_LC="$(printf '%s' "${NEEDLE}" | tr '[:upper:]' '[:lower:]')"

    local ITEM ITEM_LC
    for ITEM in "$@"; do
        ITEM_LC="$(printf '%s' "${ITEM}" | tr '[:upper:]' '[:lower:]')"
        [[ "${ITEM_LC}" == "${NEEDLE_LC}" ]] && return 0
    done

    return 1
}

function filtering_active()
{
    [[ ${#ONLY_EXAMPLES[@]} -gt 0 ]] || [[ ${#EXCLUDE_EXAMPLES[@]} -gt 0 ]]
}

function validate_filter_names()
{
    local NAME EXAMPLE_PATH EXAMPLE_NAME
    local FOUND
    local -a UNKNOWN_ONLY=()
    local -a UNKNOWN_EXCLUDE=()

    for NAME in "${ONLY_EXAMPLES[@]}"; do
        FOUND=false
        for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
            EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
            if contains_in_array "${NAME}" "${EXAMPLE_NAME}"; then
                FOUND=true
                break
            fi
        done
        if ! "${FOUND}"; then
            UNKNOWN_ONLY+=("${NAME}")
        fi
    done

    for NAME in "${EXCLUDE_EXAMPLES[@]}"; do
        FOUND=false
        for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
            EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
            if contains_in_array "${NAME}" "${EXAMPLE_NAME}"; then
                FOUND=true
                break
            fi
        done
        if ! "${FOUND}"; then
            UNKNOWN_EXCLUDE+=("${NAME}")
        fi
    done

    if [[ ${#UNKNOWN_ONLY[@]} -gt 0 ]]; then
        log_error "Unknown example(s) in --only: ${UNKNOWN_ONLY[*]}"
        log_error "Use --list-examples to see available examples"
        exit 1
    fi

    if [[ ${#UNKNOWN_EXCLUDE[@]} -gt 0 ]]; then
        log_warning "Unknown example(s) in --exclude (ignored): ${UNKNOWN_EXCLUDE[*]}"
    fi
}

GRADLE_WRAPPER=""
SDK_TEMP_DIR=""
PROJECT_DIR=""
SHOW_EXIT_MESSAGE=true

declare -a EXAMPLE_PROJECTS=()
declare -a ONLY_EXAMPLES=()
declare -a EXCLUDE_EXAMPLES=()
declare -a COMPOSE_AAR_PATHS=()

function dist_clean()
{
    [ "${CLEAN_ON_EXIT}" = true ] || return 0

    if [[ -n "${PROJECT_DIR}" ]] && [[ -d "${PROJECT_DIR}/.gradle" ]]; then
        find "${PROJECT_DIR}" -type d -name "build" -exec rm -rf {} + 2>/dev/null || true
        find "${PROJECT_DIR}" -type d -name ".gradle" -exec rm -rf {} + 2>/dev/null || true
        find "${PROJECT_DIR}" -type f -name "local.properties" -exec rm {} + 2>/dev/null || true
    fi
}

function collect_reports()
{
    [[ ${#EXAMPLE_PROJECTS[@]} -gt 0 ]] || return 0

    local EXAMPLE_PATH EXAMPLE_NAME
    for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
        EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"

        if [[ -d "${EXAMPLE_PATH}/build/app/reports" ]]; then
            mkdir -p "${PROJECT_DIR}/_REPORTS/${EXAMPLE_NAME}"
            find "${EXAMPLE_PATH}/build/app/reports" -mindepth 1 -maxdepth 1 -exec mv {} "${PROJECT_DIR}/_REPORTS/${EXAMPLE_NAME}/" \; 2>/dev/null || true
        fi

        find "${EXAMPLE_PATH}" -type f -name "*.aar" -exec rm {} + 2>/dev/null || true
    done

    if [[ -d "${PROJECT_DIR}/build/reports/problems" ]]; then
        mkdir -p "${PROJECT_DIR}/_REPORTS/problems"
        cp -r "${PROJECT_DIR}/build/reports/problems/"* "${PROJECT_DIR}/_REPORTS/problems/" 2>/dev/null || true
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

function cleanup_managed_device_emulators()
{
    log_info "Cleaning up Gradle Managed Device state..."

    if pgrep -f "_GradleManagedDevice" >/dev/null 2>&1; then
        log_info "  Killing stale Gradle Managed Device emulator processes..."
        pkill -f "_GradleManagedDevice" 2>/dev/null || true
    fi

    if pgrep -f "emulator -kill" >/dev/null 2>&1; then
        log_info "  Killing emulator watchdog processes..."
        pkill -f "emulator -kill" 2>/dev/null || true
    fi

    sleep 2

    local AVD_DIR="${HOME}/.android/avd/gradle-managed"
    if [[ -d "${AVD_DIR}" ]]; then
        log_info "  Removing lock files from ${AVD_DIR}..."
        find "${AVD_DIR}" -name "*.lock" -delete 2>/dev/null || true

        log_info "  Removing managed AVD directory for clean recreation..."
        rm -rf "${AVD_DIR}"
    fi

    local EMU_PIDS
    EMU_PIDS="$(pgrep -f "qemu-system|emulator" 2>/dev/null | while read -r PID; do
        # Skip crashpad_handler child processes — they're harmless
        ps -p "${PID}" -o args= 2>/dev/null | grep -q "crashpad_handler" && continue
        printf '%s\n' "${PID}"
    done || true)"

    if [[ -n "${EMU_PIDS}" ]]; then
        log_error "A manually launched Android emulator is still running."
        log_error "Gradle Managed Devices cannot start while another emulator is active."
        log_error ""
        log_error "Running emulator process(es):"
        local PID
        while IFS= read -r PID; do
            log_error "  PID ${PID}: $(ps -p "${PID}" -o args= 2>/dev/null || echo '<unknown>')"
        done <<< "${EMU_PIDS}"
        log_error ""
        log_error "To fix, either:"
        log_error "  1. Close the emulator from Android Studio or its window"
        log_error "  2. Kill it:  kill ${EMU_PIDS//$'\n'/ }"
        log_error "  3. Re-launch it with:  emulator @<avd_name> -read-only"
        exit 1
    fi

    log_success "Managed device cleanup done"
}

function cleanup_managed_devices()
{
    [[ -n "${GRADLE_WRAPPER}" ]] || return 0

    log_info "Cleaning up Gradle Managed Devices..."
    "${GRADLE_WRAPPER}" cleanManagedDevices --unused-only 2>/dev/null || true
}

function on_exit()
{
    local EXIT_CODE=$?
    set +e

    collect_reports
    dist_clean

    if [[ -n "${GRADLE_WRAPPER}" ]]; then
        "${GRADLE_WRAPPER}" --stop || true
    fi

    if "${RUN_INSTRUMENTED_TESTS}"; then
        cleanup_managed_devices
    fi

    if [[ -n "${SDK_TEMP_DIR}" ]] && [[ -d "${SDK_TEMP_DIR}" ]]; then
        rm -rf "${SDK_TEMP_DIR:?}"
    fi

    if "${SHOW_EXIT_MESSAGE}"; then
        if [[ ${EXIT_CODE} -eq 0 ]]; then
            log_info "Reports: ${PROJECT_DIR}/_REPORTS"
            log_info "APKs:    ${PROJECT_DIR}/_APK"
            if "${TAKE_SCREENSHOTS}"; then
                log_info "Screenshots: ${PROJECT_DIR}/_SCREENSHOTS"
            fi
            if "${INSTALL_APKS}"; then
                log_info "APKs installed to device/emulator"
            fi
        else
            log_info "Reports (if any): ${PROJECT_DIR}/_REPORTS"
            log_error "Build failed (exit code ${EXIT_CODE})"
        fi

        printf '\n'
        log_info "Bye-Bye"
    fi

    exit "${EXIT_CODE}"
}
trap on_exit EXIT

function check_prerequisites()
{
    log_step "Checking prerequisites..."

    if [[ -n "${LOCAL_MAVEN_REPOSITORY}" ]]; then
        if [[ ! -d "${LOCAL_MAVEN_REPOSITORY}/com/magiclane" ]]; then
            log_error "Local Maven repository path is invalid: ${LOCAL_MAVEN_REPOSITORY}"
            usage
            exit 1
        fi
    elif [[ -n "${SDK_ARCHIVE_PATH}" ]] && [[ ! -f "${SDK_ARCHIVE_PATH}" ]]; then
        log_error "You must provide local path to SDK archive (file not found): ${SDK_ARCHIVE_PATH}"
        usage
        exit 1
    fi

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

    local JAVA_VERSION_LINE
    JAVA_VERSION_LINE="$("${JAVA_HOME}/bin/java" -version 2>&1 | head -n 1)"
    if ! printf '%s\n' "${JAVA_VERSION_LINE}" | grep -Eq 'version "((17|21)(\.|"))'; then
        log_error "Wrong Java version. Need 17 or 21. Found: ${JAVA_VERSION_LINE}"
        exit 1
    fi
}

function check_screenshot_prerequisites()
{
    "${TAKE_SCREENSHOTS}" || "${INSTALL_APKS}" || return 0

    log_step "Checking device prerequisites..."

    if "${TAKE_SCREENSHOTS}"; then
        if ! check_cmd convert; then
            log_error "ImageMagick not found. Please install it:"
            if is_mac; then
                log_error "  brew install imagemagick"
            else
                log_error "  sudo apt install imagemagick"
            fi
            exit 1
        fi

        if ! check_cmd identify; then
            log_error "ImageMagick 'identify' not found. Please install ImageMagick:"
            if is_mac; then
                log_error "  brew install imagemagick"
            else
                log_error "  sudo apt install imagemagick"
            fi
            exit 1
        fi
    fi

    if ! check_cmd adb; then
        log_error "adb not found. Ensure ANDROID_SDK_ROOT/platform-tools is in PATH"
        exit 1
    fi

    # Apply device selection if specified
    if [[ -n "${DEVICE_SERIAL}" ]]; then
        export ANDROID_SERIAL="${DEVICE_SERIAL}"
        log_info "Using device: ${ANDROID_SERIAL}"
    fi

    # Check for at least one connected device
    local DEVICE_COUNT
    DEVICE_COUNT="$(adb devices | grep -cE '\s+device$' || true)"

    if [[ "${DEVICE_COUNT}" -eq 0 ]]; then
        log_error "No Android device/emulator connected. Please start an emulator or connect a device"
        exit 1
    fi

    if [[ "${DEVICE_COUNT}" -gt 1 ]] && [[ -z "${ANDROID_SERIAL:-}" ]]; then
        log_error "Multiple devices connected. Use --device to specify which one:"
        adb devices -l
        log_error "Example: --device=emulator-5554"
        exit 1
    fi

    # Verify the specified device exists
    if [[ -n "${ANDROID_SERIAL:-}" ]]; then
        if ! adb devices | grep -q "^${ANDROID_SERIAL}"; then
            log_error "Device not found: ${ANDROID_SERIAL}"
            log_error "Available devices:"
            adb devices -l
            exit 1
        fi
    fi

    log_success "Device prerequisites OK"
}

function get_device_dimensions()
{
    # Take a test screenshot to get actual dimensions
    local TEST_SCREENSHOT
    TEST_SCREENSHOT="$(mktemp --suffix=.png)"

    if ! adb exec-out screencap -p > "${TEST_SCREENSHOT}" 2>/dev/null; then
        log_error "Failed to capture test screenshot"
        rm -f "${TEST_SCREENSHOT}"
        exit 1
    fi

    # Get actual screenshot dimensions using ImageMagick
    local ACTUAL_DIMS
    ACTUAL_DIMS="$(identify -format '%wx%h' "${TEST_SCREENSHOT}" 2>/dev/null || true)"
    rm -f "${TEST_SCREENSHOT}"

    if [[ -z "${ACTUAL_DIMS}" ]]; then
        log_error "Failed to get screenshot dimensions"
        exit 1
    fi

    DEVICE_WIDTH="${ACTUAL_DIMS%%x*}"
    DEVICE_HEIGHT="${ACTUAL_DIMS##*x}"

    local DENSITY
    DENSITY="$(adb shell wm density | grep -oP '\d+' | tail -1)"

    # Default values based on density
    STATUS_BAR_HEIGHT=$(( 25 * DENSITY / 160 ))
    NAV_BAR_HEIGHT=$(( 48 * DENSITY / 160 ))

    # Parse actual values from dumpsys window
    local WINDOW_DUMP
    WINDOW_DUMP="$(adb shell dumpsys window 2>/dev/null)"

    # Extract status bar height from: InsetsSource id=... type=statusBars frame=[0,0][1080,142]
    local STATUS_FRAME
    STATUS_FRAME="$(printf '%s' "${WINDOW_DUMP}" | grep -oP 'type=statusBars frame=\[0,0\]\[\d+,\K\d+' | head -1 || true)"
    if [[ -n "${STATUS_FRAME}" ]] && [[ "${STATUS_FRAME}" -gt 0 ]]; then
        STATUS_BAR_HEIGHT="${STATUS_FRAME}"
    fi

    # Extract navigation bar from: type=navigationBars frame=[0,2361][1080,2424]
    local NAV_FRAME
    NAV_FRAME="$(printf '%s' "${WINDOW_DUMP}" | grep -oP 'type=navigationBars frame=\[0,\K\d+' | head -1 || true)"
    if [[ -n "${NAV_FRAME}" ]] && [[ "${NAV_FRAME}" -gt 0 ]]; then
        NAV_BAR_HEIGHT=$((DEVICE_HEIGHT - NAV_FRAME))
    fi

    log_info "Screenshot dimensions: ${DEVICE_WIDTH}x${DEVICE_HEIGHT}"
    log_info "Density: ${DENSITY}, Status bar: ${STATUS_BAR_HEIGHT}px, Nav bar: ${NAV_BAR_HEIGHT}px"
    log_info "Crop area: ${DEVICE_WIDTH}x$((DEVICE_HEIGHT - STATUS_BAR_HEIGHT - NAV_BAR_HEIGHT)) starting at y=${STATUS_BAR_HEIGHT}"
}

function get_package_and_activity()
{
    local APK_PATH="$1"

    if ! check_cmd aapt; then
        local AAPT_PATH
        AAPT_PATH="$(find "${ANDROID_SDK_ROOT}/build-tools" -name "aapt" -type f 2>/dev/null | sort -V | tail -1)"
        if [[ -n "${AAPT_PATH}" ]]; then
            AAPT_CMD="${AAPT_PATH}"
        else
            log_error "aapt not found in ANDROID_SDK_ROOT/build-tools"
            exit 1
        fi
    else
        AAPT_CMD="aapt"
    fi

    local BADGING_OUTPUT
    BADGING_OUTPUT="$("${AAPT_CMD}" dump badging "${APK_PATH}" 2>/dev/null)"

    # Extract package name: package: name='com.example.app' versionCode='1' ...
    PACKAGE_NAME="$(printf '%s\n' "${BADGING_OUTPUT}" | awk -F"'" '/^package:/{print $2}')"

    # Extract launch activity: launchable-activity: name='com.example.app.MainActivity' ...
    LAUNCH_ACTIVITY="$(printf '%s\n' "${BADGING_OUTPUT}" | awk -F"'" '/^launchable-activity:/{print $2}')"

    if [[ -z "${PACKAGE_NAME}" ]] || [[ -z "${LAUNCH_ACTIVITY}" ]]; then
        return 1
    fi

    return 0
}

function perform_circular_map_gesture()
{
    "${MAP_GESTURE_ENABLED}" || return 0

    log_info "  Performing circular map gesture (${MAP_GESTURE_DURATION}s)..."

    # Calculate center and radius for circular motion
    local CENTER_X=$((DEVICE_WIDTH / 2))
    local CENTER_Y=$((DEVICE_HEIGHT / 2))
    local RADIUS=$((DEVICE_WIDTH / 4))

    # Duration per swipe segment (ms) - we'll do 8 segments per circle
    local SEGMENTS=8
    local SWIPE_DURATION=300

    # Calculate how many full circles we can do in the given duration
    local CIRCLE_TIME=$((SEGMENTS * SWIPE_DURATION / 1000 + 1))
    local CIRCLES=$((MAP_GESTURE_DURATION / CIRCLE_TIME))
    [[ "${CIRCLES}" -lt 1 ]] && CIRCLES=1

    local CIRCLE SEGMENT
    for ((CIRCLE = 0; CIRCLE < CIRCLES; CIRCLE++)); do
        for ((SEGMENT = 0; SEGMENT < SEGMENTS; SEGMENT++)); do
            # Calculate start and end angles
            local ANGLE_START ANGLE_END
            ANGLE_START=$(awk "BEGIN {printf \"%.4f\", ${SEGMENT} * 2 * 3.14159 / ${SEGMENTS}}")
            ANGLE_END=$(awk "BEGIN {printf \"%.4f\", (${SEGMENT} + 1) * 2 * 3.14159 / ${SEGMENTS}}")

            # Calculate start and end points
            local X1 Y1 X2 Y2
            X1=$(awk "BEGIN {printf \"%.0f\", ${CENTER_X} + ${RADIUS} * cos(${ANGLE_START})}")
            Y1=$(awk "BEGIN {printf \"%.0f\", ${CENTER_Y} + ${RADIUS} * sin(${ANGLE_START})}")
            X2=$(awk "BEGIN {printf \"%.0f\", ${CENTER_X} + ${RADIUS} * cos(${ANGLE_END})}")
            Y2=$(awk "BEGIN {printf \"%.0f\", ${CENTER_Y} + ${RADIUS} * sin(${ANGLE_END})}")

            # Perform swipe
            adb shell input swipe "${X1}" "${Y1}" "${X2}" "${Y2}" "${SWIPE_DURATION}" 2>/dev/null || true
        done
    done

    # Small pause to let the map settle
    sleep 0.5
}

function take_screenshot()
{
    local EXAMPLE_NAME="$1"
    local APK_PATH="$2"
    local OUTPUT_PATH="$3"

    log_info "Taking screenshot for: ${EXAMPLE_NAME}"

    if ! get_package_and_activity "${APK_PATH}"; then
        log_warning "Could not extract package/activity from APK: ${APK_PATH}"
        return 1
    fi

    log_info "  Package: ${PACKAGE_NAME}"
    log_info "  Activity: ${LAUNCH_ACTIVITY}"

    if ! adb install -r "${APK_PATH}" >/dev/null 2>&1; then
        log_warning "Failed to install APK: ${APK_PATH}"
        return 1
    fi

    if ! adb shell am start -n "${PACKAGE_NAME}/${LAUNCH_ACTIVITY}" -W >/dev/null 2>&1; then
        log_warning "Failed to launch app: ${PACKAGE_NAME}"
        if ! "${INSTALL_APKS}"; then
            adb uninstall "${PACKAGE_NAME}" >/dev/null 2>&1 || true
        fi
        return 1
    fi

    sleep "${SCREENSHOT_WAIT_TIME}"

    perform_circular_map_gesture

    local RAW_SCREENSHOT
    RAW_SCREENSHOT="$(mktemp --suffix=.png)"

    if ! adb exec-out screencap -p > "${RAW_SCREENSHOT}"; then
        log_warning "Failed to capture screenshot"
        rm -f "${RAW_SCREENSHOT}"
        adb shell am force-stop "${PACKAGE_NAME}" >/dev/null 2>&1 || true
        if ! "${INSTALL_APKS}"; then
            adb uninstall "${PACKAGE_NAME}" >/dev/null 2>&1 || true
        fi
        return 1
    fi

    # Crop status bar (top) and navigation bar (bottom)
    local CROP_HEIGHT=$((DEVICE_HEIGHT - STATUS_BAR_HEIGHT - NAV_BAR_HEIGHT))
    if ! convert "${RAW_SCREENSHOT}" \
        -crop "${DEVICE_WIDTH}x${CROP_HEIGHT}+0+${STATUS_BAR_HEIGHT}" \
        +repage \
        "${OUTPUT_PATH}"; then
        log_warning "Failed to crop screenshot"
        rm -f "${RAW_SCREENSHOT}"
        adb shell am force-stop "${PACKAGE_NAME}" >/dev/null 2>&1 || true
        if ! "${INSTALL_APKS}"; then
            adb uninstall "${PACKAGE_NAME}" >/dev/null 2>&1 || true
        fi
        return 1
    fi

    rm -f "${RAW_SCREENSHOT}"

    adb shell am force-stop "${PACKAGE_NAME}" >/dev/null 2>&1 || true
    if ! "${INSTALL_APKS}"; then
        adb uninstall "${PACKAGE_NAME}" >/dev/null 2>&1 || true
    fi

    log_success "  Saved: ${OUTPUT_PATH}"
    return 0
}

function take_screenshots()
{
    "${TAKE_SCREENSHOTS}" || return 0

    log_step "Taking screenshots..."

    rm -rf "${PROJECT_DIR}/_SCREENSHOTS"
    mkdir -p "${PROJECT_DIR}/_SCREENSHOTS"

    get_device_dimensions

    local -a FAILED_SCREENSHOTS=()
    local TOTAL_EXAMPLES=${#EXAMPLE_PROJECTS[@]}
    local CURRENT_INDEX=0
    local EXAMPLE_PATH EXAMPLE_NAME APK_PATH OUTPUT_PATH

    for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
        CURRENT_INDEX=$((CURRENT_INDEX + 1))
        EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
        APK_PATH="${PROJECT_DIR}/_APK/${EXAMPLE_NAME}_app-release.apk"
        OUTPUT_PATH="${PROJECT_DIR}/_SCREENSHOTS/${EXAMPLE_NAME}.png"

        log_info "Processing (${CURRENT_INDEX}/${TOTAL_EXAMPLES}): ${EXAMPLE_NAME}"

        if [[ ! -f "${APK_PATH}" ]]; then
            log_warning "APK not found: ${APK_PATH}"
            FAILED_SCREENSHOTS+=("${EXAMPLE_NAME}")
            continue
        fi

        if ! take_screenshot "${EXAMPLE_NAME}" "${APK_PATH}" "${OUTPUT_PATH}"; then
            FAILED_SCREENSHOTS+=("${EXAMPLE_NAME}")
            if "${FAIL_FAST}"; then
                log_error "Screenshot failed for '${EXAMPLE_NAME}'"
                exit 1
            fi
        fi
    done

    if [[ ${#FAILED_SCREENSHOTS[@]} -gt 0 ]]; then
        log_warning "Screenshots failed for ${#FAILED_SCREENSHOTS[@]} example(s):"
        for EXAMPLE_NAME in "${FAILED_SCREENSHOTS[@]}"; do
            log_warning "  - ${EXAMPLE_NAME}"
        done
    fi

    local SUCCESS_COUNT=$((TOTAL_EXAMPLES - ${#FAILED_SCREENSHOTS[@]}))
    log_success "Screenshots completed: ${SUCCESS_COUNT}/${TOTAL_EXAMPLES}"
}

function install_apks()
{
    "${INSTALL_APKS}" || return 0

    # Skip if screenshots already installed everything
    "${TAKE_SCREENSHOTS}" && return 0

    log_step "Installing APKs to device/emulator..."

    local -a FAILED_INSTALLS=()
    local TOTAL_EXAMPLES=${#EXAMPLE_PROJECTS[@]}
    local CURRENT_INDEX=0
    local EXAMPLE_PATH EXAMPLE_NAME APK_PATH

    for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
        CURRENT_INDEX=$((CURRENT_INDEX + 1))
        EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
        APK_PATH="${PROJECT_DIR}/_APK/${EXAMPLE_NAME}_app-release.apk"

        log_info "Installing (${CURRENT_INDEX}/${TOTAL_EXAMPLES}): ${EXAMPLE_NAME}"

        if [[ ! -f "${APK_PATH}" ]]; then
            log_warning "APK not found: ${APK_PATH}"
            FAILED_INSTALLS+=("${EXAMPLE_NAME}")
            continue
        fi

        if ! get_package_and_activity "${APK_PATH}"; then
            log_warning "Could not extract package info from APK: ${APK_PATH}"
            FAILED_INSTALLS+=("${EXAMPLE_NAME}")
            continue
        fi

        if ! adb install -r "${APK_PATH}" >/dev/null 2>&1; then
            log_warning "Failed to install APK: ${APK_PATH}"
            FAILED_INSTALLS+=("${EXAMPLE_NAME}")
            if "${FAIL_FAST}"; then
                log_error "Install failed for '${EXAMPLE_NAME}'"
                exit 1
            fi
            continue
        fi

        log_success "  Installed: ${PACKAGE_NAME}"
    done

    if [[ ${#FAILED_INSTALLS[@]} -gt 0 ]]; then
        log_warning "Install failed for ${#FAILED_INSTALLS[@]} example(s):"
        for EXAMPLE_NAME in "${FAILED_INSTALLS[@]}"; do
            log_warning "  - ${EXAMPLE_NAME}"
        done
    fi

    local SUCCESS_COUNT=$((TOTAL_EXAMPLES - ${#FAILED_INSTALLS[@]}))
    log_success "APK installation completed: ${SUCCESS_COUNT}/${TOTAL_EXAMPLES}"
}

function discover_examples()
{
    log_step "Discovering examples..."

    mapfile -t EXAMPLE_PROJECTS < <(
        find "${PROJECT_DIR}" -mindepth 1 -maxdepth 1 -type d -exec [ -d "{}/app/libs" ] \; -print 2>/dev/null | sort
    )

    if [[ ${#EXAMPLE_PROJECTS[@]} -eq 0 ]]; then
        log_error "No examples found under ${PROJECT_DIR} (expected <example>/app/libs)"
        exit 1
    fi

    log_info "Found ${#EXAMPLE_PROJECTS[@]} example(s)"
}

function list_examples()
{
    SHOW_EXIT_MESSAGE=false

    local EXAMPLE_PATH
    for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
        printf '%s\n' "$(basename "${EXAMPLE_PATH}")"
    done
}

function filter_examples()
{
    local -a FILTERED=()
    local EXAMPLE_PATH EXAMPLE_NAME

    if [[ ${#ONLY_EXAMPLES[@]} -gt 0 ]] && [[ ${#EXCLUDE_EXAMPLES[@]} -gt 0 ]]; then
        log_error "Do not use --only and --exclude together."
        usage
        exit 1
    fi

    for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
        EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"

        if [[ ${#ONLY_EXAMPLES[@]} -gt 0 ]]; then
            if contains_in_array "${EXAMPLE_NAME}" "${ONLY_EXAMPLES[@]}"; then
                FILTERED+=("${EXAMPLE_PATH}")
            fi
            continue
        fi

        if [[ ${#EXCLUDE_EXAMPLES[@]} -gt 0 ]]; then
            if contains_in_array "${EXAMPLE_NAME}" "${EXCLUDE_EXAMPLES[@]}"; then
                continue
            fi
        fi

        FILTERED+=("${EXAMPLE_PATH}")
    done

    EXAMPLE_PROJECTS=("${FILTERED[@]}")

    if [[ ${#EXAMPLE_PROJECTS[@]} -eq 0 ]]; then
        log_error "After filtering, no examples remain to build."
        exit 1
    fi

    log_info "Selected ${#EXAMPLE_PROJECTS[@]} example(s): $(printf '%s ' "${EXAMPLE_PROJECTS[@]##*/}")"
}

function extract_sdk_archive()
{
    [[ -z "${LOCAL_MAVEN_REPOSITORY}" ]] && [[ -n "${SDK_ARCHIVE_PATH}" ]] || return 0

    if [[ "${SDK_ARCHIVE_PATH##*/}" != *.zip ]]; then
        log_error "Invalid SDK archive '${SDK_ARCHIVE_PATH}'"
        log_error "Supported format: .zip (SDK distribution zip)"
        exit 1
    fi

    if ! check_cmd unzip; then
        log_error "unzip command not found. Please install unzip to extract .zip archives"
        exit 2
    fi

    log_info "Extracting SDK archive..."
    SDK_TEMP_DIR="$(mktemp -d)"

    unzip -q "${SDK_ARCHIVE_PATH}" -d "${SDK_TEMP_DIR}"
    if [[ $(find "${SDK_TEMP_DIR}" -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ') -eq 1 ]]; then
        local TOP_DIR
        TOP_DIR="$(find "${SDK_TEMP_DIR}" -mindepth 1 -maxdepth 1 -type d)"
        mv "${TOP_DIR}"/* "${SDK_TEMP_DIR}/"
        rmdir "${TOP_DIR}"
    fi

    SDK_AAR_PATH="$(find "${SDK_TEMP_DIR}" -maxdepth 2 -type f -iname "MAGICLANE-*SDK-ANDROID-*.aar" 2>/dev/null | sort | head -1 || true)"

    mapfile -t COMPOSE_AAR_PATHS < <(find "${SDK_TEMP_DIR}" -maxdepth 2 -type f -iname "MAGICLANE-MAPS-COMPOSE-*.aar" 2>/dev/null | sort)
    if [[ ${#COMPOSE_AAR_PATHS[@]} -gt 0 ]]; then
        log_info "Found ${#COMPOSE_AAR_PATHS[@]} Compose AAR(s) in the archive"
    fi

    log_success "SDK archive extracted successfully"

    if [[ -z "${SDK_AAR_PATH}" ]] || [[ ! -f "${SDK_AAR_PATH}" ]]; then
        log_error "No SDK AAR found in archive '${SDK_ARCHIVE_PATH}'"
        exit 1
    fi
}

function copy_sdk_to_examples()
{
    [[ -n "${SDK_ARCHIVE_PATH}" ]] && [[ -n "${SDK_AAR_PATH}" ]] || return 0

    local EXAMPLE_PATH
    for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
        cp "${SDK_AAR_PATH}" "${EXAMPLE_PATH}/app/libs"

        # Compose AARs go only into the examples that use the Compose extension
        if [[ ${#COMPOSE_AAR_PATHS[@]} -gt 0 ]] \
            && grep -q "magiclane\.maps\.compose" "${EXAMPLE_PATH}/app/build.gradle.kts" 2>/dev/null; then
            cp "${COMPOSE_AAR_PATHS[@]}" "${EXAMPLE_PATH}/app/libs"
        fi
    done
}

function setup_gradle()
{
    GRADLE_WRAPPER="$(find "${PROJECT_DIR}" -maxdepth 1 -type f -executable -name gradlew -print -quit)"
    if [[ -z "${GRADLE_WRAPPER}" ]]; then
        log_error "gradlew not found in ${PROJECT_DIR}"
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
}

function run_gradle()
{
    function _gradle_console_arg()
    {
        case "${CONSOLE_MODE}" in
            plain) printf '%s' "--console=plain" ;;
            auto) printf '%s' "--console=auto" ;;
            colored|verbose) printf '%s' "--console=rich" ;;
            *) printf '%s' "--console=auto" ;;
        esac
    }

    local -a ARGS=("$@" "$(_gradle_console_arg)")

    set +e
    "${GRADLE_WRAPPER}" "${ARGS[@]}"
    local EXIT_CODE=$?
    set -e
    return "${EXIT_CODE}"
}

function build_examples()
{
    local CACHE_OPT=""
    if "${NO_CACHE}"; then
        CACHE_OPT="--no-build-cache"
    fi

    if "${FAIL_FAST}"; then
        local TOTAL_EXAMPLES=${#EXAMPLE_PROJECTS[@]}
        local CURRENT_INDEX=0
        local EXAMPLE_PATH EXAMPLE_NAME
        for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
            CURRENT_INDEX=$((CURRENT_INDEX + 1))
            EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
            log_step "Building example (${CURRENT_INDEX}/${TOTAL_EXAMPLES}): ${EXAMPLE_NAME}"
            # shellcheck disable=SC2086
            run_gradle ":${EXAMPLE_NAME}:app:assembleRelease" --no-parallel --no-watch-fs ${CACHE_OPT} --stacktrace --warning-mode all -Pandroid.testoptions.manageddevices.emulator.gpu=auto
            log_success "Build completed for '${EXAMPLE_NAME}'"
        done
        return
    fi

    local BUILD_EXIT_CODE=0

    if ! filtering_active; then
        log_step "Building all examples..."
        # shellcheck disable=SC2086
        if ! run_gradle buildAll --parallel --no-watch-fs ${CACHE_OPT} --stacktrace --warning-mode all --continue; then
            BUILD_EXIT_CODE=1
        fi
    else
        log_step "Building selected examples..."

        local -a TASKS=()
        local EXAMPLE_PATH EXAMPLE_NAME
        for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
            EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
            TASKS+=(":${EXAMPLE_NAME}:app:assembleRelease")
        done

        # shellcheck disable=SC2086
        if ! run_gradle "${TASKS[@]}" --parallel --no-watch-fs ${CACHE_OPT} --stacktrace --warning-mode all --continue; then
            BUILD_EXIT_CODE=1
        fi
    fi

    if [[ ${BUILD_EXIT_CODE} -ne 0 ]]; then
        local -a FAILED_EXAMPLES=()
        local EXAMPLE_PATH EXAMPLE_NAME APK_PATH
        for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
            EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
            APK_PATH="${EXAMPLE_PATH}/build/app/outputs/apk/release/app-release.apk"
            if [[ ! -f "${APK_PATH}" ]]; then
                FAILED_EXAMPLES+=("${EXAMPLE_NAME}")
            fi
        done

        if [[ ${#FAILED_EXAMPLES[@]} -gt 0 ]]; then
            log_error "Build failed for ${#FAILED_EXAMPLES[@]} example(s):"
            for EXAMPLE_NAME in "${FAILED_EXAMPLES[@]}"; do
                log_error "  - ${EXAMPLE_NAME}"
            done
        else
            log_error "Build failed"
        fi
        exit 1
    fi

    log_success "All examples built successfully"
}

function collect_apks()
{
    rm -rf "${PROJECT_DIR}/_APK"
    mkdir -p "${PROJECT_DIR}/_APK"

    rm -rf "${PROJECT_DIR}/_REPORTS"
    mkdir -p "${PROJECT_DIR}/_REPORTS"

    local EXAMPLE_PATH EXAMPLE_NAME APK_PATH
    for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
        EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
        APK_PATH="${EXAMPLE_PATH}/build/app/outputs/apk/release/app-release.apk"
        if [[ -f "${APK_PATH}" ]]; then
            mv "${APK_PATH}" "${PROJECT_DIR}/_APK/${EXAMPLE_NAME}_app-release.apk"
        fi
    done
}

function run_unit_tests()
{
    "${RUN_UNIT_TESTS}" || return 0

    if ! filtering_active; then
        log_step "Running unit tests from all examples..."
        if ! run_gradle runUnitTestsAll --no-parallel --no-watch-fs --warning-mode all; then
            log_error "Unit tests failed"
            exit 1
        fi
        log_success "Unit tests completed"
        return
    fi

    log_step "Running unit tests from selected examples..."

    local -a TASKS=()
    local EXAMPLE_PATH EXAMPLE_NAME
    for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
        EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
        TASKS+=(":${EXAMPLE_NAME}:app:testDebugUnitTest")
    done

    if ! run_gradle "${TASKS[@]}" --no-parallel --no-watch-fs --warning-mode all; then
        log_error "Unit tests failed"
        exit 1
    fi

    log_success "Unit tests completed"
}

function run_instrumented_tests()
{
    "${RUN_INSTRUMENTED_TESTS}" || return 0

    cleanup_managed_device_emulators

    [[ -d "${PROJECT_DIR}/_REPORTS/androidTests" ]] && rm -rf "${PROJECT_DIR}/_REPORTS/androidTests"

    local -a FAILED_EXAMPLES=()
    local TOTAL_EXAMPLES=${#EXAMPLE_PROJECTS[@]}
    local CURRENT_INDEX=0
    local EXAMPLE_PATH EXAMPLE_NAME

    for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
        CURRENT_INDEX=$((CURRENT_INDEX + 1))
        EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
        log_step "Running instrumented tests (${CURRENT_INDEX}/${TOTAL_EXAMPLES}): ${EXAMPLE_NAME}"
        if ! run_gradle ":${EXAMPLE_NAME}:app:pixel_9api36googleDebugAndroidTest" --no-parallel --no-watch-fs --no-configuration-cache --no-daemon -Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect; then
            FAILED_EXAMPLES+=("${EXAMPLE_NAME}")
            if "${FAIL_FAST}"; then
                log_error "Instrumented tests failed for '${EXAMPLE_NAME}'"
                exit 1
            fi
        else
            log_success "Instrumented tests passed for '${EXAMPLE_NAME}'"
        fi
    done

    if [[ ${#FAILED_EXAMPLES[@]} -gt 0 ]]; then
        log_error "Instrumented tests failed for ${#FAILED_EXAMPLES[@]} example(s):"
        for EXAMPLE_NAME in "${FAILED_EXAMPLES[@]}"; do
            log_error "  - ${EXAMPLE_NAME}"
        done
        exit 1
    fi

    log_success "Instrumented tests completed"
}

function run_analysis()
{
    "${ANALYZE}" || return 0

    [[ -d "${PROJECT_DIR}/_REPORTS/detekt" ]] && rm -rf "${PROJECT_DIR}/_REPORTS/detekt"
    [[ -d "${PROJECT_DIR}/_REPORTS/ktlint" ]] && rm -rf "${PROJECT_DIR}/_REPORTS/ktlint"

    if ! filtering_active; then
        log_step "Analyzing Kotlin code for all examples..."
        if ! run_gradle checkAll --no-parallel --no-watch-fs --warning-mode all; then
            log_error "Code analysis failed"
            exit 1
        fi
        log_success "Code analysis completed"
        return
    fi

    log_step "Analyzing Kotlin code for selected examples..."

    local -a TASKS=()
    local EXAMPLE_PATH EXAMPLE_NAME
    for EXAMPLE_PATH in "${EXAMPLE_PROJECTS[@]}"; do
        EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
        TASKS+=(":${EXAMPLE_NAME}:app:check")
    done

    if ! run_gradle "${TASKS[@]}" --no-parallel --no-watch-fs --warning-mode all; then
        log_error "Code analysis failed"
        exit 1
    fi

    log_success "Code analysis completed"
}

function usage()
{
    SHOW_EXIT_MESSAGE=false

    printf '%b\n' "${COLOR_GREEN}
Usage: ${PROGNAME} [options]

Options:
    -h, --help                   Show this help message

    --sdk-archive=<path>         Set path to the Maps SDK for Android distribution zip.
                                 A zip that also carries the Compose extension AARs makes
                                 the Compose examples build from them as well.
                                 If missing, SDK will be retrieved from Maven SDK Registry

    --local-maven-repository=<path>
                                 Set specific local Maven repository path to search for
                                 Maps SDK for Android. If given, any other SDK path is
                                 ignored, including local SDK archive path

    --api-token=<token>          Specify API token to be hardcoded into examples

    --list-examples              List detected example names and exit

    --only <name>                Build/test only this example (can be repeated)

    --exclude <name>             Exclude this example from build/test (can be repeated)

    --run-unit-tests             Run unit tests locally

    --run-instrumented-tests     Run instrumented tests under Emulator

    --screenshots                Take screenshots of each example (requires connected
                                 device/emulator). Saves to _SCREENSHOTS folder.
                                 Screenshots exclude status bar and navigation bar.

    --install-apks                Install APKs to connected device/emulator without
                                 uninstalling them afterward. When combined with
                                 --screenshots, apps remain installed after screenshots.

    --screenshot-wait=<seconds>  Time to wait for app to render before screenshot
                                 (default: 3 seconds)

    --device=<serial>            Specify device/emulator serial for screenshots
                                 (required when multiple devices are connected)
                                 Example: --device=emulator-5554

    --map-gesture                Enable circular map gesture before screenshot
                                 (helps load map tiles and shows map interaction)

    --map-gesture-duration=<seconds>
                                 Duration of circular map gesture (default: 3 seconds)

    --analyze                    Analyze Kotlin code for all examples with Detekt and ktlint

    --fail-fast                  Exit on first error

    --no-cache                   Disable Gradle build cache

    --clean                      Remove Gradle/build artifacts on exit (default: on in CI, off locally)

    --console=(auto|plain|colored|verbose)
                                 Specifies which type of console output to generate.

                                 auto:    colored when attached to a terminal, plain otherwise (default)
                                 plain:   plain text only; disables all color
                                 colored: colored output
                                 verbose: colored output and verbose logging
${COLOR_RESET}"
}

# =============================================================================
# Main
# =============================================================================

SDK_ARCHIVE_PATH=""
SDK_AAR_PATH=""
LOCAL_MAVEN_REPOSITORY=""
API_TOKEN=""

RUN_UNIT_TESTS=false
RUN_INSTRUMENTED_TESTS=false
ANALYZE=false
FAIL_FAST=false
NO_CACHE=false

INSTALL_APKS=false

TAKE_SCREENSHOTS=false
SCREENSHOT_WAIT_TIME=3
DEVICE_SERIAL=""
MAP_GESTURE_ENABLED=false
MAP_GESTURE_DURATION=3

# Screenshot helper variables
DEVICE_WIDTH=0
DEVICE_HEIGHT=0
STATUS_BAR_HEIGHT=0
NAV_BAR_HEIGHT=0
PACKAGE_NAME=""
LAUNCH_ACTIVITY=""
AAPT_CMD=""

LIST_EXAMPLES=false

CLEAN_ON_EXIT=false

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

setup_mac_deps

if is_ci; then
    CLEAN_ON_EXIT=true
fi

SHORTOPTS="h"
LONGOPTS_LIST=(
    "help"
    "sdk-archive:"
    "local-maven-repository:"
    "api-token:"
    "list-examples"
    "only:"
    "exclude:"
    "run-unit-tests"
    "run-instrumented-tests"
    "screenshots"
    "install-apks"
    "screenshot-wait:"
    "device:"
    "map-gesture"
    "map-gesture-duration:"
    "analyze"
    "fail-fast"
    "no-cache"
    "clean"
    "console:"
)

if ! PARSED_OPTIONS="$(getopt \
    -s bash \
    --options "${SHORTOPTS}" \
    --longoptions "$(printf "%s," "${LONGOPTS_LIST[@]}")" \
    --name "${PROGNAME}" \
    -- "$@")"; then
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
        --list-examples)
            LIST_EXAMPLES=true
            ;;
        --only)
            shift
            ONLY_EXAMPLES+=("${1}")
            ;;
        --exclude)
            shift
            EXCLUDE_EXAMPLES+=("${1}")
            ;;
        --run-unit-tests)
            RUN_UNIT_TESTS=true
            ;;
        --run-instrumented-tests)
            RUN_INSTRUMENTED_TESTS=true
            ;;
        --screenshots)
            TAKE_SCREENSHOTS=true
            ;;
        --install-apks)
            INSTALL_APKS=true
            ;;
        --screenshot-wait)
            shift
            SCREENSHOT_WAIT_TIME="${1}"
            ;;
        --device)
            shift
            DEVICE_SERIAL="${1}"
            ;;
        --map-gesture)
            MAP_GESTURE_ENABLED=true
            ;;
        --map-gesture-duration)
            shift
            MAP_GESTURE_DURATION="${1}"
            ;;
        --analyze)
            ANALYZE=true
            ;;
        --fail-fast)
            FAIL_FAST=true
            ;;
        --no-cache)
            NO_CACHE=true
            ;;
        --clean)
            CLEAN_ON_EXIT=true
            ;;
        --console)
            shift
            CONSOLE_MODE="${1}"
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

apply_console_mode

if [[ -n "${LOCAL_MAVEN_REPOSITORY}" ]] && [[ -n "${SDK_ARCHIVE_PATH}" ]]; then
    log_warning "--local-maven-repository overrides --sdk-archive; ignoring --sdk-archive."
    SDK_ARCHIVE_PATH=""
fi

pushd "${PROJECT_DIR}" &> /dev/null

check_prerequisites
check_screenshot_prerequisites
discover_examples

if "${LIST_EXAMPLES}"; then
    list_examples
    popd &> /dev/null
    exit 0
fi

if filtering_active; then
    validate_filter_names
fi
filter_examples

extract_sdk_archive
copy_sdk_to_examples

setup_gradle
build_examples
collect_apks
take_screenshots
install_apks
run_unit_tests
run_instrumented_tests
run_analysis

popd &> /dev/null

exit 0
