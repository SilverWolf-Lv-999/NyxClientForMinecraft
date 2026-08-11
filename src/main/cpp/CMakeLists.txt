cmake_minimum_required(VERSION 3.26)
project(sera_native LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED)

add_library(sera_native SHARED library.cpp)
target_include_directories(sera_native PRIVATE ${JNI_INCLUDE_DIRS})

if (WIN32)
    file(GLOB cppwinrtIncludeDirectories LIST_DIRECTORIES true
        "$ENV{WindowsSdkDir}/Include/*/cppwinrt"
        "C:/Program Files (x86)/Windows Kits/10/Include/*/cppwinrt"
    )
    list(SORT cppwinrtIncludeDirectories COMPARE NATURAL ORDER DESCENDING)

    if (cppwinrtIncludeDirectories)
        list(GET cppwinrtIncludeDirectories 0 cppwinrtIncludeDirectory)
        target_include_directories(sera_native PRIVATE "${cppwinrtIncludeDirectory}")
        target_compile_definitions(sera_native PRIVATE SERA_NATIVE_HAS_SMTC=1)
        target_link_libraries(sera_native PRIVATE windowsapp)
    else()
        message(WARNING "C++/WinRT headers were not found; SMTC support is disabled.")
    endif()
endif()
