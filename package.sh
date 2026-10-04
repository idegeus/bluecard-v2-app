#!/usr/bin/env bash
# Runs all tests and lint, builds debug + release APKs and the release App Bundle, and puts them plus the R8
# mapping file and a source ZIP in dist/.
set -euo pipefail
cd "$(dirname "$0")"
# Unit tests of every module, Android lint, the iOS compile check (linking the iOS app needs Xcode, see docs/IOS.md),
# then the Android builds.
./gradlew :engine:jvmTest :multiplayer:jvmTest :shared:testDebugUnitTest :app:testDebugUnitTest :app:lintDebug \
    :shared:compileKotlinIosArm64 :shared:compileKotlinIosSimulatorArm64 \
    assembleDebug assembleRelease :app:bundleRelease
mkdir -p dist
cp app/build/outputs/apk/debug/app-debug.apk dist/BlueCard-debug.apk
cp app/build/outputs/apk/release/app-release.apk dist/BlueCard-release.apk
# The App Bundle is what you upload to the Play Console (signed with the upload key when configured).
cp app/build/outputs/bundle/release/app-release.aab dist/BlueCard-release.aab
cp app/build/outputs/mapping/release/mapping.txt dist/BlueCard-mapping.txt
rm -f dist/BlueCard-source.zip
zip -qr dist/BlueCard-source.zip . \
    -x '*/build/*' 'build/*' '.gradle/*' '.kotlin/*' '*/.kotlin/*' 'dist/*' 'local.properties' '.idea/*' '*.iml' '.DS_Store' \
       'iosApp/build/*' '*/xcuserdata/*'
ls -lh dist
