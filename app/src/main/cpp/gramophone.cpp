#include <jni.h>
#include "android_linker_ns.h"
#include <dlfcn.h>
#include <android/log.h>
#include <cstdlib>
#include <string>
extern "C" {
#include "audio-hal-enums.h"
}
#include <aaudio/AAudio.h>

static bool init_done = false;
static void* handle = nullptr;
static void* handle2 = nullptr;
typedef uint32_t(*ZNK7android10AudioTrack16getHalSampleRateEv_t)(void*);
static ZNK7android10AudioTrack16getHalSampleRateEv_t ZNK7android10AudioTrack16getHalSampleRateEv = nullptr;
typedef uint32_t(*ZNK7android10AudioTrack18getHalChannelCountEv_t)(void*);
static ZNK7android10AudioTrack18getHalChannelCountEv_t ZNK7android10AudioTrack18getHalChannelCountEv = nullptr;
typedef uint32_t(*ZNK7android10AudioTrack12getHalFormatEv_t)(void*);
static ZNK7android10AudioTrack12getHalFormatEv_t ZNK7android10AudioTrack12getHalFormatEv = nullptr;
typedef aaudio_format_t(*AAudioConvert_androidToAAudioDataFormat_t)(audio_format_t);
static AAudioConvert_androidToAAudioDataFormat_t AAudioConvert_androidToAAudioDataFormat = nullptr;

bool initLib() {
	if (init_done)
		return true;
	if (android_get_device_api_level() < 28) {
		if (!handle) {
			handle = dlopen("libaudioclient.so", RTLD_GLOBAL);
			if (handle == nullptr) {
				__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
				                    "dlopen returned nullptr for libaudioclient.so: %s", dlerror());
				return false;
			}
		}
		if (!handle2) {
			handle2 = dlopen("libaaudio.so", RTLD_GLOBAL);
			if (handle2 == nullptr) {
				__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
				                    "dlopen returned nullptr for libaaudio.so: %s", dlerror());
				return false;
			}
		}
		init_done = true;
		return true;
	}
	if (!linkernsbypass_load_status()) {
		__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)", "linker namespace bypass init failed");
		return false;
	}
	android_namespace_t* ns = nullptr;
	if (!handle) {
		ns = android_create_namespace_escape("default_copy", nullptr, nullptr,
		                                     ANDROID_NAMESPACE_TYPE_SHARED, nullptr, nullptr);
		handle = linkernsbypass_namespace_dlopen("libaudioclient.so", RTLD_GLOBAL, ns);
		if (handle == nullptr) {
			__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
			                    "dlopen returned nullptr for libaudioclient.so: %s", dlerror());
			return false;
		}
	}
	if (!handle2) {
		if (!ns)
			ns = android_create_namespace_escape("default_copy", nullptr,
												 nullptr, ANDROID_NAMESPACE_TYPE_SHARED, nullptr, nullptr);
		handle2 = linkernsbypass_namespace_dlopen("libaaudio.so", RTLD_GLOBAL, ns);
		if (handle2 == nullptr) {
			__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
			                    "dlopen returned nullptr for libaaudio.so: %s", dlerror());
			return false;
		}
	}
	init_done = true;
	return true;
}

