package me.zeroeightsix.kami.module.modules.gui;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/***
 * @author S-B99
 * Created by S-B99 on 20/12/19
 * Updated by S-B99 on 22/12/19
 */
@Module.Info(name = "Zoom", category = Module.Category.GUI, description = "Configures FOV", showOnArray = Module.ShowOnArray.OFF)
public class Zoom extends Module {

    private float fov = 0;
    private float sensi = 0;

    private Setting<Integer> fovChange = register(Settings.integerBuilder("FOV").withMinimum(30).withValue(30).withMaximum(150).build());
    private Setting<Float> sensChange = register(Settings.floatBuilder("Sensitivity").withMinimum(0.25F).withValue(1.3F).withMaximum(2F).build());
    private Setting<Boolean> smoothCamera = register(Settings.b("Cinematic Camera", true));
    private Setting<Boolean> sens = register(Settings.b("Sensitivity", true));

    public void onEnable() {
        if (mc.player == null) return;
        fov = mc.gameSettings.fovSetting;
        sensi = mc.gameSettings.mouseSensitivity;
        if (smoothCamera.getValue()) mc.gameSettings.smoothCamera = true;
    }

    public void onDisable() {
        mc.gameSettings.fovSetting = fov;
        mc.gameSettings.mouseSensitivity = sensi;
        if (smoothCamera.getValue()) mc.gameSettings.smoothCamera = false;
    }

    public void onUpdate() {
        if (mc.player == null) return;
        mc.gameSettings.fovSetting = fovChange.getValue();
        if (smoothCamera.getValue()) mc.gameSettings.smoothCamera = true; else mc.gameSettings.smoothCamera = false;
        if (sens.getValue()) mc.gameSettings.mouseSensitivity = sensi * sensChange.getValue();
    }
}
