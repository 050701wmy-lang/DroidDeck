# The app's native side: the Wayland compositor the session presents into, the adrenotools loader
# it opens Turnip with, and one small library for the fake-input ring's memory fence.
cmake_minimum_required(VERSION 3.22.1)
project(steamdeck C CXX)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

add_subdirectory(adrenotools)
add_subdirectory(waylandcomp)
add_library(sdnative SHARED sdnative/sdnative.c)
target_link_libraries(sdnative log)
