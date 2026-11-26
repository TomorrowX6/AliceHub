cmake_minimum_required(VERSION 3.10)

# Rebuild external projects when download URL changes
if (CMAKE_VERSION VERSION_GREATER_EQUAL "3.24.0")
  cmake_policy(SET CMP0135 NEW)
endif()

project(xsystem4-android)

include(ExternalProject)

set(stagingDir ${CMAKE_CURRENT_BINARY_DIR}/stage)
set(androidProjectDir ${CMAKE_CURRENT_LIST_DIR}/project)

set(COMMON_CMAKE_ARGS
  -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
  -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
  -DCMAKE_FIND_ROOT_PATH=${stagingDir}
  -DCMAKE_ANDROID_ARCH_ABI=${CMAKE_ANDROID_ARCH_ABI}
  -DANDROID_PLATFORM=${ANDROID_PLATFORM}
  -DCMAKE_STAGING_PREFIX:PATH=${stagingDir}
  -DCMAKE_INSTALL_PREFIX:PATH=${stagingDir}
  -DANDROID_USE_LEGACY_TOOLCHAIN_FILE=OFF
  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON
  -DCMAKE_POLICY_VERSION_MINIMUM=3.5
  -DCMAKE_INTERPROCEDURAL_OPTIMIZATION:BOOL=ON
  # Honor CMAKE_INTERPROCEDURAL_OPTIMIZATION in projects with an old
  # cmake_minimum_required
  -DCMAKE_POLICY_DEFAULT_CMP0069=NEW
  # https://github.com/android/ndk/issues/2032
  -DCMAKE_POLICY_DEFAULT_CMP0057=NEW
)

ExternalProject_Add(
  libogg
  URL https://github.com/xiph/ogg/releases/download/v1.3.5/libogg-1.3.5.tar.xz
  URL_HASH SHA1=5a368421a636f7faa4c2f662857cb507dffd7c99
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DCMAKE_POLICY_VERSION_MINIMUM=3.5
)

ExternalProject_Add(
  libvorbis
  URL https://github.com/xiph/vorbis/releases/download/v1.3.7/libvorbis-1.3.7.tar.xz
  URL_HASH SHA1=0a2dd71a999656b8091506839e8007a61a8fda1f
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DCMAKE_POLICY_VERSION_MINIMUM=3.5
  )

ExternalProject_Add_StepDependencies(libvorbis configure libogg)

ExternalProject_Add(
  libopus
  URL https://github.com/xiph/opus/releases/download/v1.5.2/opus-1.5.2.tar.gz
  URL_HASH SHA256=65c1d2f78b9f2fb20082c38cbe47c951ad5839345876e46941612ee87f9a7ce1
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DCMAKE_INSTALL_LIBDIR=lib
             -DBUILD_SHARED_LIBS=OFF
             -DOPUS_BUILD_SHARED_LIBRARY=OFF
             -DOPUS_BUILD_PROGRAMS=OFF
             -DOPUS_BUILD_TESTING=OFF
             -DOPUS_DISABLE_INTRINSICS=ON
             -DOPUS_INSTALL_PKG_CONFIG_MODULE=ON
             -DOPUS_INSTALL_CMAKE_CONFIG_MODULE=ON
)

ExternalProject_Add(
  libjpeg-turbo
  URL https://github.com/libjpeg-turbo/libjpeg-turbo/releases/download/3.1.0/libjpeg-turbo-3.1.0.tar.gz
  URL_HASH SHA1=ba3c0152f14a504018de19c9f62250d8f3351525
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
)

ExternalProject_Add(
  libwebp
  URL https://storage.googleapis.com/downloads.webmproject.org/releases/webp/libwebp-1.3.2.tar.gz
  URL_HASH SHA1=f941661a869df7347d7ca9208acb4e6d41f614de
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DWEBP_BUILD_ANIM_UTILS=OFF
             -DWEBP_BUILD_CWEBP=OFF
             -DWEBP_BUILD_DWEBP=OFF
             -DWEBP_BUILD_GIF2WEBP=OFF
             -DWEBP_BUILD_IMG2WEBP=OFF
             -DWEBP_BUILD_VWEBP=OFF
             -DWEBP_BUILD_WEBPINFO=OFF
             -DWEBP_BUILD_WEBPMUX=OFF
             -DWEBP_BUILD_EXTRAS=OFF
             -DWEBP_BUILD_WEBP_JS=OFF
)

ExternalProject_Add(
  libpng
  URL https://download.sourceforge.net/libpng/libpng-1.6.47.tar.xz
  URL_HASH SHA256=b213cb381fbb1175327bd708a77aab708a05adde7b471bc267bd15ac99893631
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DPNG_SHARED=OFF
             -DPNG_ARM_NEON=on
)

