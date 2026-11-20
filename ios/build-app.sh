#!/bin/bash
set -euo pipefail
root=$(cd "$(dirname "$0")" && pwd)
mode=${1:-sim}
case "$mode" in
  sim) sdk=iphonesimulator; configuration=Debug ;;
  ipa) sdk=iphoneos; configuration=Release ;;
  *) echo "Usage: $0 [sim|ipa]" >&2; exit 2 ;;
esac
if [[ ! -f "$root/build/$sdk/stage/lib/libxsystem4.a" ]]; then
  "$root/build-native.sh" "$sdk"
fi
xcodegen generate --spec "$root/project.yml"
xcodebuild -project "$root/AliceHub.xcodeproj" -scheme AliceHub \
  -configuration "$configuration" -sdk "$sdk" \
  -derivedDataPath "$root/build/app-$sdk" CODE_SIGNING_ALLOWED=NO build
if [[ "$mode" == ipa ]]; then
  package="$root/build/package"
  mkdir -p "$package/Payload"
  rm -rf "$package/Payload/AliceHub.app"
  ditto "$root/build/app-$sdk/Build/Products/Release-iphoneos/AliceHub.app" "$package/Payload/AliceHub.app"
  rm -f "$root/build/AliceHub.ipa"
  (cd "$package" && /usr/bin/zip -qry "$root/build/AliceHub.ipa" Payload)
  echo "Unsigned IPA: $root/build/AliceHub.ipa"
fi
