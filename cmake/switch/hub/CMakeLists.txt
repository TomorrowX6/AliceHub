find_package(Freetype REQUIRED)
find_package(PNG REQUIRED)
find_package(JPEG REQUIRED)
find_package(ZLIB REQUIRED)

add_executable(AliceHub app.c features.c install_transaction.c library.c details.c settings.c model.c progress.c ../picker_art.c)
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
dkp_add_asset_target(AliceHub_romfs "${romfs}")
dkp_track_assets(AliceHub_romfs FILES alicehub.png fonts/VL-Gothic-Regular.ttf
  fonts/VL-Gothic-Regular.license fonts/HanaMinA.ttf.gz fonts/HanaMinA.license)
nx_generate_nacp(AliceHub.nacp NAME "AliceHub" AUTHOR "AliceHub" VERSION "1.0.0")
nx_create_nro(AliceHub NACP AliceHub.nacp ROMFS AliceHub_romfs)
add_custom_target(AliceHub_artifact ALL
  COMMAND ${CMAKE_COMMAND} -E make_directory "${artifacts}"
  COMMAND ${CMAKE_COMMAND} -E copy_if_different "${CMAKE_CURRENT_BINARY_DIR}/AliceHub.nro" "${artifacts}/AliceHub.nro"
  DEPENDS AliceHub_nro)
