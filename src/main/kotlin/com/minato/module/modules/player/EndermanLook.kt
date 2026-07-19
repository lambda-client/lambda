
package com.minato.module.modules.player

import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.math.distSq
import net.minecraft.entity.EquipmentSlot
import net.minecraft.entity.mob.EndermanEntity
import net.minecraft.item.Items
import net.minecraft.util.math.Vec3d

@Suppress("unused")
object EndermanLook : Module(
	name = "EndermanLook",
	description = "Either stares at every enderman or stops you from staring at them",
	tag = ModuleTag.PLAYER,
) {
	private val mode by setting("Mode", Mode.Away, "Whether to stare down endermen or avoid their gaze")
	private val stunHostiles by setting("Stun Hostiles", true, "Stare back at already provoked endermen to freeze them") { mode == Mode.Away }
	private val disableWhileGliding by setting("Disable While Gliding", true, "Disables when gliding with an elytra")

	private const val STARE_CONE = 0.025 // vanilla to aggro endermen

	init {
		setDefaultAutomationConfig()
			.withEdits {
				hideAllExcept(::rotationConfig)
			}

		listen<TickEvent.Pre> {
			if (player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.CARVED_PUMPKIN) ||
				player.abilities.creativeMode ||
				(disableWhileGliding && player.isGliding)) return@listen

			val endermen = world.entities
				.filterIsInstance<EndermanEntity>()
				.filter { it.isAlive && player.canSee(it) }

			when (mode) {
				Mode.At -> closest(endermen.filter { !it.isAngry })
					?.let { stareAt(it) }

				Mode.Away -> {
					val (stunnable, rest) = endermen.partition { it.isAngry && stunHostiles }

					closest(stunnable)?.let { stareAt(it) }
						?: closest(rest.filter { isStaringAt(it) })?.let { lookAway() }
				}
			}
		}
	}

	private fun SafeContext.stareAt(enderman: EndermanEntity) =
		rotationRequest { rotation(player.eyePos.rotationTo(enderman.eyePos)) }.submit()

	private fun lookAway() =
		rotationRequest { pitch(90f) }.submit()

	private fun SafeContext.closest(endermen: List<EndermanEntity>) =
		endermen.minByOrNull { it.eyePos distSq player.eyePos }

	private fun SafeContext.isStaringAt(enderman: EndermanEntity): Boolean {
		val diff = Vec3d(
			enderman.x - player.x,
			enderman.eyeY - player.eyeY,
			enderman.z - player.z
		)

		return player.getRotationVec(1f).normalize()
			.dotProduct(diff.normalize()) > 1.0 - STARE_CONE / diff.length()
	}

	enum class Mode {
		At,
		Away
	}
}
