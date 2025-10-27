# reflection in ReflectionAudioEffect
-keep class org.nift4.audiofxfwd.OnParameterChangeListenerAdapter { *; }
-dontwarn android.media.audiofx.AudioEffect$OnParameterChangeListener

# reflection in AudioSystemHiddenApi
-keep class org.nift4.audiofxfwd.VolumeGroupCallbackAdapter { *; }
-dontwarn android.media.AudioManager$VolumeGroupCallback
-keep class org.nift4.audiosysfwd.AudioVolumeGroupCallbackAdapter { *; }
-dontwarn android.media.INativeAudioVolumeGroupCallback
-dontwarn android.media.audio.common.AudioVolumeGroupChangeEvent