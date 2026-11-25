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
list(REMOVE_ITEM vm_sources "${src}/sys/mako_midi.cpp" "${src}/sys/nact_sys3.cpp")

set(orig_nact_sys3 "${src}/sys/nact_sys3.cpp")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${orig_nact_sys3}")
file(READ "${orig_nact_sys3}" nact_sys3_source)
set(sys3_cmd_m_orig [=[	} else if(tvar_index == 31) {
		sco.open(string);
	} else if(tvar_index == 32) {
		ags->set_cg_file(string);
	} else if(tvar_index == 33) {
		mako->amus.open(string);
		strcpy(string + strlen(string) - 3, "MDA");
		mako->mda.open(string);]=])
set(sys3_cmd_m_repl [=[	} else if(tvar_index == 31) {
		sco.open(encoding->toUtf8(string).c_str());
	} else if(tvar_index == 32) {
		ags->set_cg_file(encoding->toUtf8(string).c_str());
	} else if(tvar_index == 33) {
		std::string utf8_name = encoding->toUtf8(string);
		mako->amus.open(utf8_name.c_str());
		if (utf8_name.size() >= 3) {
			utf8_name.replace(utf8_name.size() - 3, 3, "MDA");
		}
		mako->mda.open(utf8_name.c_str());]=])
string(REPLACE "${sys3_cmd_m_orig}" "${sys3_cmd_m_repl}" patched_nact_sys3 "${nact_sys3_source}")
if(patched_nact_sys3 STREQUAL nact_sys3_source)
  message(FATAL_ERROR "Could not apply System 3 UTF-8 cmd_m filename patch")
endif()
set(sys3_y254_orig [=[		case 254:
			RND = (game_id.is(GameId::YAKATA3_CD) || game_id.is(GameId::YAKATA3_FD) || game_id.is(GameId::NINGYO)) ? 1 : 0;
			break;]=])
set(sys3_y254_repl [=[		case 254:
			RND = (game_id.is(GameId::YAKATA3_CD) || game_id.is(GameId::YAKATA3_FD) || game_id.is(GameId::NINGYO) || ((game_id.is(GameId::PROG_CD) || game_id.is(GameId::MUGEN)) && mako->is_cd_available())) ? 1 : 0;
			break;]=])
string(REPLACE "${sys3_y254_orig}" "${sys3_y254_repl}" patched_nact_sys3_2 "${patched_nact_sys3}")
if(patched_nact_sys3_2 STREQUAL patched_nact_sys3)
  message(FATAL_ERROR "Could not apply System 3 Prostudent G CD-DA Y254 patch")
