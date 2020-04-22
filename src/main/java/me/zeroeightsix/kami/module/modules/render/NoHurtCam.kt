package me.zeroeightsix.kami.module.modules.render

import me.zeroeightsix.kami.module.Module

/**
 * @author 086
 */
@Module.Info(
        name = "NoHurtCam",
        category = Module.Category.RENDER,
        description = "Disables the 'hurt' camera effect"
)
class NoHurtCam : Module() {
    companion object {
        private var INSTANCE: NoHurtCam? = null

        @JvmStatic
        fun shouldDisable(): Boolean {
            return INSTANCE != null && INSTANCE!!.isEnabled
        }
    }

    init {
        INSTANCE = this
    }
}