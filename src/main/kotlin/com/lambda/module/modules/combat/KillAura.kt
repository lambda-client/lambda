/*
 * Copyright 2026 Lambda
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

import com.lambda.config.ConfigEditor.edit
import com.lambda.config.ConfigEditor.hide
import com.lambda.config.ConfigEditor.hideAllBlocksExcept
import com.lambda.config.Tab
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.settings.blocks.TargetingSettings
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.hotbar.HotbarRequest
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafeAutomated
import com.lambda.util.NamedEnum
import com.lambda.util.item.ItemStackUtils.attackDamage
import com.lambda.util.item.ItemStackUtils.attackSpeed
import com.lambda.util.math.random
import com.lambda.util.player.RotationUtils.lookAtEntity
import com.lambda.util.player.SlotUtils.hotbarStacks
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.util.Hand
import net.minecraft.world.GameMode

object KillAura : Module(
    name = "KillAura",
    description = "Attacks entities",
    tag = ModuleTag.Combat,
    modulePriority = 90
) {
    private const val GeneralTab = "General"
    private const val TargetingTab = "Targeting"

    @Tab(GeneralTab) private val rotate by setting("Rotate", true)
    @Tab(GeneralTab) private val swap by setting("Swap", true, "Swap to the item with the highest damage")
    @Tab(GeneralTab) private val disableWhileGliding by setting("Disable While Gliding", false, "Disables when gliding with an elytra")
    @Tab(GeneralTab) private val damageMode by setting("Damage Mode", DamageMode.DPS)
    @Tab(GeneralTab) private val attackMode by setting("Attack Mode", AttackMode.Cooldown)
    @Tab(GeneralTab) private val cooldownShrink by setting("Cooldown Offset", 0, 0..5, 1) { attackMode == AttackMode.Cooldown }
    @Tab(GeneralTab) private val hitDelay1 by setting("Hit Delay 1", 2.0, 0.0..20.0, 1.0) { attackMode == AttackMode.Delay }
    @Tab(GeneralTab) private val hitDelay2 by setting("Hit Delay 2", 6.0, 0.0..20.0, 1.0) { attackMode == AttackMode.Delay }

    @Tab(TargetingTab) private val targetingSettings by settingBlock(TargetingSettings.CombatSettings(this))

    val target: Entity?
        get() = targetingSettings.target<Entity>()

    private var prevEntity = target
    private var validServerRot = false

    private var lastAttackTime = 0L
    private var hitDelay = 100.0
    private var cooldownFromSwap = false

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
        setDefaultAutomationConfig()
            .withEdits {
                hideAllBlocksExcept(::buildConfig, ::hotbarConfig, ::rotationConfig)
                buildConfig.apply {
                    hide(
                        ::pathing, ::stayInRange, ::collectDrops,
                        ::spleefEntities, ::maxPendingActions, ::actionTimeout,
                        ::maxBuildDependencies, ::blockReach
                    )
                }
                hotbarConfig.apply {
                    ::tickStageMask.edit { defaultValue(mutableSetOf(TickEvent.Pre)) }
                }
            }

        listen<InventoryEvent.HotbarSlot.Update> { cooldownFromSwap = true }

        listen<TickEvent.Pre> {
            if (disableWhileGliding && player.isGliding) return@listen

            target?.let { entity ->
                // Wait until the rotation has a hit result on the entity
                var rotated = true
                if (rotate) runSafeAutomated {
                    val rotationRequest = lookAtEntity(entity)?.rotation?.let { rotationRequest { rotation(it) } }?.submit() ?: return@listen
                    rotated = rotationRequest.done && entity === prevEntity && validServerRot
                    prevEntity = entity
                    validServerRot = rotationRequest.done
                }

                if (swap) {
                    val selection = selectStack().sortByDescending {
                        damageMode.block(this, it)
                    }

                    selection.bestItemMatch(player.hotbarStacks)?.let { bestStack ->
                        val slotId = player.hotbarStacks.indexOf(bestStack)
                        if (!HotbarRequest(slotId, this@KillAura, nowOrNothing = false).submit().done) return@listen
                    }
                }

                if (!rotated) return@listen

                // Cooldown check
                when (attackMode) {
                    AttackMode.Cooldown -> if (player.getAttackCooldownProgress(0.5f) + (cooldownShrink / 20f) < 1.0f && !cooldownFromSwap) return@listen
                    AttackMode.Delay -> if (System.currentTimeMillis() - lastAttackTime < hitDelay) return@listen
                }

                cooldownFromSwap = false

                // Attack
                connection.sendPacket(PlayerInteractEntityC2SPacket.attack(target, player.isSneaking))
                if (interaction.gameMode != GameMode.SPECTATOR) {
                    player.attack(target)
                    player.resetTicksSince()
                }
                if (interactConfig.swing) player.swingHand(Hand.MAIN_HAND)

                lastAttackTime = System.currentTimeMillis()
                hitDelay = (hitDelay1..hitDelay2).random() * 50
            }
        }
    }
}
