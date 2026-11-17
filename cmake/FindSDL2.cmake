if (NOT TARGET SDL2::SDL2)
  find_package(SDL2 CONFIG QUIET PATHS ${CMAKE_FIND_ROOT_PATH}/lib/cmake/SDL2 NO_DEFAULT_PATH)
endif()
if (NOT TARGET SDL2::SDL2)
  include(FetchContent)
  FetchContent_Declare(
    SDL
    URL https://github.com/libsdl-org/SDL/releases/download/release-2.30.9/SDL2-2.30.9.tar.gz
    URL_HASH SHA1=9403df0573d47f62f2de074b582b87576bb4abbc
  )
  FetchContent_MakeAvailable(SDL)
  install(TARGETS SDL2)
endif()
