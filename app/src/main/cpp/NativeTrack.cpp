/*
 *     Copyright (C) 2025 nift4
 *
 *     Gramophone is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     Gramophone is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

#define LOG_TAG "NativeTrack"

#include <jni.h>
#include <dlfcn.h>
#include <cstring>
#include <android/log_macros.h>
#include <bits/timespec.h>
#include <unistd.h>
#include <cstdlib>
#include <vector>
#include <pthread.h>
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
        (void* thisptr, int32_t streamType, uint32_t sampleRate, uint32_t format, uint32_t channelMask, size_t frameCount /* = 0 */, uint32_t flags /* = 0 */, legacy_callback_t callback /* = nullptr */, void* user /* = nullptr */, int32_t notificationFrames /* = 0 */, fake_sp& sharedMemory /* = nullptr */, bool threadCanCallJava /* = false */, int32_t audioSessionId /* = 0 */, int32_t transferType /* = TRANSFER_DEFAULT */, void* offloadInfo /* = nullptr */, int uid, pid_t pid, void* attributes /* = nullptr */);
static ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_t_t ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_t = nullptr;
typedef int32_t(*ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_t20audio_channel_mask_tm20audio_output_flags_tRKNS_2wpINS0_19IAudioTrackCallbackEEEiRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tRKNS_7content22AttributionSourceStateEPK18audio_attributes_tbfi_t)
        (void* thisptr, int32_t streamType, uint32_t sampleRate, uint32_t format, uint32_t channelMask, size_t frameCount /* = 0 */, uint32_t flags /* = 0 */, fake_wp& callback /* = nullptr */, int32_t notificationFrames /* = 0 */, fake_sp& sharedMemory /* = nullptr */, bool threadCanCallJava /* = false */, int32_t audioSessionId /* = 0 */, int32_t transferType /* = TRANSFER_DEFAULT */, void* offloadInfo /* = nullptr */, int& attributionSource, void* attributes /* = nullptr */, bool doNotReconnect /* = false */, float maxRequiredSpeed /* = 1.0f */, int selectedDeviceId /* = 0 */);
static ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_t20audio_channel_mask_tm20audio_output_flags_tRKNS_2wpINS0_19IAudioTrackCallbackEEEiRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tRKNS_7content22AttributionSourceStateEPK18audio_attributes_tbfi_t ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_t20audio_channel_mask_tm20audio_output_flags_tRKNS_2wpINS0_19IAudioTrackCallbackEEEiRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tRKNS_7content22AttributionSourceStateEPK18audio_attributes_tbfi = nullptr;
typedef int32_t(*ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbfi_t)
        (void* thisptr, int32_t streamType, uint32_t sampleRate, uint32_t format, uint32_t channelMask, size_t frameCount /* = 0 */, uint32_t flags /* = 0 */, legacy_callback_t callback /* = nullptr */, void* user /* = nullptr */, int32_t notificationFrames /* = 0 */, fake_sp& sharedMemory /* = nullptr */, bool threadCanCallJava /* = false */, int32_t audioSessionId /* = 0 */, int32_t transferType /* = TRANSFER_DEFAULT */, void* offloadInfo /* = nullptr */, uid_t uid, pid_t pid, void* attributes /* = nullptr */, bool doNotReconnect /* = false */, float maxRequiredSpeed /* = 1.0f */, int selectedDeviceId /* = 0 */);
static ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbfi_t ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbfi = nullptr;
typedef int32_t(*ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbf_t)
        (void* thisptr, int32_t streamType, uint32_t sampleRate, uint32_t format, uint32_t channelMask, size_t frameCount /* = 0 */, uint32_t flags /* = 0 */, legacy_callback_t callback /* = nullptr */, void* user /* = nullptr */, int32_t notificationFrames /* = 0 */, fake_sp& sharedMemory /* = nullptr */, bool threadCanCallJava /* = false */, int32_t audioSessionId /* = 0 */, int32_t transferType /* = TRANSFER_DEFAULT */, void* offloadInfo /* = nullptr */, uid_t uid, pid_t pid, void* attributes /* = nullptr */, bool doNotReconnect /* = false */, float maxRequiredSpeed /* = 1.0f */);
static ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbf_t ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbf = nullptr;
typedef int32_t(*ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_tbf_t)
        (void* thisptr, int32_t streamType, uint32_t sampleRate, uint32_t format, uint32_t channelMask, size_t frameCount /* = 0 */, uint32_t flags /* = 0 */, legacy_callback_t callback /* = nullptr */, void* user /* = nullptr */, int32_t notificationFrames /* = 0 */, fake_sp& sharedMemory /* = nullptr */, bool threadCanCallJava /* = false */, int32_t audioSessionId /* = 0 */, int32_t transferType /* = TRANSFER_DEFAULT */, void* offloadInfo /* = nullptr */, int uid, pid_t pid, void* attributes /* = nullptr */, bool doNotReconnect /* = false */, float maxRequiredSpeed /* = 1.0f */);
static ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_tbf_t ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_tbf = nullptr;
typedef int32_t(*ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_tb_t)
        (void* thisptr, int32_t streamType, uint32_t sampleRate, uint32_t format, uint32_t channelMask, size_t frameCount /* = 0 */, uint32_t flags /* = 0 */, legacy_callback_t callback /* = nullptr */, void* user /* = nullptr */, int32_t notificationFrames /* = 0 */, fake_sp& sharedMemory /* = nullptr */, bool threadCanCallJava /* = false */, int32_t audioSessionId /* = 0 */, int32_t transferType /* = TRANSFER_DEFAULT */, void* offloadInfo /* = nullptr */, uid_t uid, pid_t pid, void* attributes /* = nullptr */, bool doNotReconnect /* = false */);
static ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_tb_t ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_tb = nullptr;
typedef int32_t(*ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tj14audio_format_tj20audio_output_flags_tPK20audio_offload_info_t_t)(void *attr, uint32_t samplingRate, int32_t format, int32_t channelMask, int32_t flags, void *offloadInfo);
static ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tj14audio_format_tj20audio_output_flags_tPK20audio_offload_info_t_t ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tj14audio_format_tj20audio_output_flags_tPK20audio_offload_info_t = nullptr;
typedef int32_t(*ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tjj14audio_format_tj20audio_output_flags_tiPK20audio_offload_info_t_t)(void *attr, int32_t *output, int32_t session, int32_t *stream, uid_t uid, uint32_t samplingRate, int32_t format, int32_t channelMask, int32_t flags, int32_t selectedDeviceId, void *offloadInfo);
static ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tjj14audio_format_tj20audio_output_flags_tiPK20audio_offload_info_t_t ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tjj14audio_format_tj20audio_output_flags_tiPK20audio_offload_info_t = nullptr;
typedef int32_t(*ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tj14audio_format_tj20audio_output_flags_tPK20audio_offload_info_t_t)(void *attr, int32_t *output, int32_t session, int32_t *stream, uint32_t samplingRate, int32_t format, int32_t channelMask, int32_t flags, void *offloadInfo);
static ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tj14audio_format_tj20audio_output_flags_tPK20audio_offload_info_t_t ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tj14audio_format_tj20audio_output_flags_tPK20audio_offload_info_t = nullptr;
typedef int32_t(*ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tjPK12audio_config20audio_output_flags_tS4_S4__t)(void *attr, int32_t *output, int32_t session, int32_t *stream, uid_t uid, void* config, uint32_t flags, int32_t* selectedDeviceId, int32_t *portId);
static ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tjPK12audio_config20audio_output_flags_tS4_S4__t ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tjPK12audio_config20audio_output_flags_tS4_S4_ = nullptr;
typedef int32_t(*ZN7android11AudioSystem10getLatencyEiPj_t)(int32_t output, uint32_t* latency);
static ZN7android11AudioSystem10getLatencyEiPj_t ZN7android11AudioSystem10getLatencyEiPj = nullptr;
typedef int32_t(*ZN7android11AudioSystem13getFrameCountEiPm_t)(int32_t output, size_t* frameCount);
static ZN7android11AudioSystem13getFrameCountEiPm_t ZN7android11AudioSystem13getFrameCountEiPm = nullptr;
typedef int32_t(*ZN7android11AudioSystem15getSamplingRateEiPj_t)(int32_t output, uint32_t* sampleRate);
static ZN7android11AudioSystem15getSamplingRateEiPj_t ZN7android11AudioSystem15getSamplingRateEiPj = nullptr;
typedef void(*ZN7android11AudioSystem13releaseOutputEi19audio_stream_type_t15audio_session_t_t)(uint32_t output, int32_t stream, int32_t session);
static ZN7android11AudioSystem13releaseOutputEi19audio_stream_type_t15audio_session_t_t ZN7android11AudioSystem13releaseOutputEi19audio_stream_type_t15audio_session_t = nullptr;
typedef void(*ZN7android11AudioSystem13releaseOutputEi_t)(uint32_t output);
static ZN7android11AudioSystem13releaseOutputEi_t ZN7android11AudioSystem13releaseOutputEi = nullptr;
typedef bool(*ZNK7android10AudioTrack19isOffloadedOrDirectEv_t)(void* thisptr);
static ZNK7android10AudioTrack19isOffloadedOrDirectEv_t ZNK7android10AudioTrack19isOffloadedOrDirectEv = nullptr;
typedef bool(*ZN7android11AudioSystem18isOffloadSupportedERK20audio_offload_info_t_t)(audio_offload_info_t_v26 & offloadInfo);
static ZN7android11AudioSystem18isOffloadSupportedERK20audio_offload_info_t_t ZN7android11AudioSystem18isOffloadSupportedERK20audio_offload_info_t = nullptr;

