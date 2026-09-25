cmake_minimum_required(VERSION 3.22.1)
project("ffmpegJNI" C CXX)

set(CMAKE_CXX_STANDARD 11)

include_directories(${CMAKE_CURRENT_SOURCE_DIR}/include)

set(FFMPEG_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}")

add_library(avutil SHARED IMPORTED)
set_target_properties(avutil PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/libavutil.so")

add_library(swresample SHARED IMPORTED)
set_target_properties(swresample PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/libswresample.so")

add_library(avcodec SHARED IMPORTED)
set_target_properties(avcodec PROPERTIES IMPORTED_LOCATION "${FFMPEG_DIR}/libavcodec.so")

add_library(ffmpegJNI SHARED ffmpeg_jni.cc)

find_library(log-lib log)
find_library(android-lib android)

target_link_libraries(ffmpegJNI
                      avcodec
                      swresample
                      avutil
                      ${log-lib}
                      ${android-lib})

target_link_options(ffmpegJNI PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-Bsymbolic")
