cmake_minimum_required(VERSION 3.18)
project(AliceHubSystem3 LANGUAGES C CXX)

if(POLICY CMP0135)
  cmake_policy(SET CMP0135 NEW)
endif()

include(FetchContent)
FetchContent_Declare(system3_source
  URL https://codeload.github.com/kichikuou/system3-sdl2/tar.gz/ca0f09a19e149f1906e0dd423cf6e3eb4ff06efe
  URL_HASH SHA256=bd1944c7703ca7fe9086b0492186961e8449b97f4f1eb021cab42b3fbaf81626
  SOURCE_SUBDIR unused) # v1.8.0; build against AliceHub's SDL, not the standalone app
FetchContent_Declare(ymfm
  URL https://codeload.github.com/aaronsgiles/ymfm/tar.gz/10c72f79bea3e0ab66af32d4295519aa17e6ea0f
  URL_HASH SHA256=f361fe98f7d420e9f5cde8f78687f97215729bd53776e020013c2808ebc4e955
  SOURCE_SUBDIR unused)
FetchContent_Declare(sdl2_ttf
  URL https://github.com/libsdl-org/SDL_ttf/releases/download/release-2.24.0/SDL2_ttf-2.24.0.tar.gz
  URL_HASH SHA256=0b2bf1e7b6568adbdbc9bb924643f79d9dedafe061fa1ed687d1d9ac4e453bfd
  SOURCE_SUBDIR unused)
FetchContent_MakeAvailable(system3_source ymfm sdl2_ttf)

find_package(SDL2 CONFIG REQUIRED)
find_package(Freetype REQUIRED)

set(src "${system3_source_SOURCE_DIR}/src")
file(GLOB vm_sources "${src}/sys/*.cpp")
list(REMOVE_ITEM vm_sources "${src}/sys/mako_midi.cpp")
if(ANDROID)
  add_library(system3 SHARED)
  target_link_options(system3 PRIVATE "LINKER:-z,max-page-size=16384" "LINKER:--exclude-libs,ALL")
  target_sources(system3 PRIVATE "${src}/android/nact_android.cpp")
  target_link_libraries(system3 PRIVATE log)
elseif(ALICEHUB_IOS)
  enable_language(OBJC OBJCXX)
  add_library(system3 STATIC)
  target_compile_definitions(system3 PRIVATE SDL_MAIN_HANDLED ALICEHUB_IOS)
  set_source_files_properties("${src}/sdlmain.cpp" PROPERTIES
    COMPILE_DEFINITIONS "main=system3_entry;SDL_CreateWindow=alicehub_system3_create_window;SDL_CreateRenderer=alicehub_system3_create_renderer")
  target_sources(system3 PRIVATE nact.ios.mm window.ios.m)
  set_target_properties(system3 PROPERTIES OBJCXX_STANDARD 17 OBJCXX_STANDARD_REQUIRED ON)
  set_source_files_properties(nact.ios.mm window.ios.m PROPERTIES COMPILE_OPTIONS -fobjc-arc)
else()
  add_executable(system3 "${src}/generic/nact_generic.cpp")
endif()

target_sources(system3 PRIVATE
  platform.cpp
  "${src}/sdlmain.cpp"
  "${src}/config.cpp"
  "${src}/fileio.cpp"
  "${src}/encoding.cpp"
  "${src}/texthook.cpp"
  "${src}/fm/makofm.cpp"
  "${src}/fm/mako_ymfm.cpp"
  "${src}/generic/mako.cpp"
  "${ymfm_SOURCE_DIR}/src/ymfm_opn.cpp"
  "${ymfm_SOURCE_DIR}/src/ymfm_adpcm.cpp"
  "${ymfm_SOURCE_DIR}/src/ymfm_ssg.cpp"
  "${sdl2_ttf_SOURCE_DIR}/SDL_ttf.c"
  ${vm_sources})
target_include_directories(system3 PRIVATE "${src}" "${src}/sys"
  "${ymfm_SOURCE_DIR}/src" "${sdl2_ttf_SOURCE_DIR}"
  "${system3_source_SOURCE_DIR}/deps/dr_libs" "${system3_source_SOURCE_DIR}/deps/stb")
target_compile_features(system3 PRIVATE cxx_std_17)
target_link_libraries(system3 PRIVATE SDL2::SDL2 Freetype::Freetype)
set_target_properties(system3 PROPERTIES C_VISIBILITY_PRESET hidden CXX_VISIBILITY_PRESET hidden
  VISIBILITY_INLINES_HIDDEN ON)

install(TARGETS system3)
install(DIRECTORY "${system3_source_SOURCE_DIR}/resources/mda" DESTINATION share/system3)
install(FILES "${system3_source_SOURCE_DIR}/COPYING.txt" DESTINATION share/system3/licenses RENAME system3-sdl2)
install(FILES "${ymfm_SOURCE_DIR}/LICENSE" DESTINATION share/system3/licenses RENAME ymfm)
install(FILES "${sdl2_ttf_SOURCE_DIR}/LICENSE.txt" DESTINATION share/system3/licenses RENAME SDL2_ttf)
install(FILES "${system3_source_SOURCE_DIR}/deps/dr_libs/dr_mp3.h" DESTINATION share/system3/licenses RENAME dr_mp3)
install(FILES "${system3_source_SOURCE_DIR}/deps/stb/stb_vorbis.h" DESTINATION share/system3/licenses RENAME stb_vorbis)
