#define LOG_TAG "NativeTrack"

#include <jni.h>
#include <dlfcn.h>
#include <cstring>
#include <android/log_macros.h>

extern void *libaudioclient_handle;
extern void *libpermission_handle;
extern void *libandroid_runtime_handle;
extern bool initLib(JNIEnv *env);

typedef void*(*ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject_t)(_JNIEnv*, _jobject*);
static ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject_t ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject = nullptr;
typedef int32_t(*ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE_t)(void* thisptr, void* parcel);
static ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE_t ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE = nullptr;
typedef void*(*ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE_t)(void* thisptr, void* attributionSourceState);
static ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE_t ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE = nullptr;
typedef void*(*ZN7android10AudioTrackC1Ev_t)(void* thisptr);
static ZN7android10AudioTrackC1Ev_t ZN7android10AudioTrackC1Ev = nullptr;

// last observed AOSP size (arm64) was 1312
#define AUDIO_TRACK_SIZE 5000
// last observed AOSP size (arm64) was 152
#define ATTRIBUTION_SOURCE_SIZE 500
#define FAKE_PTR_SIZE 32

// make sure to call RefBase::incStrong on thePtr before giving fake_sp away, and call
// decStrong when you don't need it anymore :)
struct fake_sp {
    void* thePtr;
};
// make sure to call RefBase::createWeak(thePtr, <arbitrary unique id>) and save returned pointer
// to refs, then call RefBase::weakref_type::decWeak(refs) when you don't need it anymore
struct fake_wp {
    void* thePtr;
    void* refs;
};

extern "C" JNIEXPORT jlong JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_create(
        JNIEnv *env, jobject, jobject parcel) {
    if (!initLib(env))
        return NULL;
    auto theTrack = ::operator new(AUDIO_TRACK_SIZE);
    memset(theTrack, 0, AUDIO_TRACK_SIZE);
    if (parcel != nullptr) { // implies SDK >= 31
        auto ats = ::operator new(ATTRIBUTION_SOURCE_SIZE);
        memset(ats, 0, ATTRIBUTION_SOURCE_SIZE);
        if (!ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject) {
            ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject =
                    (ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject_t)
                            dlsym(libandroid_runtime_handle,"_ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject");
            if (ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject == nullptr) {
                ALOGE("dlsym returned nullptr for _ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject: %s",
                      dlerror());
                return NULL;
            }
        }
        auto myParcel = ZN7android19parcelForJavaObjectEP7_JNIEnvP8_jobject(env, parcel);
        if (myParcel == nullptr) {
            ALOGE("myParcel is NULL");
            return NULL;
        }
        if (!ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE) {
            ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE =
                    (ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE_t)
                    dlsym(libpermission_handle,"_ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE");
            if (ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE == nullptr) {
                ALOGE("dlsym returned nullptr for _ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE: %s",
                      dlerror());
                return NULL;
            }
        }
        // I'm too cool to call AttributionSourceState ctor before using it.
        ZN7android7content22AttributionSourceState14readFromParcelEPKNS_6ParcelE(ats, myParcel);
        if (!ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE) {
            ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE =
                    (ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE_t)
                    dlsym(libaudioclient_handle,"_ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE");
            if (ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE == nullptr) {
                ALOGE("dlsym returned nullptr for _ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE: %s",
                      dlerror());
                return NULL;
            }
        }
        ZN7android10AudioTrackC1ERKNS_7content22AttributionSourceStateE(theTrack, ats);
        ::operator delete(ats); // copied by AudioTrack ctor
    } else {
        if (!ZN7android10AudioTrackC1Ev) {
            ZN7android10AudioTrackC1Ev = (ZN7android10AudioTrackC1Ev_t)
                    dlsym(libaudioclient_handle,"_ZN7android10AudioTrackC1Ev");
            if (ZN7android10AudioTrackC1Ev == nullptr) {
                ALOGE("dlsym returned nullptr for _ZN7android10AudioTrackC1Ev: %s",
                      dlerror());
                return NULL;
            }
        }
        ZN7android10AudioTrackC1Ev(theTrack);
    }
    return (intptr_t)theTrack;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_akanework_gramophone_logic_utils_NativeTrack_doSet(
        JNIEnv *env, jobject, jlong ptr) {
    // TODO support all 5 gazillion variants of set()

}