package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.network.play.client.CPacketPlayer;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by 086 on 24/01/2018.
 */
@Module.Info(name = "Blink", category = Module.Category.PLAYER)
public class Blink extends Module {

    Queue<CPacketPlayer> packets = new LinkedList<>();

    @EventHandler
    public Listener<PacketEvent.Send> listener = new Listener<>(event -> {
       if (isEnabled() && event.getPacket() instanceof CPacketPlayer) {
           event.cancel();
           packets.add((CPacketPlayer) event.getPacket());
       }
    });

    @Override
    protected void onDisable() {
        while (!packets.isEmpty())
            mc.player.connection.sendPacket(packets.poll());
    }

    @Override
    public String getHudInfo() {
        return String.valueOf(packets.size());
    }
}
