cmake_minimum_required(VERSION 3.22.1)
project("dexprotector")

add_library(
    dexprotector
    SHARED
    dex_loader.cpp
)

find_library(log-lib log)

target_link_libraries(
    dexprotector
    ${log-lib}
    android
    dl
)

target_compile_options(dexprotector PRIVATE
    -O2
    -fvisibility=hidden
    -fno-exceptions
    -fno-rtti
    -ffunction-sections
    -fdata-sections
    -Wl,--gc-sections
    -Wl,--strip-all
)
