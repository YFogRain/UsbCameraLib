cmake_minimum_required(VERSION 3.22.1)

project(uvcCamera)

# libusb库导入
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/libusb")

# 设置libjpeg-turbo源码路径
#add_library(jpeg-turbo STATIC IMPORTED)
#set_target_properties(jpeg-turbo PROPERTIES IMPORTED_LOCATION ${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo/libs/${ANDROID_ABI}/libjpeg.a)


# uvc库导入
# uvc库的引入
add_library(uvc SHARED
        libuvc/src/ctrl.c
        libuvc/src/ctrl-gen.c
        libuvc/src/device.c
        libuvc/src/diag.c
        libuvc/src/frame.c
        libuvc/src/frame-mjpeg.c
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
        "${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo"
)
# 关联动态库的依赖
target_link_libraries(uvc
        # List libraries link to the target library
        android
        log
        usb100
#        jpeg-turbo
        ${CMAKE_CURRENT_SOURCE_DIR}/libjpeg-turbo/libs/${ANDROID_ABI}/libjpeg.a
)


add_library(uvcCamera SHARED
        camera_uvc.cpp
        camera/source/UvcPreview.cpp
        camera/source/UvcCamera.cpp

)
target_include_directories(uvcCamera PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/libuvc/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/rapidjson/include"
        log)

target_link_libraries(uvcCamera
        android
        log
        uvc
)