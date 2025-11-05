cmake_minimum_required(VERSION 3.22.1)

# 设置为c++17
set(CMAKE_CXX_STANDARD 17)

project(uvc_camera)


# libusb库导入
add_library(usb1.0.26 SHARED
        libusb/core.c
        libusb/descriptor.c
        libusb/hotplug.c
        libusb/io.c
        libusb/sync.c
        libusb/strerror.c
        libusb/os/linux_usbfs.c
        libusb/os/events_posix.c
        libusb/os/threads_posix.c
        libusb/os/linux_netlink.c
)

target_include_directories(usb1.0.26 PRIVATE
        libusb
        libusb/os
)

target_link_libraries(usb1.0.26
        android
        log
)

# jpeg-turbo库导入
add_library(jpeg-turbo2.1.2 SHARED
        libjpeg-turbo/jcapimin.c libjpeg-turbo/jcapistd.c libjpeg-turbo/jccoefct.c libjpeg-turbo/jccolor.c libjpeg-turbo/jcdctmgr.c
        libjpeg-turbo/jchuff.c libjpeg-turbo/jcicc.c libjpeg-turbo/jcinit.c libjpeg-turbo/jcmainct.c libjpeg-turbo/jcmarker.c
        libjpeg-turbo/jcmaster.c libjpeg-turbo/jcomapi.c libjpeg-turbo/jcparam.c libjpeg-turbo/jcphuff.c libjpeg-turbo/jcprepct.c
        libjpeg-turbo/jcsample.c libjpeg-turbo/jctrans.c libjpeg-turbo/jdapimin.c libjpeg-turbo/jdapistd.c libjpeg-turbo/jdatadst.c
        libjpeg-turbo/jdatasrc.c libjpeg-turbo/jdcoefct.c libjpeg-turbo/jdcolor.c libjpeg-turbo/jddctmgr.c libjpeg-turbo/jdhuff.c
        libjpeg-turbo/jdicc.c libjpeg-turbo/jdinput.c libjpeg-turbo/jdmainct.c libjpeg-turbo/jdmarker.c libjpeg-turbo/jdmaster.c
        libjpeg-turbo/jdmerge.c libjpeg-turbo/jdphuff.c libjpeg-turbo/jdpostct.c libjpeg-turbo/jdsample.c libjpeg-turbo/jdtrans.c
        libjpeg-turbo/jerror.c libjpeg-turbo/jfdctflt.c libjpeg-turbo/jfdctfst.c libjpeg-turbo/jfdctint.c libjpeg-turbo/jidctflt.c
        libjpeg-turbo/jidctfst.c libjpeg-turbo/jidctint.c libjpeg-turbo/jidctred.c libjpeg-turbo/jquant1.c libjpeg-turbo/jquant2.c
        libjpeg-turbo/jutils.c libjpeg-turbo/jmemmgr.c libjpeg-turbo/jmemnobs.c libjpeg-turbo/jaricom.c libjpeg-turbo/jcarith.c
        libjpeg-turbo/jdarith.c libjpeg-turbo/turbojpeg.c libjpeg-turbo/transupp.c libjpeg-turbo/jdatadst-tj.c libjpeg-turbo/jdatasrc-tj.c
        libjpeg-turbo/rdbmp.c libjpeg-turbo/rdppm.c libjpeg-turbo/wrbmp.c libjpeg-turbo/wrppm.c
)

target_include_directories(jpeg-turbo2.1.2 PRIVATE
        libjpeg-turbo
        libjpeg-turbo/simd
        libjpeg-turbo/include
)

# 根据 ABI 设置宏
# 默认宏
set(SIZEOF_SIZE_T 4)
set(WITH_SIMD 0)

if (CMAKE_ANDROID_ARCH_ABI STREQUAL "arm64-v8a" OR CMAKE_ANDROID_ARCH_ABI STREQUAL "x86_64")
    set(SIZEOF_SIZE_T 8)
    set(WITH_SIMD 1)
elseif (CMAKE_ANDROID_ARCH_ABI STREQUAL "armeabi-v7a" OR CMAKE_ANDROID_ARCH_ABI STREQUAL "x86")
    set(SIZEOF_SIZE_T 4)
    set(WITH_SIMD 1)
