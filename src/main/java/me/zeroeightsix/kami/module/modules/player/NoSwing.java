package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.network.play.client.CPacketAnimation;

/**
 * Created 13 August 2019 by hub
 * Updated 14 November 2019 by hub
 */
@Module.Info(name = "NoSwing", category = Module.Category.PLAYER, description = "Prevents arm swing animation server side")
public class NoSwing extends Module {

    @EventHandler
    public Listener<PacketEvent.Send> listener = new Listener<>(event -> {
        if (event.getPacket() instanceof CPacketAnimation) {
            event.cancel();
        }
    });

}
