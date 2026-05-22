cmake_minimum_required(VERSION 3.22.1)
project(removal_native)

add_library(removal_native SHARED
    removal_native.cpp
    video_temporal.cpp
)

find_library(log-lib log)

target_link_libraries(removal_native
    ${log-lib}
)

target_compile_features(removal_native PRIVATE cxx_std_17)
