#!/bin/bash

# Copyright (C) 2019-2021, General Magic B.V.
# All rights reserved.
#
# This software is confidential and proprietary information of General Magic
# ("Confidential Information"). You shall not disclose such Confidential
# Information and shall use it only in accordance with the terms of the
# license agreement you entered into with General Magic.

function on_exit()
{
	if [ -n "$MY_DIR" ]; then
		find "$MY_DIR" -type d -name "build" -exec rm -rf {} +
		find "$MY_DIR" -type d -name ".gradle" -exec rm -rf {} +
		find "$MY_DIR" -type f -name "*.aar" -exec rm {} +
	fi
}
trap 'on_exit' EXIT

if [ -z "${ANDROID_SDK_ROOT}" ]; then
	echo "ANDROID_SDK_ROOT env. variable must be defined"
	exit 1
fi

set -euox pipefail

MY_DIR="$(cd "$(dirname "$0")" && pwd)"
pushd "${MY_DIR}" &>/dev/null || exit 1

if [[ "$#" -eq 0 ]]; then
	echo "You must provide local path to Maps SDK"
    echo
    exit 1
fi

MAPS_SDK="$1"

MAPS_SDK_NAME="$(basename $MAPS_SDK .tar.bz2)"

tar -xjf "$MAPS_SDK" --wildcards --no-anchored '*.aar'

# Find paths that contain an app module
EXAMPLE_PROJECTS=$(find . -maxdepth 1 -type d -exec [ -d {}/app/libs ] \; -print -prune)

for EXAMPLE_PATH in $EXAMPLE_PROJECTS; do
    cp "$MAPS_SDK_NAME/$MAPS_SDK_NAME.aar" "$EXAMPLE_PATH/app/libs"
done

rm -rf "$MAPS_SDK_NAME"

GRADLE_WRAPPER=$(find . -type f -executable -name gradlew -print -quit)
$GRADLE_WRAPPER assembleAll

if [ -d "APK" ]; then
	rm -rf "APK"
fi
mkdir APK
for EXAMPLE_PATH in $EXAMPLE_PROJECTS; do
	mkdir -p "APK/$EXAMPLE_PATH"
    cp "$EXAMPLE_PATH/build/app/outputs/apk/release/app-release.apk" "APK/$EXAMPLE_PATH"
done

popd &>/dev/null || exit 1
