package me.zeroeightsix.kami.module.modules.misc;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.entity.passive.AbstractChestHorse;
import net.minecraft.network.play.client.CPacketUseEntity;

/*
 * by ionar2
 */
@Module.Info(
        name = "MountBypass",
        category = Module.Category.MISC,
        description = "Might allow you to mount chested animals on servers that block it",
        alwaysListening = false
)
public class MountBypass extends Module
{
    @EventHandler
    private Listener<PacketEvent.Send> onPacketEventSend = new Listener<>(event ->
    {
        if (event.getPacket() instanceof CPacketUseEntity)
        {
            CPacketUseEntity packet = (CPacketUseEntity)event.getPacket();

            if (packet.getEntityFromWorld(mc.world) instanceof AbstractChestHorse)
            {
                if (packet.getAction() == CPacketUseEntity.Action.INTERACT_AT)
                {
                    event.cancel();
                }
            }
        }
    });
}
