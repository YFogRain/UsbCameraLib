cmake_minimum_required(VERSION 3.22.1)

project(uvcCamera)

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
        camera/UvcPreview.cpp
        camera/UvcCamera.cpp
        camera/img_util.cpp
)


target_include_directories(uvcCamera PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/libuvc/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/rapidjson/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/camera/headers"
        "${CMAKE_CURRENT_SOURCE_DIR}/log"
        "${CMAKE_CURRENT_SOURCE_DIR}/opencv/include"
        log)

file(GLOB 3RDPARTY_LIB ${CMAKE_CURRENT_SOURCE_DIR}/opencv/3rdparty/libs/${ANDROID_ABI}/*.a)

target_link_libraries(uvcCamera
        android
        log
        uvc
        z
        ${CMAKE_SOURCE_DIR}/opencv/staticlibs/${ANDROID_ABI}/libopencv_core.a
        ${CMAKE_SOURCE_DIR}/opencv/staticlibs/${ANDROID_ABI}/libopencv_imgproc.a
        ${CMAKE_SOURCE_DIR}/opencv/staticlibs/${ANDROID_ABI}/libopencv_imgcodecs.a
        ${3RDPARTY_LIB}
)