//package me.robeart.voidclient.module.modules.render;
package me.zeroeightsix.kami.module.modules.sdashb.experimental;

//1//import me.robeart.voidclient.module.Module;
//1//import me.robeart.voidclient.setting.Setting;
//1//import me.robeart.voidclient.setting.Settings;
//1//import me.robeart.voidclient.setting.builder.numerical.NumericalSettingBuilder;
//1//import me.robeart.voidclient.setting.impl.numerical.NumberSetting;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.setting.builder.numerical.NumericalSettingBuilder;
import me.zeroeightsix.kami.setting.impl.numerical.NumberSetting;

@Module.Info(name="GUI Color", description="Change GUI Colors", category=Module.Category.EXPERIMENTAL)
public class GUIColor extends Module {
    public Setting<Integer> red = this.register(Settings.integerBuilder("Red").withMinimum(0).withValue(80).withMaximum(255).build());
    public Setting<Integer> green = this.register(Settings.integerBuilder("Green").withMinimum(0).withValue(80).withMaximum(255).build());
    public Setting<Integer> blue = this.register(Settings.integerBuilder("Blue").withMinimum(0).withValue(80).withMaximum(255).build());
    public Setting<Integer> alpha = this.register(Settings.integerBuilder("Alpha").withMinimum(0).withValue(200).withMaximum(255).build());
}
