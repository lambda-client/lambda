package com.lambda.module.tag

import com.lambda.util.Nameable

class ModuleTag(override val name: String) : Nameable {
    companion object {
        val COMBAT = ModuleTag("Combat")
        val MOVEMENT = ModuleTag("Movement")
        val RENDER = ModuleTag("Render")
        val PLAYER = ModuleTag("Player")
        val WORLD = ModuleTag("World")
        val MISC = ModuleTag("Misc")
        val CLIENT = ModuleTag("Client")
        val HIDDEN = ModuleTag("Hidden")
    }
}