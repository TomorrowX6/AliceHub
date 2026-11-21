cmake_minimum_required(VERSION 3.24)
project(alicehub-ios LANGUAGES C CXX)
if(NOT CMAKE_SYSTEM_NAME STREQUAL "iOS")
  message(FATAL_ERROR "Use build-native.sh to select an iOS SDK")
endif()
include(ExternalProject)
set(stage "${CMAKE_BINARY_DIR}/stage")
set(common
  -DCMAKE_SYSTEM_NAME=iOS
  -DCMAKE_SYSTEM_PROCESSOR=arm64
  -DCMAKE_OSX_SYSROOT=${CMAKE_OSX_SYSROOT}
  -DCMAKE_OSX_ARCHITECTURES=${CMAKE_OSX_ARCHITECTURES}
  -DCMAKE_OSX_DEPLOYMENT_TARGET=${CMAKE_OSX_DEPLOYMENT_TARGET}
  -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
  -DCMAKE_INSTALL_PREFIX=${stage}
  -DCMAKE_STAGING_PREFIX=${stage}
  -DCMAKE_PREFIX_PATH=${stage}
  -DCMAKE_FIND_ROOT_PATH=${stage}
  -DCMAKE_FIND_ROOT_PATH_MODE_LIBRARY=ONLY
  -DCMAKE_FIND_ROOT_PATH_MODE_INCLUDE=ONLY
  -DCMAKE_FIND_ROOT_PATH_MODE_PACKAGE=ONLY
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5
  -DBUILD_SHARED_LIBS=OFF
  -DBUILD_TESTING=OFF)
function(dependency name url hash)
  ExternalProject_Add(${name} URL ${url} URL_HASH ${hash}
    CMAKE_ARGS ${common} ${ARGN})
