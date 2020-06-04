package me.zeroeightsix.kami.module.modules.render

import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.EntityUtil.mobTypeSettings
import net.minecraft.entity.Entity
import net.minecraft.entity.player.EntityPlayer

/**
 * Created by 086 on 12/12/2017.
 */
@Module.Info(
        name = "Chams",
        category = Module.Category.RENDER,
        description = "See entities through walls"
)
class Chams : Module() {
    companion object {
        private val players = Settings.b("Players", true)
        private val passive = Settings.b("Passive Mobs", false)
        private val neutral = Settings.b("Neutral Mobs", true)
        private val hostile = Settings.b("Hostile Mobs", true)

        @JvmStatic
        fun renderChams(entity: Entity?): Boolean {
            return if (entity is EntityPlayer) players.value
            else mobTypeSettings(entity, true, passive.value, neutral.value, hostile.value)
        }
    }

    init { /* needed because the settings are static */
        registerAll(players, passive, neutral, hostile)
    }
}