endif ()

target_compile_definitions(jpeg-turbo2.1.2 PRIVATE BMP_SUPPORTED PPM_SUPPORTED)
target_compile_options(jpeg-turbo2.1.2 PRIVATE -Wno-incompatible-pointer-types)
set_target_properties(jpeg-turbo2.1.2 PROPERTIES POSITION_INDEPENDENT_CODE ON)
target_link_libraries(jpeg-turbo2.1.2 PRIVATE dl log)
target_link_options(jpeg-turbo2.1.2 PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
)

# libyuv库导入
add_library(libyuv SHARED
        libyuv/source/compare.cc
        libyuv/source/compare_common.cc
        libyuv/source/compare_gcc.cc
        libyuv/source/compare_msa.cc
        libyuv/source/compare_neon.cc
        libyuv/source/compare_neon64.cc
        libyuv/source/compare_win.cc
        libyuv/source/convert.cc
        libyuv/source/convert_argb.cc
        libyuv/source/convert_from.cc
        libyuv/source/convert_from_argb.cc
        libyuv/source/convert_to_argb.cc
        libyuv/source/convert_to_i420.cc
        libyuv/source/cpu_id.cc
        libyuv/source/planar_functions.cc
        libyuv/source/rotate.cc
        libyuv/source/rotate_any.cc
        libyuv/source/rotate_argb.cc
        libyuv/source/rotate_common.cc
        libyuv/source/rotate_gcc.cc
        libyuv/source/rotate_msa.cc
        libyuv/source/rotate_neon.cc
        libyuv/source/rotate_neon64.cc
        libyuv/source/rotate_win.cc
        libyuv/source/row_any.cc
        libyuv/source/row_common.cc
        libyuv/source/row_gcc.cc
        libyuv/source/row_msa.cc
        libyuv/source/row_neon.cc
        libyuv/source/row_neon64.cc
        libyuv/source/row_win.cc
        libyuv/source/scale.cc
        libyuv/source/scale_any.cc
        libyuv/source/scale_argb.cc
        libyuv/source/scale_common.cc
        libyuv/source/scale_gcc.cc
        libyuv/source/scale_msa.cc
        libyuv/source/scale_neon.cc
        libyuv/source/scale_neon64.cc
        libyuv/source/scale_rgb.cc
        libyuv/source/scale_uv.cc
        libyuv/source/scale_win.cc
        libyuv/source/video_common.cc
        libyuv/source/convert_jpeg.cc
        libyuv/source/mjpeg_decoder.cc
        libyuv/source/mjpeg_validate.cc
)

target_link_options(libyuv PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
)

target_link_libraries(libyuv PRIVATE
        android
        log
        jpeg-turbo2.1.2
)


# libuvc库导入
add_library(uvc0.6 SHARED
        libuvc/src/example.c
        libuvc/src/ctrl.c
        libuvc/src/ctrl-gen.c
        libuvc/src/device.c
        libuvc/src/diag.c
        libuvc/src/frame.c
        libuvc/src/init.c
        libuvc/src/stream.c
        libuvc/src/misc.c
        libuvc/src/frame-mjpeg.c
)

target_include_directories(uvc0.6 PRIVATE
        libuvc/include
        libuvc/include/libuvc
        libusb
        libusb/os
        libjpeg-turbo
        libjpeg-turbo/simd
        libjpeg-turbo/include
)
target_link_options(uvc0.6 PRIVATE
        "-Wl,-z,max-page-size=16384"
        "-Wl,-z,common-page-size=16384"
)
target_link_libraries(uvc0.6
        android
        log
        usb1.0.26
        jpeg-turbo2.1.2
        libyuv
)


# uvc预览库
add_library(uvc_camera SHARED
        uvc_camera_jni.cpp
        camera/camera_factory_helper.cpp
        camera/src/camera_device.cpp
        camera/src/camera_stream.cpp
)

target_link_directories(uvc_camera PRIVATE
        libuvc/include
        libuvc/include/libuvc
        camera/headers
        log
)

target_link_libraries(uvc_camera PRIVATE
        android
        log
        uvc0.6
        usb1.0.26
        jpeg-turbo2.1.2
)

