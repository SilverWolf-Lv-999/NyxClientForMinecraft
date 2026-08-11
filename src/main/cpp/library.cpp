#include "library.h"

#include <jni.h>
#include <array>
#include <chrono>
#include <cstdint>
#include <string>
#include <string_view>
#include <utility>

#if defined(_WIN32)
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#if defined(SERA_NATIVE_HAS_SMTC)
#include <roapi.h>
#include <winrt/Windows.Foundation.h>
#include <winrt/Windows.Media.Control.h>
#endif

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

#if defined(SERA_NATIVE_HAS_SMTC)
struct SmtcSnapshot {
    bool hasActiveSession;
    std::string title;
    std::string artist;
    std::int64_t positionMilliseconds;
    std::int64_t durationMilliseconds;
    jint playbackStatus;
    std::string sourceAppId;
    std::string diagnostic;
};

SRWLOCK smtcManagerLock = SRWLOCK_INIT;
winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSessionManager smtcManager{nullptr};

class SmtcManagerLock {
public:
    SmtcManagerLock() {
        AcquireSRWLockExclusive(&smtcManagerLock);
    }

    ~SmtcManagerLock() {
        ReleaseSRWLockExclusive(&smtcManagerLock);
    }
};

class WinRtApartment {
public:
    WinRtApartment() {
        const HRESULT result = RoInitialize(RO_INIT_MULTITHREADED);
        thisInitialized = SUCCEEDED(result);
        usable = thisInitialized || result == RPC_E_CHANGED_MODE;
    }

    ~WinRtApartment() {
        if (thisInitialized) {
            RoUninitialize();
        }
    }

    bool isUsable() const {
        return usable;
    }

private:
    bool thisInitialized = false;
    bool usable = false;
};

winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSessionManager get_smtc_manager() {
    SmtcManagerLock lock;
    if (smtcManager) {
        return smtcManager;
    }

    smtcManager = winrt::Windows::Media::Control::GlobalSystemMediaTransportControlsSessionManager::RequestAsync().get();
    return smtcManager;
}

SmtcSnapshot smtc_inactive_snapshot(std::string diagnostic) {
    return SmtcSnapshot {
        .hasActiveSession = false,
        .title = "",
        .artist = "",
        .positionMilliseconds = 0L,
        .durationMilliseconds = 0L,
        .playbackStatus = 0,
        .sourceAppId = "",
        .diagnostic = std::move(diagnostic)
    };
}

std::string smtc_hresult_diagnostic(const winrt::hresult_error& error) {
    return "SMTC WinRT error " + std::to_string(error.code().value);
}

SmtcSnapshot get_smtc_snapshot() {
    WinRtApartment apartment;
    if (!apartment.isUsable()) {
        return smtc_inactive_snapshot("SMTC WinRT apartment is unavailable");
    }

    try {
        const auto manager = get_smtc_manager();
        if (!manager) {
            return smtc_inactive_snapshot("SMTC session manager is unavailable");
        }
        const auto session = manager.GetCurrentSession();
        if (!session) {
            return smtc_inactive_snapshot("No active SMTC session");
        }

        const auto properties = session.TryGetMediaPropertiesAsync().get();
        const auto timeline = session.GetTimelineProperties();
        const auto playback = session.GetPlaybackInfo();

        return SmtcSnapshot {
            .hasActiveSession = true,
            .title = winrt::to_string(properties.Title()),
            .artist = winrt::to_string(properties.Artist()),
            .positionMilliseconds = std::chrono::duration_cast<std::chrono::milliseconds>(
                timeline.Position()
            ).count(),
            .durationMilliseconds = std::chrono::duration_cast<std::chrono::milliseconds>(
                timeline.EndTime()
            ).count(),
            .playbackStatus = static_cast<jint>(playback.PlaybackStatus()),
            .sourceAppId = winrt::to_string(session.SourceAppUserModelId()),
            .diagnostic = ""
        };
    } catch (const winrt::hresult_error& error) {
        return smtc_inactive_snapshot(smtc_hresult_diagnostic(error));
    } catch (...) {
        return smtc_inactive_snapshot("SMTC native query failed");
    }
}

jstring new_java_string(JNIEnv* environment, std::string_view value) {
    const auto length = static_cast<jsize>(value.size());
    jbyteArray bytes = environment->NewByteArray(length);
    if (bytes == nullptr) {
        return nullptr;
    }

    environment->SetByteArrayRegion(
        bytes,
        0,
        length,
        reinterpret_cast<const jbyte*>(value.data())
    );
    if (environment->ExceptionCheck()) {
        environment->DeleteLocalRef(bytes);
        return nullptr;
    }

    jclass stringClass = environment->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        environment->DeleteLocalRef(bytes);
        return nullptr;
    }

    jmethodID constructor = environment->GetMethodID(
        stringClass,
        "<init>",
        "([BLjava/lang/String;)V"
    );
    jstring charset = environment->NewStringUTF("UTF-8");
    jstring result = nullptr;
    if (constructor != nullptr && charset != nullptr) {
        result = static_cast<jstring>(environment->NewObject(
            stringClass,
            constructor,
            bytes,
            charset
        ));
    }

    if (charset != nullptr) {
        environment->DeleteLocalRef(charset);
    }
    environment->DeleteLocalRef(stringClass);
    environment->DeleteLocalRef(bytes);
    return result;
}

jobjectArray new_java_smtc_snapshot(JNIEnv* environment, const SmtcSnapshot& snapshot) {
    jclass stringClass = environment->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        return nullptr;
    }

    jobjectArray result = environment->NewObjectArray(8, stringClass, nullptr);
    environment->DeleteLocalRef(stringClass);
    if (result == nullptr) {
        return nullptr;
    }

    const std::array<std::string, 8> values {
        snapshot.title,
        snapshot.artist,
        std::to_string(snapshot.positionMilliseconds),
        std::to_string(snapshot.durationMilliseconds),
        std::to_string(snapshot.playbackStatus),
        snapshot.sourceAppId,
        snapshot.hasActiveSession ? "1" : "0",
        snapshot.diagnostic
    };
    for (jsize index = 0; index < static_cast<jsize>(values.size()); ++index) {
        jstring value = new_java_string(environment, values[index]);
        if (value == nullptr) {
            environment->DeleteLocalRef(result);
            return nullptr;
        }

        environment->SetObjectArrayElement(result, index, value);
        environment->DeleteLocalRef(value);
        if (environment->ExceptionCheck()) {
            environment->DeleteLocalRef(result);
            return nullptr;
        }
    }

    return result;
}
#endif
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

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_seraphina_nyx_client_utility_SeraNative_nativeIsSmtcSupported(JNIEnv*, jclass) {
#if defined(_WIN32) && defined(SERA_NATIVE_HAS_SMTC)
    return JNI_TRUE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_seraphina_nyx_client_utility_SeraNative_nativeGetSmtcSnapshot(JNIEnv* environment, jclass) {
#if defined(_WIN32) && defined(SERA_NATIVE_HAS_SMTC)
    return new_java_smtc_snapshot(environment, get_smtc_snapshot());
#else
    return nullptr;
#endif
}
