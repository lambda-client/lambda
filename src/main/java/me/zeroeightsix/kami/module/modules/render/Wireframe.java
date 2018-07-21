package me.zeroeightsix.kami.module.modules.render;

import me.zeroeightsix.kami.module.Module;

/**
 * Created by 086 on 11/04/2018.
 */
@Module.Info(name = "Wireframe", category = Module.Category.RENDER, description = "Turns everything into lines!")
public class Wireframe extends Module {

    public static Wireframe INSTANCE;

    public Wireframe() {
        Wireframe.INSTANCE = this;
    }
}
