package me.zeroeightsix.kami.module.modules.render

import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Setting
import me.zeroeightsix.kami.setting.Settings

/**
 * Created by 086 on 8/04/2018.
 */
@Module.Info(
        name = "ExtraTab",
        description = "Expands the player tab menu",
        category = Module.Category.RENDER
)
class ExtraTab : Module() {
    @JvmField
    var tabSize: Setting<Int> = register(Settings.integerBuilder("Players").withMinimum(1).withValue(80).build())

    companion object {
        @JvmField
        var INSTANCE: ExtraTab? = null
    }

    init {
        INSTANCE = this
    }
}