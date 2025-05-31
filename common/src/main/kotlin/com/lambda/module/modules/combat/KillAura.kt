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

import com.lambda.config.groups.InteractionSettings
import com.lambda.config.groups.RotationSettings
import com.lambda.config.groups.Targeting
import com.lambda.context.SafeContext
import com.lambda.event.events.PlayerPacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.interaction.material.container.ContainerManager.transfer
import com.lambda.interaction.material.container.containers.MainHandContainer
import com.lambda.interaction.request.rotation.RotationManager
import com.lambda.interaction.request.rotation.visibilty.lookAtEntity
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.util.item.ItemStackUtils.attackDamage
import com.lambda.util.item.ItemStackUtils.itemAttackSpeed
import com.lambda.util.math.random
import com.lambda.util.player.SlotUtils.combined
import com.lambda.util.world.raycast.InteractionMask
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.entity.LivingEntity
import net.minecraft.util.Hand
import net.minecraft.util.math.Vec3d

object KillAura : Module(
    name = "KillAura",
    description = "Attacks entities",
    defaultTags = setOf(ModuleTag.COMBAT, ModuleTag.RENDER)
) {
    private val page by setting("Page", Page.Interact)

    // Interact
    private val interactionSettings = InteractionSettings(this, InteractionMask.Entity) { page == Page.Interact }
    private val swap by setting("Swap", true, "Swap to the item with the highest damage")
    private val attackMode by setting("Attack Mode", AttackMode.Cooldown) { page == Page.Interact }
    private val cooldownOffset by setting("Cooldown Offset", 0, -5..5, 1) { page == Page.Interact && attackMode == AttackMode.Cooldown }
    private val hitDelay1 by setting("Hit Delay 1", 2.0, 0.0..20.0, 1.0) { page == Page.Interact && attackMode == AttackMode.Delay }
    private val hitDelay2 by setting("Hit Delay 2", 6.0, 0.0..20.0, 1.0) { page == Page.Interact && attackMode == AttackMode.Delay }

    // Targeting
    private val targeting = Targeting.Combat(this) { page == Page.Targeting }

    // Aiming
    private val rotate by setting("Rotate", true) { page == Page.Aiming }
    private val rotation = RotationSettings(this) { page == Page.Aiming && rotate }

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

    enum class Page {
        Interact,
        Targeting,
        Aiming
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
                    val selection = player.combined
                        .maxBy { stack -> stack.attackDamage } // ToDo: Write our own enchantment utils
                        .select()

                    if (!selection.selector(player.mainHandStack)) {
                        selection.transfer(MainHandContainer)
                            ?.finally {
                                // Wait until the rotation has a hit result on the entity
                                if (lookAtEntity(entity).requestBy(rotation).done) runAttack(entity)
                            }?.run()

                        return@listen
                    }
                }

                // Wait until the rotation has a hit result on the entity
                if (lookAtEntity(entity).requestBy(rotation).done) runAttack(entity)
            }
        }

        onEnable { reset() }
        onDisable { reset() }
    }

    private fun SafeContext.runAttack(target: LivingEntity) {
        // Cooldown check
        when (attackMode) {
            AttackMode.Cooldown -> {
                if (player.lastAttackedTicks < 20/player.itemAttackSpeed + cooldownOffset) return
            }

            AttackMode.Delay -> {
                if (System.currentTimeMillis() - lastAttackTime < hitDelay) return
            }
        }

        // Rotation check
        run {
            if (!rotate) return@run
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
        if (interactionSettings.swingHand) player.swingHand(Hand.MAIN_HAND)

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
