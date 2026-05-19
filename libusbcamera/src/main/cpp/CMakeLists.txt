cmake_minimum_required(VERSION 3.22.1)

# 设置为c++17
set(CMAKE_CXX_STANDARD 17)

project(uvcCamera)

# 开启simd支持
if (${ANDROID_ABI} STREQUAL "armeabi-v7a")
    set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -march=armv7-a -mfpu=vfpv3-d16 -mfloat-abi=softfp")
    set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -march=armv7-a -mfpu=vfpv3-d16 -mfloat-abi=softfp")
endif ()

# libusb库导入
add_subdirectory("${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb")

# uvc库导入
add_library(uvc06 SHARED
        third_party/libuvc/src/ctrl.c
        third_party/libuvc/src/ctrl-gen.c
        third_party/libuvc/src/device.c
        third_party/libuvc/src/diag.c
        third_party/libuvc/src/frame.c
        #        libuvc/src/frame-mjpeg.c
        third_party/libuvc/src/init.c
        third_party/libuvc/src/stream.c
        third_party/libuvc/src/misc.c
)


# 导入对应的头文件
target_include_directories(
        uvc06 PUBLIC
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libuvc"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb/os"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libuvc/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/log"
)
# 关联动态库的依赖
target_link_libraries(uvc06
        # List libraries link to the target library
        android
        log
        usb100
)


add_library(uvcCamera SHARED
        jni/camera_uvc_jni.cpp

        camera/camera_factory_helper.cpp
        camera/usb/camera_device_usb.cpp
        camera/usb/camera_stream_usb.cpp
        camera/v4l2/camera_device_v4l2.cpp
        camera/v4l2/camera_stream_v4l2.cpp
        camera/v4l2/video_v4l2_utils.cpp
        camera/img_util.cpp

        gl/src/gl_preview.cpp
        gl/src/gl_render.cpp
        gl/src/gl_shader_util.cpp
)

target_include_directories(uvcCamera PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/libuvc/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/rapidjson/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/camera/headers"
        "${CMAKE_CURRENT_SOURCE_DIR}/log"
        "${CMAKE_CURRENT_SOURCE_DIR}/third_party/opencv/include"
        "${CMAKE_CURRENT_SOURCE_DIR}/gl/include"
)

file(GLOB 3RDPARTY_LIB ${CMAKE_CURRENT_SOURCE_DIR}/third_party/opencv/3rdparty/libs/${ANDROID_ABI}/*.a)

#file(GLOB FFMPEG_LIBS ${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg/staticlibs/${ANDROID_ABI}/*.a)
#file(GLOB FFMPEG_LIBS_3r ${CMAKE_CURRENT_SOURCE_DIR}/ffmpeg/3rdparty/libs/${ANDROID_ABI}/*.a)

target_link_libraries(uvcCamera
        android
        log
        uvc06
        z
        mediandk
        GLESv3
        EGL
        ${CMAKE_SOURCE_DIR}/third_party/opencv/staticlibs/${ANDROID_ABI}/libopencv_core.a
        ${CMAKE_SOURCE_DIR}/third_party/opencv/staticlibs/${ANDROID_ABI}/libopencv_imgproc.a
        ${CMAKE_SOURCE_DIR}/third_party/opencv/staticlibs/${ANDROID_ABI}/libopencv_imgcodecs.a
        ${3RDPARTY_LIB}
)