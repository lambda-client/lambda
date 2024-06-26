package com.lambda.module.tag

import com.lambda.util.Nameable

/**
 * The [ModuleTag] class represents a tag, that can be associated in any cardinality with a [Module].
 *
 * Tags are used to categorize and organize modules, making them easier to find.
 * They can be custom created as per the user's needs.
 *
 * Additionally, [ModuleTag] can be used to create groups of tags, which can be useful for creating new GUI windows.
 *
 * The companion object provides a set of predefined `ModuleTag` instances for common categories like "Combat",
 * "Movement", "Render", etc.
 *
 * @param name The name of the tag.
 */
data class ModuleTag(override val name: String) : Nameable {
    companion object {
        val COMBAT = ModuleTag("Combat")
        val MOVEMENT = ModuleTag("Movement")
        val RENDER = ModuleTag("Render")
        val PLAYER = ModuleTag("Player")
        val CLIENT = ModuleTag("Client")
        val NETWORK = ModuleTag("Network")
        val DEBUG = ModuleTag("Debug")
        val defaults = setOf(COMBAT, MOVEMENT, RENDER, PLAYER, NETWORK, DEBUG, CLIENT)

        val HUD = ModuleTag("Hud") // omg
        val hudDefaults = setOf(HUD)

        // currently secondary tags
        val WORLD = ModuleTag("World")
        val AUTOMATION = ModuleTag("Automation")
        val GRIM = ModuleTag("Grim")
    }
}
