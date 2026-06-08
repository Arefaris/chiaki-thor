# Install OpenSSL build artifacts on Windows
# Replaces `make install_dev` which uses Unix commands (cp, [) unavailable on Windows
#
# Required variables (passed via -D):
#   BUILD_DIR   - OpenSSL build directory
#   SOURCE_DIR  - OpenSSL source directory  
#   INSTALL_DIR - Installation prefix

# Create directories
file(MAKE_DIRECTORY "${INSTALL_DIR}/lib")
file(MAKE_DIRECTORY "${INSTALL_DIR}/include/openssl")

# Copy static libraries
file(GLOB OPENSSL_LIBS "${BUILD_DIR}/libcrypto.a" "${BUILD_DIR}/libssl.a")
foreach(LIB ${OPENSSL_LIBS})
    get_filename_component(LIB_NAME "${LIB}" NAME)
    message(STATUS "Installing ${LIB_NAME}")
    file(COPY "${LIB}" DESTINATION "${INSTALL_DIR}/lib")
endforeach()

# Copy public headers from source
file(GLOB OPENSSL_HEADERS "${SOURCE_DIR}/include/openssl/*.h")
foreach(HDR ${OPENSSL_HEADERS})
    file(COPY "${HDR}" DESTINATION "${INSTALL_DIR}/include/openssl")
endforeach()

# Copy generated headers from build dir
file(GLOB OPENSSL_GEN_HEADERS "${BUILD_DIR}/include/openssl/*.h")
foreach(HDR ${OPENSSL_GEN_HEADERS})
    file(COPY "${HDR}" DESTINATION "${INSTALL_DIR}/include/openssl")
endforeach()

message(STATUS "OpenSSL installed to ${INSTALL_DIR}")
