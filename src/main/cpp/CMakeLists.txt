cmake_minimum_required(VERSION 3.26)
project(sera_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)

add_library(sera_native SHARED library.cpp)
target_include_directories(sera_native PRIVATE ${JNI_INCLUDE_DIRS})
