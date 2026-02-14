

package com.lambda.graphics.mc

import net.minecraft.util.Identifier

data class ItemOverlay(
    val texture: Identifier,
    val scale: Float = 1.0f,
    val speed: Float = 1.0f,
    val angle: Float = 0f,
    val alpha: Float = 0.5f
) {
    companion object {
        val ENCHANT_GLINT = ItemOverlay(
            texture = Identifier.of("minecraft", "textures/misc/enchanted_glint_item.png"),
            scale = 8.0f,
            speed = 1.0f,
            angle = 10f,
            alpha = 0.5f
        )
        
        val ENTITY_GLINT = ItemOverlay(
            texture = Identifier.of("minecraft", "textures/misc/enchanted_glint_armor.png"),
            scale = 8.0f,
            speed = 1.0f,
            angle = 10f,
            alpha = 0.5f
        )

        val DISABLED = ItemOverlay(
            texture = Identifier.of("minecraft", "textures/misc/unknown.png"),
            alpha = 0f
        )
    }
}