class MyCallback;
struct track_holder {
    explicit track_holder(JNIEnv* env) {
        int err = env->GetJavaVM(&vm);
        if (err != 0) {
            ALOGE("could not get JavaVM: %d. aborting!", err);
            abort();
        }
    }
    void* track = nullptr;
    MyCallback* callback = nullptr;
    void* ats = nullptr;
    bool deathEmulation = false;
    bool died = false;
    JavaVM* vm = nullptr;
};
static void myJniDetach(void* arg) {
    int ret = ((JavaVM*)arg)->DetachCurrentThread();
    if (ret != JNI_OK) {
        ALOGE("failed to detach thread: %d", ret);
    }
}
class MyCallback : public virtual android::AudioTrack::IAudioTrackCallback {
    track_holder* mHolder;
    jobject mCallback;
    jmethodID mOnUnderrun;
    jmethodID mOnMarker;
    jmethodID mOnNewPos;
    jmethodID mOnNewIAudioTrack;
    jmethodID mOnStreamEnd;
    jmethodID mOnNewTimestamp;
    jmethodID mOnLoopEnd;
    jmethodID mOnBufferEnd;
    jmethodID mOnMoreData;
    jmethodID mOnCanWriteMoreData;
public:
    MyCallback(track_holder& holder, JNIEnv* env, jobject jcallback) : RefBase(), mHolder(&holder) {
        static int idCounter = 0;
        mId = idCounter++;
        mCallback = env->NewGlobalRef(jcallback);
        jclass callbackClass = env->GetObjectClass(mCallback);
#define TRY_GET_JNI(FIELD, METHOD, SIGNATURE) \
        FIELD = env->GetMethodID(callbackClass, METHOD, SIGNATURE); \
        if (FIELD == nullptr) { \
            ALOGI("callback does not have matching " METHOD SIGNATURE " method, " \
                "assuming it does not care"); \
            env->ExceptionClear(); \
        }
        TRY_GET_JNI(mOnUnderrun, "onUnderrun", "()V")
        TRY_GET_JNI(mOnMarker, "onMarker", "(I)V")
        TRY_GET_JNI(mOnNewPos, "onNewPos", "(I)V")
        TRY_GET_JNI(mOnStreamEnd, "onStreamEnd", "()V")
        TRY_GET_JNI(mOnNewIAudioTrack, "onNewIAudioTrack", "()V")
        TRY_GET_JNI(mOnNewTimestamp, "onNewTimestamp", "(IJJ)V")
        TRY_GET_JNI(mOnLoopEnd, "onLoopEnd", "(I)V")
        TRY_GET_JNI(mOnBufferEnd, "onBufferEnd", "()V")
        TRY_GET_JNI(mOnMoreData, "onMoreData", "(JLjava/nio/ByteBuffer;)J")
        TRY_GET_JNI(mOnCanWriteMoreData, "onCanWriteMoreData", "(JJ)V")
#undef TRY_GET_JNI
        env->DeleteLocalRef(callbackClass);
    };
    void onUnderrun() override {
        if (!mCallback || mHolder->died || !mOnUnderrun || !maybeAttachThread(__func__)) return;
        mEnv->CallVoidMethod(mCallback, mOnUnderrun);
    }
    void onMarker(uint32_t markerPosition) override {
        if (!mCallback || mHolder->died || !mOnMarker || !maybeAttachThread(__func__)) return;
        mEnv->CallVoidMethod(mCallback, mOnMarker, (jint) markerPosition);
    }
    void onNewPos(uint32_t newPos) override {
        if (!mCallback || mHolder->died || !mOnNewPos || !maybeAttachThread(__func__)) return;
        mEnv->CallVoidMethod(mCallback, mOnNewPos, (jint) newPos);
    }
    void onNewIAudioTrack() override {
        if (!mCallback || mHolder->died) return;
        if (mHolder->deathEmulation) {
            // block any further callbacks, and access to track object other than dtor
            mHolder->died = true;
            // TODO pause+stop the track ASAP
            return;
        }
        if (mOnNewIAudioTrack && maybeAttachThread(__func__)) {
            mEnv->CallVoidMethod(mCallback, mOnNewIAudioTrack);
        }
    }
    void onStreamEnd() override {
        if (!mCallback || mHolder->died || !mOnStreamEnd || !maybeAttachThread(__func__)) return;
        mEnv->CallVoidMethod(mCallback, mOnStreamEnd);
    }
    void onNewTimestamp(android::AudioTimestamp timestamp) override {
        if (!mCallback || mHolder->died || !mOnNewTimestamp || !maybeAttachThread(__func__)) return;
        mEnv->CallVoidMethod(mCallback, mOnNewTimestamp,
                                     (jint) timestamp.mPosition, (jlong) timestamp.mTime.tv_sec,
                                     (jlong) timestamp.mTime.tv_nsec);
    }
    void onLoopEnd(int32_t loopsRemaining) override {
        if (!mCallback || mHolder->died || !mOnLoopEnd || !maybeAttachThread(__func__)) return;
        mEnv->CallVoidMethod(mCallback, mOnLoopEnd, (jint) loopsRemaining);
    }
    void onBufferEnd() override {
        if (!mCallback || mHolder->died || !mOnBufferEnd || !maybeAttachThread(__func__)) return;
        mEnv->CallVoidMethod(mCallback, mOnBufferEnd);
    }
    size_t onMoreData(const android::AudioTrack::Buffer &buffer) override {
        if (!mCallback || mHolder->died || !mOnMoreData || !maybeAttachThread(__func__)) return 0;
        jobject buf = mEnv->NewDirectByteBuffer(buffer.raw, (jlong) (uint64_t) buffer.mSize);
        auto ret = (size_t) mEnv->CallLongMethod(mCallback, mOnMoreData, buf,
                                                     (jlong) (uint64_t) buffer.frameCount);
        mEnv->DeleteLocalRef(buf);
        return ret;
    }
    size_t onCanWriteMoreData(const android::AudioTrack::Buffer &buffer) override {
        if (!mCallback || mHolder->died || !mOnCanWriteMoreData || !maybeAttachThread(__func__)) return 0;
        // this method is a bit of a misnomer, we're supposed to never write in the buffer and
        // always return 0. only the available write capacity is of interest.
        uint64_t size = buffer.mSize;
        uint64_t frames = buffer.frameCount;
        mEnv->CallVoidMethod(mCallback, mOnCanWriteMoreData, frames, size);
        return 0;
    }
    void onLastStrongRef(const void *id) override {
        mAttached = false; // we're probably _not_ on the callback thread anymore, invalidate mEnv
        if (maybeAttachThread(__func__)) {
            mEnv->DeleteGlobalRef(mCallback);
        } else {
            ALOGE("leaking callback reference %p because thread is not attached?!", mCallback);
        }
        // this will end up crashing if someone with a weak ref calls a callback method and the
        // method does not check if mCallback != null
        mEnv = nullptr;
        mCallback = nullptr;
        mHolder = nullptr;
    }
    bool onIncStrongAttempted(uint32_t flags, const void *id) override {
        return false; // never revive
    }
    private:
    bool mAttached = false;
    JNIEnv* mEnv = nullptr;
    int mId;
    bool maybeAttachThread(const char* caller) {
        // This relies on the fact that all callbacks will be called on the same thread.
        if (!mAttached) {
            JNIEnv* env;
            int ret = mHolder->vm->GetEnv((void**)&env, JNI_VERSION_1_6);
            if (ret == JNI_OK) {
                mEnv = env;
                mAttached = true;
            } else if (ret == JNI_EDETACHED) {
                char buf[50] = {'\0'};
                snprintf(buf, 50, "NativeTrack_cb%d", mId);
                struct {
                    jint version;
                    char *name;
                    jobject group;
                } attachArgs = {.version = JNI_VERSION_1_6, .name = &buf[0], .group = nullptr};
                mHolder->vm->AttachCurrentThread(&env, &attachArgs);
                static pthread_once_t initialized = PTHREAD_ONCE_INIT;
                static pthread_key_t jni_env_key;
                pthread_once(&initialized, [] {
                    int err = pthread_key_create(&jni_env_key, myJniDetach);
                    if (err != 0) {
                        ALOGE("failed to create pthread key: %d, aborting!", err);
                        abort();
                    }
                });
                int err = pthread_setspecific(jni_env_key, mHolder->vm);
                if (err != 0) {
                    ALOGE("failed to set pthread key: %d, aborting!", err);
                    abort();
                }
                mEnv = env;
                mAttached = true;
            } else {
                ALOGE("failed to get jni env %d", ret);
            }
        }
        if (!mAttached) {
            ALOGE("tried to attach thread but failed (in %s)", caller);
            return false;
        }
        return true;
    }
};

