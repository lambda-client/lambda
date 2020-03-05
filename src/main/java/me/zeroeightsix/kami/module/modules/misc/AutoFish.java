package me.zeroeightsix.kami.module.modules.misc;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.event.events.PacketEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.network.play.server.SPacketSoundEffect;

import java.util.Random;

/**
 * Created by 086 on 22/03/2018.
 * Updated by Qther on 04/03/20
 * Updated by S-B99 on 04/03/20
 */
@Module.Info(name = "AutoFish", category = Module.Category.MISC, description = "Automatically catch fish")
public class AutoFish extends Module {
    private Setting<Integer> baseDelay = register(Settings.integerBuilder("Throw Delay (ms)").withValue(100).withMinimum(50).withMaximum(1000));
    private Setting<Integer> extraDelay = register(Settings.integerBuilder("Catch Delay (ms)").withValue(300).withMinimum(0).withMaximum(1000));
    private Setting<Integer> variation = register(Settings.integerBuilder("Variation (ms)").withValue(50).withMinimum(0).withMaximum(1000));

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
                        Random ran = new Random();
                        try {
                            Thread.sleep(extraDelay.getValue() + ran.ints(1, -variation.getValue(), variation.getValue()).findFirst().getAsInt());
                        } catch (InterruptedException ignored) { }
                        mc.rightClickMouse();
                        ran = new Random();
                        try {
                            Thread.sleep(baseDelay.getValue() + ran.ints(1, -variation.getValue(), variation.getValue()).findFirst().getAsInt());
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
