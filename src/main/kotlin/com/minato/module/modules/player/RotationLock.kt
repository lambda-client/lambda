
package com.minato.module.modules.player

import com.minato.config.ConfigEditor.editSetting
import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.withEdits
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.interaction.managers.rotating.RotationMode
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import kotlin.math.roundToInt

@Suppress("unused")
object RotationLock : Module(
	name = "RotationLock",
	description = "Locks the player rotation to the given configuration",
	tag = ModuleTag.PLAYER,
	modulePriority = 100
) {
	@JvmStatic val yawMode by setting("Yaw Mode", Mode.Snap)
	private val yawStep by setting("Yaw Step", 45.0, 1.0..180.0, 0.1) { yawMode == Mode.Snap }
	private val customYaw by setting("Custom Yaw", 0.0, -179.0..180.0, 0.1) { yawMode == Mode.Custom }
	@JvmStatic val pitchMode by setting("Pitch Mode", Mode.None)
	private val pitchStep by setting("Pitch Step", 45.0, 1.0..90.0, 0.1) { pitchMode == Mode.Snap }
	private val customPitch by setting("Custom Pitch", 0.0, -90.0..90.0, 0.1) { pitchMode == Mode.Custom }

    init {
	    setDefaultAutomationConfig()
		    .withEdits {
				hideAllExcept(::rotationConfig)
		        rotationConfig::rotationMode.editSetting { defaultValue(RotationMode.Lock) }
	        }

        listen<TickEvent.Pre> {
            val yaw = when (yawMode) {
                Mode.Custom -> customYaw
                Mode.Snap -> {
                    val normalizedYaw = (player.yaw % 360.0 + 360.0) % 360.0
                    (normalizedYaw / yawStep).roundToInt() * yawStep
                }
                Mode.None -> null
            }
            val pitch = when (pitchMode) {
                Mode.Custom -> customPitch
                Mode.Snap -> {
                    val clampedPitch = player.pitch.coerceIn(-90f, 90f)
                    (clampedPitch / pitchStep).roundToInt() * pitchStep
                }
                Mode.None -> null
            }

			if (yaw == null && pitch == null) return@listen

			rotationRequest {
				yaw?.let { yaw(it) }
				pitch?.let { pitch(it) }
			}.submit()
		}
	}

	enum class Mode {
		Snap,
		Custom,
		None
	}
}
