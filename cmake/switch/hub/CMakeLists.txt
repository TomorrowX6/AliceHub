find_package(Freetype REQUIRED)
find_package(PNG REQUIRED)
find_package(JPEG REQUIRED)
find_package(ZLIB REQUIRED)

add_executable(AliceHub app.c features.c install_transaction.c library.c details.c settings.c model.c progress.c ../picker_art.c)
set(xsystem4_dir "${CMAKE_CURRENT_LIST_DIR}/../../../xsystem4")
set(libsys4_dir "${xsystem4_dir}/subprojects/libsys4")
set(modifier_src "${xsystem4_dir}/src/modifier.c")
set_source_files_properties("${modifier_src}" PROPERTIES COMPILE_DEFINITIONS MODIFIER_OFFLINE_ONLY)
target_sources(AliceHub PRIVATE save_editor.c save_storage.c "${modifier_src}"
  "${xsystem4_dir}/src/cJSON.c")
foreach(source savefile buffer file mt19937int string system utfsjis zlib)
  target_sources(AliceHub PRIVATE "${libsys4_dir}/src/${source}.c")
endforeach()
target_include_directories(AliceHub PRIVATE "${xsystem4_dir}/include" "${libsys4_dir}/include")
target_link_libraries(AliceHub PRIVATE Freetype::Freetype PNG::PNG JPEG::JPEG ZLIB::ZLIB)
target_compile_definitions(AliceHub PRIVATE ALICEHUB_SWITCH_HUB)

set(romfs "${CMAKE_CURRENT_BINARY_DIR}/romfs")
set(fonts "${CMAKE_CURRENT_LIST_DIR}/../../../xsystem4/fonts")
set(cjk "${fonts}/HanaMinA.ttf")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${cjk}")
file(MAKE_DIRECTORY "${romfs}/fonts")
file(SIZE "${cjk}" font_size)
file(ARCHIVE_CREATE OUTPUT "${romfs}/fonts/HanaMinA.ttf.gz" PATHS "${cjk}" FORMAT raw COMPRESSION GZip)
target_compile_definitions(AliceHub PRIVATE ALICEHUB_PICKER_COMPRESSED_FONT_SIZE=${font_size})
foreach(asset VL-Gothic-Regular.ttf VL-Gothic-Regular.license HanaMinA.license)
  configure_file("${fonts}/${asset}" "${romfs}/fonts/${asset}" COPYONLY)
endforeach()
configure_file("${CMAKE_CURRENT_LIST_DIR}/../../../project/app/src/main/res/mipmap-xxxhdpi/ic_launcher_round.png"
  "${romfs}/alicehub.png" COPYONLY)
set(unlock_zip "${CMAKE_CURRENT_LIST_DIR}/../../../project/app/src/main/res/raw/full_unlock_saves.zip")
set_property(DIRECTORY APPEND PROPERTY CMAKE_CONFIGURE_DEPENDS "${unlock_zip}")
file(ARCHIVE_EXTRACT INPUT "${unlock_zip}" DESTINATION "${romfs}/save-unlock")
dkp_add_asset_target(AliceHub_romfs "${romfs}")
dkp_track_assets(AliceHub_romfs FILES alicehub.png fonts/VL-Gothic-Regular.ttf
  fonts/VL-Gothic-Regular.license fonts/HanaMinA.ttf.gz fonts/HanaMinA.license
  save-unlock/GlobalSave.asd save-unlock/Collection.asd save-unlock/AFCGMode.asd save-unlock/AFBGMMode.asd)
nx_generate_nacp(AliceHub.nacp NAME "AliceHub" AUTHOR "AliceHub" VERSION "1.0.0")
nx_create_nro(AliceHub NACP AliceHub.nacp ROMFS AliceHub_romfs)
add_custom_target(AliceHub_artifact ALL
  COMMAND ${CMAKE_COMMAND} -E make_directory "${artifacts}"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_BINARY_DIR}/AliceHub.nro" "${artifacts}/AliceHub.nro"
  DEPENDS AliceHub_nro)
