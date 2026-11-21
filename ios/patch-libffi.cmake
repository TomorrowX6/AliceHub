file(READ "${SOURCE_DIR}/src/aarch64/ffitarget.h" header)
string(REPLACE "#define FFI_CLOSURES 1" "#define FFI_CLOSURES 0" header "${header}")
string(REPLACE "#define FFI_GO_CLOSURES 1" "/* Go closures disabled. */" header "${header}")
file(WRITE "${SOURCE_DIR}/src/aarch64/ffitarget.h" "${header}")
