#!/usr/bin/env bash
# vim:ts=4:sts=4:sw=4:et

# SPDX-FileCopyrightText: 1995-2025 Magic Lane International B.V. <info@magiclane.com>
# SPDX-License-Identifier: BSD-3-Clause
#
# Contact Magic Lane at <info@magiclane.com> for commercial licensing options.

declare -r PROGNAME=${0##*/}

function msg()
{
    echo -e "\033[33;1m[*] $*\033[0m\n"
}

function error_msg()
{
    echo -e "\033[31;1m[!] $*\033[0m\n" >&2
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
        ${GRADLE_WRAPPER} --stop
    fi

    if [[ -n ${SDK_TEMP_DIR} ]]; then
        rm -fr "${SDK_TEMP_DIR:?}"
    fi

    echo
    msg "Bye-Bye"
}
trap 'on_exit' EXIT

function is_mac()
{
    local OS_NAME
    OS_NAME=$(uname | tr "[:upper:]" "[:lower:]")
    if [[ ${OS_NAME} =~ "darwin" ]]; then
        return 0
    fi

    return 1
}

set -eEuo pipefail

SDK_ARCHIVE_PATH=""
LOCAL_MAVEN_REPOSITORY=""
API_TOKEN=""
RUN_UNIT_TESTS=false
RUN_INSTRUMENTED_TESTS=false
EMULATOR_HEADLESS=false
ANALYZE=false

MY_DIR="$(cd "$(dirname "$0")" && pwd)"

if is_mac; then
    if [ ! -f "$(brew --prefix)/opt/gnu-getopt/bin/getopt" ]; then
        error_msg "This script requires 'brew install gnu-getopt && brew link --force gnu-getopt'"
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
                    Set path to the Maps SDK for Android archive (tar.bz2 or aar path).
                    If missing, SDK will be retrieved from Maven SDK Registry
    [OPTIONAL] --local-maven-repository=<path>
                    Set specific local Maven repository path to search for Maps SDK for Android.
                    If given, any other SDK path is ignored, including local SDK archive path

    [OPTIONAL] --api-token
                    Specify API token to be hardcoded into examples
    [OPTIONAL] --run-unit-tests
                    Run unit tests locally
    [OPTIONAL] --run-instrumented-tests
                    Run instrumented tests under Emulator
    [OPTIONAL] --emulator-headless
                    Emulator is used headless when running instrumented tests
    [OPTIONAL] --analyze
                    Analyze Kotlin code for all examples with Detekt and ktlint
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
    "dependency-updates"
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
        --)
            shift
            break
            ;;
        *)
            error_msg "Internal error"
            exit 1
            ;;
    esac
    shift
done

msg "Checking prerequisites..."

if [ -n "${LOCAL_MAVEN_REPOSITORY}" ]; then
	if [ ! -d "${LOCAL_MAVEN_REPOSITORY}"/com/magiclane ]; then
		error_msg "Local Maven repository path is invalid"
		usage
		exit 1
	fi
else
	if [ -n "${SDK_ARCHIVE_PATH}" ]; then
		if [ ! -f "${SDK_ARCHIVE_PATH}" ]; then
			error_msg "You must provide local path to SDK archive"
			usage
			exit 1
		fi
	fi
fi

if [ -z ${ANDROID_SDK_ROOT+x} ]; then
    error_msg "ANDROID_SDK_ROOT not set. Please export ANDROID_SDK_ROOT env. variable"
    exit 1
fi

if [ -z ${JAVA_HOME+x} ]; then
    error_msg "JAVA_HOME not set. Please export JAVA_HOME env. variable"
    exit 1
fi

JAVA_VERSION_STRING=$($JAVA_HOME/bin/java -version 2>&1)
JAVA_VERSION=$(echo "$JAVA_VERSION_STRING" | grep 'version [ "]\(17\|21\).*[ "]' || test $? = 1;)
if [ "${JAVA_VERSION}" == "" ]; then
    error_msg "Wrong Java version. 17 or 21 is required. Found '$JAVA_VERSION_STRING'"
    exit 1
fi

if [ -z "${LOCAL_MAVEN_REPOSITORY}" ]; then
	if [ -n "${SDK_ARCHIVE_PATH}" ]; then
		SDK_ARCHIVE_FILENAME="${SDK_ARCHIVE_PATH##*/}"
		SDK_AAR_PATH="${SDK_ARCHIVE_PATH}"
		if [[ ! "${SDK_ARCHIVE_FILENAME}" =~ (.tar.bz2|.aar)$ ]]; then
			error_msg "Invalid SDK archive provided '${SDK_ARCHIVE_PATH}'"
			exit 1
		else
			if [[ "${SDK_ARCHIVE_FILENAME}" =~ .tar.bz2$ ]]; then
				msg "Extract SDK..."

				SDK_TEMP_DIR="$(mktemp -d)"
				tar -xvf "${SDK_ARCHIVE_PATH}" --strip-components=1 -C "${SDK_TEMP_DIR}"
				SDK_AAR_PATH="$(find "${SDK_TEMP_DIR}" -maxdepth 2 -type f -iname "*.aar" | head -1)"
			fi
		fi

		if [ ! -f "${SDK_AAR_PATH}" ]; then
			error_msg "Invalid aar path '${SDK_AAR_PATH}'"
			exit 1
		fi
	fi
