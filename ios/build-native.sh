#!/bin/bash
set -euo pipefail
sdk=${1:-iphonesimulator}
case "$sdk" in
  iphoneos|iphonesimulator) ;;
  *) echo "Usage: $0 [iphoneos|iphonesimulator]" >&2; exit 2 ;;
esac
root=$(cd "$(dirname "$0")" && pwd)
brew_prefix=$(brew --prefix)
export PATH="$brew_prefix/opt/bison/bin:$brew_prefix/opt/flex/bin:$PATH"
cmake -S "$root" -B "$root/build/$sdk" -G Ninja \
  -DCMAKE_SYSTEM_NAME=iOS -DCMAKE_OSX_SYSROOT="$sdk" \
  -DCMAKE_OSX_ARCHITECTURES=arm64 -DCMAKE_OSX_DEPLOYMENT_TARGET=15.0 \
  -DCMAKE_BUILD_TYPE=Release
cmake --build "$root/build/$sdk" --parallel "$(sysctl -n hw.ncpu)"
