cmake_minimum_required(VERSION 3.18)
project(AliceHubSwitch LANGUAGES C CXX)

if(NOT NINTENDO_SWITCH)
  message(FATAL_ERROR "Configure with devkitPro's aarch64-none-elf-cmake")
endif()

include(ExternalProject)
find_program(MAKE_EXECUTABLE make REQUIRED)

set(stage "${CMAKE_CURRENT_BINARY_DIR}/stage")
set(artifacts "${CMAKE_CURRENT_BINARY_DIR}/artifacts")
add_subdirectory(hub)
set(repo "${CMAKE_CURRENT_LIST_DIR}/../..")
set(common
  -DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}
  -DCMAKE_BUILD_TYPE=${CMAKE_BUILD_TYPE}
  -DPKG_CONFIG_EXECUTABLE=$ENV{DEVKITPRO}/portlibs/switch/bin/aarch64-none-elf-pkg-config)

ExternalProject_Add(libffi
  URL https://github.com/libffi/libffi/releases/download/v3.4.6/libffi-3.4.6.tar.gz
  URL_HASH SHA1=19251dfee520dff42acefe36bfe76d7168071e01
  PATCH_COMMAND ${CMAKE_COMMAND} -DSOURCE_DIR=<SOURCE_DIR>
    -P "${CMAKE_CURRENT_LIST_DIR}/ConfigureLibffi.cmake"
  CONFIGURE_COMMAND <SOURCE_DIR>/configure
    --host=aarch64-none-elf --prefix=${stage}
    --disable-shared --disable-dependency-tracking --disable-multi-os-directory
    --disable-exec-static-tramp --disable-docs
    CC=${CMAKE_C_COMPILER} AR=${CMAKE_AR} RANLIB=${CMAKE_RANLIB}
    "CFLAGS=${CMAKE_C_FLAGS} -O2 -fPIC" "CCASFLAGS=${CMAKE_C_FLAGS} -fPIC"
  BUILD_COMMAND ${MAKE_EXECUTABLE}
  INSTALL_COMMAND ${MAKE_EXECUTABLE} install)

ExternalProject_Add(libsndfile
  URL https://codeload.github.com/kichikuou/libsndfile/tar.gz/be82f4be73c77f1e759bda9dfe1625204441465a
  URL_HASH SHA256=926d146673bb6a9a9c1b600289e911e0f08cdc90bb9c6e54aef3a33c9e9bccb5
  CMAKE_ARGS ${common} -DCMAKE_INSTALL_PREFIX=${stage}
    -DCMAKE_PROJECT_libsndfile_INCLUDE=${CMAKE_CURRENT_LIST_DIR}/ConfigureLibsndfile.cmake
    -DENABLE_EXTERNAL_LIBS=ON
    -DBUILD_SHARED_LIBS=OFF -DBUILD_PROGRAMS=OFF -DBUILD_EXAMPLES=OFF
    -DBUILD_TESTING=OFF -DENABLE_CPACK=OFF -DENABLE_MPEG=OFF
    -DINSTALL_MANPAGES=OFF -DINSTALL_PKGCONFIG_MODULE=OFF)

function(add_engine name source artifact)
  ExternalProject_Add(${name}
    SOURCE_DIR "${repo}/${source}"
    CMAKE_ARGS ${common} ${ARGN}
    BUILD_COMMAND ${CMAKE_COMMAND} --build <BINARY_DIR> --target ${name}_nro
    BUILD_ALWAYS ON
    INSTALL_COMMAND ${CMAKE_COMMAND} -E make_directory "${artifacts}"
      COMMAND ${CMAKE_COMMAND} -E copy_if_different "<BINARY_DIR>/${artifact}" "${artifacts}/${name}.nro")
endfunction()

add_engine(system3 cmake/system3 system3.nro)
add_engine(xsystem35 xsystem35 src/xsystem35.nro -DXSYSTEM35_SDL_VERSION=2 -DENABLE_DEBUGGER=OFF)
add_engine(xsystem4 xsystem4 xsystem4.nro
  -DCMAKE_STAGING_PREFIX=${stage} -DSndFile_DIR=${stage}/lib/cmake/SndFile)
add_dependencies(xsystem4 libffi libsndfile)
