cmake_minimum_required(VERSION 3.22.1)

# 设置为c++17
set(CMAKE_CXX_STANDARD 17)

project(uvcCamera)

# 开启simd支持
if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -mcpu=cortex-a53 -mfpu=neon -mfloat-abi=softfp")
endif ()

# libusb库导入
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/libusb")

# uvc库导入
add_library(uvc SHARED
        libuvc/src/ctrl.c
        libuvc/src/ctrl-gen.c
        libuvc/src/device.c
        libuvc/src/diag.c
        libuvc/src/frame.c
        #        libuvc/src/frame-mjpeg.c
        libuvc/src/init.c
        libuvc/src/stream.c
        libuvc/src/misc.c
)


# 导入对应的头文件
target_include_directories(
        uvc PUBLIC
        "${CMAKE_CURRENT_SOURCE_DIR}/libuvc"
        "${CMAKE_CURRENT_SOURCE_DIR}/libusb"
        "${CMAKE_CURRENT_SOURCE_DIR}/libusb/os"
        "${CMAKE_CURRENT_SOURCE_DIR}/libuvc/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/log"
)
# 关联动态库的依赖
target_link_libraries(uvc
        # List libraries link to the target library
        android
        log
        usb100
)


add_library(uvcCamera SHARED
        camera_uvc.cpp

        camera/camera_factory_helper.cpp
        camera/usb/camera_device_usb.cpp
        camera/usb/camera_stream_usb.cpp
        camera/v4l2/camera_device_v4l2.cpp
        camera/v4l2/camera_stream_v4l2.cpp
        camera/v4l2/video_v4l2_utils.cpp
        camera/img_util.cpp
        camera/video_record.cpp
)


target_include_directories(uvcCamera PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/libuvc/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/rapidjson/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/camera/headers"
        "${CMAKE_CURRENT_SOURCE_DIR}/log"
        "${CMAKE_CURRENT_SOURCE_DIR}/opencv/include"
)

file(GLOB 3RDPARTY_LIB ${CMAKE_CURRENT_SOURCE_DIR}/opencv/3rdparty/libs/${ANDROID_ABI}/*.a)

target_link_libraries(uvcCamera
        android
        log
        uvc
        z
        mediandk
        ${CMAKE_SOURCE_DIR}/opencv/staticlibs/${ANDROID_ABI}/libopencv_core.a
        ${CMAKE_SOURCE_DIR}/opencv/staticlibs/${ANDROID_ABI}/libopencv_imgproc.a
        ${CMAKE_SOURCE_DIR}/opencv/staticlibs/${ANDROID_ABI}/libopencv_imgcodecs.a
        ${CMAKE_SOURCE_DIR}/opencv/staticlibs/${ANDROID_ABI}/libopencv_videoio.a
        ${3RDPARTY_LIB}
)