extern "C" JNIEXPORT jint JNICALL
Java_org_akanework_gramophone_logic_utils_AudioTrackHalInfoDetector_getHalSampleRateInternal(
		JNIEnv*, jobject, jlong audioTrack) {
	if (!initLib())
		return 0;
	if (!ZNK7android10AudioTrack16getHalSampleRateEv) {
		ZNK7android10AudioTrack16getHalSampleRateEv =
				(ZNK7android10AudioTrack16getHalSampleRateEv_t)
						dlsym(handle, "_ZNK7android10AudioTrack16getHalSampleRateEv");
		if (ZNK7android10AudioTrack16getHalSampleRateEv == nullptr) {
			__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
			                    "dlsym returned nullptr for _ZNK7android10AudioTrack16getHalSampleRateEv: %s",
			                    dlerror());
			return 0;
		}
	}
	return (int32_t) ZNK7android10AudioTrack16getHalSampleRateEv((void*) audioTrack);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_akanework_gramophone_logic_utils_AudioTrackHalInfoDetector_getHalChannelCountInternal(
		JNIEnv*, jobject, jlong audioTrack) {
	if (!initLib())
		return 0;
	if (!ZNK7android10AudioTrack18getHalChannelCountEv) {
		ZNK7android10AudioTrack18getHalChannelCountEv =
				(ZNK7android10AudioTrack18getHalChannelCountEv_t)
						dlsym(handle, "_ZNK7android10AudioTrack18getHalChannelCountEv");
		if (ZNK7android10AudioTrack18getHalChannelCountEv == nullptr) {
			__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
			                    "dlsym returned nullptr for _ZNK7android10AudioTrack18getHalChannelCountEv: %s",
			                    dlerror());
			return 0;
		}
	}
	return (int32_t) ZNK7android10AudioTrack18getHalChannelCountEv((void*) audioTrack);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_akanework_gramophone_logic_utils_AudioTrackHalInfoDetector_getHalFormatInternal(
		JNIEnv*, jobject, jlong audioTrack) {
	if (!initLib())
		return -1;
	if (!ZNK7android10AudioTrack12getHalFormatEv) {
		ZNK7android10AudioTrack12getHalFormatEv =
				(ZNK7android10AudioTrack12getHalFormatEv_t)
						dlsym(handle, "_ZNK7android10AudioTrack12getHalFormatEv");
		if (ZNK7android10AudioTrack12getHalFormatEv == nullptr) {
			__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
			                    "dlsym returned nullptr for _ZNK7android10AudioTrack12getHalFormatEv: %s",
			                    dlerror());
			return -1;
		}
	}
	if (!AAudioConvert_androidToAAudioDataFormat) {
		// _Z46AAudioConvert_androidToNearestAAudioDataFormat14audio_format_t is ABI compatible with
		// _Z39AAudioConvert_androidToAAudioDataFormat14audio_format_t.
		AAudioConvert_androidToAAudioDataFormat =
				(AAudioConvert_androidToAAudioDataFormat_t)
						dlsym(handle2, "_Z46AAudioConvert_androidToNearestAAudioDataFormat14audio_format_t");
		if (AAudioConvert_androidToAAudioDataFormat == nullptr) {
			AAudioConvert_androidToAAudioDataFormat =
					(AAudioConvert_androidToAAudioDataFormat_t)
							dlsym(handle2, "_Z39AAudioConvert_androidToAAudioDataFormat14audio_format_t");
			if (AAudioConvert_androidToAAudioDataFormat == nullptr) {
				__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
				                    "dlsym returned nullptr for _Z39AAudioConvert_androidToAAudioDataFormat14audio_format_t: %s",
				                    dlerror());
				return -1;
			}
		}
	}
	return AAudioConvert_androidToAAudioDataFormat(
			(audio_format_t)ZNK7android10AudioTrack12getHalFormatEv((void*) audioTrack));
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_akanework_gramophone_logic_utils_AudioTrackHalInfoDetector_getHalFormatInternal2(
		JNIEnv* env, jobject, jlong audioTrack) {
	if (!initLib())
		return nullptr;
	if (!ZNK7android10AudioTrack12getHalFormatEv) {
		ZNK7android10AudioTrack12getHalFormatEv =
				(ZNK7android10AudioTrack12getHalFormatEv_t)
						dlsym(handle, "_ZNK7android10AudioTrack12getHalFormatEv");
		if (ZNK7android10AudioTrack12getHalFormatEv == nullptr) {
			__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
			                    "dlsym returned nullptr for _ZNK7android10AudioTrack12getHalFormatEv: %s",
			                    dlerror());
			return nullptr;
		}
	}
	const char* ret = audio_format_to_string((audio_format_t)
			ZNK7android10AudioTrack12getHalFormatEv((void*) audioTrack));
	return env->NewStringUTF(ret);
}