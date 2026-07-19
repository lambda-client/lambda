
package com.minato.module.modules.render

import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.context.SafeContext
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listenOnce
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import net.minecraft.entity.effect.StatusEffectInstance
import net.minecraft.entity.effect.StatusEffects

object Fullbright : Module(
    name = "Fullbright",
    description = "Makes everything brighter",
    tag = ModuleTag.RENDER,
) {
    private val nightVision by setting("Night Vision", false, description = "Adds the night vision effect client-side")
        .onValueChange { _, to -> setNightVision(to) }

    private val instance = StatusEffectInstance(StatusEffects.NIGHT_VISION, -1, 1, false, false)

    init {
        listenOnce<TickEvent.Pre> {
            if (nightVision && !player.hasStatusEffect(StatusEffects.NIGHT_VISION)) setNightVision(true)

            // Destroy the listener
            true
        }
    }

    private fun SafeContext.setNightVision(value: Boolean) {
        if (value) player.addStatusEffect(instance)
        else player.removeStatusEffect(StatusEffects.NIGHT_VISION)
    }
}