static void callbackAdapter(int event, void* userptr, void* info) {
    auto user = ((track_holder*) userptr)->callback;
    if (user == nullptr) {
        ALOGE("LEAKED callbackAdapter trying to call destroyed callback!!!");
        if (event == 0 || event == 9) {
            ((android::AudioTrack::Buffer*)info)->mSize = 0;
        }
        return;
    }
    switch (event) {
        case 0 /* EVENT_MORE_DATA */:
            ((android::AudioTrack::Buffer*)info)->mSize =
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
        case 9 /* EVENT_CAN_WRITE_MORE_DATA */:
            ((android::AudioTrack::Buffer*)info)->mSize =
                    user->onCanWriteMoreData(*(android::AudioTrack::Buffer*)info);
            break;
        default:
            ALOGE("unsupported event %d (user=%p info=%p infoVal=%d)", event, user, info,
                  info ? *(int32_t*)info : 0);
            break;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_00024Companion_initDlsym(JNIEnv* env, jobject) {
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
    if (android_get_device_api_level() <= 30) {
        DLSYM_OR_RETURN(libaudioclient, ZN7android11AudioSystem18isOffloadSupportedERK20audio_offload_info_t, false)
    }
    if (android_get_device_api_level() <= 22) {
        DLSYM_OR_RETURN(libaudioclient, ZNK7android10AudioTrack19isOffloadedOrDirectEv, false)
    }
    if (android_get_device_api_level() == 23) {
        DLSYM_OR_RETURN(libaudioclient, ZN7android11AudioSystem10getLatencyEiPj, false)
        DLSYM_OR_RETURN(libaudioclient, ZN7android11AudioSystem13getFrameCountEiPm, false)
        DLSYM_OR_RETURN(libaudioclient, ZN7android11AudioSystem15getSamplingRateEiPj, false)
    }
    if (android_get_device_api_level() >= 22 && android_get_device_api_level() < 29) {
        DLSYM_OR_RETURN(libaudioclient, ZN7android11AudioSystem13releaseOutputEi19audio_stream_type_t15audio_session_t, false)
    } else {
        DLSYM_OR_RETURN(libaudioclient, ZN7android11AudioSystem13releaseOutputEi, false)
    }
    if (android_get_device_api_level() < 28) {
        if (android_get_device_api_level() >= 26) {
            DLSYM_OR_RETURN(libaudioclient, ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tjPK12audio_config20audio_output_flags_tS4_S4_, false)
        } else if (android_get_device_api_level() >= 23) {
            DLSYM_OR_RETURN(libaudioclient, ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tjj14audio_format_tj20audio_output_flags_tiPK20audio_offload_info_t, false)
        } else if (android_get_device_api_level() >= 22) {
            DLSYM_OR_RETURN(libaudioclient, ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tj14audio_format_tj20audio_output_flags_tPK20audio_offload_info_t, false)
        } else {
            DLSYM_OR_RETURN(libaudioclient, ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tj14audio_format_tj20audio_output_flags_tPK20audio_offload_info_t, false)
        }
    }
    if (android_get_device_api_level() >= 31) {
        DLSYM_OR_RETURN(libaudioclient, ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_t20audio_channel_mask_tm20audio_output_flags_tRKNS_2wpINS0_19IAudioTrackCallbackEEEiRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tRKNS_7content22AttributionSourceStateEPK18audio_attributes_tbfi, false)
    } else if (android_get_device_api_level() >= 28) {
        DLSYM_OR_RETURN(libaudioclient, ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbfi, false)
    } else if (android_get_device_api_level() >= 26) {
        DLSYM_OR_RETURN(libaudioclient, ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbf, false)
    } else if (android_get_device_api_level() >= 24) {
        DLSYM_OR_RETURN(libaudioclient, ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_tbf, false)
    } else if (android_get_device_api_level() >= 23) {
        DLSYM_OR_RETURN(libaudioclient, ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_tb, false)
    } else {
        DLSYM_OR_RETURN(libaudioclient, ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_t, false)
    }
    return true;
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_create(
        JNIEnv *env, jobject thiz, jobject parcel) {
    auto theTrack = ::operator new(AUDIO_TRACK_SIZE);
    memset(theTrack, (unsigned char)0xde, AUDIO_TRACK_SIZE);
    auto holder = new track_holder(env);
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
    auto callback = new MyCallback(*holder, env, thiz);
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
    if (android_get_device_api_level() < 23 && maxRequiredSpeed != 1.0f) {
        ALOGE("Android 5.x does not support speed adjustment, maxRequiredSpeed != 1f is wrong");
        return INT32_MIN;
    }
    if (android_get_device_api_level() < 30 && (contentId != 0 || syncId != 0)) {
        ALOGE("Tuner is supported since Android 11, (contentId != 0 || syncId != 0) is wrong");
        return INT32_MIN;
    }
    // TODO can we backport selectedDeviceId
    if (android_get_device_api_level() < 28 && selectedDeviceId != 0) {
        ALOGE("Selected devices are supported since Android 9, selectedDeviceId != 0 is wrong");
        return INT32_MIN;
    }
    auto holder = (track_holder*) ptr;
    jint ret = 0;
    fake_sp sharedMemory = {.thePtr = nullptr}; // TODO impl shared memory
    const char* tags = env->GetStringUTFChars(inTags, nullptr);
    union {
        audio_offload_info_t_v30 newInfo = {};
        audio_offload_info_t_v26 oldInfo;
    } offloadInfo;
    union {
        audio_attributes_v28 newAttrs = {};
        audio_attributes_legacy oldAttrs;
    } audioAttributes;
    if (android_get_device_api_level() >= 28) {
        offloadInfo.newInfo = {
                .version = AUDIO_MAKE_OFFLOAD_INFO_VERSION(0, 2),
                .size = sizeof(audio_offload_info_t_v30),
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
                .encapsulation_mode = encapsulationMode, // informative, since Android 11
                .content_id = contentId, // since Android 11
                .sync_id = syncId, // since Android 11
        };
        audioAttributes.newAttrs = {
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
                .bit_width = (uint32_t)bitWidth, // informative, since Android 8.0
                .offload_buffer_size = (uint32_t)offloadBufferSize, // informative, since Android 8.0
                .usage = usage, // informative, since Android 8.0
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
    if (android_get_device_api_level() >= 31) { // Android 12.0 (SDK 31) or later
        auto refs = holder->callback->createWeak(holder);
        fake_wp callback = {.thePtr = holder->callback, .refs = refs};
        ret = ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_t20audio_channel_mask_tm20audio_output_flags_tRKNS_2wpINS0_19IAudioTrackCallbackEEEiRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tRKNS_7content22AttributionSourceStateEPK18audio_attributes_tbfi(
                holder->track,
                /* streamType = */ streamType,
                /* sampleRate = */ sampleRate,
                /* format = */ format,
                /* channelMask = */ channelMask,
                /* frameCount = */ frameCount,
                /* flags = */ trackFlags,
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
    } else if (android_get_device_api_level() >= 28) { // Android 9 (SDK 28) to Android 11 (SDK 30)
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
    } else if (android_get_device_api_level() >= 26) { // Android 8.x
        ret = ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tjiPK18audio_attributes_tbf(
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
                /* maxRequiredSpeed = */ maxRequiredSpeed
        );
    } else if (android_get_device_api_level() >= 24) { // Android 7.x
        ret = ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_iRKNS_2spINS_7IMemoryEEEb15audio_session_tNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_tbf(
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
                /* uid = */ (int)getuid(),
                /* pid = */ getpid(),
                /* pAttributes = */ &audioAttributes.newAttrs,
                /* doNotReconnect = */ doNotReconnect,
                /* maxRequiredSpeed = */ maxRequiredSpeed
        );
    } else if (android_get_device_api_level() >= 23) { // Android 6.0 (SDK 23)
        if (maxRequiredSpeed > 1.0f) {
            if (trackFlags & 4 /* AUDIO_OUTPUT_FLAG_FAST */) {
                // if we're unlucky, the calculated frame count may be smaller than HAL frame count
                // and fast track will be rejected just because of this. but that's sort-of far-fetched
                // because our frame count calculation is quite conservative, and using fast tracks with
                // large buffer sizes is sort of counter productive as well, so let's just try our best
                ALOGI("fast requested with maxRequiredSpeed(%f) emulation, "
                      "trying to set frame count anyway", maxRequiredSpeed);
            }
            int32_t output = 0;
            int32_t myStreamType = streamType;
            int32_t status = ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tjj14audio_format_tj20audio_output_flags_tiPK20audio_offload_info_t(
                    &audioAttributes.oldAttrs, &output,sessionId, &myStreamType,
                    getuid(),sampleRate, format, channelMask,trackFlags,
                    selectedDeviceId,&offloadInfo.oldInfo);
            uint32_t afLatencyMs = 0;
            size_t afFrameCount = 0;
            uint32_t afSampleRate = 0;
            uint32_t minBufCount = 0;
            int32_t minFrameCount = 0;
            if (status != 0 || output == 0) {
                ALOGE("Could not get audio output for session %d, stream type %d, usage %d, "
                      "sample rate %u, format %#x, channel mask %#x, flags %#x",
                      sessionId, streamType, audioAttributes.oldAttrs.usage, sampleRate, format,
                      channelMask, trackFlags);
                goto fallback;
            }
            status = ZN7android11AudioSystem10getLatencyEiPj(output, &afLatencyMs);
            if (status != 0) {
                ALOGE("getLatency(%d) failed status %d", output, status);
                goto release;
            }
            status = ZN7android11AudioSystem13getFrameCountEiPm(output, &afFrameCount);
            if (status != 0) {
                ALOGE("getFrameCount(output=%d) status %d", output, status);
                goto release;
            }
            status = ZN7android11AudioSystem15getSamplingRateEiPj(output, &afSampleRate);
            if (status != 0) {
                ALOGE("getSamplingRate(output=%d) status %d", output, status);
                goto release;
            }
            // emulate maxRequiredSpeed feature in M, where speed adjustment was added, by
            // calculating the minimum frame count like AudioTrack would in N
            // (where maxRequiredSpeed was added)
            minBufCount = afLatencyMs / ((1000 * afFrameCount) / afSampleRate);
            minFrameCount = (int32_t)((minBufCount < 2 ? 2 : minBufCount) *
                    (sampleRate == afSampleRate ? afFrameCount : size_t((uint64_t)afFrameCount *
                    sampleRate / afSampleRate + 1 + 1))
                                    * (double)maxRequiredSpeed + 1 + 1);
            if (frameCount < minFrameCount) {
                ALOGI("corrected frameCount(%d) to minFrameCount(%d) for maxRequiredSpeed(%f) "
                      "emulation", frameCount, minFrameCount, maxRequiredSpeed);
                frameCount = minFrameCount;
            }
            release:
            ZN7android11AudioSystem13releaseOutputEi19audio_stream_type_t15audio_session_t(output, streamType, sessionId);
        }
        fallback:
        ret = ZN7android10AudioTrack3setE19audio_stream_type_tj14audio_format_tjm20audio_output_flags_tPFviPvS4_ES4_jRKNS_2spINS_7IMemoryEEEbiNS0_13transfer_typeEPK20audio_offload_info_tiiPK18audio_attributes_tb(
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
                /* doNotReconnect = */ doNotReconnect
                );
    } else { // Android 5.0 / 5.1 (SDK 21 / 22)
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
        if (ret == 0 && doNotReconnect) {
            holder->deathEmulation = !ZNK7android10AudioTrack19isOffloadedOrDirectEv(holder->track);
        }
    }
    return ret;
}

extern "C" JNIEXPORT jlong JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_getRealPtr(
        JNIEnv *, jobject, jlong ptr) {
    auto holder = (track_holder*) ptr;
    if (holder->died)
        return 0;
    return (intptr_t)holder->track;
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
    holder->track = nullptr;
    holder->callback = nullptr;
    delete holder;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_00024Companion_isOffloadSupported(
        JNIEnv*, jobject, jint sampleRate, jint format,
        jint channelMask) {
    if (android_get_device_api_level() > 30) {
        ALOGE("isOffloadSupported() should only be used on L-R");
        return false;
    }
    union {
        audio_offload_info_t_v30 newInfo = {};
        audio_offload_info_t_v26 oldInfo;
    } offloadInfo;
    if (android_get_device_api_level() >= 28) {
        offloadInfo.newInfo = {
                .version = AUDIO_MAKE_OFFLOAD_INFO_VERSION(0, 2),
                .size = sizeof(audio_offload_info_t_v30),
                .sample_rate = (uint32_t)sampleRate,
                .channel_mask = (uint32_t)channelMask,
                .format = (uint32_t)format,
                .stream_type = LEGACY_AUDIO_STREAM_MUSIC, // must be MUSIC
                .bit_rate = (uint32_t)0,
                .duration_us = 2100 /* 3.5min * 60 */ * 1000 * 1000, // must be >60s
                .has_video = (bool)false,
                .is_streaming = (bool)false,
                .bit_width = (uint32_t)0,
                .offload_buffer_size = (uint32_t)0,
                .usage = 0,
                .encapsulation_mode = 0,
                .content_id = 0,
                .sync_id = 0
        };
    } else {
        offloadInfo.oldInfo = {
                .version = AUDIO_MAKE_OFFLOAD_INFO_VERSION(0, 1),
                .size = sizeof(audio_offload_info_t_v26),
                .sample_rate = (uint32_t)sampleRate,
                .channel_mask = (uint32_t)channelMask,
                .format = (uint32_t)format,
                .stream_type = LEGACY_AUDIO_STREAM_MUSIC, // must be MUSIC
                .bit_rate = (uint32_t)0,
                .duration_us = 2100 /* 3.5min * 60 */ * 1000 * 1000, // must be >60s
                .has_video = (bool)false,
                .is_streaming = (bool)false,
                .bit_width = (uint32_t)0,
                .offload_buffer_size = (uint32_t)0,
                .usage = 0,
        };
    } // TODO verify Q/R?
    return ZN7android11AudioSystem18isOffloadSupportedERK20audio_offload_info_t(offloadInfo.oldInfo);
}

extern "C"
JNIEXPORT void JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_00024Companion_runWithOpenedOutput(
        JNIEnv *env, jobject, jint streamType, jint sampleRate, jint format,
        jint channelMask, jint frameCount, jint trackFlags, jint sessionId,
        jint selectedDeviceId, jint bitRate, jlong durationUs, jboolean hasVideo,
        jboolean isStreaming, jint bitWidth, jint offloadBufferSize, jint usage,
        jint contentType, jint source,
        jint attrFlags, jstring inTags, jobject action) {
    if (android_get_device_api_level() >= 28) {
        ALOGE("runWithOpenedOutput doesn't work on Pie or later");
        return;
    }
    const char* tags = env->GetStringUTFChars(inTags, nullptr);
    audio_attributes_legacy audioAttributes = {
            .content_type = contentType,
            .usage = usage,
            .source = source,
            .flags = (uint32_t) attrFlags,
    };
    audioAttributes.tags[255] = '\0';
    strncpy(audioAttributes.tags, tags, 255);
    audio_config_v26 config = {};
    config.format = format;
    config.channel_mask = channelMask;
    config.frame_count = frameCount;
    config.sample_rate = sampleRate;
    config.offload_info = {
            .version = AUDIO_MAKE_OFFLOAD_INFO_VERSION(0, 1),
            .size = sizeof(audio_offload_info_t_legacy),
            .sample_rate = (uint32_t) sampleRate,
            .channel_mask = (uint32_t) channelMask,
            .format = (uint32_t) format,
            .stream_type = streamType,
            .bit_rate = (uint32_t) bitRate,
            .duration_us = durationUs,
            .has_video = (bool) hasVideo,
            .is_streaming = (bool) isStreaming,
            .bit_width = (uint32_t) bitWidth, // informative, since Android 8.0
            .offload_buffer_size = (uint32_t) offloadBufferSize, // informative, since Android 8.0
            .usage = usage, // informative, since Android 8.0
    };
    env->ReleaseStringUTFChars(inTags, tags);
    int32_t streamTypeOut = streamType;
    int32_t output = 0;
    int32_t portId = 0;
    int32_t status = 0;
    int32_t selectedDeviceIdOut = selectedDeviceId;
    std::vector<int32_t> secondaryOutputs;
    if (android_get_device_api_level() >= 26) {
        status = ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tjPK12audio_config20audio_output_flags_tS4_S4_(
                &audioAttributes, &output, sessionId, &streamTypeOut, getuid(), &config,
                trackFlags, &selectedDeviceIdOut, &portId);
    } else if (android_get_device_api_level() >= 23) {
        status = ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tjj14audio_format_tj20audio_output_flags_tiPK20audio_offload_info_t(
                &audioAttributes, &output, sessionId, &streamTypeOut, getuid(), sampleRate, format, channelMask,
                trackFlags, selectedDeviceId, &config.offload_info);
    } else if (android_get_device_api_level() >= 22) {
        status = ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tPi15audio_session_tP19audio_stream_type_tj14audio_format_tj20audio_output_flags_tPK20audio_offload_info_t(
                &audioAttributes, &output, sessionId, &streamTypeOut, sampleRate, format, channelMask,
                trackFlags, &config.offload_info);
    } else {
        output = ZN7android11AudioSystem16getOutputForAttrEPK18audio_attributes_tj14audio_format_tj20audio_output_flags_tPK20audio_offload_info_t(
                &audioAttributes, sampleRate, channelMask, format, trackFlags, &config.offload_info);
    }
    if (status != 0 || output == 0) {
        ALOGE("failed runWithOpenedOutput with status %d output %d", status, output);
        return;
    }
    jclass intCls = env->FindClass("java/lang/Integer");
    jmethodID intId = env->GetMethodID(intCls, "<init>", "(I)V");
    jobject outputInt = env->NewObject(intCls, intId, output);
    env->DeleteLocalRef(intCls);
    jclass actionCls = env->GetObjectClass(action);
    jmethodID id = env->GetMethodID(actionCls, "accept", "(Ljava/lang/Object;)V");
    env->DeleteLocalRef(actionCls);
    env->CallVoidMethod(action, id, outputInt);
    env->DeleteLocalRef(outputInt);
    if (android_get_device_api_level() >= 29) {
        ZN7android11AudioSystem13releaseOutputEi(portId);
    } else if (android_get_device_api_level() >= 22) {
        ZN7android11AudioSystem13releaseOutputEi19audio_stream_type_t15audio_session_t(
                output, streamType, sessionId);
    } else {
        ZN7android11AudioSystem13releaseOutputEi(output);
    }
}

// TODO
// void setAudioTrackCallback(const sp<media::IAudioTrackCallback>& callback) {
//     mAudioTrackCallback->setAudioTrackCallback(callback);
// }

// TODO
// audio_port_handle_t getPortId() const { return mPortId; };

// TODO
// bool isPlaying() {
//     AutoMutex lock(mLock);
//     return isPlaying_l();
// }
// bool isPlaying_l() {
//     return mState == STATE_ACTIVE || mState == STATE_STOPPING;
// }

// TODO
// audio_output_flags_t getFlags() const { AutoMutex _l(mLock); return mFlags; }

// TODO
// audio_session_t getSessionId() const { return mSessionId; }

// TODO
// void setCallerName(const std::string &name) {
//    mCallerName = name;
// }
// std::string getCallerName() const {
//     return mCallerName;
// };

// TODO we can just keep ref here?
// sp<IMemory> sharedBuffer() const { return mSharedBuffer; }

// TODO
// uint32_t   getNotificationPeriodInFrames() const { return mNotificationFramesAct; }

// TODO
// size_t      frameSize() const   { return mFrameSize; }
// uint32_t    channelCount() const { return mChannelCount; }
// size_t      frameCount() const  { return mFrameCount; }
// audio_channel_mask_t channelMask() const { return mChannelMask; }
// audio_format_t format() const   { return mFormat; }
// status_t    initCheck() const   { return mStatus; }

// TODO
// status_t addAudioDeviceCallback(const sp<AudioSystem::AudioDeviceCallback>& callback);
// status_t removeAudioDeviceCallback(
//      const sp<AudioSystem::AudioDeviceCallback>& callback);

// TODO
// status_t getTimestamp(ExtendedTimestamp *timestamp);

// TODO
// media::VolumeShaper::Status applyVolumeShaper(
//       const sp<media::VolumeShaper::Configuration>& configuration,
//       const sp<media::VolumeShaper::Operation>& operation);

// TODO
// status_t getMetrics(mediametrics::Item * &item);