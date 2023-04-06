#!/bin/bash

# Copyright (C) 2019-2023, Magic Lane B.V.
# All rights reserved.
#
# This software is confidential and proprietary information of Magic Lane
# ("Confidential Information"). You shall not disclose such Confidential
# Information and shall use it only in accordance with the terms of the
# license agreement you entered into with Magic Lane.

declare GRADLE_WRAPPER=""

function on_err()
{
	echo "Error on line $1"
	
	exit 1
}
trap 'on_err ${LINENO}' ERR

function on_exit()
{
	if [ -n "${EXAMPLE_PROJECTS}" ]; then
		for EXAMPLE_PATH in ${EXAMPLE_PROJECTS}; do
			find "${EXAMPLE_PATH}" -type d -name "build" -exec rm -rf {} +
			find "${EXAMPLE_PATH}" -type d -name ".gradle" -exec rm -rf {} +
			find "${EXAMPLE_PATH}" -type f -name "*.aar" -exec rm {} +
		done
	fi

	if [ -n "${GRADLE_WRAPPER}" ]; then
		${GRADLE_WRAPPER} --stop
	fi

	if [ -d "${MY_DIR}/.gradle" ]; then
		rm -rf "${MY_DIR}/.gradle"
	fi
}
trap 'on_exit' EXIT

function num_cpus() 
{
	local N_CPUS

	if [[ -f /proc/cpuinfo ]]; then
		N_CPUS=$(grep -c ^processor /proc/cpuinfo)
	else
		# Fallback method
		N_CPUS=$(getconf _NPROCESSORS_ONLN)
	fi
	if [[ -z ${N_CPUS} ]]; then
		echo "ERROR: Unable to determine the number of CPUs"
	fi

	echo ${N_CPUS}
}


if [ -z "${ANDROID_SDK_ROOT}" ]; then
	echo "ANDROID_SDK_ROOT env. variable must be defined"
	exit 1
fi

set -euox pipefail

MY_DIR="$(cd "$(dirname "$0")" && pwd)"
pushd "${MY_DIR}" &>/dev/null || exit 1

if [[ "$#" -eq 0 ]]; then
	echo "You must provide local path to AAR"
    echo
    exit 1
fi

MAPS_SDK_AAR="${1}"

# Find paths that contain an app module
EXAMPLE_PROJECTS=$(find ${MY_DIR} -maxdepth 1 -type d -exec [ -d {}/app/libs ] \; -print -prune)

for EXAMPLE_PATH in ${EXAMPLE_PROJECTS}; do
    cp "${MAPS_SDK_AAR}" "${EXAMPLE_PATH}/app/libs"
done

GRADLE_WRAPPER=$(find ${MY_DIR} -maxdepth 1 -type f -executable -name gradlew -print -quit)
${GRADLE_WRAPPER} --no-parallel --no-watch-fs assembleAll 
${GRADLE_WRAPPER} --stop

if [ -d "APK" ]; then
	rm -rf "APK"
fi
mkdir APK

for EXAMPLE_PATH in ${EXAMPLE_PROJECTS}; do
	mkdir -p "APK/$(basename ${EXAMPLE_PATH})"
    cp "${EXAMPLE_PATH}/build/app/outputs/apk/release/app-release.apk" "APK/$(basename ${EXAMPLE_PATH})"
done

popd &>/dev/null || exit 1
