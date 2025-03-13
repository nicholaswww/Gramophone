#define LOG_TAG "NativeTrack"

#include <jni.h>
#include <dlfcn.h>
#include <cstring>
#include <android/log_macros.h>
#include <bits/timespec.h>
#include <unistd.h>
#include "helpers.h"

extern void *libaudioclient_handle;
extern void *libpermission_handle;
extern void *libandroid_runtime_handle;
extern void *libutils_handle;
extern bool initLib(JNIEnv *env);

typedef void*(*ZN7android7RefBaseC2Ev_t)(void* thisptr);
static ZN7android7RefBaseC2Ev_t ZN7android7RefBaseC2Ev = nullptr;
typedef void*(*ZN7android7RefBaseD2Ev_t)(void* thisptr);
static ZN7android7RefBaseD2Ev_t ZN7android7RefBaseD2Ev = nullptr;
typedef void(*ZNK7android7RefBase9incStrongEPKv_t)(void* thisptr, void* id);
static ZNK7android7RefBase9incStrongEPKv_t ZNK7android7RefBase9incStrongEPKv = nullptr;
typedef void(*ZNK7android7RefBase9decStrongEPKv_t)(void* thisptr, void* id);
static ZNK7android7RefBase9decStrongEPKv_t ZNK7android7RefBase9decStrongEPKv = nullptr;
typedef void*(*ZNK7android7RefBase10createWeakEPKv_t)(void* thisptr, void* id);
static ZNK7android7RefBase10createWeakEPKv_t ZNK7android7RefBase10createWeakEPKv = nullptr;
#include "aosp_stubs.h"
typedef void*(*ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject_t)(_JNIEnv*, _jobject*);
static ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject_t ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject = nullptr;
typedef int32_t(*ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE_t)(void* thisptr, void* parcel);
static ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE_t ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE = nullptr;
typedef void*(*ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE_t)(void* thisptr, void* attributionSourceState);
static ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE_t ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE = nullptr;
typedef void*(*ZN7android10AudioTrackC1Ev_t)(void* thisptr);
static ZN7android10AudioTrackC1Ev_t ZN7android10AudioTrackC1Ev = nullptr;
typedef void(*ZN7android7RefBase12weakref_type7decWeakEPKv_t)(void* thisptr, void* id);
static ZN7android7RefBase12weakref_type7decWeakEPKv_t ZN7android7RefBase12weakref_type7decWeakEPKv = nullptr;
typedef int32_t(*ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_t_t)
        (void* thisptr, int32_t streamType, uint32_t sampleRate, uint32_t format, uint32_t channelMask, size_t frameCount /* = 0 */, uint32_t flags /* = 0 */, legacy_callback_t callback /* = nullptr */, void* user /* = nullptr */, int32_t notificationFrames /* = 0 */, fake_sp& sharedMemory /* = nullptr */, bool threadCanCallJava /* = false */, int32_t audioSessionId /* = 0 */, int32_t transferType /* = TRANSFER_DEFAULT */, void* offloadInfo /* = nullptr */, int uid, pid_t pid, audio_attributes_t* attributes /* = nullptr */);
static ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_t_t ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_t = nullptr;
typedef int32_t(*ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_t20audio_channel_mask_tm20audio_output_flags_tRKNS_2wpINS0_19IAudioTrackCallbackEEEiRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tRKNS_7content22AttributionSourceStateEPK18audio_attributes_tbfi_t)
        (void* thisptr, int32_t streamType, uint32_t sampleRate, uint32_t format, uint32_t channelMask, size_t frameCount /* = 0 */, uint32_t flags /* = 0 */, fake_wp& callback /* = nullptr */, int32_t notificationFrames /* = 0 */, fake_sp& sharedMemory /* = nullptr */, bool threadCanCallJava /* = false */, int32_t audioSessionId /* = 0 */, int32_t transferType /* = TRANSFER_DEFAULT */, void* offloadInfo /* = nullptr */, int& attributionSource, audio_attributes_t* attributes /* = nullptr */, bool doNotReconnect /* = false */, float maxRequiredSpeed /* = 1.0f */, int selectedDeviceId /* = 0 */);
static ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_t20audio_channel_mask_tm20audio_output_flags_tRKNS_2wpINS0_19IAudioTrackCallbackEEEiRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tRKNS_7content22AttributionSourceStateEPK18audio_attributes_tbfi_t ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_t20audio_channel_mask_tm20audio_output_flags_tRKNS_2wpINS0_19IAudioTrackCallbackEEEiRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tRKNS_7content22AttributionSourceStateEPK18audio_attributes_tbfi = nullptr;
typedef int32_t(*ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbfi_t)
        (void* thisptr, int32_t streamType, uint32_t sampleRate, uint32_t format, uint32_t channelMask, size_t frameCount /* = 0 */, uint32_t flags /* = 0 */, legacy_callback_t callback /* = nullptr */, void* user /* = nullptr */, int32_t notificationFrames /* = 0 */, fake_sp& sharedMemory /* = nullptr */, bool threadCanCallJava /* = false */, int32_t audioSessionId /* = 0 */, int32_t transferType /* = TRANSFER_DEFAULT */, void* offloadInfo /* = nullptr */, uid_t uid, pid_t pid, audio_attributes_t* attributes /* = nullptr */, bool doNotReconnect /* = false */, float maxRequiredSpeed /* = 1.0f */, int selectedDeviceId /* = 0 */);
static ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbfi_t ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbfi = nullptr;

class MyCallback : public virtual android::AudioTrack::IAudioTrackCallback {
public:
    MyCallback() : RefBase() {};
    void onUnderrun() override {
        ALOGI("MyCallback::onMoreData called");
    }
    void onMarker(uint32_t markerPosition) override {
        ALOGI("MyCallback::onMarker called");
    }
    void onNewPos(uint32_t newPos) override {
        ALOGI("MyCallback::onNewPos called");
    }
    void onNewIAudioTrack() override {
        ALOGI("MyCallback::onNewIAudioTrack called");
    }
    void onStreamEnd() override {
        ALOGI("MyCallback::onStreamEnd called");
    }
};

struct track_holder {
    void* track;
    MyCallback* callback;
    void* ats;
};

