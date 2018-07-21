package me.zeroeightsix.kami.module.modules.render;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.*;

/**
 * Created by 086 on 4/02/2018.
 */
@Module.Info(name = "NoRender", category = Module.Category.RENDER, description = "Ignore entity spawn packets")
public class NoRender extends Module {

    @Setting(name = "Mob") private boolean mob = true;
    @Setting(name = "GEntity") private boolean gentity = true;
    @Setting(name = "Object") private boolean object = true;
    @Setting(name = "XP") private boolean xp = true;
    @Setting(name = "Painting") private boolean paint = true;

    @EventHandler
    public Listener<PacketEvent.Receive> receiveListener = new Listener<>(event -> {
        Packet packet = event.getPacket();
        if ((packet instanceof SPacketSpawnMob && mob) ||
                (packet instanceof SPacketSpawnGlobalEntity && gentity) ||
                (packet instanceof SPacketSpawnObject && object) ||
                (packet instanceof SPacketSpawnExperienceOrb && xp) ||
                (packet instanceof SPacketSpawnPainting && paint))
            event.cancel();
    });

}
