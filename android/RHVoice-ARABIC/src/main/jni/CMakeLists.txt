cmake_minimum_required(VERSION 3.10.2)
project(rhvoice_android)

add_library(rhvoice_android SHARED
    native.cpp
)

target_include_directories(rhvoice_android PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}
    ${CMAKE_CURRENT_SOURCE_DIR}/generated
)

find_library(log-lib log)

target_link_libraries(rhvoice_android
    ${log-lib}
)
