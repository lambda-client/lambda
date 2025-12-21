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

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.config.groups.Targeting
import com.lambda.context.SafeContext
import com.lambda.event.events.PlayerPacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.Request.Companion.submit
import com.lambda.interaction.managers.hotbar.HotbarRequest
import com.lambda.interaction.managers.rotating.RotationRequest
import com.lambda.interaction.managers.rotating.visibilty.lookAtEntity
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeAutomated
import com.lambda.util.NamedEnum
import com.lambda.util.item.ItemStackUtils.attackDamage
import com.lambda.util.item.ItemStackUtils.attackSpeed
import com.lambda.util.math.random
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.entity.LivingEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.Hand

object KillAura : Module(
    name = "KillAura",
    description = "Attacks entities",
    tag = ModuleTag.COMBAT,
) {
    // Interact
    private val rotate by setting("Rotate", true).group(Group.General)
    private val swap by setting("Swap", true, "Swap to the item with the highest damage").group(Group.General)
    private val damageMode by setting("Damage Mode", DamageMode.DPS).group(Group.General)
    private val attackMode by setting("Attack Mode", AttackMode.Cooldown).group(Group.General)
    private val cooldownShrink by setting("Cooldown Offset", 0, 0..5, 1) { attackMode == AttackMode.Cooldown }.group(Group.General)
    private val hitDelay1 by setting("Hit Delay 1", 2.0, 0.0..20.0, 1.0) { attackMode == AttackMode.Delay }.group(Group.General)
    private val hitDelay2 by setting("Hit Delay 2", 6.0, 0.0..20.0, 1.0) { attackMode == AttackMode.Delay }.group(Group.General)

    // Targeting
    private val targeting = Targeting.Combat(this, Group.Targeting)

    val target: LivingEntity?
        get() = targeting.target()

    private var prevEntity = target
    private var validServerRot = false

    private var lastAttackTime = 0L
    private var hitDelay = 100.0

    private var prevY = 0.0
    private var lastY = 0.0
    private var lastOnGround = true

    enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Targeting("Targeting"),
    }

    enum class AttackMode {
        Cooldown,
        Delay
    }

    @Suppress("unused")
    enum class DamageMode(override val displayName: String, val block: SafeContext.(ItemStack) -> Double) : NamedEnum {
        DPS("Damage Per Second", { player.attackDamage(stack = it) * player.attackSpeed(stack = it) }),
        Total("Hit Damage", { player.attackDamage(stack = it) })
    }

    init {
        setDefaultAutomationConfig {
            applyEdits {
                hideAllGroupsExcept(buildConfig, hotbarConfig, rotationConfig)
                buildConfig.apply {
                    hide(::pathing, ::stayInRange, ::collectDrops, ::spleefEntities, ::maxPendingActions, ::actionTimeout, ::maxBuildDependencies, ::blockReach)
                }
            }
        }

        listen<PlayerPacketEvent.Pre>(Int.MIN_VALUE) { event ->
            prevY = lastY
            lastY = event.position.y
            lastOnGround = event.onGround
        }

        listen<TickEvent.Pre> {
            target?.let { entity ->
                // Wait until the rotation has a hit result on the entity
                if (rotate) runSafeAutomated {
                    val rotationRequest = RotationRequest(lookAtEntity(entity)?.rotation ?: return@listen, this@KillAura).submit()
                    val canContinue = !rotationRequest.done || entity !== prevEntity || !validServerRot
                    prevEntity = entity
                    validServerRot = rotationRequest.done
                    if (canContinue) return@listen
                }

                if (swap) {
	                val selection = selectStack().sortByDescending {
		                damageMode.block(this, it)
	                }

                    selection.bestItemMatch(player.hotbar)?.let { bestStack ->
                        val slotId = player.hotbar.indexOf(bestStack)
                        if (!submit(HotbarRequest(slotId, this@KillAura, nowOrNothing = false)).done) return@listen
                    }
                }

                // Cooldown check
                when (attackMode) {
                    AttackMode.Cooldown -> if (player.getAttackCooldownProgress(0.5f) + (cooldownShrink / 20f) < 1.0f) return@listen
                    AttackMode.Delay -> if (System.currentTimeMillis() - lastAttackTime < hitDelay) return@listen
                }

                // Attack
                interaction.attackEntity(player, target)
                if (interactConfig.swing) player.swingHand(Hand.MAIN_HAND)

                lastAttackTime = System.currentTimeMillis()
                hitDelay = (hitDelay1..hitDelay2).random() * 50
            }
        }

        onEnable { reset() }
        onDisable { reset() }
    }

    private fun reset() {
        lastY = 0.0
        prevY = 0.0
        lastOnGround = true

        lastAttackTime = 0L
        hitDelay = 100.0
    }
}
