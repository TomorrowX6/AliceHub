find_package(Freetype REQUIRED)
find_package(PNG REQUIRED)
find_package(JPEG REQUIRED)
find_package(LibXml2 CONFIG REQUIRED)
find_package(ZLIB REQUIRED)
find_package(Python3 REQUIRED COMPONENTS Interpreter)
find_package(LibArchive REQUIRED)

add_executable(AliceHub app.c features.c install_transaction.c library.c details.c settings.c model.c progress.c
  support_files.c support_storage.c support_text.c support_ui.c ../picker_art.c)
set(xsystem4_dir "${CMAKE_CURRENT_LIST_DIR}/../../../xsystem4")
set(libsys4_dir "${xsystem4_dir}/subprojects/libsys4")
set(modifier_src "${xsystem4_dir}/src/modifier.c")
set_source_files_properties("${modifier_src}" PROPERTIES COMPILE_DEFINITIONS MODIFIER_OFFLINE_ONLY)
target_sources(AliceHub PRIVATE save_editor.c save_presets.c save_storage.c "${modifier_src}"
  "${xsystem4_dir}/src/cJSON.c")
foreach(source savefile buffer file mt19937int string system utfsjis zlib)
  target_sources(AliceHub PRIVATE "${libsys4_dir}/src/${source}.c")
endforeach()
target_sources(AliceHub PRIVATE controller_editor.c controller_profile.c)
target_include_directories(AliceHub PRIVATE "${xsystem4_dir}/include" "${libsys4_dir}/include" ${LIBXML2_INCLUDE_DIRS})
target_sources(AliceHub PRIVATE instance_actions.c instances.c)
add_library(AliceHub_save_codec STATIC save_codec.c "${xsystem4_dir}/src/savedata.c")
target_include_directories(AliceHub_save_codec PRIVATE "${xsystem4_dir}/include" "${libsys4_dir}/include")
target_compile_options(AliceHub_save_codec PRIVATE -ffunction-sections -fdata-sections)
target_compile_definitions(AliceHub_save_codec PRIVATE
  atomic_save_open=hub_codec_atomic_save_open
  atomic_save_commit=hub_codec_atomic_save_commit
  atomic_save_abort=hub_codec_atomic_save_abort)
target_link_libraries(AliceHub_save_codec PRIVATE ZLIB::ZLIB)
target_sources(AliceHub PRIVATE save_manager.c save_store.c)
target_link_options(AliceHub PRIVATE -Wl,--gc-sections)
target_link_libraries(AliceHub PRIVATE AliceHub_save_codec)
target_link_libraries(AliceHub PRIVATE Freetype::Freetype PNG::PNG JPEG::JPEG ${LIBXML2_LIBRARIES} ZLIB::ZLIB)
target_compile_definitions(AliceHub PRIVATE ALICEHUB_SWITCH_HUB)
target_sources(AliceHub PRIVATE import_screen.c local_import.c import_zip.c import_iso.c)

set(online_root "${CMAKE_CURRENT_LIST_DIR}/../../..")
set(online_android "${online_root}/project/app/src/main")
set(online_kotlin "${online_android}/java/io/github/kichikuou/xsystem4")
set(online_catalog "${CMAKE_CURRENT_BINARY_DIR}/online_catalog.inc")
add_custom_command(OUTPUT "${online_catalog}"
  COMMAND Python3::Interpreter "${CMAKE_CURRENT_LIST_DIR}/generate_online_catalog.py"
    "${online_root}" "${online_catalog}"
  DEPENDS generate_online_catalog.py "${online_kotlin}/GameCatalog.kt"
    "${online_kotlin}/OnlineInstaller.kt" "${online_android}/res/values/strings.xml"
  VERBATIM)
target_sources(AliceHub PRIVATE online.c online_download.c online_install.c online_manifest.c
  "${online_catalog}")
target_include_directories(AliceHub PRIVATE "${CMAKE_CURRENT_BINARY_DIR}")
target_link_libraries(AliceHub PRIVATE curl LibArchive::LibArchive bz2 lzma lz4 zstd expat ZLIB::ZLIB)

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
set(support_repo "${CMAKE_CURRENT_LIST_DIR}/../../..")
configure_file("${support_repo}/xsystem4/COPYING" "${romfs}/support/xsystem4.txt" COPYONLY)
configure_file("${support_repo}/xsystem4/subprojects/libsys4/COPYING" "${romfs}/support/libsys4.txt" COPYONLY)
configure_file("${support_repo}/xsystem35/COPYING" "${romfs}/support/xsystem35.txt" COPYONLY)
foreach(asset MTLc3m.txt mincho.txt nanojpeg.txt microui.txt)
  configure_file("${support_repo}/xsystem35/licenses/${asset}" "${romfs}/support/${asset}" COPYONLY)
endforeach()
configure_file("${LIBXML2_INSTALL_PREFIX}/share/doc/libxml2-${LIBXML2_VERSION_STRING}/Copyright"
  "${romfs}/support/libxml2.txt" COPYONLY)
dkp_add_asset_target(AliceHub_romfs "${romfs}")
dkp_track_assets(AliceHub_romfs FILES alicehub.png fonts/VL-Gothic-Regular.ttf
  fonts/VL-Gothic-Regular.license fonts/HanaMinA.ttf.gz fonts/HanaMinA.license
  save-unlock/GlobalSave.asd save-unlock/Collection.asd save-unlock/AFCGMode.asd save-unlock/AFBGMMode.asd
  support/xsystem4.txt support/libsys4.txt support/xsystem35.txt support/MTLc3m.txt
  support/mincho.txt support/nanojpeg.txt support/microui.txt support/libxml2.txt)
nx_generate_nacp(AliceHub.nacp NAME "AliceHub" AUTHOR "AliceHub" VERSION "1.0.0")
nx_create_nro(AliceHub NACP AliceHub.nacp ROMFS AliceHub_romfs)
add_custom_target(AliceHub_artifact ALL
  COMMAND ${CMAKE_COMMAND} -E make_directory "${artifacts}"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_BINARY_DIR}/AliceHub.nro" "${artifacts}/AliceHub.nro"
  DEPENDS AliceHub_nro)
