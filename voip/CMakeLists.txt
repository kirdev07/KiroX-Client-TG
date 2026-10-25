# KiroX call engine: tgcalls (LGPL-3.0) + WebRTC (BSD-3-Clause) + own JNI bridge in kirox/.
# Third-party build lists in voip/CMakeLists.txt come from the tgcalls Android build.
cmake_minimum_required(VERSION 3.16)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)
set(CMAKE_CXX_FLAGS "-DANDROID")
set(CMAKE_C_FLAGS "-w -std=c11 -DANDROID -D_LARGEFILE_SOURCE=1 -Wno-error=implicit-function-declaration")
set(CMAKE_ASM_FLAGS "${CFLAGS} -x assembler-with-cpp")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -ffunction-sections -fdata-sections -fvisibility=hidden -fvisibility-inlines-hidden")
# Optimized in every variant: an -O0 engine is three times larger and too slow for real-time audio.
foreach(lang C CXX)
    set(CMAKE_${lang}_FLAGS_DEBUG "-O2")
    set(CMAKE_${lang}_FLAGS_RELEASE "-O2")
endforeach()
set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--gc-sections -Wl,--icf=safe -Wl,-Bsymbolic")

enable_language(ASM)

set(CAN_COMPILE_SME FALSE CACHE INTERNAL "Disable libyuv SME" FORCE)
add_subdirectory(third_party/libyuv ${CMAKE_CURRENT_BINARY_DIR}/libyuv EXCLUDE_FROM_ALL)

set(ABSL_PROPAGATE_CXX_STD ON)
set(ABSL_BUILD_TESTING OFF)
set(ABSL_ENABLE_INSTALL OFF)
add_subdirectory(third_party/absl ${CMAKE_CURRENT_BINARY_DIR}/absl EXCLUDE_FROM_ALL)
add_library(webrtc_absl INTERFACE)
target_link_libraries(webrtc_absl INTERFACE
        absl::base absl::strings absl::str_format absl::cord absl::synchronization absl::time
        absl::status absl::statusor absl::hash absl::flat_hash_map absl::flat_hash_set
        absl::flags absl::flags_parse absl::random_random absl::random_distributions
        absl::crc32c absl::log absl::check)

foreach(lib avutil avformat avcodec swresample swscale crypto ssl tde2e tdutils)
    add_library(${lib} STATIC IMPORTED)
    set_target_properties(${lib} PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/lib${lib}.a)
endforeach()
foreach(lib vpx dav1d opus)
    add_library(lib${lib} STATIC IMPORTED)
    set_target_properties(lib${lib} PROPERTIES IMPORTED_LOCATION ${CMAKE_HOME_DIRECTORY}/prebuild/lib/${ANDROID_ABI}/lib${lib}.a)
endforeach()

include(${CMAKE_HOME_DIRECTORY}/voip/CMakeLists.txt)

add_library(kiroxvoip SHARED kirox/kirox_voip_jni.cpp)
target_include_directories(kiroxvoip PRIVATE tgcalls/tgcalls)
target_link_options(kiroxvoip PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384" "-Wl,--version-script=${CMAKE_CURRENT_SOURCE_DIR}/kirox/exports.map")
target_link_libraries(kiroxvoip
        -Wl,--whole-archive rnnoise openh264 voipandroid -Wl,--no-whole-archive
        webrtc_absl tgcalls tgcalls_tp
        swscale avformat avcodec swresample libvpx libdav1d libopus tde2e tdutils yuv avutil ssl crypto
        log z GLESv2 EGL android OpenSLES)