set(TARGET_CC "${CMAKE_ANDROID_NDK_TOOLCHAIN_UNIFIED}/bin/clang -target ${CMAKE_C_COMPILER_TARGET}")

ExternalProject_Add(
  libffi
  URL https://github.com/libffi/libffi/releases/download/v3.4.6/libffi-3.4.6.tar.gz
  URL_HASH SHA1=19251dfee520dff42acefe36bfe76d7168071e01
  CONFIGURE_COMMAND <SOURCE_DIR>/configure
    --host ${CMAKE_C_COMPILER_TARGET}
    --prefix=${stagingDir}
    --with-pic
    --disable-exec-static-tramp
    --disable-shared
    --disable-dependency-tracking
    --disable-builddir
    --disable-multi-os-directory
    --disable-structs
    --disable-raw-api
    --disable-docs
    AR=${CMAKE_AR}
    CC=${TARGET_CC}
    AS=${TARGET_CC}
    LD=${CMAKE_ANDROID_NDK_TOOLCHAIN_UNIFIED}/bin/ld
    RANLIB=${CMAKE_RANLIB}
    STRIP=${CMAKE_STRIP}
)

# This fork disables FLAC; AliceHub re-enables Ogg Opus via patch-libsndfile-opus.cmake.
ExternalProject_Add(
  libsndfile
  URL https://codeload.github.com/kichikuou/libsndfile/tar.gz/be82f4be73c77f1e759bda9dfe1625204441465a
  URL_HASH SHA256=926d146673bb6a9a9c1b600289e911e0f08cdc90bb9c6e54aef3a33c9e9bccb5
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DENABLE_EXTERNAL_LIBS=ON
             -DENABLE_MPEG=OFF
             -DBUILD_SHARED_LIBS=OFF
             -DBUILD_PROGRAMS=OFF
             -DBUILD_EXAMPLES=OFF
             -DBUILD_TESTING=OFF
             -DENABLE_CPACK=OFF
)

ExternalProject_Add_Step(
  libsndfile alicehub_opus_patch
  COMMAND ${CMAKE_COMMAND} -DSOURCE_DIR=<SOURCE_DIR>
    -P ${CMAKE_CURRENT_LIST_DIR}/cmake/patch-libsndfile-opus.cmake
  DEPENDEES patch
  DEPENDERS configure
  DEPENDS ${CMAKE_CURRENT_LIST_DIR}/cmake/patch-libsndfile-opus.cmake
)

ExternalProject_Add(
  libdeflate
  URL https://github.com/ebiggers/libdeflate/releases/download/v1.25/libdeflate-1.25.tar.gz
  URL_HASH SHA1=7e70557d094b54c3ee16f6c012976dc8d685b22a
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DCMAKE_POLICY_VERSION_MINIMUM=3.5
             -DLIBDEFLATE_BUILD_SHARED_LIB=OFF
             -DLIBDEFLATE_GZIP_SUPPORT=OFF
             -DLIBDEFLATE_BUILD_GZIP=OFF
)

ExternalProject_Add_StepDependencies(libsndfile configure libvorbis libopus)

ExternalProject_Add(
  xz
  URL https://github.com/tukaani-project/xz/releases/download/v5.8.4/xz-5.8.4.tar.xz
  URL_HASH SHA256=4ce24038fd4221e0d13bc1a2de7a4db56e90b92b3bf75321f6c14be73f65de4b
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DCMAKE_POSITION_INDEPENDENT_CODE=ON
             -DBUILD_SHARED_LIBS=OFF
             -DXZ_TOOL_XZ=OFF
             -DXZ_TOOL_XZDEC=OFF
             -DXZ_TOOL_LZMADEC=OFF
             -DXZ_TOOL_LZMAINFO=OFF
             -DXZ_TOOL_SCRIPTS=OFF
             -DXZ_DOC=OFF
             -DXZ_NLS=OFF
)

ExternalProject_Add(
  libarchive
  URL https://github.com/libarchive/libarchive/releases/download/v3.8.11/libarchive-3.8.11.tar.xz
  URL_HASH SHA256=a3dc0872b1b39da34dc18ef1b944f0f11ffd37425d70ae04be679279f1cf43d2
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DCMAKE_POSITION_INDEPENDENT_CODE=ON
             -DBUILD_SHARED_LIBS=OFF
             -DENABLE_TAR=OFF
             -DENABLE_CPIO=OFF
             -DENABLE_CAT=OFF
             -DENABLE_UNZIP=OFF
             -DENABLE_TEST=OFF
             -DENABLE_OPENSSL=OFF
             -DENABLE_NETTLE=OFF
             -DENABLE_MBEDTLS=OFF
             -DENABLE_LIBB2=OFF
             -DENABLE_LZ4=OFF
             -DENABLE_LZO=OFF
             -DENABLE_ZSTD=OFF
             -DENABLE_BZip2=OFF
             -DENABLE_LIBXML2=OFF
             -DENABLE_EXPAT=OFF
             -DENABLE_PCREPOSIX=OFF
             -DENABLE_PCRE2POSIX=OFF
             -DENABLE_ICONV=OFF
             -DENABLE_ACL=OFF
             -DENABLE_XATTR=OFF
             -DENABLE_LZMA=ON
             -DENABLE_ZLIB=ON
)

