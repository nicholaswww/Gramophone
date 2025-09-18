package org.akanework.gramophone.logic.utils

import java.util.UUID

/**
 * Class constructor. See also OpenSL ES 1.1 specification, SLVolumeItf interface.
 *
 * @param priority the priority level requested by the application for
 *            controlling the effect engine. As the same effect engine can
 *            be shared by several applications, this parameter indicates
 *            how much the requesting application needs control of effect
 *            parameters. The normal priority is 0, above normal is a
 *            positive number, below normal a negative number.
 * @param audioSession system wide unique audio session identifier.
 *            The effect will be attached to the MediaPlayer or AudioTrack in
 *            the same audio session.
 *
 * @throws java.lang.IllegalArgumentException
 * @throws java.lang.UnsupportedOperationException
 * @throws java.lang.RuntimeException
 */
@Suppress("unused")
class Volume(priority: Int, audioSession: Int) : ReflectionAudioEffect(
	EFFECT_TYPE_VOLUME, EFFECT_UUID_SWVOLUME, priority, audioSession) {
	companion object {
		private val EFFECT_TYPE_VOLUME =
			UUID.fromString("09e8ede0-ddde-11db-b4f6-0002a5d5c51b")
		private val EFFECT_UUID_SWVOLUME =
			UUID.fromString("119341a0-8469-11df-81f9-0002a5d5c51b")
		fun isAvailable() = isEffectTypeAvailable(EFFECT_TYPE_VOLUME)
	}

	var level: Short // SLmillibel
		get() = getShortParameter(0)
		set(value) {
			setParameter(0, value)
		}
	val maxLevel: Short // SLmillibel
		get() = getShortParameter(1)
	var mute: Boolean
		get() = getBoolParameter(2)
		set(value) {
			setParameter(2, value)
		}
	var enableStereoPosition: Boolean
		get() = getBoolParameter(3)
		set(value) {
			setParameter(3, value)
		}
	var stereoPosition: Short // SLpermille
		get() = getShortParameter(4)
		set(value) {
			setParameter(4, value)
		}
}