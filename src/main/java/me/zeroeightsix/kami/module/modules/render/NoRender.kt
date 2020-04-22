package me.zeroeightsix.kami.module.modules.render

import me.zero.alpine.listener.EventHandler
import me.zero.alpine.listener.EventHook
import me.zero.alpine.listener.Listener
import me.zeroeightsix.kami.event.events.PacketEvent.Receive
import me.zeroeightsix.kami.event.events.WorldCheckLightForEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Setting
import me.zeroeightsix.kami.setting.Settings
import net.minecraft.network.play.server.*
import net.minecraft.world.EnumSkyBlock
import net.minecraftforge.client.event.RenderBlockOverlayEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent

/**
 * Created by 086 on 4/02/2018.
 * Updated by dominikaaaa on 14/04/20
 *
 * Skylight Updates taken from https://github.com/fr1kin/ForgeHax/blob/1a4f98d/src/main/java/com/matt/forgehax/mods/NoSkylightUpdates.java
 */
@Module.Info(
        name = "NoRender",
        category = Module.Category.RENDER,
        description = "Ignore entity spawn packets"
)
class NoRender : Module() {
    private val mob = register(Settings.b("Mob", false))
    private val sand = register(Settings.b("Sand", false))
    private val gEntity = register(Settings.b("GEntity", false))
    private val `object` = register(Settings.b("Object", false))
    @JvmField
    var items: Setting<Boolean> = register(Settings.b("Items", false))
    private val xp = register(Settings.b("XP", false))
    private val paint = register(Settings.b("Paintings", false))
    private val fire = register(Settings.b("Fire", true))
    private val explosion = register(Settings.b("Explosions", true))
    @JvmField
    var beacon: Setting<Boolean> = register(Settings.b("Beacon Beams", false))
    private val skylight = register(Settings.b("Skylight Updates", false))

    @EventHandler
    var receiveListener = Listener(EventHook { event: Receive ->
        val packet = event.packet
        if (packet is SPacketSpawnMob && mob.value ||
                packet is SPacketSpawnGlobalEntity && gEntity.value ||
                packet is SPacketSpawnObject && `object`.value ||
                packet is SPacketSpawnExperienceOrb && xp.value ||
                packet is SPacketSpawnObject && sand.value ||
                packet is SPacketExplosion && explosion.value ||
                packet is SPacketSpawnPainting && paint.value) event.cancel()
    })

    @EventHandler
    var blockOverlayEventListener = Listener(EventHook { event: RenderBlockOverlayEvent -> if (fire.value && event.overlayType == RenderBlockOverlayEvent.OverlayType.FIRE) event.isCanceled = true })

    @SubscribeEvent
    fun onLightingUpdate(event: WorldCheckLightForEvent) {
        if (skylight.value && event.enumSkyBlock == EnumSkyBlock.SKY) {
            event.isCanceled = true
        }
    }
}