ExternalProject_Add_StepDependencies(libarchive configure xz)

ExternalProject_Add(
  SDL2
  URL https://github.com/libsdl-org/SDL/releases/download/release-2.30.9/SDL2-2.30.9.tar.gz
  URL_HASH SHA1=9403df0573d47f62f2de074b582b87576bb4abbc
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DSDL_SHARED=ON
             -DSDL_STATIC=OFF
             -DSDL_TEST=OFF
             -DSDL_TESTS=OFF
             "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384"
)

ExternalProject_Add(
  SDL2_mixer
  URL https://github.com/libsdl-org/SDL_mixer/releases/download/release-2.8.1/SDL2_mixer-2.8.1.tar.gz
  URL_HASH SHA1=b38a337de10b04c51053ca0d17b57c31f5dcdd96
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DBUILD_SHARED_LIBS=OFF
             -DSDL2MIXER_VENDORED=OFF
             -DSDL2MIXER_DEPS_SHARED=OFF
             -DSDL2MIXER_INSTALL=ON
             -DSDL2MIXER_SAMPLES=OFF
             -DSDL2MIXER_FLAC=OFF
             -DSDL2MIXER_GME=OFF
             -DSDL2MIXER_MOD=OFF
             -DSDL2MIXER_MIDI=OFF
             -DSDL2MIXER_OPUS=OFF
             -DSDL2MIXER_WAVPACK=OFF
             -DSDL2MIXER_MP3=ON
             -DSDL2MIXER_MP3_MINIMP3=ON
             -DSDL2MIXER_VORBIS=VORBISFILE
             -DSDL2MIXER_VORBIS_VORBISFILE_SHARED=OFF
)

ExternalProject_Add_StepDependencies(SDL2_mixer configure SDL2 libvorbis libogg)

ExternalProject_Add(
  archive_jni
  SOURCE_DIR ${CMAKE_CURRENT_LIST_DIR}/archive_jni
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
  BUILD_ALWAYS YES
)

ExternalProject_Add_StepDependencies(archive_jni configure libarchive)

ExternalProject_Add(
  xsystem4
  SOURCE_DIR ${CMAKE_CURRENT_LIST_DIR}/xsystem4
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DCMAKE_MODULE_PATH=${CMAKE_CURRENT_LIST_DIR}/cmake
  BUILD_ALWAYS YES
)

ExternalProject_Add_StepDependencies(xsystem4 configure
  SDL2 libjpeg-turbo libwebp libpng libffi libsndfile libdeflate)

ExternalProject_Add(
  xsystem35
  SOURCE_DIR ${CMAKE_CURRENT_LIST_DIR}/xsystem35
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
             -DXSYSTEM35_SDL_VERSION=2
             -DENABLE_DEBUGGER=OFF
             -DCMAKE_MODULE_PATH=${CMAKE_CURRENT_LIST_DIR}/cmake
  BUILD_ALWAYS YES
)

ExternalProject_Add_StepDependencies(xsystem35 configure
  SDL2 SDL2_mixer libwebp libogg libvorbis xsystem4)

ExternalProject_Add(system3
  SOURCE_DIR ${CMAKE_CURRENT_LIST_DIR}/cmake/system3
  CMAKE_ARGS ${COMMON_CMAKE_ARGS}
    -DANDROID_STL=c++_static
    -DCMAKE_MODULE_PATH=${CMAKE_CURRENT_LIST_DIR}/cmake
  BUILD_ALWAYS YES)
ExternalProject_Add_StepDependencies(system3 configure SDL2 xsystem4)

install(
  FILES ${stagingDir}/lib/libxsystem4.so
        ${stagingDir}/lib/libxsystem35.so
        ${stagingDir}/lib/libsystem3.so
        ${stagingDir}/lib/libSDL2.so
        ${stagingDir}/lib/libcglm.so
        ${stagingDir}/lib/libarchive_jni.so
  DESTINATION ${androidProjectDir}/app/src/main/jniLibs/${CMAKE_ANDROID_ARCH_ABI})

install(
  DIRECTORY xsystem4/shaders xsystem4/fonts
  DESTINATION ${androidProjectDir}/app/src/main/assets)

install(
  FILES xsystem35/fonts/MTLc3m.ttf xsystem35/fonts/mincho.ttf
  DESTINATION ${androidProjectDir}/app/src/main/assets/fonts)

install(DIRECTORY ${stagingDir}/share/system3/mda
  DESTINATION ${androidProjectDir}/app/src/main/assets)
