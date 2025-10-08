/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.combat

import com.lambda.config.groups.InteractSettings
import com.lambda.config.groups.InteractionSettings
import com.lambda.config.groups.RotationSettings
import com.lambda.config.groups.Targeting
import com.lambda.context.SafeContext
import com.lambda.event.events.PlayerPacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.ContainerManager.transfer
import com.lambda.interaction.material.container.containers.MainHandContainer
import com.lambda.interaction.request.rotating.RotationManager
import com.lambda.interaction.request.rotating.visibilty.lookAtEntity
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.util.NamedEnum
import com.lambda.util.item.ItemStackUtils.attackDamage
import com.lambda.util.item.ItemStackUtils.attackSpeed
import com.lambda.util.item.ItemStackUtils.equal
import com.lambda.util.math.random
import com.lambda.util.player.SlotUtils.hotbarAndStorage
import com.lambda.util.world.raycast.InteractionMask
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.entity.LivingEntity
import net.minecraft.util.Hand
import net.minecraft.util.math.Vec3d

object KillAura : Module(
    name = "KillAura",
    description = "Attacks entities",
    tag = ModuleTag.COMBAT,
) {
    // Interact
    private val interactionSettings = InteractionSettings(this, Group.Interaction, InteractionMask.Entity)
    private val interactSettings = InteractSettings(this, listOf(Group.Interact))
    private val swap by setting("Swap", true, "Swap to the item with the highest damage").group(Group.Interact)
    private val attackMode by setting("Attack Mode", AttackMode.Cooldown).group(Group.Interact)
    private val cooldownOffset by setting("Cooldown Offset", 0, -5..5, 1) { attackMode == AttackMode.Cooldown }.group(Group.Interact)
    private val hitDelay1 by setting("Hit Delay 1", 2.0, 0.0..20.0, 1.0) { attackMode == AttackMode.Delay }.group(Group.Interact)
    private val hitDelay2 by setting("Hit Delay 2", 6.0, 0.0..20.0, 1.0) { attackMode == AttackMode.Delay }.group(Group.Interact)

    // Targeting
    private val targeting = Targeting.Combat(this, Group.Targeting)

    // Aiming
    private val rotate by setting("Rotate", true).group(Group.Aiming)
    private val rotation = RotationSettings(this, Group.Aiming) { rotate }

    val target: LivingEntity?
        get() = targeting.target()

    private var shakeRandom = Vec3d.ZERO
    private var speedMultiplier = 1.0

    private var lastAttackTime = 0L
    private var hitDelay = 100.0

    private var prevY = 0.0
    private var lastY = 0.0
    private var lastOnGround = true
    private var onGroundTicks = 0

    enum class Group(override val displayName: String) : NamedEnum {
        Interaction("Interaction"),
        Interact("Interact"),
        Targeting("Targeting"),
        Aiming("Aiming")
    }

    enum class AttackMode {
        Cooldown,
        Delay
    }

    init {
        listen<PlayerPacketEvent.Pre>(Int.MIN_VALUE) { event ->
            prevY = lastY
            lastY = event.position.y
            lastOnGround = event.onGround
        }

        listen<TickEvent.Pre> {
            target?.let { entity ->
                if (swap) {
                    val selection = selectStack().sortByDescending { player.attackDamage(stack = it) }

                    if (!selection.bestItemMatch(player.hotbarAndStorage).equal(player.mainHandStack))
                        selection.transfer(MainHandContainer)?.run()
                }

                // Wait until the rotation has a hit result on the entity
                if (lookAtEntity(entity).requestBy(this@KillAura).done) runAttack(entity)
            }
        }

        onEnable { reset() }
        onDisable { reset() }
    }

    private fun SafeContext.runAttack(target: LivingEntity) {
        // Cooldown check
        when (attackMode) {
            AttackMode.Cooldown -> if (player.lastAttackedTicks < 1 / player.attackSpeed() * 20 + cooldownOffset) return
            AttackMode.Delay -> if (System.currentTimeMillis() - lastAttackTime < hitDelay) return
        }

        // Rotation check
        if (rotate) {
            val angle = RotationManager.activeRotation

            if (interactionSettings.strictRayCast) {
                val cast = angle.rayCast(interactionSettings.attackReach)
                if (cast?.entityResult?.entity != target) return
            }

            // Perform a raycast without checking the environment
            angle.castBox(target.boundingBox, interactionSettings.attackReach) ?: return
        }

        // Attack
        interaction.attackEntity(player, target)
        if (interactSettings.swingHand) player.swingHand(Hand.MAIN_HAND)

        lastAttackTime = System.currentTimeMillis()
        hitDelay = (hitDelay1..hitDelay2).random() * 50
    }

    private fun reset() {
        speedMultiplier = 1.0
        shakeRandom = Vec3d.ZERO

        lastY = 0.0
        prevY = 0.0
        lastOnGround = true
        onGroundTicks = 0

        lastAttackTime = 0L
        hitDelay = 100.0
    }
}
