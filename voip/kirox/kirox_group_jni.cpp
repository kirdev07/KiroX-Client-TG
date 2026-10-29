// JNI bridge between com.kirox.app.voip.NativeGroupCall and tgcalls group calls (video chats,
// voice part). Written for KiroX; tgcalls itself is LGPL-3.0, WebRTC is BSD-3-Clause.
//
// Flow: start → the engine emits a join payload (our audio SSRC + JSON) → Kotlin sends it to
// Telegram with JoinVideoChat → Telegram's answer comes back through setJoinResponse, which
// connects the engine over RTC. Incoming audio streams are described by their SSRC alone;
// video streams by the participant's endpoint and source groups, which Kotlin passes from TDLib.
// The camera is the same VideoCapturerDevice the 1:1 calls use.

#include <jni.h>

#include <functional>
#include <map>
#include <memory>
#include <sstream>
#include <string>
#include <vector>

#include "StaticThreads.h"
#include "VideoCaptureInterface.h"
#include "group/GroupInstanceCustomImpl.h"
#include "platform/android/AndroidContext.h"

#include "sdk/android/native_api/jni/jvm.h"
#include "sdk/android/native_api/video/wrapper.h"

using namespace tgcalls;

namespace {

/** Media channel descriptions are answered at once, so there is nothing to cancel. */
class ImmediateDescriptionTask final : public RequestMediaChannelDescriptionTask {
public:
    void cancel() override {}
};

using VideoSink = rtc::VideoSinkInterface<webrtc::VideoFrame>;

/**
 * Global reference to the Java NativeGroupCall, shared by every engine callback. Engine tasks can
 * still run after stop() completes, so the reference lives until the last callback is destroyed.
 */
struct JavaRef {
    jobject ref = nullptr;
    explicit JavaRef(JNIEnv *env, jobject object) : ref(env->NewGlobalRef(object)) {}
    ~JavaRef() {
        if (ref) webrtc::AttachCurrentThreadIfNeeded()->DeleteGlobalRef(ref);
    }
    JavaRef(const JavaRef &) = delete;
    JavaRef &operator=(const JavaRef &) = delete;
};

struct GroupCall {
    std::unique_ptr<GroupInstanceCustomImpl> instance;
    std::shared_ptr<PlatformContext> context;
    std::shared_ptr<VideoCaptureInterface> capture;
    std::shared_ptr<VideoSink> localSink;
    /** Renderers of other participants by endpoint; the engine only keeps weak references. */
    std::map<std::string, std::shared_ptr<VideoSink>> remoteSinks;
    std::string cameraId = "front";
    std::shared_ptr<JavaRef> java;
};

/** "SIM:1,2,3;FID:1,4" → source groups, as TDLib's GroupCallVideoSourceGroup lists them. */
std::vector<MediaSsrcGroup> parseGroups(const std::string &encoded) {
    std::vector<MediaSsrcGroup> result;
    std::stringstream groups(encoded);
    std::string group;
    while (std::getline(groups, group, ';')) {
        auto colon = group.find(':');
        if (colon == std::string::npos) continue;
        MediaSsrcGroup item;
        item.semantics = group.substr(0, colon);
        std::stringstream ids(group.substr(colon + 1));
        std::string id;
        while (std::getline(ids, id, ',')) {
            if (!id.empty()) item.ssrcs.push_back(static_cast<uint32_t>(std::stoll(id)));
        }
        if (!item.ssrcs.empty()) result.push_back(std::move(item));
    }
    return result;
}

GroupCall *fromHandle(jlong handle) { return reinterpret_cast<GroupCall *>(static_cast<intptr_t>(handle)); }

std::string toStd(JNIEnv *env, jstring value) {
    if (!value) return {};
    const char *chars = env->GetStringUTFChars(value, nullptr);
    std::string result(chars ? chars : "");
    if (chars) env->ReleaseStringUTFChars(value, chars);
    return result;
}

/** Calls a void method on the Java NativeGroupCall from any engine thread. */
void callJava(jobject target, const char *name, const char *signature, const std::function<void(JNIEnv *, jmethodID)> &invoke) {
    JNIEnv *env = webrtc::AttachCurrentThreadIfNeeded();
    jclass cls = env->GetObjectClass(target);
    jmethodID method = env->GetMethodID(cls, name, signature);
    env->DeleteLocalRef(cls);
    if (method) invoke(env, method);
    if (env->ExceptionCheck()) env->ExceptionClear();
}

}  // namespace

