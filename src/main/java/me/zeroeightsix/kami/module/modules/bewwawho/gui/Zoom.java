package me.zeroeightsix.kami.module.modules.bewwawho.gui;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/***
 * @author S-B99
 * Created by S-B99 on 20/12/19
 */
@Module.Info(name = "Zoom", category = Module.Category.GUI, description = "Configures FOV", showOnArray = Module.ShowOnArray.OFF)
public class FovScale extends Module {
    private Setting<Integer> scaleGlobal = this.register(Settings.integerBuilder("Scale").withMinimum(30).withValue(50).withMaximum(150).build());

    public void onUpdate() {
        if (mc.player == null) return;
        mc.gameSettings.fovSetting = scaleGlobal.getValue();
//        Command.sendWarningMessage(Wrapper.getMinecraft().gameSettings.guiScale + "");
//        Command.sendWarningMessage(scaleGlobal.getValue() + "");
//        Command.sendWarningMessage();
    }

//    public void onDisable() { this.enable(); }
}
