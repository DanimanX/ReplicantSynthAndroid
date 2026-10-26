cmake_minimum_required(VERSION 3.22)
project(repsynth_host LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

add_library(repsynth_core STATIC
    ../app/src/main/cpp/core/Acid303.cpp
    ../app/src/main/cpp/core/acid/oscillator.cpp
    ../app/src/main/cpp/core/acid/filter.cpp
    ../app/src/main/cpp/core/acid/distortion.cpp
    ../app/src/main/cpp/core/DrumKit909.cpp
    ../app/src/main/cpp/core/Wavetable.cpp
    ../app/src/main/cpp/core/Effects.cpp
    ../app/src/main/cpp/core/Recorder.cpp
    ../app/src/main/cpp/core/ClipEngine.cpp
    ../app/src/main/cpp/core/drums909/BassDrumVoice.cpp
    ../app/src/main/cpp/core/drums909/SnareDrumVoice.cpp
    ../app/src/main/cpp/core/drums909/TomVoice.cpp
    ../app/src/main/cpp/core/drums909/RimShotVoice.cpp
    ../app/src/main/cpp/core/drums909/HandClapVoice.cpp
    ../app/src/main/cpp/core/drums909/CymbalVoice.cpp
)
target_include_directories(repsynth_core PUBLIC
    ../app/src/main/cpp/core
    ../app/src/main/cpp/core/acid
    ../app/src/main/cpp/core/drums909
)
target_compile_options(repsynth_core PRIVATE -O2 -Wall -Wextra -Wno-unused-parameter)

find_package(Threads REQUIRED)

add_executable(engine_tests engine_tests.cpp)
target_link_libraries(engine_tests PRIVATE repsynth_core Threads::Threads)
target_compile_options(engine_tests PRIVATE -O2)

add_executable(render_demo render_demo.cpp)
target_link_libraries(render_demo PRIVATE repsynth_core Threads::Threads)
target_compile_options(render_demo PRIVATE -O2)
