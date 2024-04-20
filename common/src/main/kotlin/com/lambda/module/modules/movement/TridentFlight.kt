package com.lambda.module.modules.movement

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.entity.MovementType
import net.minecraft.item.TridentItem
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt


object TridentFlight : Module(
    name = "TridentFlight",
    description = "Allows you to fly with tridents",
    defaultTags = setOf(ModuleTag.MOVEMENT, ModuleTag.BYPASS, ModuleTag.GRIM),
) {
    private val bounce by setting("Bounce", true, description = "Automatically use the trident")
    private val delay by setting("Delay", 0, 0..20, 1, description = "Delay in ticks before releasing the trident", visibility = { bounce })
    private val tridentSpeed by setting("Speed Factor", 1.0, 0.1..5.0, 0.1, description = "Speed factor of the trident flight")

    val rain by setting("Rain", true, description = "Set rain client-side to allow flight")

    private var ticks = 0

    init {
        listener<TickEvent.Pre> {
            if (ticks >= delay &&
                player.activeItem.item is TridentItem)
            {
                val tridentSlot = player.inventory.selectedSlot
                val spoofSlot = player.inventory.swappableHotbarSlot

                connection.sendPacket(UpdateSelectedSlotC2SPacket(tridentSlot))
                connection.sendPacket(PlayerActionC2SPacket(PlayerActionC2SPacket.Action.RELEASE_USE_ITEM, BlockPos.ORIGIN, Direction.DOWN))

                val level = EnchantmentHelper.getRiptide(player.activeItem)
                val yaw = player.yaw * (Math.PI / 180)
                val pitch = player.pitch * (Math.PI / 180)

                val x = -sin(yaw) * cos(pitch)
                val y = -sin(pitch)
                val z = cos(yaw) * cos(pitch)

                val dot = sqrt(x * x + y * y + z * z)
                val multiplier = (3 * ((1.0 + level) / 4.0)) / dot

                player.addVelocity(
                    x * multiplier * tridentSpeed,
                    y * multiplier * tridentSpeed,
                    z * multiplier * tridentSpeed
                )

                if (player.isOnGround)
                    player.move(MovementType.SELF, Vec3d(0.0, 1.19999, 0.0))

                connection.sendPacket(UpdateSelectedSlotC2SPacket(spoofSlot))

                ticks = 0
            }

            ticks++
        }
    }
}
