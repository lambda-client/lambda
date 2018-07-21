package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.network.play.client.CPacketPlayer;

/**
 * Created by 086 on 19/11/2017.
 */
@Module.Info(category = Module.Category.PLAYER, description = "Prevents fall damage", name = "NoFall")
public class NoFall extends Module {

    @EventHandler
    public Listener<PacketEvent.Send> sendListener = new Listener<PacketEvent.Send>(event -> {
        if (event.getPacket() instanceof CPacketPlayer) {
            ((CPacketPlayer) event.getPacket()).onGround = true;
        }
    });

}
