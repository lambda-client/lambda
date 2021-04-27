package org.kamiblue.client.module.modules.misc

import net.minecraftforge.client.event.RenderGameOverlayEvent
import org.kamiblue.client.KamiMod
import org.kamiblue.client.module.Category
import org.kamiblue.client.module.Module
import org.kamiblue.client.module.modules.misc.AntiAFK.setting
import org.kamiblue.event.listener.listener

internal object F3Spoof : Module(
    name = "F3Spoof",
    category = Category.MISC,
    description = "Modifies your Debug Menu"
) {

    private val hideCoords = setting("Coords", true)
    private val hideFrames = setting("FPS", false)
    private val hideBiome = setting("Biome", false)
    private val useCustomText = setting("Use custom text", false)
    private val customText by setting("Custom text", KamiMod.KAMI_KATAKANA, { useCustomText.value })

    var replaceText = KamiMod.KAMI_KATAKANA

    init {
        listener<RenderGameOverlayEvent.Text> {

            replaceText = if (useCustomText.value) customText else KamiMod.KAMI_KATAKANA
            for (i in 0 until it.left.size) {
                if (hideCoords.value) {
                    if (it.left[i].contains("Looking"))
                        it.left[i] = "Looking at $replaceText"
                    if (it.left[i].contains("XYZ"))
                        it.left[i] = "XYZ: $replaceText"
                    if (it.left[i].contains("Block:"))
                        it.left[i] = "Block: $replaceText"
                    if (it.left[i].contains("Chunk:"))
                        it.left[i] = "Chunk: $replaceText"
                    if (it.left[i].contains("Facing:"))
                        it.left[i] = "Facing: $replaceText"
                }
                if (hideFrames.value) {
                    if (it.left[i].contains("fps"))
                        it.left[i] = "$replaceText fps"
                }
                if (hideBiome.value) {
                    if (it.left[i].contains("Biome:"))
                        it.left[i] = "Biome: $replaceText"
                }
            }
        }
    }
}