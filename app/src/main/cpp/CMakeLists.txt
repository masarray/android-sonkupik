cmake_minimum_required(VERSION 3.22)
project(arsonkupik_android_dsp LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(arsonkupik_android_dsp SHARED
        src/arsonkupik_dsp.cpp
        src/arsonkupik_presets.cpp
        src/arsonkupik_android_jni.cpp
)

target_include_directories(arsonkupik_android_dsp PRIVATE include)

find_library(log-lib log)
target_link_libraries(arsonkupik_android_dsp PRIVATE ${log-lib})
