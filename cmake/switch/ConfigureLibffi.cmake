set(target_header "${SOURCE_DIR}/src/aarch64/ffitarget.h")
file(READ "${target_header}" header)
# Switch NROs cannot relocate the complex type descriptors in read-only memory.
string(REPLACE "#define FFI_TARGET_HAS_COMPLEX_TYPE" "" header "${header}")
file(WRITE "${target_header}" "${header}")
