#include <jni.h>
#include "android_linker_ns.h"
#include <dlfcn.h>
#include <android/log.h>
#include <cstdlib>
#include <string>
extern "C" {
#include "audio-hal-enums.h"
#include <dlfunc.h>
}
#include <aaudio/AAudio.h>

static bool init_done = false;
static void* handle = nullptr;
static void* handle2 = nullptr;
typedef int audio_io_handle_t;
typedef audio_io_handle_t(*ZNK7android10AudioTrack9getOutputEv_t)(void*);
static ZNK7android10AudioTrack9getOutputEv_t ZNK7android10AudioTrack9getOutputEv = nullptr;
typedef uint32_t(*ZNK7android10AudioTrack16getHalSampleRateEv_t)(void*);
static ZNK7android10AudioTrack16getHalSampleRateEv_t ZNK7android10AudioTrack16getHalSampleRateEv = nullptr;
typedef uint32_t(*ZNK7android10AudioTrack18getHalChannelCountEv_t)(void*);
static ZNK7android10AudioTrack18getHalChannelCountEv_t ZNK7android10AudioTrack18getHalChannelCountEv = nullptr;
typedef uint32_t(*ZNK7android10AudioTrack12getHalFormatEv_t)(void*);
static ZNK7android10AudioTrack12getHalFormatEv_t ZNK7android10AudioTrack12getHalFormatEv = nullptr;
typedef aaudio_format_t(*AAudioConvert_androidToAAudioDataFormat_t)(audio_format_t);
static AAudioConvert_androidToAAudioDataFormat_t AAudioConvert_androidToAAudioDataFormat = nullptr;

bool initLib(JNIEnv* env) {
	if (init_done)
		return true;
	if (android_get_device_api_level() < 24) {
		if (!handle) {
			handle = dlopen("libmedia.so", RTLD_GLOBAL);
			if (handle == nullptr) {
				__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
				                    "dlopen returned nullptr for libmedia.so: %s", dlerror());
				return false;
			}
		}
		init_done = true;
		return true;
	}
	linkernsbypass_load(env);
	if (android_get_device_api_level() < 26) {
		if (!handle) {
			handle = dlfunc_dlopen(env, "libmedia.so", RTLD_GLOBAL);
			if (handle == nullptr) {
				__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
				                    "dlopen returned nullptr for libmedia.so: %s", dlerror());
				return false;
			}
		}
		return true;
	}
	if (!linkernsbypass_load_status()) {
		__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)", "linker namespace bypass init failed");
		return false;
	}
	android_namespace_t* ns = android_create_namespace_escape("default_copy", nullptr, nullptr,
	                                                          ANDROID_NAMESPACE_TYPE_SHARED, nullptr, nullptr);
	if (!handle) {
		handle = linkernsbypass_namespace_dlopen("libaudioclient.so", RTLD_GLOBAL, ns);
		if (handle == nullptr) {
			__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
			                    "dlopen returned nullptr for libaudioclient.so: %s", dlerror());
			return false;
		}
	}
	if (!handle2) {
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
		JNIEnv* env, jobject, jlong audioTrack) {
	if (!initLib(env))
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
		JNIEnv* env, jobject, jlong audioTrack) {
	if (!initLib(env))
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
Java_org_akanework_gramophone_logic_utils_AudioTrackHalInfoDetector_audioFormatToAAudioFormat(
		JNIEnv* env, jobject, jint audioFormat) {
	if (!initLib(env))
		return -1;
	if (android_get_device_api_level() < 26)
		return -1;
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
	return AAudioConvert_androidToAAudioDataFormat((audio_format_t)audioFormat);
}

extern "C" JNIEXPORT jint JNICALL
Java_org_akanework_gramophone_logic_utils_AudioTrackHalInfoDetector_getHalFormatInternal(
		JNIEnv* env, jobject, jlong audioTrack) {
	if (!initLib(env))
		return 0;
	if (!ZNK7android10AudioTrack12getHalFormatEv) {
		ZNK7android10AudioTrack12getHalFormatEv =
				(ZNK7android10AudioTrack12getHalFormatEv_t)
						dlsym(handle, "_ZNK7android10AudioTrack12getHalFormatEv");
		if (ZNK7android10AudioTrack12getHalFormatEv == nullptr) {
			__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
			                    "dlsym returned nullptr for _ZNK7android10AudioTrack12getHalFormatEv: %s",
			                    dlerror());
			return 0;
		}
	}
	return (int32_t) ZNK7android10AudioTrack12getHalFormatEv((void*) audioTrack);
}

extern "C" JNIEXPORT jstring JNICALL
Java_org_akanework_gramophone_logic_utils_AudioTrackHalInfoDetector_audioFormatToString(
		JNIEnv* env, jobject, jint format) {
	return env->NewStringUTF(audio_format_to_string((audio_format_t) format));
}

extern "C" JNIEXPORT jint JNICALL
Java_org_akanework_gramophone_logic_utils_AudioTrackHalInfoDetector_getOutputInternal(
		JNIEnv* env, jobject, jlong audioTrack) {
	if (!initLib(env))
		return 0;
	if (!ZNK7android10AudioTrack9getOutputEv) {
		ZNK7android10AudioTrack9getOutputEv =
				(ZNK7android10AudioTrack9getOutputEv_t)
						dlsym(handle, "_ZNK7android10AudioTrack9getOutputEv");
		if (ZNK7android10AudioTrack9getOutputEv == nullptr) {
			__android_log_print(ANDROID_LOG_ERROR, "AudioTrackHalInfo(JNI)",
			                    "dlsym returned nullptr for _ZNK7android10AudioTrack9getOutputEv: %s",
			                    dlerror());
			return 0;
		}
	}
	return ZNK7android10AudioTrack9getOutputEv((void*) audioTrack);
}