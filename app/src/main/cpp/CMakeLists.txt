cmake_minimum_required(VERSION 3.22.1)
project(replicantsynth_engine LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(oboe REQUIRED CONFIG)

add_library(replicantsynth_engine SHARED
    jni_bridge.cpp
    audio/AudioDriver.cpp
    core/Acid303.cpp
    core/DrumKit909.cpp
    core/Wavetable.cpp
    core/Effects.cpp
    core/Recorder.cpp
    core/ClipEngine.cpp
    core/acid/oscillator.cpp
    core/acid/filter.cpp
    core/acid/distortion.cpp
    core/drums909/BassDrumVoice.cpp
    core/drums909/SnareDrumVoice.cpp
    core/drums909/TomVoice.cpp
    core/drums909/RimShotVoice.cpp
    core/drums909/HandClapVoice.cpp
    core/drums909/CymbalVoice.cpp
)

target_include_directories(replicantsynth_engine PRIVATE
    core
    core/acid
    core/drums909
    audio
)

target_compile_options(replicantsynth_engine PRIVATE
    -O3
    -fno-math-errno
    -fno-trapping-math
    -Wall
    -Wextra
    -Wno-unused-parameter
)

target_link_libraries(replicantsynth_engine PRIVATE
    oboe::oboe
    android
    log
)
