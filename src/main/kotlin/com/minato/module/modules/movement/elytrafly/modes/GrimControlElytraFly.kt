
package com.minato.module.modules.movement.elytrafly.modes

import com.minato.config.Config
import com.minato.context.SafeContext
import com.minato.event.events.PacketEvent
import com.minato.event.events.PlayerPacketEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.interaction.managers.rotating.RotationManager
import com.minato.interaction.material.StackSelection.Companion.select
import com.minato.module.modules.movement.BetterFirework.startFirework
import com.minato.module.modules.movement.elytrafly.ElytraFly.FlyMode
import com.minato.module.modules.movement.elytrafly.ElytraFlyMode
import com.minato.module.modules.render.Freecam
import com.minato.util.NamedEnum
import com.minato.util.TickTimer
import com.minato.util.Timer
import com.minato.util.math.MathUtils.toFloat
import com.minato.util.player.PlayerUtils.hasFirework
import com.minato.util.player.SlotUtils.hotbarStacks
import com.minato.util.player.SlotUtils.inventoryStacks
import net.minecraft.component.DataComponentTypes
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket
import net.minecraft.util.math.Vec3d
import kotlin.time.Duration.Companion.seconds

class GrimControlElytraFly(
	override val c: Config
) : ElytraFlyMode(FlyMode.GrimControl) {
	private val inventory by c.setting("Inventory", true, "Allow using fireworks from the players inventory")
	private val safetyMargin by c.setting("Safety Margin", 0.2, 0.0..2.0, 0.01, "The time (in seconds) to shorten the firework use delay to account for ping variation", "s")
	val flipFlopMode by c.setting("Flip Flop Mode", FlipFlopMode.WithFirework)
	private val packetGap by c.setting("Packet Gap", 20, 0..100, 1, "The gap between allowing player movement packets to pass") { flipFlopMode != FlipFlopMode.None }

	enum class FlipFlopMode(
		override val displayName: String,
		val isFlipFlopping: (hasFirework: Boolean) -> Boolean
	) : NamedEnum {
		Full("Full", { true }),
		WithFirework("With Firework", { hasFirework -> hasFirework }),
		None("None", { false })
	}

	private var flipFlop = false
	private var rotFlipFlop = false
	private var lastDuration = -1.0
	private val fireworkTimer = Timer()
	var still = false
	var moving = false
	// Seems to be a weird bug with fireworks not showing every second or so i'd say.
	// This ensures it stays constant to avoid random stutters
	var hasFirework = false
	private val stillTickTimer = TickTimer()

	init {
		listen<TickEvent.Pre> {
			if (!player.isGliding) return@listen

			if (fakeGliding) flyOrFakeFly()

			var vec = Vec3d.ZERO
			val yaw = player.yaw
			if (mc.options.forwardKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw))
			if (mc.options.backKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw + 180f))
			if (mc.options.leftKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw - 90f))
			if (mc.options.rightKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw + 90f))
			if (mc.options.jumpKey.isPressed) vec = vec.add(Vec3d(0.0, 1.0, 0.0))
			if (mc.options.sneakKey.isPressed) vec = vec.add(Vec3d(0.0, -1.0, 0.0))

			val prevStill = still
			still = (vec.lengthSquared() < 1e-4 || Freecam.isEnabled)
			if (still) moving = false

			if (still && flipFlopMode != FlipFlopMode.Full) {
				stillTickTimer.tick()
				hasFirework = player.hasFirework
				if (!flipFlopMode.isFlipFlopping(hasFirework)) return@listen
			} else {
				if (fireworkTimer.timePassed(lastDuration.seconds - safetyMargin.seconds)) {
					val firework = findFirework()
					hasFirework = firework != null
					if (firework == null) return@listen
					lastDuration = (firework.get(DataComponentTypes.FIREWORKS)?.flightDuration ?: 1) * 0.5 + 0.5
					startFirework(inventory)
					fireworkTimer.reset()
				}
			}

			if (still) {
				rotationRequest { rotation(if (flipFlop) 0.0 else 180.0, 0.0) }.submit()
				flipFlop = !flipFlop
			} else {
				moving = true
				if (prevStill && !player.hasFirework) player.velocity = Vec3d.ZERO
				val rot = vec.yawAndPitch
				rotationRequest { rotation(rot.y, rot.x) }.submit()
			}
		}

		listen<PlayerPacketEvent.Send> { event ->
			if (!player.isGliding) return@listen
			val rot = RotationManager.activeRotation
			val flipFlop = rotFlipFlop.toFloat() * 0.0001f
			rotFlipFlop = !rotFlipFlop
			event.packet = PlayerMoveC2SPacket.Full(player.pos, rot.yawF + flipFlop, rot.pitchF, player.isOnGround, player.horizontalCollision)
		}

		listen<PacketEvent.Send.Pre> { event ->
			if (event.packet !is PlayerMoveC2SPacket || !player.isGliding || flipFlopMode.isFlipFlopping(hasFirework) || moving) return@listen
			if (stillTickTimer.hasSurpassed(packetGap)) {
				stillTickTimer.reset()
				return@listen
			}
			event.cancel()
		}
	}

	private fun SafeContext.findFirework(): ItemStack? {
		val stack = Items.FIREWORK_ROCKET.select()
		return stack.bestItemMatch(player.hotbarStacks) ?: if (inventory) stack.bestItemMatch(player.inventoryStacks) else null
	}
}