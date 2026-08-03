cmake_minimum_required(VERSION 3.22.1)
project(aura_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
# The app is routinely installed as a debug APK on real phones. Native speech
# code must still be optimized on ARM.
if(CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(-O3 -DNDEBUG)
endif()
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_ALL_WARNINGS OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp
                 ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp)

add_library(aura_whisper SHARED aura_whisper_jni.cpp)
target_include_directories(aura_whisper PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp/include)
target_link_libraries(aura_whisper PRIVATE whisper android log)
