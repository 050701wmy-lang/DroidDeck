# The app's native side: the Wayland compositor the session presents into, the adrenotools loader
# it opens Turnip with, and one small library for the fake-input ring's memory fence.
cmake_minimum_required(VERSION 3.22.1)
project(steamdeck C CXX)

set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -O2 -Wno-unused-function -Wimplicit-function-declaration")

add_subdirectory(adrenotools)

# DXVK's DXBC->SPIR-V translator (zlib licence), vendored under thirdparty/dxbc from the same
# subset upstream lsfg-vk uses. Native LSFG frame generation needs it: the Lossless Scaling
# shader chain is DXBC in every build downloadable from Steam.
add_subdirectory(thirdparty/dxbc)

# The Win-FG chain self-identifies by two halves: the upstream tag it was copied from (hand-set,
# UPDATE IT when re-copying) and a hash over the sources themselves, computed here, which cannot
# rot. The compositor's frame-generation leg (waylandcomp/CMakeLists.txt) stamps both in.
set(WINFG_UPSTREAM "v0.3.0")
set(WINFG_CHAIN_SRC
        winlator/winfg/framegen.cpp
        winlator/winfg/framegen.hpp
        winlator/winfg/record_impl.inc
        winlator/winfg/config.hpp
        winlator/winfg/embedded_shaders.hpp
        winlator/winfg/winfg_engine.cpp
)
set(WINFG_HASH_INPUT "")
foreach(f ${WINFG_CHAIN_SRC})
    file(SHA256 "${CMAKE_CURRENT_SOURCE_DIR}/${f}" _h)
    string(APPEND WINFG_HASH_INPUT "${_h}")
endforeach()
string(SHA256 WINFG_CHAIN_HASH_FULL "${WINFG_HASH_INPUT}")
string(SUBSTRING "${WINFG_CHAIN_HASH_FULL}" 0 12 WINFG_CHAIN_HASH)
message(STATUS "win-fg chain: ${WINFG_UPSTREAM} (src ${WINFG_CHAIN_HASH})")

add_subdirectory(waylandcomp)
add_library(sdnative SHARED sdnative/sdnative.c)
target_link_libraries(sdnative log)
