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
        val GRIM = ModuleTag("Grim")
        val BYPASS = ModuleTag("Bypass")
    }
}