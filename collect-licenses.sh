#!/bin/sh
set -eu

build_dir=${1:?build directory required}
dest_dir=${2:?destination directory required}
source_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)

mkdir -p "$dest_dir"
cp "$build_dir/libogg-prefix/src/libogg/COPYING" "$dest_dir/libogg"
cp "$build_dir/libvorbis-prefix/src/libvorbis/COPYING" "$dest_dir/libvorbis"
cp "$build_dir/libjpeg-turbo-prefix/src/libjpeg-turbo/LICENSE.md" "$dest_dir/libjpeg-turbo"
cp "$build_dir/libwebp-prefix/src/libwebp/COPYING" "$dest_dir/libwebp"
cp "$build_dir/libpng-prefix/src/libpng/LICENSE" "$dest_dir/libpng"
cp "$build_dir/libffi-prefix/src/libffi/LICENSE" "$dest_dir/libffi"
cp "$build_dir/libsndfile-prefix/src/libsndfile/COPYING" "$dest_dir/libsndfile"
cp "$build_dir/libdeflate-prefix/src/libdeflate/COPYING" "$dest_dir/libdeflate"
cp "$build_dir/xz-prefix/src/xz/COPYING" "$dest_dir/xz"
cp "$build_dir/libarchive-prefix/src/libarchive/COPYING" "$dest_dir/libarchive"

xsystem4_deps_dir=$build_dir/xsystem4-prefix/src/xsystem4-build/_deps
cp "$xsystem4_deps_dir/cglm-src/LICENSE" "$dest_dir/cglm"
if [ -f "$build_dir/SDL2-prefix/src/SDL2/LICENSE.txt" ]; then
  cp "$build_dir/SDL2-prefix/src/SDL2/LICENSE.txt" "$dest_dir/SDL"
elif [ -f "$xsystem4_deps_dir/sdl-src/LICENSE.txt" ]; then
  cp "$xsystem4_deps_dir/sdl-src/LICENSE.txt" "$dest_dir/SDL"
fi
if [ -f "$build_dir/SDL2_mixer-prefix/src/SDL2_mixer/LICENSE.txt" ]; then
  cp "$build_dir/SDL2_mixer-prefix/src/SDL2_mixer/LICENSE.txt" "$dest_dir/SDL2_mixer"
fi
if [ -f "$xsystem4_deps_dir/freetype-src/docs/GPLv2.TXT" ]; then
  cp "$xsystem4_deps_dir/freetype-src/docs/GPLv2.TXT" "$dest_dir/freetype"
elif [ -f "$build_dir/freetype-prefix/src/freetype/docs/GPLv2.TXT" ]; then
  cp "$build_dir/freetype-prefix/src/freetype/docs/GPLv2.TXT" "$dest_dir/freetype"
fi
cp "$source_dir/xsystem4/COPYING" "$dest_dir/xsystem4"
cp "$source_dir/xsystem4/subprojects/libsys4/COPYING" "$dest_dir/libsys4"
cp "$source_dir/xsystem4/fonts/VL-Gothic-Regular.license" "$dest_dir/VL-Gothic-Regular"
cp "$source_dir/xsystem4/fonts/HanaMinA.license" "$dest_dir/HanaMinA"
cp "$source_dir/xsystem35/COPYING" "$dest_dir/xsystem35"
cp "$source_dir/xsystem35/licenses/MTLc3m.txt" "$dest_dir/MTLc3m"
cp "$source_dir/xsystem35/licenses/mincho.txt" "$dest_dir/mincho"
cp "$source_dir/xsystem35/licenses/nanojpeg.txt" "$dest_dir/nanojpeg"
cp "$source_dir/xsystem35/licenses/microui.txt" "$dest_dir/microui"
cp "$build_dir"/stage/share/system3/licenses/* "$dest_dir/"
