
include(ExternalProject)

set(OPENSSL_INSTALL_DIR "${CMAKE_CURRENT_BINARY_DIR}/openssl-install-prefix")

unset(OPENSSL_OS_COMPILER)
unset(OPENSSL_CONFIG_EXTRA_ARGS)
unset(OPENSSL_BUILD_ENV)

if(ANDROID_ABI)
	if(ANDROID_ABI STREQUAL "armeabi-v7a")
		set(OPENSSL_OS_COMPILER "android-arm")
	elseif(ANDROID_ABI STREQUAL "arm64-v8a")
		set(OPENSSL_OS_COMPILER "android-arm64")
	elseif(ANDROID_ABI STREQUAL "x86")
		set(OPENSSL_OS_COMPILER "android-x86")
	elseif(ANDROID_ABI STREQUAL "x86_64")
		set(OPENSSL_OS_COMPILER "android-x86_64")
	endif()

	set(OPENSSL_CONFIG_EXTRA_ARGS "-D__ANDROID_API__=${ANDROID_NATIVE_API_LEVEL}")
	get_filename_component(ANDROID_NDK_BIN_PATH "${CMAKE_C_COMPILER}" DIRECTORY)
	set(OPENSSL_BUILD_ENV "ANDROID_NDK_HOME=${ANDROID_NDK}" "AR=${ANDROID_NDK_BIN_PATH}/llvm-ar.exe" "RANLIB=${ANDROID_NDK_BIN_PATH}/llvm-ranlib.exe")
else()
	if(UNIX AND NOT APPLE AND CMAKE_SIZEOF_VOID_P STREQUAL "8")
		set(OPENSSL_OS_COMPILER "linux-x86_64")
	endif()
endif()

if(NOT OPENSSL_OS_COMPILER)
	message(FATAL_ERROR "Failed to match OPENSSL_OS_COMPILER")
endif()

find_program(MAKE_EXE NAMES gmake make PATHS "${ANDROID_NDK}/prebuilt/windows-x86_64/bin" "${ANDROID_NDK}/prebuilt/linux-x86_64/bin" "${ANDROID_NDK}/prebuilt/darwin-x86_64/bin")
find_program(PERL_EXE NAMES perl)
ExternalProject_Add(OpenSSL-ExternalProject
		URL https://www.openssl.org/source/openssl-1.1.1w.tar.gz
		INSTALL_DIR "${OPENSSL_INSTALL_DIR}"
		PATCH_COMMAND ${CMAKE_COMMAND} -P "${CMAKE_CURRENT_SOURCE_DIR}/cmake/patch_openssl.cmake"
		CONFIGURE_COMMAND ${CMAKE_COMMAND} -E env ${OPENSSL_BUILD_ENV}
			"${PERL_EXE}" "<SOURCE_DIR>/Configure" "--prefix=<INSTALL_DIR>" no-shared no-asm ${OPENSSL_CONFIG_EXTRA_ARGS} "${OPENSSL_OS_COMPILER}"
		BUILD_COMMAND ${CMAKE_COMMAND} -E env ${OPENSSL_BUILD_ENV} "${MAKE_EXE}" -j4 build_libs
		INSTALL_COMMAND ${CMAKE_COMMAND} -DBUILD_DIR=<BINARY_DIR> -DSOURCE_DIR=<SOURCE_DIR> -DINSTALL_DIR=<INSTALL_DIR> -P "${CMAKE_CURRENT_SOURCE_DIR}/cmake/install_openssl.cmake")

add_library(OpenSSL_Crypto INTERFACE)
add_dependencies(OpenSSL_Crypto OpenSSL-ExternalProject)
if(${CMAKE_VERSION} VERSION_GREATER_EQUAL "3.13.0")
	target_link_directories(OpenSSL_Crypto INTERFACE "${OPENSSL_INSTALL_DIR}/lib")
else()
	link_directories("${OPENSSL_INSTALL_DIR}/lib")
endif()
target_link_libraries(OpenSSL_Crypto INTERFACE crypto)
target_include_directories(OpenSSL_Crypto INTERFACE "${OPENSSL_INSTALL_DIR}/include")
