package me.zeroeightsix.kami.module.modules.player;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.network.play.client.CPacketPlayer;

import java.util.ArrayList;

/**
 * Created by 086 on 24/01/2018.
 */
@Module.Info(name = "Blink", category = Module.Category.PLAYER)
public class Blink extends Module {

    ArrayList<CPacketPlayer> packets = new ArrayList<>();

    @EventHandler
    public Listener<PacketEvent.Send> listener = new Listener<>(event -> {
       if (isEnabled() && event.getPacket() instanceof CPacketPlayer) {
           event.cancel();
           packets.add((CPacketPlayer) event.getPacket());
       }
    });

    @Override
    protected void onDisable() {
        for (CPacketPlayer cPacketPlayer : packets)
            mc.player.connection.sendPacket(cPacketPlayer);
        packets.clear();
    }

    @Override
    public String getHudInfo() {
        return String.valueOf(packets.size());
    }
}
