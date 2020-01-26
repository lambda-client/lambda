package me.zeroeightsix.kami.module.modules.misc;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPacketChatMessage;

import java.text.SimpleDateFormat;
import java.util.Date;

/***
 * @author S-B99
 * Updated by @S-B99 on 29/11/19
 * Updated by d1gress/Qther on 5/12/2019
 */
@Module.Info(name = "AutoQMain", description = "Automatically does \"/queue main\" every X minutes.", category = Module.Category.MISC)
public class AutoQMain extends Module {

    private Setting<Boolean> debug = register(Settings.b("Debug", true));
    private Setting<Boolean> debugWarn = register(Settings.b("Connection Warning", true));
    private Setting<Boolean> endDi = register(Settings.b("Dimension Warning", true));
    private Setting<Double> delay = this.register(Settings.doubleBuilder("Wait time").withMinimum(0.2).withValue(7.1).withMaximum(10.0).build());

    private double delayTime;
    private double oldDelay = 0;

    @Override
    public void onUpdate() {
        if (mc.player == null) return;
        if (oldDelay == 0) oldDelay = delay.getValue();
        else if (oldDelay != delay.getValue()) {
            delayTime = delay.getValue();
            oldDelay = delay.getValue();
        }

        if (delayTime <= 0) {
            delayTime = (int) (delay.getValue() * 2400);
        } else if (delayTime > 0) {
            delayTime--;
            return;
        }
        if (mc.player == null) return;

        if (Minecraft.getMinecraft().getCurrentServerData() == null) {
            Command.sendWarningMessage("[AutoQMain] &l&6Warning: &r&6You are on singleplayer");
            return;
        }
        if (!Minecraft.getMinecraft().getCurrentServerData().serverIP.equalsIgnoreCase("2b2t.org") && debugWarn.getValue()) {
            Command.sendWarningMessage("[AutoQMain] &l&6Warning: &r&6You are not connected to 2b2t.org");
        }
        if (mc.player.dimension != 1 && endDi.getValue()) {
            Command.sendWarningMessage("[AutoQMain] &l&6Warning: &r&6You are not in the end. Not running &b/queue main&7.");
//            Command.sendWarningMessage("[AutoQMain] " + mc.player.dimension);
            return;
        }
        if (debug.getValue()) {
            SimpleDateFormat formatter = new SimpleDateFormat("HH:mm:ss");
            Date date = new Date(System.currentTimeMillis());
            System.out.println(formatter.format(date));
            Command.sendChatMessage("&7Run &b/queue main&7 at " + (formatter.format(date)));
        }
        Minecraft.getMinecraft().playerController.connection.sendPacket(new CPacketChatMessage("/queue main"));
    }

    public void onDisable() {
        delayTime = 0;
    }
}