endif()
file(CONFIGURE OUTPUT nact_sys3.cpp CONTENT "${patched_nact_sys3_2}" @ONLY)
list(APPEND vm_sources "${CMAKE_CURRENT_BINARY_DIR}/nact_sys3.cpp")
if(ANDROID)
  add_library(system3 SHARED)
  target_link_options(system3 PRIVATE "LINKER:-z,max-page-size=16384" "LINKER:--exclude-libs,ALL")
  set(android_nact "${src}/android/nact_android.cpp")
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${android_nact}")
  file(READ "${android_nact}" android_nact_source)
  set(original [=[bool NACT::handle_platform_event(const SDL_Event& e)
{
	return false;
}]=])
  set(replacement [=[#include "ags.h"

bool NACT::handle_platform_event(const SDL_Event& e)
{
	if (e.type == SDL_APP_DIDENTERFOREGROUND || e.type == SDL_RENDER_DEVICE_RESET) {
		ags->invalidate_screen(0, 0, ags->screen_width, ags->screen_height);
		return true;
	}
	return false;
}]=])
  string(REPLACE "${original}" "${replacement}" patched_android_nact "${android_nact_source}")
  if(patched_android_nact STREQUAL android_nact_source)
    message(FATAL_ERROR "Could not apply System 3 Android redraw patch")
  endif()
  file(CONFIGURE OUTPUT nact_android.cpp CONTENT "${patched_android_nact}" @ONLY)
  target_include_directories(system3 PRIVATE "${src}/android")
  target_sources(system3 PRIVATE "${CMAKE_CURRENT_BINARY_DIR}/nact_android.cpp")
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
elseif(NINTENDO_SWITCH)
  add_executable(system3 "${src}/generic/nact_generic.cpp")
  set(nact_input "${src}/sys/nact_input.cpp")
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${nact_input}")
  file(READ "${nact_input}" nact_input_source)
  set(quit_dialog "case SDL_QUIT:\n\t\tshow_quit_dialog();")
  string(FIND "${nact_input_source}" "${quit_dialog}" quit_offset)
  if(quit_offset EQUAL -1)
    message(FATAL_ERROR "Could not apply System 3 Switch quit patch")
  endif()
  string(REPLACE "${quit_dialog}" "case SDL_QUIT:\n\t\tquit(0);" switch_input "${nact_input_source}")
  foreach(button A B)
    set(call "SDL_GameControllerGetButton(sdl_gamecontroller, SDL_CONTROLLER_BUTTON_${button})")
    string(FIND "${switch_input}" "${call}" button_offset)
    if(button_offset EQUAL -1)
      message(FATAL_ERROR "Could not apply System 3 Switch button ${button} patch")
    endif()
    string(REPLACE "${call}"
      "SDL_GameControllerGetButton(sdl_gamecontroller, alicehub_switch_face_button(SDL_JoystickInstanceID(SDL_GameControllerGetJoystick(sdl_gamecontroller)), SDL_CONTROLLER_BUTTON_${button}))"
      switch_input "${switch_input}")
  endforeach()
  set(input_pump "void NACT::pump_events()\n{\n")
  string(FIND "${switch_input}" "${input_pump}" pump_offset)
  if(pump_offset EQUAL -1)
    message(FATAL_ERROR "Could not apply System 3 Switch runtime menu patch")
  endif()
  string(REPLACE "${input_pump}"
    "${input_pump}\tif (switch_menu_system3_poll(ags, msgskip)) {\n\t\ttouch_state = TOUCH_NONE;\n\t\twheel = 0;\n\t}\n"
    switch_input "${switch_input}")
  file(CONFIGURE OUTPUT nact_input.cpp CONTENT
    "#include \"controller.h\"\nclass AGS;\nclass MsgSkip;\nbool switch_menu_system3_poll(AGS *, MsgSkip *);\n${switch_input}" @ONLY)
  list(REMOVE_ITEM vm_sources "${nact_input}")
  list(APPEND vm_sources "${CMAKE_CURRENT_BINARY_DIR}/nact_input.cpp")
  set(sdlmain "${src}/sdlmain.cpp")
  set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${sdlmain}")
  file(READ "${sdlmain}" switch_main)
  set(startup_begin "\tg_window = create_window(config, game_id);")
  set(startup_end "\n\tg_nact.reset(NACT::create(config, game_id));\n")
  set(fatal_dialog "\tSDL_ShowSimpleMessageBox(SDL_MESSAGEBOX_ERROR, \"system3\", buf, g_window);")
  foreach(fragment startup_begin startup_end fatal_dialog)
    string(FIND "${switch_main}" "${${fragment}}" offset)
    if(offset EQUAL -1)
      message(FATAL_ERROR "Could not apply System 3 Switch startup patch: ${fragment}")
    endif()
  endforeach()
  string(REPLACE "${startup_begin}" "\tswitch_starting = true;\n\ttry {\n${startup_begin}" switch_main "${switch_main}")
  string(REPLACE "${startup_end}" "${startup_end}\n\t} catch (const SwitchStartupError&) {\n\t\tswitch_starting = false;\n\t\tSDL_DestroyRenderer(g_renderer);\n\t\tSDL_DestroyWindow(g_window);\n\t\tSDL_Quit();\n\t\tg_renderer = nullptr;\n\t\tg_window = nullptr;\n\t\tromfsExit();\n\t\treturn 1;\n\t}\n\tswitch_starting = false;" switch_main "${switch_main}")
  string(REPLACE "${fatal_dialog}" "\tif (switch_starting) {\n\t\talicehub_switch_set_launch_error(buf);\n\t\tthrow SwitchStartupError{};\n\t}\n${fatal_dialog}" switch_main "${switch_main}")
  set(sdlmain_source "${CMAKE_CURRENT_BINARY_DIR}/sdlmain.cpp")
  file(CONFIGURE OUTPUT "${sdlmain_source}" CONTENT "#include \"launcher.h\"\nnamespace { struct SwitchStartupError {}; bool switch_starting; }\n${switch_main}" @ONLY)
  set_source_files_properties("${sdlmain_source}" PROPERTIES
    COMPILE_DEFINITIONS "SDL_MAIN_HANDLED;main=system3_switch_entry")
  include(../switch/Packaging.cmake)
  set(romfs "${CMAKE_CURRENT_BINARY_DIR}/romfs")
  file(COPY "${system3_source_SOURCE_DIR}/resources/mda"
            "${system3_source_SOURCE_DIR}/resources/fonts" DESTINATION "${romfs}")
  alicehub_switch_package(system3 "${romfs}")
  target_sources(system3 PRIVATE ../switch/hub/runtime_menu_system3.cpp)
else()
  add_executable(system3 "${src}/generic/nact_generic.cpp")
endif()

if(NOT DEFINED sdlmain_source)
  set(sdlmain_source "${src}/sdlmain.cpp")
endif()
target_sources(system3 PRIVATE
  platform.cpp
  "${sdlmain_source}"
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
