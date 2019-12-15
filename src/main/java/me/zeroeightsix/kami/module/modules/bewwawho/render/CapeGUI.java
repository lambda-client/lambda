package me.zeroeightsix.kami.module.modules.bewwawho.render;

import me.zeroeightsix.kami.module.Module;

/***
 * Created by @S-B99 on 26/11/19
 */
@Module.Info(name = "Cape", category = Module.Category.RENDER, description = "Shiny custom donator cape", showOnArray = false)
public class CapeGUI extends Module {
    public void onDisable() {
        this.enable();
    }
}
