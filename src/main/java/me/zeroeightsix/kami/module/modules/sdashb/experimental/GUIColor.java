package me.zeroeightsix.kami.module.modules.sdashb.experimental;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
//import me.zeroeightsix.kami.setting.builder.numerical.NumericalSettingBuilder;
//import me.zeroeightsix.kami.setting.impl.numerical.NumberSetting;

@Module.Info(name="GUI Color", description="Change GUI Colors", category=Module.Category.EXPERIMENTAL)
public class GUIColor extends Module {
    public Setting<Integer> red = this.register(Settings.integerBuilder("Red").withMinimum(0).withValue(13).withMaximum(255).build());
    public Setting<Integer> green = this.register(Settings.integerBuilder("Green").withMinimum(0).withValue(13).withMaximum(255).build());
    public Setting<Integer> blue = this.register(Settings.integerBuilder("Blue").withMinimum(0).withValue(13).withMaximum(255).build());
    public Setting<Integer> alpha = this.register(Settings.integerBuilder("Alpha").withMinimum(0).withValue(117).withMaximum(255).build());
}
