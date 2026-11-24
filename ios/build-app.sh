#!/bin/bash
set -euo pipefail
root=$(cd "$(dirname "$0")" && pwd)
mode=${1:-sim}
case "$mode" in
  sim) sdk=iphonesimulator; configuration=Debug ;;
  ipa) sdk=iphoneos; configuration=Release ;;
  *) echo "Usage: $0 [sim|ipa]" >&2; exit 2 ;;
esac
"$root/build-native.sh" "$sdk"
cmake -E copy_directory "$root/build/$sdk/stage/share/system3" "$root/build/system3"
"$root/../collect-licenses.sh" "$root/build/$sdk" "$root/build/licenses"
xcodegen generate --spec "$root/project.yml"
hvf_ldflags=
if grep -q '^#define FT_CONFIG_OPTION_HVF' "$root/build/$sdk/stage/include/freetype2/freetype/config/ftoption.h"; then
  hvf_ldflags=-weak-lhvf
fi
xcodebuild -project "$root/AliceHub.xcodeproj" -scheme AliceHub \
  -configuration "$configuration" -sdk "$sdk" \
  -derivedDataPath "$root/build/app-$sdk" CODE_SIGNING_ALLOWED=NO HVF_LDFLAGS="$hvf_ldflags" \
  CURRENT_PROJECT_VERSION="${IOS_BUILD_NUMBER:-1}" \
  MARKETING_VERSION="${IOS_MARKETING_VERSION:-0.1.0}" build
if [[ "$mode" == ipa ]]; then
  package="$root/build/package"
  mkdir -p "$package/Payload"
  rm -rf "$package/Payload/AliceHub.app"
  ditto "$root/build/app-$sdk/Build/Products/Release-iphoneos/AliceHub.app" "$package/Payload/AliceHub.app"
  rm -f "$root/build/AliceHub.ipa"
  (cd "$package" && /usr/bin/zip -qry "$root/build/AliceHub.ipa" Payload)
  echo "Unsigned IPA: $root/build/AliceHub.ipa"
fi
