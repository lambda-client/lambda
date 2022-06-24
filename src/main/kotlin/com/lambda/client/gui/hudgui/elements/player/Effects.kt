package com.lambda.client.gui.hudgui.elements.player

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.color.ColorConverter
import com.lambda.client.util.text.RomanNumerals
import net.minecraft.client.resources.I18n
import net.minecraft.potion.Potion

internal object Effects : LabelHud(
    name = "Effects",
    category = Category.PLAYER,
    description = "Displays active effects"
) {

    private val romanNumerals by setting("Roman Numerals", true)
    private val coloredEffectNames by setting("Colored Effect Names", true)

    override fun SafeClientEvent.updateText() {
        player.activePotionEffects.sortedBy { it.duration }.forEach {
            val amplifier = if (romanNumerals) RomanNumerals.numberToRoman(it.amplifier + 1) else (it.amplifier + 1).toString()
            val duration = Potion.getPotionDurationString(it, 1f)
            val color = if (coloredEffectNames) ColorConverter.hexToRgb(it.potion.liquidColor) else secondaryColor
            val name = I18n.format(it.potion.name)

            displayText.add(name, color)
            displayText.add(amplifier, primaryColor)
            displayText.addLine("(${duration})", primaryColor)
        }
    }
}
