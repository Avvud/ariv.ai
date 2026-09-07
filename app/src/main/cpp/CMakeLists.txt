cmake_minimum_required(VERSION 3.22.1)
project("llama_android")

include(FetchContent)

FetchContent_Declare(
        llama
        GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
        GIT_TAG        b10819
        GIT_SHALLOW    TRUE
)

set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)

FetchContent_MakeAvailable(llama)

add_library(llama_android SHARED
        llama_android.cpp
)

target_link_options(llama_android PRIVATE "-Wl,-z,max-page-size=16384")

find_library(log-lib log)

target_link_libraries(llama_android
        llama
        ${log-lib}
)