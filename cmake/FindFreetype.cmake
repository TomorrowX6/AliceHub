if (NOT TARGET Freetype::Freetype)
  find_package(freetype CONFIG QUIET PATHS ${CMAKE_FIND_ROOT_PATH}/lib/cmake/freetype NO_DEFAULT_PATH)
endif()
if (NOT TARGET Freetype::Freetype)
  include(FetchContent)
  FetchContent_Declare(
    freetype
    GIT_REPOSITORY https://github.com/freetype/freetype.git
    GIT_TAG VER-2-13-3
  )
  FetchContent_MakeAvailable(freetype)
  add_library(Freetype::Freetype ALIAS freetype)
  install(TARGETS freetype)
endif()
