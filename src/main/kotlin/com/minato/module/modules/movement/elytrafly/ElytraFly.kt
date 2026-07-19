
package com.minato.module.modules.movement.elytrafly

import com.minato.config.ConfigEditor.editSetting
import com.minato.config.ConfigEditor.forEachSetting
import com.minato.config.ConfigEditor.hideAllExcept
import com.minato.config.Tab
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.config.withEdits
import com.minato.event.events.ClientEvent
import com.minato.event.events.MovementEvent
import com.minato.event.events.PacketEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.modules.movement.elytrafly.modes.BounceElytraFly
import com.minato.module.modules.movement.elytrafly.modes.GeneralElytraFly
import com.minato.module.modules.movement.elytrafly.modes.GrimControlElytraFly
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.extension.isElytraFlying
import com.minato.util.player.MovementUtils.addSpeed
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.sound.SoundEvents

object ElytraFly : Module(
    name = "ElytraFly",
    description = "Modifies elytra functionality to allow for more control",
    tag = ModuleTag.MOVEMENT,
) {
    @JvmStatic val mode by setting("Fly Mode", FlyMode.Bounce)
        .onValueChange { from, to ->
            from.elytraFly.onDisableListeners.forEach { it() }
            to.elytraFly.onEnableListeners.forEach { it() }
        }

    private val boostSpeed by setting("Boost", 0.00, 0.0..0.5, 0.005, description = "Speed to add when flying")
    private val rocketSpeed by setting("Rocket Speed", 1.0, 0.0..2.0, 0.01, description = "Speed multiplier that the rocket gives you")
    private val mute by setting("Mute Elytra", false, "Mutes the elytra sound when gliding")
    @JvmStatic val fakeFly by setting("Fake Fly", false, "Rapidly swaps the chestplate and elytra to give the appearance the player is flying without an elytra. May also reduce durability loss")

    private const val BOUNCE_TAB = "Bounce"
    private const val CONTROL_TAB = "Control"
    private const val GRIM_CONTROL_TAB = "Grim Control"
    private const val PACKET_TAB = "Packet"
    private const val GENERAL_TAB = "None"

    @Tab(GENERAL_TAB) @JvmStatic val generalMode by configBlock(GeneralElytraFly(this))
        .withEdits { forEachSetting { visibility { old -> { old() && mode == FlyMode.General } } } }
    @Tab(BOUNCE_TAB) @JvmStatic val bounceMode by configBlock(BounceElytraFly(this))
        .withEdits { forEachSetting { visibility { old -> { old() && mode == FlyMode.Bounce } } } }
    @Tab(GRIM_CONTROL_TAB) @JvmStatic val grimControlMode by configBlock(GrimControlElytraFly(this))
        .withEdits { forEachSetting { visibility { old -> { old() && mode == FlyMode.GrimControl } } } }
//    @Tab(CONTROL_TAB) @JvmStatic val controlMode by configBlock(ControlElytraFly(this))
//    @Tab(PACKET_TAB) @JvmStatic val packetMode by configBlock(PacketElytraFly(this))

    init {
        setDefaultAutomationConfig()
            .withEdits {
                hotbarConfig::tickStageMask.editSetting { defaultValue(TickEvent.ALL_STAGES.toMutableList()) }
	            hideAllExcept(::inventoryConfig, ::rotationConfig)
            }

        onEnable { mode.elytraFly.onEnableListeners.forEach { it() } }
        onDisable { mode.elytraFly.onDisableListeners.forEach { it() } }

        listen<PacketEvent.Receive.Pre> { event ->
            if (event.packet !is PlayerPositionLookS2CPacket) return@listen
            mode.elytraFly.onFlagListeners.forEach { it() }
        }

        listen<MovementEvent.Player.Pre> {
            if (player.isElytraFlying && !player.isUsingItem) {
                addSpeed(boostSpeed)
            }
        }

        listen<ClientEvent.Sound> { event ->
            if (!mute) return@listen
            if (event.sound.id != SoundEvents.ITEM_ELYTRA_FLYING.id) return@listen
            event.cancel()
        }
    }

    @JvmStatic
    fun boostRocket() = runSafe {
	    val vec = player.rotationVector
	    val velocity = player.velocity

	    val d = 1.5 * rocketSpeed
	    val e = 0.1 * rocketSpeed

	    player.velocity = velocity.add(
		    vec.x * e + (vec.x * d - velocity.x) * 0.5,
		    vec.y * e + (vec.y * d - velocity.y) * 0.5,
		    vec.z * e + (vec.z * d - velocity.z) * 0.5
	    )
    }

    enum class FlyMode(private val elytraFlyGetter: () -> ElytraFlyMode) {
        Bounce({ bounceMode }),
//        Control({ controlMode }),
        GrimControl({ grimControlMode }),
//        Packet({ packetMode }),
        General({ generalMode });

        val elytraFly get() = elytraFlyGetter()
    }
}