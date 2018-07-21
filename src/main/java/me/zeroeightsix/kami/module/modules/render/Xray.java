package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.module.Module;

/**
 * Created by 086 on 12/12/2017.
 */
@Module.Info(name = "Xray", description = "See blocks through walls", category = Module.Category.RENDER)
public class Xray extends Module {

    public static Xray INSTANCE;

    public Xray() {
        Xray.INSTANCE = this;
    }
}