void callbackAdapter(int event, void* userptr, void* info) {
    auto user = (MyCallback*) userptr;
    switch (event) {
        case 0 /* EVENT_MORE_DATA */:
            user->onMoreData(*(android::AudioTrack::Buffer*)info);
            break;
        case 1 /* EVENT_UNDERRUN */:
            user->onUnderrun();
            break;
        case 2 /* EVENT_LOOP_END */:
            user->onLoopEnd(*(int32_t*)info);
            break;
        case 3 /* EVENT_MARKER */:
            user->onMarker(*(uint32_t*)info);
            break;
        case 4 /* EVENT_NEW_POS */:
            user->onNewPos(*(uint32_t*)info);
            break;
        case 5 /* EVENT_BUFFER_END */:
            user->onBufferEnd();
            break;
        case 6 /* EVENT_NEW_IAUDIOTRACk */:
            user->onNewIAudioTrack();
            break;
        case 7 /* EVENT_STREAM_END */:
            user->onStreamEnd();
            break;
        case 8 /* EVENT_NEW_TIMESTAMP */:
            user->onNewTimestamp(*(android::AudioTimestamp*)info);
            break;
        default:
            ALOGE("unsupported event %d (user=%p info=%p infoVal=%d)", event, user, info,
                  info ? *(int32_t*)info : 0);
            break;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_initDlsym(JNIEnv* env, jobject) {
    if (!initLib(env))
        return false;
    if (android_get_device_api_level() >= 31) {
        DLSYM_OR_RETURN(libandroid_runtime, ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject, false)
        DLSYM_OR_RETURN(libpermission, ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE, false)
        DLSYM_OR_RETURN(libaudioclient, ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE, false)
    } else {
        DLSYM_OR_RETURN(libaudioclient, ZN7android10AudioTrackC1Ev, false)
    }
    DLSYM_OR_RETURN(libutils, ZN7android7RefBaseC2Ev, false)
    DLSYM_OR_RETURN(libutils, ZN7android7RefBaseD2Ev, false)
    DLSYM_OR_RETURN(libutils, ZNK7android7RefBase9incStrongEPKv, false)
    DLSYM_OR_RETURN(libutils, ZNK7android7RefBase9decStrongEPKv, false)
    DLSYM_OR_RETURN(libutils, ZNK7android7RefBase10createWeakEPKv, false)
    DLSYM_OR_RETURN(libutils, ZN7android7RefBase12weakref_type7decWeakEPKv, false)
    // TODO support all 5 gazillion variants of set()
    DLSYM_OR_ELSE(libaudioclient, ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_t20audio_channel_mask_tm20audio_output_flags_tRKNS_2wpINS0_19IAudioTrackCallbackEEEiRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tRKNS_7content22AttributionSourceStateEPK18audio_attributes_tbfi) {
        DLSYM_OR_ELSE(libaudioclient, ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbfi) {
            DLSYM_OR_ELSE(libaudioclient, ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_t) {
                ALOGE("error: found no variant of set()");
                return false;
            }
        }
    }
    return true;
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_create(
        JNIEnv *env, jobject, jobject parcel) {
    auto theTrack = ::operator new(AUDIO_TRACK_SIZE);
    memset(theTrack, (unsigned char)0xde, AUDIO_TRACK_SIZE);
    auto holder = new track_holder();
    if (parcel != nullptr) { // implies SDK >= 31
        // I'm too cool to call AttributionSourceState ctor before using it.
        auto myParcel = ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject(env, parcel);
        if (myParcel == nullptr) {
            ALOGE("myParcel is NULL");
            ::operator delete(theTrack);
            return 0;
        }
        auto ats = ::operator new(ATTRIBUTION_SOURCE_SIZE);
        memset(ats, 0, ATTRIBUTION_SOURCE_SIZE);
        ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE(ats, myParcel);
        ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE(theTrack, ats);
        holder->ats = ats;
    } else {
        ZN7android10AudioTrackC1Ev(theTrack);
    }
    auto callback = new MyCallback();
    callback->incStrong(holder);
    if (android_get_device_api_level() >= 33) {
        // virtual inheritance, let's have the compiler generate the vtable stuff
        ((android::AudioTrack *) theTrack)->incStrong(holder);
    } else {
        ZNK7android7RefBase9incStrongEPKv(theTrack, holder);
    }
    holder->track = theTrack;
    holder->callback = callback;
    return (intptr_t)holder;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_doSet(
        JNIEnv * env, jobject, jlong ptr, jint streamType, jint sampleRate, jint format,
        jint channelMask, jint frameCount, jint trackFlags, jint sessionId, jfloat maxRequiredSpeed,
        jint selectedDeviceId, jint bitRate, jlong durationUs, jboolean hasVideo,
        jboolean isStreaming, jint bitWidth, jint offloadBufferSize, jint usage,
        jint encapsulationMode, jint contentId, jint syncId, jint contentType, jint source,
        jint attrFlags, jstring inTags, jint notificationFrames, jboolean doNotReconnect,
        jint transferMode) {
    auto holder = (track_holder*) ptr;
    jint ret = 0;
    fake_sp sharedMemory = {.thePtr = nullptr};
    const char* tags = env->GetStringUTFChars(inTags, nullptr);
    union {
        audio_offload_info_t newInfo;
        audio_offload_info_t_legacy oldInfo;
    } offloadInfo;
    union {
        audio_attributes_t newAttrs;
        audio_attributes_t_legacy oldAttrs;
    } audioAttributes;
    if (android_get_device_api_level() >= 28) {
        offloadInfo.newInfo = {
                .version = AUDIO_MAKE_OFFLOAD_INFO_VERSION(0, 2),
                .size = sizeof(audio_offload_info_t),
                .sample_rate = (uint32_t)sampleRate,
                .channel_mask = (uint32_t)channelMask,
                .format = (uint32_t)format,
                .stream_type = streamType,
                .bit_rate = (uint32_t)bitRate,
                .duration_us = durationUs,
                .has_video = (bool)hasVideo,
                .is_streaming = (bool)isStreaming,
                .bit_width = (uint32_t)bitWidth,
                .offload_buffer_size = (uint32_t)offloadBufferSize,
                .usage = usage,
                .encapsulation_mode = encapsulationMode,
                .content_id = contentId,
                .sync_id = syncId,
        };
        audioAttributes.newAttrs = audio_attributes_t {
                .content_type = contentType,
                .usage = usage,
                .source = source,
                .flags = (uint32_t)attrFlags,
        };
        audioAttributes.newAttrs.tags[255] = '\0';
        strncpy(audioAttributes.newAttrs.tags, tags, 255);
    } else {
        offloadInfo.oldInfo = {
                .version = AUDIO_MAKE_OFFLOAD_INFO_VERSION(0, 1),
                .size = sizeof(audio_offload_info_t_legacy),
                .sample_rate = (uint32_t)sampleRate,
                .channel_mask = (uint32_t)channelMask,
                .format = (uint32_t)format,
                .stream_type = streamType,
                .bit_rate = (uint32_t)bitRate,
                .duration_us = durationUs,
                .has_video = (bool)hasVideo,
                .is_streaming = (bool)isStreaming,
                .bit_width = (uint32_t)bitWidth,
                .offload_buffer_size = (uint32_t)offloadBufferSize,
                .usage = usage,
        };
        audioAttributes.oldAttrs = {
                .content_type = contentType,
                .usage = usage,
                .source = source,
                .flags = (uint32_t)attrFlags,
        };
        audioAttributes.oldAttrs.tags[255] = '\0';
        strncpy(audioAttributes.oldAttrs.tags, tags, 255);
    }
    env->ReleaseStringUTFChars(inTags, tags);
    if (holder->ats != nullptr) {
        auto refs = holder->callback->createWeak(holder);
        fake_wp callback = {.thePtr = holder->callback, .refs = refs};
        ret = ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_t20audio_channel_mask_tm20audio_output_flags_tRKNS_2wpINS0_19IAudioTrackCallbackEEEiRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tRKNS_7content22AttributionSourceStateEPK18audio_attributes_tbfi(
                holder->track,
                /* streamType = */ streamType /* AUDIO_STREAM_MUSIC */,
                /* sampleRate = */ sampleRate,
                /* format = */ format,
                /* channelMask = */ channelMask,
                /* frameCount = */ frameCount /* default */,
                /* flags = */ trackFlags /* AUDIO_OUTPUT_FLAG_NONE */,
                /* callback = */ callback,
                /* notificationFrames = */ notificationFrames,
                /* sharedBuffer = */ sharedMemory,
                /* threadCanCallJava = */ true,
                /* sessionId = */ sessionId,
                /* transferType = */ transferMode,
                /* offloadInfo = */ &offloadInfo,
                /* attributionSource = */ *((int *) holder->ats),
                /* pAttributes = */ &audioAttributes.newAttrs,
                /* doNotReconnect = */ doNotReconnect,
                /* maxRequiredSpeed = */ maxRequiredSpeed,
                /* selectedDeviceId = */ selectedDeviceId
        );
        // wp copy constructor increased weak with it's own id, so we're done here
        ZN7android7RefBase12weakref_type7decWeakEPKv(refs /* yes, refs */, holder);
        // we own attributionSource, so get rid of it
        ::operator delete(holder->ats);
        holder->ats = nullptr;
    } else if (ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbfi) {
        ret = ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbfi(
                holder->track,
                /* streamType = */ streamType,
                /* sampleRate = */ sampleRate,
                /* format = */ format,
                /* channelMask = */ channelMask,
                /* frameCount = */ frameCount,
                /* flags = */ trackFlags,
                /* callback = */ callbackAdapter,
                /* user = */ holder->callback,
                /* notificationFrames = */ notificationFrames,
                /* sharedBuffer = */ sharedMemory,
                /* threadCanCallJava = */ true,
                /* sessionId = */ sessionId,
                /* transferType = */ transferMode,
                /* offloadInfo = */ &offloadInfo,
                /* uid = */ getuid(),
                /* pid = */ getpid(),
                /* pAttributes = */ &audioAttributes.newAttrs,
                /* doNotReconnect = */ doNotReconnect,
                /* maxRequiredSpeed = */ maxRequiredSpeed,
                /* selectedDeviceId = */ selectedDeviceId
                );
    } else {
        ret = ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_t(
                holder->track,
                /* streamType = */ streamType,
                /* sampleRate = */ sampleRate,
                /* format = */ format,
                /* channelMask = */ channelMask,
                /* frameCount = */ frameCount,
                /* flags = */ trackFlags,
                /* callback = */ callbackAdapter,
                /* user = */ holder->callback,
                /* notificationFrames = */ notificationFrames,
                /* sharedBuffer = */ sharedMemory,
                /* threadCanCallJava = */ true,
                /* sessionId = */ sessionId,
                /* transferType = */ transferMode,
                /* offloadInfo = */ &offloadInfo,
                /* uid = */ (int32_t)getuid(),
                /* pid = */ getpid(),
                /* pAttributes = */ &audioAttributes.newAttrs
                );
    }
    return ret;
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_getRealPtr(
        JNIEnv *, jobject, jlong ptr) {
    return (intptr_t)((track_holder*)ptr)->track;
}

extern "C" JNIEXPORT void JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_dtor(
        JNIEnv *, jobject, jlong ptr) {
    auto holder = (track_holder*) ptr;
    // RefBase will call the dtor
    if (android_get_device_api_level() >= 33) {
        // virtual inheritance, let's have the compiler generate the vtable stuff
        ((android::AudioTrack *) holder->track)->decStrong(holder);
    } else {
        ZNK7android7RefBase9decStrongEPKv(holder->track, holder);
    }
    holder->callback->decStrong(holder);
    delete holder;
}