extern "C" {

JNIEXPORT jlong JNICALL Java_com_kirox_app_voip_NativeGroupCall_nativeStart(JNIEnv *env, jobject thiz) {
    auto *call = new GroupCall();
    call->java = std::make_shared<JavaRef>(env, thiz);
    call->context = std::make_shared<AndroidContext>(env, nullptr, thiz, false);
    std::shared_ptr<JavaRef> java = call->java;

    GroupInstanceDescriptor descriptor;
    descriptor.threads = StaticThreads::getThreads();
    descriptor.config.need_log = false;
    // Announces video in the join payload, so the camera can be turned on later without rejoining.
    descriptor.videoContentType = VideoContentType::Generic;
    descriptor.networkStateUpdated = [java](GroupNetworkState state) {
        callJava(java->ref, "onNetwork", "(Z)V", [&](JNIEnv *e, jmethodID m) { e->CallVoidMethod(java->ref, m, static_cast<jboolean>(state.isConnected)); });
    };
    descriptor.audioLevelsUpdated = [java](GroupLevelsUpdate const &update) {
        // SSRC 0 is this device; levels are reported only while someone speaks.
        std::vector<jint> ssrcs;
        std::vector<jfloat> levels;
        for (auto const &item : update.updates) {
            ssrcs.push_back(static_cast<jint>(item.ssrc));
            levels.push_back(item.value.voice ? item.value.level : 0.0f);
        }
        callJava(java->ref, "onLevels", "([I[F)V", [&](JNIEnv *e, jmethodID m) {
            auto size = static_cast<jsize>(ssrcs.size());
            jintArray ssrcArray = e->NewIntArray(size);
            jfloatArray levelArray = e->NewFloatArray(size);
            e->SetIntArrayRegion(ssrcArray, 0, size, ssrcs.data());
            e->SetFloatArrayRegion(levelArray, 0, size, levels.data());
            e->CallVoidMethod(java->ref, m, ssrcArray, levelArray);
            e->DeleteLocalRef(ssrcArray);
            e->DeleteLocalRef(levelArray);
        });
    };
    descriptor.requestMediaChannelDescriptions = [](std::vector<uint32_t> const &ssrcs, std::function<void(std::vector<MediaChannelDescription> &&)> done) -> std::shared_ptr<RequestMediaChannelDescriptionTask> {
        std::vector<MediaChannelDescription> result;
        for (auto ssrc : ssrcs) {
            MediaChannelDescription description;
            description.type = MediaChannelDescription::Type::Audio;
            description.audioSsrc = ssrc;
            result.push_back(description);
        }
        done(std::move(result));
        return std::make_shared<ImmediateDescriptionTask>();
    };

    call->instance = std::make_unique<GroupInstanceCustomImpl>(std::move(descriptor));
    call->instance->emitJoinPayload([java](GroupJoinPayload const &payload) {
        callJava(java->ref, "onJoinPayload", "(ILjava/lang/String;)V", [&](JNIEnv *e, jmethodID m) {
            jstring json = e->NewStringUTF(payload.json.c_str());
            e->CallVoidMethod(java->ref, m, static_cast<jint>(payload.audioSsrc), json);
            e->DeleteLocalRef(json);
        });
    });
    return static_cast<jlong>(reinterpret_cast<intptr_t>(call));
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeGroupCall_nativeSetJoinResponse(JNIEnv *env, jobject, jlong handle, jstring payload) {
    GroupCall *call = fromHandle(handle);
    if (!call || !call->instance) return;
    call->instance->setJoinResponsePayload(toStd(env, payload));
    call->instance->setConnectionMode(GroupConnectionMode::GroupConnectionModeRtc, false, false);
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeGroupCall_nativeSetMuted(JNIEnv *, jobject, jlong handle, jboolean muted) {
    GroupCall *call = fromHandle(handle);
    if (call && call->instance) call->instance->setIsMuted(muted);
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeGroupCall_nativeSetVideo(JNIEnv *, jobject, jlong handle, jboolean enabled, jboolean front) {
    GroupCall *call = fromHandle(handle);
    if (!call || !call->instance) return;
    std::string cameraId = front ? "front" : "back";
    if (enabled && !call->capture) {
        call->cameraId = cameraId;
        call->capture = VideoCaptureInterface::Create(StaticThreads::getThreads(), call->cameraId, false, call->context);
        if (call->localSink) call->capture->setOutput(call->localSink);
        call->instance->setVideoCapture(call->capture);
    } else if (call->capture && cameraId != call->cameraId) {
        call->cameraId = cameraId;
        call->capture->switchToDevice(call->cameraId, false);
    }
    // Turning the camera off only stops capturing; the source stays attached, as in 1:1 calls.
    if (call->capture) call->capture->setState(enabled ? VideoState::Active : VideoState::Inactive);
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeGroupCall_nativeSetLocalSink(JNIEnv *env, jobject, jlong handle, jobject sink) {
    GroupCall *call = fromHandle(handle);
    if (!call || !call->instance) return;
    call->localSink = sink ? std::shared_ptr<VideoSink>(webrtc::JavaToNativeVideoSink(env, sink).release()) : nullptr;
    if (call->capture) call->capture->setOutput(call->localSink);
}

/** Video streams to receive: one entry per participant with video. */
JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeGroupCall_nativeSetVideoChannels(
        JNIEnv *env, jobject, jlong handle, jintArray audioSsrcs, jobjectArray endpoints, jobjectArray groups, jboolean full) {
    GroupCall *call = fromHandle(handle);
    if (!call || !call->instance) return;
    jsize count = env->GetArrayLength(audioSsrcs);
    std::vector<jint> ssrcs(count);
    env->GetIntArrayRegion(audioSsrcs, 0, count, ssrcs.data());
    std::vector<VideoChannelDescription> channels;
    for (jsize i = 0; i < count; i++) {
        auto endpoint = static_cast<jstring>(env->GetObjectArrayElement(endpoints, i));
        auto encoded = static_cast<jstring>(env->GetObjectArrayElement(groups, i));
        VideoChannelDescription channel;
        channel.audioSsrc = static_cast<uint32_t>(ssrcs[i]);
        channel.endpointId = toStd(env, endpoint);
        channel.ssrcGroups = parseGroups(toStd(env, encoded));
        channel.minQuality = VideoChannelDescription::Quality::Thumbnail;
        // Full quality only for a single stream on screen; a grid gets medium.
        channel.maxQuality = full ? VideoChannelDescription::Quality::Full : VideoChannelDescription::Quality::Medium;
        if (endpoint) env->DeleteLocalRef(endpoint);
        if (encoded) env->DeleteLocalRef(encoded);
        if (!channel.endpointId.empty() && !channel.ssrcGroups.empty()) channels.push_back(std::move(channel));
    }
    call->instance->setRequestedVideoChannels(std::move(channels));
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeGroupCall_nativeSetRemoteSink(JNIEnv *env, jobject, jlong handle, jstring endpoint, jobject sink) {
    GroupCall *call = fromHandle(handle);
    if (!call || !call->instance) return;
    std::string id = toStd(env, endpoint);
    if (!sink) { call->remoteSinks.erase(id); return; }
    auto shared = std::shared_ptr<VideoSink>(webrtc::JavaToNativeVideoSink(env, sink).release());
    call->remoteSinks[id] = shared;
    call->instance->addIncomingVideoOutput(id, shared);
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeGroupCall_nativeRemoveSsrcs(JNIEnv *env, jobject, jlong handle, jintArray ssrcs) {
    GroupCall *call = fromHandle(handle);
    if (!call || !call->instance || !ssrcs) return;
    jsize size = env->GetArrayLength(ssrcs);
    std::vector<jint> values(size);
    env->GetIntArrayRegion(ssrcs, 0, size, values.data());
    std::vector<uint32_t> result(values.begin(), values.end());
    call->instance->removeSsrcs(result);
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeGroupCall_nativeStop(JNIEnv *, jobject, jlong handle) {
    GroupCall *call = fromHandle(handle);
    if (!call) return;
    if (call->capture) call->capture->setState(VideoState::Inactive);
    std::shared_ptr<GroupInstanceCustomImpl> instance(std::move(call->instance));
    if (instance) {
        // The completion runs on an engine thread; Kotlin has already dropped the handle, so nothing
        // else touches `call` any more. The Java reference outlives it inside the engine's callbacks.
        instance->stop([instance, call]() {
            std::shared_ptr<JavaRef> java = call->java;
            callJava(java->ref, "onStopped", "()V", [&](JNIEnv *e, jmethodID m) { e->CallVoidMethod(java->ref, m); });
            call->remoteSinks.clear();
            call->capture.reset();
            call->context.reset();
            delete call;
        });
    } else {
        delete call;
    }
}

}  // extern "C"
