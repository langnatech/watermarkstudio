cmake_minimum_required(VERSION 3.22.1)
project(removal_native)

add_library(removal_native SHARED
    removal_native.cpp
    patchmatch_inpaint.cpp
    video_temporal.cpp
)

find_library(log-lib log)

target_link_libraries(removal_native
    ${log-lib}
)

target_compile_features(removal_native PRIVATE cxx_std_17)

# Google Play 16 KB page size: align ELF LOAD segments on arm64-v8a / x86_64.
if(ANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES)
    target_link_options(removal_native PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
    )
endif()
