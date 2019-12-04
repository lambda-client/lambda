package me.zeroeightsix.kami.module.modules.sdashb.render;

import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.module.Module;

/***
 * Created by @S-B99 on 26/11/19
 */
@Module.Info(name = "Cape", category = Module.Category.RENDER, description = "Shiny custom donator cape")
public class CapeGUI extends Module {

    public void onEnable() {
        if (mc.player == null) {
            return;
        }
        Command.sendWarningMessage("[Cape] Note: you need donator jar and to restart Minecraft is cape is not working");
    }
//    private static CapeGUI INSTANCE;
//
//    public CapeGUI() {
//        INSTANCE = this;
//    }
//
//    public static boolean shouldCape() {
//        return INSTANCE.isEnabled();
//    }

//    public boolean isEnabled() {
//        return INSTANCE.isEnabled();
//    }

}
