package me.zeroeightsix.kami.module.modules.bewwawho.gui;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

/**
 * @author S-B99
 * Created by S-B99 on 04/12/19
 * Updated by S-B99 on 18/01/20
 */
@Module.Info(name = "InfoOverlay", category = Module.Category.GUI, description = "Configures game information overlay", showOnArray = Module.ShowOnArray.OFF)
public class InfoOverlay extends Module {

    public Setting<Boolean> globalInfoNam = register(Settings.b("Username", true));
    public Setting<Boolean> globalInfoTps = register(Settings.b("TPS", true));
    public Setting<Boolean> globalInfoFps = register(Settings.b("FPS", true));
    public Setting<Boolean> globalInfoSpe = register(Settings.b("Speed", true));
    public Setting<Boolean> globalInfoPin = register(Settings.b("Ping", false));
    public Setting<Boolean> globalInfoDur = register(Settings.b("Dura", true));
    public Setting<Boolean> globalInfoMem = register(Settings.b("Memory", false));
    public Setting<SpeedUnit> speedUnitSetting = register(Settings.e("Speed Unit", SpeedUnit.KmH));

    public enum SpeedUnit {
        MpS, KmH
    }

    public boolean useUnitKmH() {
        return speedUnitSetting.getValue().equals(SpeedUnit.KmH);
    }

    public String unitType(SpeedUnit s) {
        switch (s) {
            case MpS: return "m/s";
            case KmH: return "km/h";
            default: return "Invalid unit type (mps or kmh)";
        }
    }
    public void onDisable() {
        this.enable();
    }
}
