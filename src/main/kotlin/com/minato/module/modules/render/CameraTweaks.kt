
package com.minato.module.modules.render

import com.minato.module.Module
import com.minato.module.tag.ModuleTag

object CameraTweaks : Module(
    name = "CameraTweaks",
    description = "Adjusts camera settings",
    tag = ModuleTag.RENDER,
) {
    @JvmStatic val camDistance by setting("Camera Distance", 4.0f, 1.0f..20.0f, 0.1f)
    @JvmStatic val noClipCam by setting("No Clip Camera", true)
}
