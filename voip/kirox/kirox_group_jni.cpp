// JNI bridge between com.kirox.app.voip.NativeGroupCall and tgcalls group calls (video chats,
// voice part). Written for KiroX; tgcalls itself is LGPL-3.0, WebRTC is BSD-3-Clause.
//
// Flow: start → the engine emits a join payload (our audio SSRC + JSON) → Kotlin sends it to
// Telegram with JoinVideoChat → Telegram's answer comes back through setJoinResponse, which
// connects the engine over RTC. Incoming audio streams are described by their SSRC alone.

#include <jni.h>

#include <functional>
#include <memory>
#include <string>
#include <vector>

#include "StaticThreads.h"
#include "group/GroupInstanceCustomImpl.h"

#include "sdk/android/native_api/jni/jvm.h"

using namespace tgcalls;

namespace {

/** Media channel descriptions are answered at once, so there is nothing to cancel. */
class ImmediateDescriptionTask final : public RequestMediaChannelDescriptionTask {
public:
    void cancel() override {}
};

struct GroupCall {
    std::unique_ptr<GroupInstanceCustomImpl> instance;
    jobject javaCall = nullptr;
};

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
    call->javaCall = env->NewGlobalRef(thiz);
    jobject javaCall = call->javaCall;

    GroupInstanceDescriptor descriptor;
    descriptor.threads = StaticThreads::getThreads();
    descriptor.config.need_log = false;
    descriptor.networkStateUpdated = [javaCall](GroupNetworkState state) {
        callJava(javaCall, "onNetwork", "(Z)V", [&](JNIEnv *e, jmethodID m) { e->CallVoidMethod(javaCall, m, static_cast<jboolean>(state.isConnected)); });
    };
    descriptor.audioLevelsUpdated = [javaCall](GroupLevelsUpdate const &update) {
        // SSRC 0 is this device; levels are reported only while someone speaks.
        std::vector<jint> ssrcs;
        std::vector<jfloat> levels;
        for (auto const &item : update.updates) {
            ssrcs.push_back(static_cast<jint>(item.ssrc));
            levels.push_back(item.value.voice ? item.value.level : 0.0f);
        }
        callJava(javaCall, "onLevels", "([I[F)V", [&](JNIEnv *e, jmethodID m) {
            auto size = static_cast<jsize>(ssrcs.size());
            jintArray ssrcArray = e->NewIntArray(size);
            jfloatArray levelArray = e->NewFloatArray(size);
            e->SetIntArrayRegion(ssrcArray, 0, size, ssrcs.data());
            e->SetFloatArrayRegion(levelArray, 0, size, levels.data());
            e->CallVoidMethod(javaCall, m, ssrcArray, levelArray);
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
    call->instance->emitJoinPayload([javaCall](GroupJoinPayload const &payload) {
        callJava(javaCall, "onJoinPayload", "(ILjava/lang/String;)V", [&](JNIEnv *e, jmethodID m) {
            jstring json = e->NewStringUTF(payload.json.c_str());
            e->CallVoidMethod(javaCall, m, static_cast<jint>(payload.audioSsrc), json);
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

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeGroupCall_nativeRemoveSsrcs(JNIEnv *env, jobject, jlong handle, jintArray ssrcs) {
    GroupCall *call = fromHandle(handle);
    if (!call || !call->instance || !ssrcs) return;
    jsize size = env->GetArrayLength(ssrcs);
    std::vector<jint> values(size);
    env->GetIntArrayRegion(ssrcs, 0, size, values.data());
    std::vector<uint32_t> result(values.begin(), values.end());
    call->instance->removeSsrcs(result);
}

JNIEXPORT void JNICALL Java_com_kirox_app_voip_NativeGroupCall_nativeStop(JNIEnv *env, jobject, jlong handle) {
    GroupCall *call = fromHandle(handle);
    if (!call) return;
    std::shared_ptr<GroupInstanceCustomImpl> instance(std::move(call->instance));
    jobject javaCall = call->javaCall;
    if (instance) {
        instance->stop([instance, javaCall, call]() {
            JNIEnv *e = webrtc::AttachCurrentThreadIfNeeded();
            callJava(javaCall, "onStopped", "()V", [&](JNIEnv *ee, jmethodID m) { ee->CallVoidMethod(javaCall, m); });
            e->DeleteGlobalRef(javaCall);
            delete call;
        });
    } else {
        env->DeleteGlobalRef(javaCall);
        delete call;
    }
}

}  // extern "C"