endfunction()
dependency(libogg https://github.com/xiph/ogg/releases/download/v1.3.5/libogg-1.3.5.tar.xz SHA1=5a368421a636f7faa4c2f662857cb507dffd7c99)
dependency(xz https://github.com/tukaani-project/xz/releases/download/v5.8.4/xz-5.8.4.tar.xz SHA256=4ce24038fd4221e0d13bc1a2de7a4db56e90b92b3bf75321f6c14be73f65de4b
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON
  -DXZ_TOOL_XZ=OFF -DXZ_TOOL_XZDEC=OFF -DXZ_TOOL_LZMADEC=OFF
  -DXZ_TOOL_LZMAINFO=OFF -DXZ_TOOL_SCRIPTS=OFF -DXZ_DOC=OFF -DXZ_NLS=OFF)
dependency(libarchive https://github.com/libarchive/libarchive/releases/download/v3.8.11/libarchive-3.8.11.tar.xz SHA256=a3dc0872b1b39da34dc18ef1b944f0f11ffd37425d70ae04be679279f1cf43d2
  -DCMAKE_POSITION_INDEPENDENT_CODE=ON
  -DENABLE_TAR=OFF -DENABLE_CPIO=OFF -DENABLE_CAT=OFF -DENABLE_UNZIP=OFF
  -DENABLE_TEST=OFF -DENABLE_OPENSSL=OFF -DENABLE_NETTLE=OFF -DENABLE_MBEDTLS=OFF
  -DENABLE_LIBB2=OFF -DENABLE_LZ4=OFF -DENABLE_LZO=OFF -DENABLE_ZSTD=OFF
  -DENABLE_BZip2=OFF -DENABLE_LIBXML2=OFF -DENABLE_EXPAT=OFF
  -DENABLE_PCREPOSIX=OFF -DENABLE_PCRE2POSIX=OFF -DENABLE_ICONV=OFF
  -DENABLE_ACL=OFF -DENABLE_XATTR=OFF -DENABLE_LZMA=ON -DENABLE_ZLIB=ON)
ExternalProject_Add_StepDependencies(libarchive configure xz)
dependency(libvorbis https://github.com/xiph/vorbis/releases/download/v1.3.7/libvorbis-1.3.7.tar.xz SHA1=0a2dd71a999656b8091506839e8007a61a8fda1f)
ExternalProject_Add_StepDependencies(libvorbis configure libogg)
dependency(libjpeg-turbo https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/3.1.0/libjpeg-turbo-3.1.0.tar.gz SHA1=ba3c0152f14a504018de19c9f62250d8f3351525
  -DCMAKE_PROJECT_INCLUDE=${CMAKE_CURRENT_LIST_DIR}/target-processor.cmake
  -DENABLE_SHARED=OFF -DENABLE_STATIC=ON -DWITH_TOOLS=OFF -DWITH_TESTS=OFF)
dependency(libwebp https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-1.3.2.tar.gz SHA1=f941661a869df7347d7ca9208acb4e6d41f614de
  -DWEBP_BUILD_ANIM_UTILS=OFF -DWEBP_BUILD_CWEBP=OFF -DWEBP_BUILD_DWEBP=OFF
  -DWEBP_BUILD_GIF2WEBP=OFF -DWEBP_BUILD_IMG2WEBP=OFF -DWEBP_BUILD_VWEBP=OFF
  -DWEBP_BUILD_WEBPINFO=OFF -DWEBP_BUILD_WEBPMUX=OFF -DWEBP_BUILD_EXTRAS=OFF)
dependency(libpng https://download.sourceforge.net/libpng/libpng-1.6.47.tar.xz SHA256=b213cb381fbb1175327bd708a77aab708a05adde7b471bc267bd15ac99893631
  -DPNG_SHARED=OFF -DPNG_FRAMEWORK=OFF -DPNG_TESTS=OFF -DPNG_TOOLS=OFF -DPNG_ARM_NEON=on)
execute_process(COMMAND xcrun --sdk ${CMAKE_OSX_SYSROOT} --show-sdk-path
  OUTPUT_VARIABLE sdk_path OUTPUT_STRIP_TRAILING_WHITESPACE COMMAND_ERROR_IS_FATAL ANY)
set(ffi_target arm64-apple-ios${CMAKE_OSX_DEPLOYMENT_TARGET})
if(CMAKE_OSX_SYSROOT MATCHES "[Ss]imulator")
  string(APPEND ffi_target "-simulator")
endif()
set(ffi_cc "${CMAKE_C_COMPILER} -target ${ffi_target} -isysroot ${sdk_path}")
ExternalProject_Add(libffi
  URL https://github.com/libffi/libffi/releases/download/v3.4.6/libffi-3.4.6.tar.gz
  URL_HASH SHA1=19251dfee520dff42acefe36bfe76d7168071e01
  PATCH_COMMAND ${CMAKE_COMMAND} -DSOURCE_DIR=<SOURCE_DIR>
    -P ${CMAKE_CURRENT_LIST_DIR}/patch-libffi.cmake
  CONFIGURE_COMMAND <SOURCE_DIR>/configure --host=aarch64-apple-darwin
    --prefix=${stage} --with-pic --disable-shared --disable-exec-static-tramp
    --disable-dependency-tracking --disable-builddir
    --disable-multi-os-directory --disable-structs --disable-raw-api --disable-docs
    "CC=${ffi_cc}" "AS=${ffi_cc}" AR=${CMAKE_AR} RANLIB=${CMAKE_RANLIB}
    gcc_cv_as_cfi_pseudo_op=no
  BUILD_COMMAND make -j4 INSTALL_COMMAND make install)
ExternalProject_Add(libsndfile
  GIT_REPOSITORY https://github.com/kichikuou/libsndfile.git
  GIT_TAG be82f4be73c77f1e759bda9dfe1625204441465a
  CMAKE_ARGS ${common} -DBUILD_PROGRAMS=OFF -DBUILD_EXAMPLES=OFF -DENABLE_CPACK=OFF)
ExternalProject_Add_StepDependencies(libsndfile configure libvorbis)
dependency(libdeflate https://github.com/ebiggers/libdeflate/releases/download/v1.25/libdeflate-1.25.tar.gz SHA1=7e70557d094b54c3ee16f6c012976dc8d685b22a
  -DLIBDEFLATE_BUILD_SHARED_LIB=OFF -DLIBDEFLATE_GZIP_SUPPORT=OFF -DLIBDEFLATE_BUILD_GZIP=OFF)
dependency(SDL2 https://github.com/libsdl-org/SDL/releases/download/release-2.30.9/SDL2-2.30.9.tar.gz SHA1=9403df0573d47f62f2de074b582b87576bb4abbc
  -DSDL_SHARED=OFF -DSDL_STATIC=ON -DSDL_TEST=OFF -DSDL_TESTS=OFF)
ExternalProject_Add_Step(SDL2 alicehub_messagebox_patch
  COMMAND ${CMAKE_COMMAND} -DSOURCE_DIR=<SOURCE_DIR>
    -P ${CMAKE_CURRENT_LIST_DIR}/patch-sdl-messagebox.cmake
  DEPENDEES patch DEPENDERS configure
  DEPENDS ${CMAKE_CURRENT_LIST_DIR}/patch-sdl-messagebox.cmake)
ExternalProject_Add_Step(SDL2 alicehub_lifecycle_patch
  COMMAND ${CMAKE_COMMAND} -DSOURCE_DIR=<SOURCE_DIR>
    -P ${CMAKE_CURRENT_LIST_DIR}/patch-sdl-lifecycle.cmake
  DEPENDEES patch DEPENDERS configure
  DEPENDS ${CMAKE_CURRENT_LIST_DIR}/patch-sdl-lifecycle.cmake)
dependency(SDL2_mixer https://github.com/libsdl-org/SDL_mixer/releases/download/release-2.8.1/SDL2_mixer-2.8.1.tar.gz SHA1=b38a337de10b04c51053ca0d17b57c31f5dcdd96
  -DSDL2MIXER_VENDORED=OFF -DSDL2MIXER_DEPS_SHARED=OFF
  -DSDL2MIXER_INSTALL=ON -DSDL2MIXER_SAMPLES=OFF
  -DSDL2MIXER_FLAC=OFF -DSDL2MIXER_GME=OFF -DSDL2MIXER_MOD=OFF
  -DSDL2MIXER_MIDI=OFF -DSDL2MIXER_OPUS=OFF -DSDL2MIXER_WAVPACK=OFF
  -DSDL2MIXER_MP3=ON -DSDL2MIXER_MP3_MINIMP3=ON
  -DSDL2MIXER_VORBIS=VORBISFILE -DSDL2MIXER_VORBIS_VORBISFILE_SHARED=OFF)
ExternalProject_Add_StepDependencies(SDL2_mixer configure SDL2 libogg libvorbis)
ExternalProject_Add(freetype
  GIT_REPOSITORY https://github.com/freetype/freetype.git GIT_TAG VER-2-13-3 GIT_SHALLOW TRUE
  CMAKE_ARGS ${common} -DFT_DISABLE_HARFBUZZ=ON -DFT_DISABLE_BROTLI=ON
    -DFT_DISABLE_BZIP2=ON -DFT_DISABLE_PNG=ON)
ExternalProject_Add(xsystem4
  SOURCE_DIR ${CMAKE_CURRENT_LIST_DIR}/../xsystem4
  CMAKE_ARGS ${common} -DALICEHUB_IOS=ON -DCMAKE_C_FLAGS=-fno-common
    -DCMAKE_MODULE_PATH=${CMAKE_CURRENT_LIST_DIR}/../cmake
  BUILD_ALWAYS YES)
ExternalProject_Add_StepDependencies(xsystem4 configure
  SDL2 freetype libjpeg-turbo libwebp libpng libffi libsndfile libdeflate)
ExternalProject_Add(xsystem35
  SOURCE_DIR ${CMAKE_CURRENT_LIST_DIR}/../xsystem35
  CMAKE_ARGS ${common} -DALICEHUB_IOS=ON -DCMAKE_C_FLAGS=-fno-common
    -DXSYSTEM35_SDL_VERSION=2
    -DENABLE_DEBUGGER=OFF
    -DCMAKE_MODULE_PATH=${CMAKE_CURRENT_LIST_DIR}/../cmake
  BUILD_ALWAYS YES)
ExternalProject_Add_StepDependencies(xsystem35 configure
  SDL2 SDL2_mixer freetype libwebp)

function(isolate_engine name)
  set(object "${stage}/lib/${name}_engine.o")
  set(archive "${stage}/lib/lib${name}_engine.a")
  set(inputs "")
  foreach(library IN LISTS ARGN)
    list(APPEND inputs "${stage}/lib/lib${library}.a")
  endforeach()
  set(exports -Wl,-exported_symbol,_${name}_main)
  if(name STREQUAL "xsystem4")
    list(APPEND exports -Wl,-exported_symbol,_alicehub_ios_prepare_window
      -Wl,-exported_symbol,_alicehub_ios_bind_drawable
      -Wl,-exported_symbol,_alicehub_ios_set_background_hook
      -Wl,-exported_symbol,_alicehub_ios_set_gamepad_callbacks
      -Wl,-exported_symbol,_alicehub_ios_gamepad_command
      -Wl,-exported_symbol,_alicehub_ios_gamepad_frame
      -Wl,-exported_symbols_list,${CMAKE_CURRENT_LIST_DIR}/engine-exports.txt)
  endif()
  add_custom_target(${name}_engine ALL
    COMMAND ${CMAKE_C_COMPILER} -target ${ffi_target} -isysroot ${sdk_path}
      -nostdlib -Wl,-r -Wl,-all_load ${inputs} ${exports} -o ${object}
    COMMAND ${CMAKE_COMMAND} -E rm -f ${archive}
    COMMAND ${CMAKE_AR} qc ${archive} ${object}
    COMMAND ${CMAKE_RANLIB} ${archive}
    BYPRODUCTS ${object} ${archive}
    DEPENDS ${name}
    VERBATIM)
endfunction()
isolate_engine(xsystem4 xsystem4 sys4 cglm)
isolate_engine(xsystem35 xsystem35 src_lib modules)
