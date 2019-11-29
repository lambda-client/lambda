package me.zeroeightsix.kami.module.modules.misc;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.network.play.client.CPacketChatMessage;

/**
 * Created by d1gress/Qther on 24/11/2019.
 * Updated by @S-B99 on 25/11/19
 */
@Module.Info(name = "AutoQMain", description = "Automatically does \"/queue main\" every 7.1 minutes.", category = Module.Category.MISC)
public class AutoQMain extends Module {

    private Setting<Boolean> debug = register(Settings.b("Debug", true));
    private Setting<Boolean> debugWarn = register(Settings.b("Connection Warning", true));

    private static long startTime = 0;
    private static long warnTime = 0;
    private static long errorTime = 0;

    @Override
    public void onUpdate() {
        if (mc.player == null) return;

        if (Minecraft.getMinecraft().getCurrentServerData() == null) {
            startTime = 0;
            warnTime = 0;
            if (debugWarn.getValue()) {
                if (errorTime == 0 || errorTime <= System.currentTimeMillis() - 427000)
                    errorTime = System.currentTimeMillis();
                if (errorTime + 426000 <= System.currentTimeMillis()) {
                    Command.sendErrorMessage("&l[AutoQMain] &4Error: &r&cNot connected to a server");
                    errorTime = System.currentTimeMillis();
                }
            }
        }
        if (Minecraft.getMinecraft().getCurrentServerData() !=null && !Minecraft.getMinecraft().getCurrentServerData().serverIP.equalsIgnoreCase("2b2t.org")) {
            errorTime = 0;
            if (debugWarn.getValue()) {
                if (warnTime == 0 || warnTime <= System.currentTimeMillis() - warnTime)
                    warnTime = System.currentTimeMillis();
                if (warnTime + 426000 <= System.currentTimeMillis()) {
                    Command.sendWarningMessage("&l[AutoQMain] &6Warning: &r&6You are not connected to 2b2t.org");
                    if (debug.getValue()) {
                        Command.sendChatMessage("&l[AutoQMain] &r&7Run &b/queue main&7 at " + System.currentTimeMillis());
                    }
                    Minecraft.getMinecraft().playerController.connection.sendPacket(new CPacketChatMessage("/queue main"));
                    warnTime = System.currentTimeMillis();
                }
            }
        }
        if (Minecraft.getMinecraft().getCurrentServerData() !=null && Minecraft.getMinecraft().getCurrentServerData().serverIP.equalsIgnoreCase("2b2t.org")) {
            errorTime = 0;
            warnTime = 0;
            if (startTime == 0 || startTime <= System.currentTimeMillis() - 427000) startTime = System.currentTimeMillis();
            if (startTime + 426000 <= System.currentTimeMillis()) {
                if (debug.getValue()) {
                    Command.sendChatMessage("&l[AutoQMain] &r&7Run &b/queue main&7 at " + System.currentTimeMillis());
                }
                Minecraft.getMinecraft().playerController.connection.sendPacket(new CPacketChatMessage("/queue main"));
                startTime = System.currentTimeMillis();
            }
        }
    }
}
