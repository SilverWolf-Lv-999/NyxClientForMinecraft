#include "library.h"

#include <jni.h>
#include <cstdint>

#if defined(_WIN32)
#define WIN32_LEAN_AND_MEAN
#include <windows.h>

extern "C" {
__declspec(dllexport) std::uint32_t NvOptimusEnablement = 0x00000001;
__declspec(dllexport) int AmdPowerXpressRequestHighPerformance = 1;
}

namespace {
constexpr jint NATIVE_HINT_WINDOWS = 1;
constexpr jint NATIVE_HINT_NVAPI_LOADED = 1 << 1;
constexpr jint NATIVE_HINT_NVCUDA_LOADED = 1 << 2;

bool load_driver_library(const wchar_t* name) {
    HMODULE module = LoadLibraryW(name);
    return module != nullptr;
}
}
#endif

extern "C" JNIEXPORT jint JNICALL
Java_io_github_seraphina_nyx_client_utility_SeraNative_nativeProbe(JNIEnv*, jclass) {
    return SERA_NATIVE_PROBE_MAGIC;
}

extern "C" JNIEXPORT jint JNICALL
Java_io_github_seraphina_nyx_client_utility_SeraNative_nativeRequestHighPerformanceGpu(JNIEnv*, jclass) {
#if defined(_WIN32)
    jint flags = NATIVE_HINT_WINDOWS;
    if (load_driver_library(L"nvapi64.dll") || load_driver_library(L"nvapi.dll")) {
        flags |= NATIVE_HINT_NVAPI_LOADED;
    }
    if (load_driver_library(L"nvcuda.dll")) {
        flags |= NATIVE_HINT_NVCUDA_LOADED;
    }
    return flags;
#else
    return 0;
#endif
}
