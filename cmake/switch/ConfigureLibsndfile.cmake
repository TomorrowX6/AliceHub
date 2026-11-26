# devkitPro provides Ogg and Vorbis libraries and pkg-config files, not CMake packages,
# while libopus is staged in CMAKE_INSTALL_PREFIX.
set(CMAKE_FIND_PACKAGE_PREFER_CONFIG OFF)
if(CMAKE_INSTALL_PREFIX)
  list(PREPEND CMAKE_FIND_ROOT_PATH "${CMAKE_INSTALL_PREFIX}")
endif()
