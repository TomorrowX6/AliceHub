cmake_minimum_required(VERSION 3.10)

# Enable Ogg Opus support alongside Ogg Vorbis in kichikuou/libsndfile while keeping FLAC disabled.
function(replace_exact file_path old_str new_str)
  file(READ "${file_path}" content)
  string(FIND "${content}" "${new_str}" already_pos)
  if(NOT already_pos EQUAL -1)
    return()
  endif()
  string(FIND "${content}" "${old_str}" old_pos)
  if(old_pos EQUAL -1)
    message(FATAL_ERROR "Expected pattern not found in ${file_path}:\n${old_str}")
  endif()
  string(REPLACE "${old_str}" "${new_str}" content "${content}")
  file(WRITE "${file_path}" "${content}")
endfunction()

set(cmakelists "${SOURCE_DIR}/CMakeLists.txt")
replace_exact("${cmakelists}"
  [[if (ENABLE_EXTERNAL_LIBS AND NOT (Ogg_FOUND AND Vorbis_FOUND))]]
  [[if (ENABLE_EXTERNAL_LIBS AND NOT (Ogg_FOUND AND Vorbis_FOUND AND Opus_FOUND))]])
replace_exact("${cmakelists}"
  [[set (HAVE_OPUS 0)]]
  [[set (HAVE_OPUS ${ENABLE_EXTERNAL_LIBS})]])
replace_exact("${cmakelists}"
  [[set (EXTERNAL_XIPH_REQUIRE "ogg vorbis vorbisenc")]]
  [[set (EXTERNAL_XIPH_REQUIRE "ogg vorbis vorbisenc opus")]])
replace_exact("${cmakelists}"
  [[$<$<BOOL:${HAVE_EXTERNAL_XIPH_LIBS}>:Vorbis::vorbisenc>]]
  "$<$<BOOL:\${HAVE_EXTERNAL_XIPH_LIBS}>:Vorbis::vorbisenc>\n\t\t$<$<BOOL:\${HAVE_OPUS}>:Opus::opus>")
replace_exact("${cmakelists}"
  [[set (SndFile_WITH_EXTERNAL_LIBS 1)]]
  "set (SndFile_WITH_EXTERNAL_LIBS 1)\n\t\tset (SndFile_WITH_OPUS 1)")
replace_exact("${cmakelists}"
  [[set (SndFile_WITH_EXTERNAL_LIBS 0)]]
  "set (SndFile_WITH_EXTERNAL_LIBS 0)\n\t\tset (SndFile_WITH_OPUS 0)")
replace_exact("${cmakelists}"
  [[${CMAKE_CURRENT_LIST_DIR}/cmake/FindVorbis.cmake)]]
  "\${CMAKE_CURRENT_LIST_DIR}/cmake/FindOpus.cmake\n\t\t\t\${CMAKE_CURRENT_LIST_DIR}/cmake/FindVorbis.cmake)")

set(checks "${SOURCE_DIR}/cmake/SndFileChecks.cmake")
replace_exact("${checks}"
  "\tfind_package (Vorbis CONFIG COMPONENTS Enc)\n\n\tinclude (FindPackageHandleStandardArgs)\n\tfind_package_handle_standard_args (Ogg CONFIG_MODE)\n\tfind_package_handle_standard_args (Vorbis CONFIG_MODE)"
  "\tfind_package (Vorbis CONFIG COMPONENTS Enc)\n\tfind_package (Opus CONFIG)\n\n\tinclude (FindPackageHandleStandardArgs)\n\tfind_package_handle_standard_args (Ogg CONFIG_MODE)\n\tfind_package_handle_standard_args (Vorbis CONFIG_MODE)\n\tfind_package_handle_standard_args (Opus CONFIG_MODE)")
replace_exact("${checks}"
  "\tfind_package (Vorbis COMPONENTS Enc)\nendif ()\nif (Ogg_FOUND AND Vorbis_FOUND)"
  "\tfind_package (Vorbis COMPONENTS Enc)\n\tfind_package (Opus)\nendif ()\nif (Ogg_FOUND AND Vorbis_FOUND AND Opus_FOUND)")

set(cfg_in "${SOURCE_DIR}/cmake/SndFileConfig.cmake.in")
replace_exact("${cfg_in}"
  [[set (SndFile_WITH_EXTERNAL_LIBS @SndFile_WITH_EXTERNAL_LIBS@)]]
  "set (SndFile_WITH_EXTERNAL_LIBS @SndFile_WITH_EXTERNAL_LIBS@)\nset (SndFile_WITH_OPUS @SndFile_WITH_OPUS@)")
replace_exact("${cfg_in}"
  "\tfind_dependency (Vorbis)\nendif ()"
  "\tfind_dependency (Vorbis)\n\tfind_dependency (Opus)\nendif ()")
