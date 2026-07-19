
package com.minato.module.modules.debug

import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.player.RotationUtils.lookAt
import net.minecraft.util.hit.HitResult

@Suppress("unused")
object RotationTest : Module(
    name = "RotationTest",
    tag = ModuleTag.DEBUG,
) {
    var hitPos: HitResult? = null
    
    init {
        onEnable {
            hitPos = mc.crosshairTarget
        }

        listen<TickEvent.Pre> {
            hitPos?.let { rotationRequest { rotation(lookAt(it.pos)) }.submit() }
        }
    }
}
