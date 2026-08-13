#include "library.h"

#include <jni.h>
#include <algorithm>
#include <array>
#include <chrono>
#include <cstdint>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#if defined(_WIN32)
#define WIN32_LEAN_AND_MEAN
#include <winsock2.h>
#include <windows.h>
#include <netioapi.h>
#include <windns.h>
#include <ws2tcpip.h>
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

JavaVM* thisJavaVm = nullptr;
SRWLOCK networkMonitorLock = SRWLOCK_INIT;
jclass seraNativeClass = nullptr;
jmethodID networkChangedMethod = nullptr;
HANDLE ipInterfaceChangeNotificationHandle = nullptr;
HANDLE unicastIpAddressChangeNotificationHandle = nullptr;

bool load_driver_library(const wchar_t* name) {
    HMODULE module = LoadLibraryW(name);
    return module != nullptr;
}

void publish_network_changed() {
    if (thisJavaVm == nullptr || seraNativeClass == nullptr || networkChangedMethod == nullptr) {
        return;
    }

    JNIEnv* environment = nullptr;
    bool attached = false;
    const jint state = thisJavaVm->GetEnv(reinterpret_cast<void**>(&environment), JNI_VERSION_1_8);
    if (state == JNI_EDETACHED) {
        if (thisJavaVm->AttachCurrentThread(reinterpret_cast<void**>(&environment), nullptr) != JNI_OK) {
            return;
        }
        attached = true;
    } else if (state != JNI_OK) {
        return;
    }

    environment->CallStaticVoidMethod(seraNativeClass, networkChangedMethod);
    if (environment->ExceptionCheck()) {
        environment->ExceptionClear();
    }
    if (attached) {
        thisJavaVm->DetachCurrentThread();
    }
}

void CALLBACK on_ip_interface_changed(
    PVOID,
    PMIB_IPINTERFACE_ROW,
    MIB_NOTIFICATION_TYPE
) {
    publish_network_changed();
}

void CALLBACK on_unicast_ip_address_changed(
    PVOID,
    PMIB_UNICASTIPADDRESS_ROW,
    MIB_NOTIFICATION_TYPE
) {
    publish_network_changed();
}

bool start_network_change_monitor(JNIEnv* environment, jclass nativeClass) {
    AcquireSRWLockExclusive(&networkMonitorLock);
    if (ipInterfaceChangeNotificationHandle != nullptr
        || unicastIpAddressChangeNotificationHandle != nullptr) {
        ReleaseSRWLockExclusive(&networkMonitorLock);
        return true;
    }

    jclass classReference = static_cast<jclass>(environment->NewGlobalRef(nativeClass));
    if (classReference == nullptr) {
        ReleaseSRWLockExclusive(&networkMonitorLock);
        return false;
    }

    jmethodID method = environment->GetStaticMethodID(
        classReference,
        "onNativeNetworkChanged",
        "()V"
    );
    if (method == nullptr || environment->ExceptionCheck()) {
        environment->ExceptionClear();
        environment->DeleteGlobalRef(classReference);
        ReleaseSRWLockExclusive(&networkMonitorLock);
        return false;
    }

    seraNativeClass = classReference;
    networkChangedMethod = method;

    HANDLE interfaceNotificationHandle = nullptr;
    const NETIO_STATUS interfaceStatus = NotifyIpInterfaceChange(
        AF_UNSPEC,
        on_ip_interface_changed,
        nullptr,
        FALSE,
        &interfaceNotificationHandle
    );

    HANDLE addressNotificationHandle = nullptr;
    const NETIO_STATUS addressStatus = NotifyUnicastIpAddressChange(
        AF_UNSPEC,
        on_unicast_ip_address_changed,
        nullptr,
        FALSE,
        &addressNotificationHandle
    );
    if ((interfaceStatus != NO_ERROR || interfaceNotificationHandle == nullptr)
        && (addressStatus != NO_ERROR || addressNotificationHandle == nullptr)) {
        seraNativeClass = nullptr;
        networkChangedMethod = nullptr;
        environment->DeleteGlobalRef(classReference);
        ReleaseSRWLockExclusive(&networkMonitorLock);
        return false;
    }

    ipInterfaceChangeNotificationHandle = interfaceNotificationHandle;
    unicastIpAddressChangeNotificationHandle = addressNotificationHandle;
    ReleaseSRWLockExclusive(&networkMonitorLock);
    return true;
}

bool flush_dns_resolver_cache() {
    using DnsFlushResolverCacheFunction = BOOL(WINAPI*)();

    HMODULE module = GetModuleHandleW(L"dnsapi.dll");
    bool unloadModule = false;
    if (module == nullptr) {
        module = LoadLibraryW(L"dnsapi.dll");
        unloadModule = module != nullptr;
    }
    if (module == nullptr) {
        return false;
    }

    auto function = reinterpret_cast<DnsFlushResolverCacheFunction>(
        GetProcAddress(module, "DnsFlushResolverCache")
    );
    const bool result = function != nullptr && function();
    if (unloadModule) {
        FreeLibrary(module);
    }
    return result;
}

