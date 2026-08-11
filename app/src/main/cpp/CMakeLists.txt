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

# Official llama.cpp Android binding (AURA Brain Pack). The model remains an
# app-private download; only the inference runtime is compiled into the APK.
set(LLAMA_BUILD_COMMON ON CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL ON CACHE BOOL "" FORCE)
set(GGML_CPU_ALL_VARIANTS ON CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

set(AURA_PREVIOUS_BUILD_SHARED_LIBS ${BUILD_SHARED_LIBS})
set(BUILD_SHARED_LIBS ON CACHE BOOL "" FORCE)
add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp
                 ${CMAKE_CURRENT_BINARY_DIR}/llama.cpp EXCLUDE_FROM_ALL)
set(BUILD_SHARED_LIBS ${AURA_PREVIOUS_BUILD_SHARED_LIBS} CACHE BOOL "" FORCE)

add_subdirectory(${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp
                 ${CMAKE_CURRENT_BINARY_DIR}/whisper.cpp)

add_library(aura_whisper SHARED aura_whisper_jni.cpp)
target_include_directories(aura_whisper PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/whisper.cpp/include)
target_link_libraries(aura_whisper PRIVATE whisper android log)

add_library(aura_llama SHARED aura_llama_jni.cpp)
target_include_directories(aura_llama PRIVATE
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp/common
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp/include
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp/ggml/include
    ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/llama.cpp/ggml/src)
target_link_libraries(aura_llama PRIVATE llama llama-common android log)