fi

pushd "${MY_DIR}" &> /dev/null

# Find paths that contain an app module
EXAMPLE_PROJECTS=$(find "${MY_DIR}" -maxdepth 1 -type d -exec [ -d {}/app/libs ] \; -print -prune)

if [ ! -z "${SDK_ARCHIVE_PATH}" ]; then
	for EXAMPLE_PATH in ${EXAMPLE_PROJECTS}; do
		cp "${SDK_AAR_PATH}" "${EXAMPLE_PATH}/app/libs"
	done
fi

GRADLE_OPTS="-Xms8g -Xmx8g"
GRADLE_OPTS="${GRADLE_OPTS} -XX:+HeapDumpOnOutOfMemoryError"
GRADLE_OPTS="${GRADLE_OPTS} -Dorg.gradle.daemon=false"
GRADLE_OPTS="${GRADLE_OPTS} -Dkotlin.incremental=false"
GRADLE_OPTS="${GRADLE_OPTS} -Dfile.encoding=UTF-8"
export GRADLE_OPTS

msg "Build all examples..."

GRADLE_WRAPPER=$(find "${MY_DIR}" -maxdepth 1 -type f -executable -name gradlew -print -quit)
GEM_TOKEN="${API_TOKEN}" GEM_SDK_LOCAL_MAVEN_PATH="${LOCAL_MAVEN_REPOSITORY}" ${GRADLE_WRAPPER} --parallel --no-watch-fs --stacktrace --warning-mode all buildAll || true

[ -d "_APK" ] && rm -rf _APK
mkdir _APK

[ -d "_REPORTS" ] && rm -rf _REPORTS
mkdir _REPORTS

for EXAMPLE_PATH in ${EXAMPLE_PROJECTS}; do
    EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
    if [ -f "${EXAMPLE_PATH}"/build/app/outputs/apk/release/app-release.apk ]; then
		mv "${EXAMPLE_PATH}"/build/app/outputs/apk/release/app-release.apk "_APK/${EXAMPLE_NAME}_app-release.apk"
	fi
done

if ${RUN_UNIT_TESTS}; then
    msg "Run unit tests from all examples..."
    GEM_TOKEN="${API_TOKEN}" GEM_SDK_LOCAL_MAVEN_PATH="${LOCAL_MAVEN_REPOSITORY}" ${GRADLE_WRAPPER} --no-parallel --no-watch-fs --warning-mode all runUnitTestsAll || true
fi

if ${RUN_INSTRUMENTED_TESTS}; then
    if ! ${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin/sdkmanager --list_installed | grep "system-images;android-34;aosp_atd;x86_64" > /dev/null; then
        error_msg "Please install 'system-images;android-34;aosp_atd;x86_64' SDK package"
        exit 2
    fi

    [ -d "_REPORTS/androidTests" ] && rm -rf _REPORTS/androidTests

    msg "Run instrumented tests from all examples..."
    GUI_OPTIONS=""
    if ! ${EMULATOR_HEADLESS}; then
        GUI_OPTIONS="-Pandroid.testoptions.manageddevices.emulator.gpu=swiftshader_indirect --enable-display"
    fi
    for EXAMPLE_PATH in ${EXAMPLE_PROJECTS}; do
        EXAMPLE_NAME="$(basename "${EXAMPLE_PATH}")"
        GEM_TOKEN="${API_TOKEN}" GEM_SDK_LOCAL_MAVEN_PATH="${LOCAL_MAVEN_REPOSITORY}" ${GRADLE_WRAPPER} --no-parallel --no-watch-fs --stacktrace --warning-mode all :${EXAMPLE_NAME}:app:pixel_8api34googleDebugAndroidTest ${GUI_OPTIONS} || true
    done
fi

if ${ANALYZE}; then
	[ -d "_REPORTS/detekt" ] && rm -rf _REPORTS/detekt
	[ -d "_REPORTS/ktlint" ] && rm -rf _REPORTS/ktlint

    msg "Analyze Kotlin code for all examples..."
    GEM_TOKEN="${API_TOKEN}" GEM_SDK_LOCAL_MAVEN_PATH="${LOCAL_MAVEN_REPOSITORY}" ${GRADLE_WRAPPER} --no-parallel --no-watch-fs --warning-mode all checkAll || true
   fi

${GRADLE_WRAPPER} --stop

popd &> /dev/null
