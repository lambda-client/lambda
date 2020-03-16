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
 * Updated by Qther on 05/03/20
 * Updated by S-B99 on 07/03/20
 */
@Module.Info(name = "AutoFish", category = Module.Category.MISC, description = "Automatically catch fish")
public class AutoFish extends Module {
    private Setting<Boolean> defaultSetting = register(Settings.b("Defaults", false));
    private Setting<Integer> baseDelay = register(Settings.integerBuilder("Throw Delay").withValue(450).withMinimum(50).withMaximum(1000).build());
    private Setting<Integer> extraDelay = register(Settings.integerBuilder("Catch Delay").withValue(300).withMinimum(0).withMaximum(1000).build());
    private Setting<Integer> variation = register(Settings.integerBuilder("Variation").withValue(50).withMinimum(0).withMaximum(1000).build());

    Random random;

    public void onUpdate() {
        if (defaultSetting.getValue()) {
            baseDelay.setValue(450);
            extraDelay.setValue(300);
            variation.setValue(50);
            defaultSetting.setValue(false);
            Command.sendChatMessage(getChatName() + " Set to defaults!");
            Command.sendChatMessage(getChatName() + " Close and reopen the " + getName() + " settings menu to see changes");
        }
    }

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
                        random = new Random();
                        try {
                            Thread.sleep(extraDelay.getValue() + random.ints(1, -variation.getValue(), variation.getValue()).findFirst().getAsInt());
                        } catch (InterruptedException ignored) { }
                        mc.rightClickMouse();
                        random = new Random();
                        try {
                            Thread.sleep(baseDelay.getValue() + random.ints(1, -variation.getValue(), variation.getValue()).findFirst().getAsInt());
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
