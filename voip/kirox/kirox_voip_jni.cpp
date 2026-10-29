// JNI bridge between com.kirox.app.voip.NativeCall and the tgcalls engine.
// Written for KiroX; tgcalls itself is LGPL-3.0, WebRTC is BSD-3-Clause.

#include <atomic>
#include <memory>
#include <jni.h>

#include <algorithm>
#include <array>
#include <memory>
#include <mutex>
#include <string>
#include <vector>

#include "Instance.h"
#include "InstanceImpl.h"
#include "StaticThreads.h"
#include "VideoCaptureInterface.h"
#include "platform/android/AndroidContext.h"
#include "v2/InstanceV2Impl.h"
#include "v2/InstanceV2ReferenceImpl.h"

#include "modules/utility/include/jvm_android.h"
#include "sdk/android/native_api/base/init.h"
#include "sdk/android/native_api/jni/scoped_java_ref.h"
#include "sdk/android/native_api/video/wrapper.h"
#include "sdk/android/native_api/jni/jvm.h"

using namespace tgcalls;

namespace {

const auto kRegisterV1 = Register<InstanceImpl>();
const auto kRegisterV2 = Register<InstanceV2Impl>();
const auto kRegisterV2Reference = Register<InstanceV2ReferenceImpl>();

// States reported to Kotlin: keep in sync with NativeCall.
constexpr jint kStateConnecting = 0;
constexpr jint kStateEstablished = 1;
constexpr jint kStateFailed = 2;
constexpr jint kStateReconnecting = 3;

std::string toStd(JNIEnv *env, jstring value) {
    if (!value) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

std::vector<uint8_t> toBytes(JNIEnv *env, jbyteArray array) {
    std::vector<uint8_t> result;
    if (!array) return result;
    jsize size = env->GetArrayLength(array);
    result.resize(size);
    env->GetByteArrayRegion(array, 0, size, reinterpret_cast<jbyte *>(result.data()));
    return result;
}

jbyteArray toJava(JNIEnv *env, const std::vector<uint8_t> &data) {
    jbyteArray array = env->NewByteArray(static_cast<jsize>(data.size()));
    env->SetByteArrayRegion(array, 0, static_cast<jsize>(data.size()), reinterpret_cast<const jbyte *>(data.data()));
    return array;
}

/** Calls a void method on the Java NativeCall from any engine thread. */
void callJava(jobject target, const char *name, const char *signature, const std::function<void(JNIEnv *, jmethodID)> &invoke) {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
    jclass cls = env->GetObjectClass(target);
    jmethodID method = env->GetMethodID(cls, name, signature);
    env->DeleteLocalRef(cls);
    if (method) invoke(env, method);
    if (env->ExceptionCheck()) env->ExceptionClear();
}

/**
 * Global reference to the Java NativeCall, shared by the engine callbacks. The engine frees its
 * internals (and these callbacks) on its own threads some time after stop completes, so the
 * reference lives until the last callback is gone, and callbacks after stop do nothing.
 */
struct JavaRef {
    jobject ref = nullptr;
    std::atomic<bool> stopped{false};
    explicit JavaRef(JNIEnv *env, jobject object) : ref(env->NewGlobalRef(object)) {}
    ~JavaRef() {
        if (ref) webrtc::AttachCurrentThreadIfNeeded()->DeleteGlobalRef(ref);
    }
    JavaRef(const JavaRef &) = delete;
    JavaRef &operator=(const JavaRef &) = delete;
};

struct Call {
    std::shared_ptr<PlatformContext> context;
    std::shared_ptr<VideoCaptureInterface> capture;
    std::unique_ptr<Instance> instance;
    std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> remoteSink;
    std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>> localSink;
    std::shared_ptr<JavaRef> java;
    bool videoEnabled = false;
    std::string cameraId = "front";
    /** Camera the capturer really uses; differs from cameraId after a switch while the camera was off. */
    std::string capturedCameraId = "front";
};

Call *fromHandle(jlong handle) { return reinterpret_cast<Call *>(static_cast<intptr_t>(handle)); }

}  // namespace

extern "C" {

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *) {
    webrtc::InitAndroid(vm);
    webrtc::JVM::Initialize(vm);
    return JNI_VERSION_1_6;
}

JNIEXPORT jobjectArray JNICALL Java_com_kirox_app_voip_NativeCall_nativeVersions(JNIEnv *env, jclass) {
    std::vector<std::string> versions = Meta::Versions();
    std::sort(versions.begin(), versions.end(), [](const std::string &a, const std::string &b) {
        // Numeric, newest first: "13.0.0" before "9.0.0".
        int ma = std::atoi(a.c_str()), mb = std::atoi(b.c_str());
        return ma != mb ? ma > mb : a > b;
    });
    jobjectArray result = env->NewObjectArray(static_cast<jsize>(versions.size()), env->FindClass("java/lang/String"), nullptr);
    for (size_t i = 0; i < versions.size(); i++) env->SetObjectArrayElement(result, static_cast<jsize>(i), env->NewStringUTF(versions[i].c_str()));
    return result;
}

JNIEXPORT jint JNICALL Java_com_kirox_app_voip_NativeCall_nativeMaxLayer(JNIEnv *, jclass) {
    return Meta::MaxLayer();
}

JNIEXPORT jlong JNICALL Java_com_kirox_app_voip_NativeCall_nativeStart(
        JNIEnv *env, jobject thiz,
        jstring version, jbyteArray key, jboolean isOutgoing, jboolean allowP2p, jstring customParameters,
        jdouble initTimeout, jdouble receiveTimeout, jint maxLayer,
        jlongArray ids, jobjectArray ipv4s, jobjectArray ipv6s, jintArray ports, jbooleanArray isRtcs,
        jbooleanArray turns, jbooleanArray tcps, jobjectArray usernames, jobjectArray passwords,
        jintArray reflectorIds, jobjectArray peerTags,
        jboolean video, jboolean frontCamera) {
    auto *call = new Call();
    call->java = std::make_shared<JavaRef>(env, thiz);
    call->context = std::make_shared<AndroidContext>(env, thiz, nullptr, false);
    call->videoEnabled = video;
    call->cameraId = frontCamera ? "front" : "back";
    if (video) {
        call->capture = VideoCaptureInterface::Create(StaticThreads::getThreads(), call->cameraId, false, call->context);
        call->capturedCameraId = call->cameraId;
    }

    std::vector<uint8_t> keyBytes = toBytes(env, key);
    auto keyValue = std::make_shared<std::array<uint8_t, EncryptionKey::kSize>>();
    std::copy_n(keyBytes.begin(), std::min<size_t>(keyBytes.size(), EncryptionKey::kSize), keyValue->begin());

    std::shared_ptr<JavaRef> java = call->java;
    Descriptor descriptor = {
        .version = toStd(env, version),
        .config = Config{
            .initializationTimeout = initTimeout,
            .receiveTimeout = receiveTimeout,
            .dataSaving = DataSaving::Never,
            .enableP2P = static_cast<bool>(allowP2p),
            .enableAEC = true,
            .enableNS = true,
            .enableAGC = true,
            .enableVolumeControl = true,
            .maxApiLayer = maxLayer,
            .enableHighBitrateVideo = true,
            .protocolVersion = ProtocolVersion::V1,
            .customParameters = toStd(env, customParameters),
        },
        .encryptionKey = EncryptionKey(keyValue, isOutgoing),
        .videoCapture = call->capture,
        .stateUpdated = [java](State state) {
            if (java->stopped) return;
            jint value = kStateConnecting;
            switch (state) {
                case State::Established: value = kStateEstablished; break;
                case State::Failed: value = kStateFailed; break;
                case State::Reconnecting: value = kStateReconnecting; break;
                default: break;
            }
            callJava(java->ref, "onEngineState", "(I)V", [&](JNIEnv *e, jmethodID m) { e->CallVoidMethod(java->ref, m, value); });
        },
        .signalBarsUpdated = [java](int bars) {
            if (java->stopped) return;
            callJava(java->ref, "onSignalBars", "(I)V", [&](JNIEnv *e, jmethodID m) { e->CallVoidMethod(java->ref, m, static_cast<jint>(bars)); });
        },
        .remoteMediaStateUpdated = [java](AudioState audio, VideoState videoState) {
            if (java->stopped) return;
            callJava(java->ref, "onRemoteMedia", "(ZI)V", [&](JNIEnv *e, jmethodID m) {
                e->CallVoidMethod(java->ref, m, static_cast<jboolean>(audio == AudioState::Muted), static_cast<jint>(videoState));
            });
        },
        .signalingDataEmitted = [java](const std::vector<uint8_t> &data) {
            if (java->stopped) return;
            callJava(java->ref, "onSignalingData", "([B)V", [&](JNIEnv *e, jmethodID m) {
                jbyteArray array = toJava(e, data);
                e->CallVoidMethod(java->ref, m, array);
                e->DeleteLocalRef(array);
            });
        },
    };

    jsize count = env->GetArrayLength(ids);
    std::vector<jlong> idValues(count);
    std::vector<jint> portValues(count), reflectorValues(count);
    std::vector<jboolean> rtcValues(count), turnValues(count), tcpValues(count);
    env->GetLongArrayRegion(ids, 0, count, idValues.data());
    env->GetIntArrayRegion(ports, 0, count, portValues.data());
    env->GetIntArrayRegion(reflectorIds, 0, count, reflectorValues.data());
    env->GetBooleanArrayRegion(isRtcs, 0, count, rtcValues.data());
    env->GetBooleanArrayRegion(turns, 0, count, turnValues.data());
    env->GetBooleanArrayRegion(tcps, 0, count, tcpValues.data());
    for (jsize i = 0; i < count; i++) {
        auto string = [&](jobjectArray array) {
            auto value = static_cast<jstring>(env->GetObjectArrayElement(array, i));
            std::string result = toStd(env, value);
            if (value) env->DeleteLocalRef(value);
            return result;
        };
        RtcServer server;
        server.id = static_cast<uint8_t>(reflectorValues[i]);
        server.host = string(ipv4s);
        server.port = static_cast<uint16_t>(portValues[i]);
        server.login = string(usernames);
        server.password = string(passwords);
        if (rtcValues[i]) {
            server.isTurn = turnValues[i];
            descriptor.rtcServers.push_back(server);
            continue;
        }
        // Telegram reflector: a relay for the v2 engines and a classic endpoint for v1.
        server.isTurn = true;
        server.isTcp = tcpValues[i];
        descriptor.rtcServers.push_back(server);

        Endpoint endpoint;
        endpoint.endpointId = idValues[i];
        endpoint.host = EndpointHost{server.host, string(ipv6s)};
        endpoint.port = server.port;
        endpoint.type = tcpValues[i] ? EndpointType::TcpRelay : EndpointType::UdpRelay;
        auto tag = static_cast<jbyteArray>(env->GetObjectArrayElement(peerTags, i));
        std::vector<uint8_t> tagBytes = toBytes(env, tag);
        if (tag) env->DeleteLocalRef(tag);
        std::copy_n(tagBytes.begin(), std::min<size_t>(tagBytes.size(), 16), endpoint.peerTag);
        descriptor.endpoints.push_back(endpoint);
    }

    call->instance = Meta::Create(descriptor.version, std::move(descriptor));
    if (!call->instance) {
        delete call;
        return 0;
    }
    call->instance->setNetworkType(NetworkType::WiFi);
    return static_cast<jlong>(reinterpret_cast<intptr_t>(call));
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeCall_nativeReceiveSignaling(JNIEnv *env, jobject, jlong handle, jbyteArray data) {
    Call *call = fromHandle(handle);
    if (call && call->instance) call->instance->receiveSignalingData(toBytes(env, data));
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeCall_nativeSetMuted(JNIEnv *, jobject, jlong handle, jboolean muted) {
    Call *call = fromHandle(handle);
    if (call && call->instance) call->instance->setMuteMicrophone(muted);
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeCall_nativeSetVideo(JNIEnv *, jobject, jlong handle, jboolean enabled) {
    Call *call = fromHandle(handle);
    if (!call || !call->instance) return;
    if (enabled && !call->capture) {
        call->capture = VideoCaptureInterface::Create(StaticThreads::getThreads(), call->cameraId, false, call->context);
        call->capturedCameraId = call->cameraId;
        if (call->localSink) call->capture->setOutput(call->localSink);
        call->instance->setVideoCapture(call->capture);
    }
    if (call->capture) call->capture->setState(enabled ? VideoState::Active : VideoState::Inactive);
    // The camera was flipped while off: the running capturer could not switch then.
    if (enabled && call->capture && call->capturedCameraId != call->cameraId) {
        call->capture->switchToDevice(call->cameraId, false);
        call->capturedCameraId = call->cameraId;
    }
    call->videoEnabled = enabled;
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeCall_nativeSwitchCamera(JNIEnv *, jobject, jlong handle, jboolean front) {
    Call *call = fromHandle(handle);
    if (!call) return;
    call->cameraId = front ? "front" : "back";
    // Only a running camera can switch; otherwise it switches when turned on.
    if (call->capture && call->videoEnabled) {
        call->capture->switchToDevice(call->cameraId, false);
        call->capturedCameraId = call->cameraId;
    }
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeCall_nativeSetSinks(JNIEnv *env, jobject, jlong handle, jobject remoteSink, jobject localSink) {
    Call *call = fromHandle(handle);
    if (!call || !call->instance) return;
    call->remoteSink = remoteSink ? std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>(webrtc::JavaToNativeVideoSink(env, remoteSink).release()) : nullptr;
    call->localSink = localSink ? std::shared_ptr<rtc::VideoSinkInterface<webrtc::VideoFrame>>(webrtc::JavaToNativeVideoSink(env, localSink).release()) : nullptr;
    call->instance->setIncomingVideoOutput(call->remoteSink);
    if (call->capture) call->capture->setOutput(call->localSink);
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeCall_nativeStop(JNIEnv *env, jobject, jlong handle) {
    Call *call = fromHandle(handle);
    if (!call) return;
    if (call->capture) call->capture->setState(VideoState::Inactive);
    std::shared_ptr<Instance> instance(std::move(call->instance));
    std::shared_ptr<JavaRef> java = call->java;
    // From now on engine callbacks are ignored; only onStopped below reaches Java.
    java->stopped = true;
    auto keepAlive = std::make_shared<Call *>(call);
    if (instance) {
        instance->stop([instance, java, keepAlive](FinalState) {
            Call *finished = *keepAlive;
            callJava(java->ref, "onStopped", "()V", [&](JNIEnv *ee, jmethodID m) { ee->CallVoidMethod(java->ref, m); });
            finished->capture.reset();
            finished->context.reset();
            // The reference itself goes when the engine has dropped its callbacks too.
            delete finished;
        });
    } else {
        delete call;
    }
}

}  // extern "C"