std::wstring java_string_to_wstring(JNIEnv* environment, jstring value) {
    if (value == nullptr) {
        return {};
    }

    const jsize length = environment->GetStringLength(value);
    const jchar* characters = environment->GetStringChars(value, nullptr);
    if (characters == nullptr) {
        return {};
    }

    std::wstring result;
    result.reserve(length);
    for (jsize index = 0; index < length; ++index) {
        result.push_back(static_cast<wchar_t>(characters[index]));
    }
    environment->ReleaseStringChars(value, characters);
    return result;
}

void append_dns_address(std::vector<std::string>& addresses, int family, const void* address) {
    std::array<wchar_t, INET6_ADDRSTRLEN> buffer {};
    if (InetNtopW(family, const_cast<void*>(address), buffer.data(), static_cast<DWORD>(buffer.size())) == nullptr) {
        return;
    }

    const int length = WideCharToMultiByte(
        CP_UTF8,
        0,
        buffer.data(),
        -1,
        nullptr,
        0,
        nullptr,
        nullptr
    );
    if (length <= 1) {
        return;
    }

    std::string result(static_cast<std::size_t>(length), '\0');
    WideCharToMultiByte(
        CP_UTF8,
        0,
        buffer.data(),
        -1,
        result.data(),
        length,
        nullptr,
        nullptr
    );
    result.pop_back();
    if (std::find(addresses.begin(), addresses.end(), result) == addresses.end()) {
        addresses.push_back(std::move(result));
    }
}

void append_dns_records(std::vector<std::string>& addresses, const std::wstring& hostname, WORD type) {
    PDNS_RECORD records = nullptr;
    const DNS_STATUS status = DnsQuery_W(
        hostname.c_str(),
        type,
        DNS_QUERY_BYPASS_CACHE,
        nullptr,
        &records,
        nullptr
    );
    if (status != ERROR_SUCCESS || records == nullptr) {
        return;
    }

    for (PDNS_RECORD record = records; record != nullptr; record = record->pNext) {
        if (record->wType == DNS_TYPE_A) {
            append_dns_address(addresses, AF_INET, &record->Data.A.IpAddress);
        } else if (record->wType == DNS_TYPE_AAAA) {
            append_dns_address(addresses, AF_INET6, record->Data.AAAA.Ip6Address.IP6Byte);
        }
    }
    DnsRecordListFree(records, DnsFreeRecordList);
}

jobjectArray new_java_string_array(JNIEnv* environment, const std::vector<std::string>& values) {
    jclass stringClass = environment->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        return nullptr;
    }

    jobjectArray result = environment->NewObjectArray(
        static_cast<jsize>(values.size()),
        stringClass,
        nullptr
    );
    environment->DeleteLocalRef(stringClass);
    if (result == nullptr) {
        return nullptr;
    }

    for (jsize index = 0; index < static_cast<jsize>(values.size()); ++index) {
        jstring value = environment->NewStringUTF(values[index].c_str());
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

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* javaVm, void*) {
#if defined(_WIN32)
    thisJavaVm = javaVm;
#endif
    return JNI_VERSION_1_8;
}

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

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_seraphina_nyx_client_utility_SeraNative_nativeFlushDnsResolverCache(JNIEnv*, jclass) {
#if defined(_WIN32)
    return flush_dns_resolver_cache() ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}

extern "C" JNIEXPORT jobjectArray JNICALL
Java_io_github_seraphina_nyx_client_utility_SeraNative_nativeResolveHostnameWithoutCache(JNIEnv* environment, jclass, jstring hostname) {
#if defined(_WIN32)
    const std::wstring nativeHostname = java_string_to_wstring(environment, hostname);
    if (nativeHostname.empty()) {
        return nullptr;
    }

    std::vector<std::string> addresses;
    append_dns_records(addresses, nativeHostname, DNS_TYPE_A);
    append_dns_records(addresses, nativeHostname, DNS_TYPE_AAAA);
    return new_java_string_array(environment, addresses);
#else
    return nullptr;
#endif
}

extern "C" JNIEXPORT jboolean JNICALL
Java_io_github_seraphina_nyx_client_utility_SeraNative_nativeStartNetworkChangeMonitor(JNIEnv* environment, jclass nativeClass) {
#if defined(_WIN32)
    return start_network_change_monitor(environment, nativeClass) ? JNI_TRUE : JNI_FALSE;
#else
    return JNI_FALSE;
#endif
}
