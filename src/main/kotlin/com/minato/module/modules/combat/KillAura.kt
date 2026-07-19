
package com.minato.module.modules.combat

import com.minato.config.ConfigEditor.editSetting
import com.minato.config.ConfigEditor.hide
import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.Tab
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.blocks.TargetingSettings
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.event.events.InventoryEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.hotbar.HotbarRequest
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.interaction.material.StackSelection.Companion.selectStack
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafeAutomated
import com.minato.util.NamedEnum
import com.minato.util.item.ItemStackUtils.attackDamage
import com.minato.util.item.ItemStackUtils.attackSpeed
import com.minato.util.math.random
import com.minato.util.player.RotationUtils.lookAtEntity
import com.minato.util.player.SlotUtils.hotbarStacks
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket
import net.minecraft.util.Hand
import net.minecraft.world.GameMode

object KillAura : Module(
    name = "KillAura",
    description = "Attacks entities",
    tag = ModuleTag.COMBAT,
    modulePriority = 90
) {
    private const val GENERAL_TAB = "General"
    private const val TARGETING_TAB = "Targeting"

    @Tab(GENERAL_TAB) private val rotate by setting("Rotate", true)
    @Tab(GENERAL_TAB) private val swap by setting("Swap", true, "Swap to the item with the highest damage")
    @Tab(GENERAL_TAB) private val disableWhileGliding by setting("Disable While Gliding", false, "Disables when gliding with an elytra")
    @Tab(GENERAL_TAB) private val damageMode by setting("Damage Mode", DamageMode.Dps)
    @Tab(GENERAL_TAB) private val attackMode by setting("Attack Mode", AttackMode.Cooldown)
    @Tab(GENERAL_TAB) private val cooldownShrink by setting("Cooldown Offset", 0, 0..5, 1) { attackMode == AttackMode.Cooldown }
    @Tab(GENERAL_TAB) private val hitDelay1 by setting("Hit Delay 1", 2.0, 0.0..20.0, 1.0) { attackMode == AttackMode.Delay }
    @Tab(GENERAL_TAB) private val hitDelay2 by setting("Hit Delay 2", 6.0, 0.0..20.0, 1.0) { attackMode == AttackMode.Delay }

    @Tab(TARGETING_TAB) private val targetingSettings by configBlock(TargetingSettings.CombatSettings(this))

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
        Dps("Damage Per Second", { player.attackDamage(stack = it) * player.attackSpeed(stack = it) }),
        Total("Hit Damage", { player.attackDamage(stack = it) })
    }

    init {
        setDefaultAutomationConfig()
            .withEdits {
                hideAllExcept(::buildConfig, ::hotbarConfig, ::rotationConfig)
                buildConfig.apply {
                    hide(
                        ::pathing, ::collectDrops,
                        ::spleefEntities, ::maxPendingActions, ::actionTimeout,
                        ::maxBuildDependencies, ::blockReach
                    )
                }
                hotbarConfig.apply {
                    ::tickStageMask.editSetting { defaultValue(mutableSetOf(TickEvent.Pre)) }
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
