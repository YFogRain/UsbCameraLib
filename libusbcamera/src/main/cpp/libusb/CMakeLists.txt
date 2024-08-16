cmake_minimum_required(VERSION 3.22.1)

project(usb100)

add_library(usb100 SHARED
        core.c
        descriptor.c
        hotplug.c
        io.c
        sync.c
        strerror.c
        os/linux_usbfs.c
        os/events_posix.c
        os/threads_posix.c
        os/linux_netlink.c
)

target_include_directories(usb100 PRIVATE
        "${CMAKE_CURRENT_SOURCE_DIR}"
        "${CMAKE_CURRENT_SOURCE_DIR}/os"
)

target_link_libraries(usb100
            android
            log)