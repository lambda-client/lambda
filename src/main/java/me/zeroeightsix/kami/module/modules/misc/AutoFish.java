package me.zeroeightsix.kami.module.modules.misc;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import net.minecraft.network.play.server.SPacketSoundEffect;

/**
 * Created by 086 on 22/03/2018.
 */
@Module.Info(name = "AutoFish", category = Module.Category.MISC, description = "Automatically catch fish")
public class AutoFish extends Module {

    @EventHandler
    private Listener<PacketEvent.Receive> receiveListener = new Listener<>(e -> {
        if (e.getPacket() instanceof SPacketSoundEffect) {
            SPacketSoundEffect pck = (SPacketSoundEffect) e.getPacket();
            if (pck.getSound().getSoundName().toString().toLowerCase().contains("entity.bobber.splash")) {
                if (mc.player.fishEntity == null) return;
                int soundX = (int) pck.getX();
                int soundZ = (int) pck.getZ();
                int fishX = (int) mc.player.fishEntity.posX;
                int fishZ = (int) mc.player.fishEntity.posZ;
                if (kindaEquals(soundX, fishX) && kindaEquals(fishZ, soundZ)) {
                    new Thread(() -> {
                        mc.rightClickMouse();
                        try {
                            Thread.sleep(1000L);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                        mc.rightClickMouse();
                    }).start();
                }
            }
        }
    });

    public boolean kindaEquals(int kara, int ni) {
        return ni == kara || ni == kara - 1 || ni == kara + 1;
    }

}
