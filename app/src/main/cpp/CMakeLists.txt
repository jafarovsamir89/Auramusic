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

# ChatScript Lab (debug-only experiment). This is deliberately isolated from
# Aura's production assistant/LLM paths; the native bridge is only reachable
# from the debug Lab screen.
set(AURA_CHATSCRIPT_DIR ${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/chatscript/SRC)
set(AURA_CHATSCRIPT_SOURCES
    ${AURA_CHATSCRIPT_DIR}/constructCode.cpp
    ${AURA_CHATSCRIPT_DIR}/csocket.cpp
    ${AURA_CHATSCRIPT_DIR}/cs_es.cpp
    ${AURA_CHATSCRIPT_DIR}/cs_german.cpp
    ${AURA_CHATSCRIPT_DIR}/cs_jp.cpp
    ${AURA_CHATSCRIPT_DIR}/dictionarySystem.cpp
    ${AURA_CHATSCRIPT_DIR}/english.cpp
    ${AURA_CHATSCRIPT_DIR}/englishTagger.cpp
    ${AURA_CHATSCRIPT_DIR}/factSystem.cpp
    ${AURA_CHATSCRIPT_DIR}/functionExecute.cpp
    ${AURA_CHATSCRIPT_DIR}/infer.cpp
    ${AURA_CHATSCRIPT_DIR}/jsmn.cpp
    ${AURA_CHATSCRIPT_DIR}/json.cpp
    ${AURA_CHATSCRIPT_DIR}/mainSystem.cpp
    ${AURA_CHATSCRIPT_DIR}/markSystem.cpp
    ${AURA_CHATSCRIPT_DIR}/mssql.cpp
    ${AURA_CHATSCRIPT_DIR}/os.cpp
    ${AURA_CHATSCRIPT_DIR}/outputSystem.cpp
    ${AURA_CHATSCRIPT_DIR}/patternSystem.cpp
    ${AURA_CHATSCRIPT_DIR}/privatesrc.cpp
    ${AURA_CHATSCRIPT_DIR}/scriptCompile.cpp
    ${AURA_CHATSCRIPT_DIR}/secure.cpp
    ${AURA_CHATSCRIPT_DIR}/spellcheck.cpp
    ${AURA_CHATSCRIPT_DIR}/systemVariables.cpp
    ${AURA_CHATSCRIPT_DIR}/tagger.cpp
    ${AURA_CHATSCRIPT_DIR}/testing.cpp
    ${AURA_CHATSCRIPT_DIR}/textUtilities.cpp
    ${AURA_CHATSCRIPT_DIR}/tokenSystem.cpp
    ${AURA_CHATSCRIPT_DIR}/topicSystem.cpp
    ${AURA_CHATSCRIPT_DIR}/userCache.cpp
    ${AURA_CHATSCRIPT_DIR}/userSystem.cpp
    ${AURA_CHATSCRIPT_DIR}/variableSystem.cpp
    ${AURA_CHATSCRIPT_DIR}/zif.cpp
)

add_library(aura_chatscript_core STATIC ${AURA_CHATSCRIPT_SOURCES})
target_include_directories(aura_chatscript_core PRIVATE ${AURA_CHATSCRIPT_DIR})
target_compile_definitions(aura_chatscript_core PRIVATE
    ANDROID NOMAIN AURA_CHATScript_DISABLE_HTTP
    DISCARDSERVER DISCARDPOSTGRES DISCARDMONGO DISCARDMYSQL
    DISCARDMICROSOFTSQL DISCARD_TEXT_COMPRESSION DISCARD_JAPANESE
    DISCARDJSONOPEN DISCARDTCPOPEN DISCARDWEBSOCKET DISCARDCLIENT
    DISCARDCOUNTER DISCARDJAVASCRIPT
)
target_compile_options(aura_chatscript_core PRIVATE -O2 -w -Wno-dynamic-exception-spec)

add_library(aura_chatscript SHARED aura_chatscript_jni.cpp)
target_include_directories(aura_chatscript PRIVATE ${AURA_CHATSCRIPT_DIR})
target_compile_definitions(aura_chatscript PRIVATE
    ANDROID NOMAIN AURA_CHATScript_DISABLE_HTTP
    DISCARDSERVER DISCARDPOSTGRES DISCARDMONGO DISCARDMYSQL
    DISCARDMICROSOFTSQL DISCARD_TEXT_COMPRESSION DISCARD_JAPANESE
    DISCARDJSONOPEN DISCARDTCPOPEN DISCARDWEBSOCKET DISCARDCLIENT
    DISCARDCOUNTER DISCARDJAVASCRIPT
)
target_compile_options(aura_chatscript PRIVATE -Wno-dynamic-exception-spec)
target_link_libraries(aura_chatscript PRIVATE aura_chatscript_core android log)
