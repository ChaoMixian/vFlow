cmake_minimum_required(VERSION 3.22.1)
project(vflow_ppocrv5)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

set(NCNN_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ncnn-20260113-android-vulkan/${ANDROID_ABI})
set(ncnn_DIR ${NCNN_ROOT}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

find_package(OpenCV REQUIRED CONFIG)

add_library(ppocrv5_jni SHARED
    ppocr_v5.cpp
    ppocr_v5_jni.cpp
)

target_link_libraries(ppocrv5_jni
    ncnn
    OpenCV::opencv_java4
    jnigraphics
    android
    log
)
