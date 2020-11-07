package me.zeroeightsix.kami.module.modules.misc

import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.WebUtils
import java.net.URI

/**
 * Reverse engineered from Impact Client
 */
@Module.Info(
        name = "Franky",
        category = Module.Category.MISC,
        description = "Does exactly what you think it does"
)
@Suppress("UNUSED")
object Franky : Module() {
    private val maxExploit = register(Settings.b("MaxExploit", false))
    private val bigrat = register(Settings.b("Bigrat", false))

    override fun onEnable() {
        if (bigrat.value) WebUtils.openWebLink(URI("https://bigrat.monster/"))
    }
}