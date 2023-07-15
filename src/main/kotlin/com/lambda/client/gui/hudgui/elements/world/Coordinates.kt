package com.lambda.client.gui.hudgui.elements.world

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.math.VectorUtils.times
import net.minecraft.util.math.Vec3d

internal object Coordinates : LabelHud(
    name = "Coordinates",
    category = Category.WORLD,
    description = "Display the current coordinate"
) {

    private val showX by setting("Show X", true)
    private val showY by setting("Show Y", true)
    private val showZ by setting("Show Z", true)
    private val showXYZText by setting("Show XYZ Text", true)
    private val showNetherOverworld by setting("Show Nether/Overworld", true)
    private val printDimensionName by setting("Print Dimension Name", false)
    private val showNetherOverworldMultiline by setting("Show Nether/Overworld Multiline", false, { showNetherOverworld })
    private val decimalPlaces by setting("Decimal Places", 1, 0..4, 1)
    private val thousandsSeparator by setting("Thousands Separator", false)

    private val netherToOverworld = Vec3d(8.0, 1.0, 8.0)
    private val overworldToNether = Vec3d(0.125, 1.0, 0.125)

    override fun SafeClientEvent.updateText() {
        val entity = mc.renderViewEntity ?: player
        if (showXYZText) {
            displayText.add("XYZ", secondaryColor)
        }
        if (showNetherOverworldMultiline)
            displayText.addLine(getFormattedCoords(entity.positionVector))
        else
            displayText.add(getFormattedCoords(entity.positionVector))
        if (showNetherOverworld) {
            when (world.provider.dimension) {
                -1 -> { // Nether
                    if (printDimensionName) displayText.add("Nether", secondaryColor)
                    displayText.add(getFormattedCoords(entity.positionVector * netherToOverworld, true))
                }
                0 -> { // Overworld
                    if (printDimensionName)
                        displayText.add("Overworld", secondaryColor)
                    displayText.add(getFormattedCoords(entity.positionVector * overworldToNether, true))
                }
            }
        }
    }

    private fun getFormattedCoords(pos: Vec3d, brackets: Boolean = false): TextComponent.TextElement {
        if (!showX && !showY && !showZ) return TextComponent.TextElement("", primaryColor)
        val x = roundOrInt(pos.x)
        val y = roundOrInt(pos.y)
        val z = roundOrInt(pos.z)
        return StringBuilder().run {
            if (brackets) append("[")
            if (showX) append(x)
            if (showY) appendWithComma(y)
            if (showZ) appendWithComma(z)
            if (brackets) append("]")
            TextComponent.TextElement(toString(), primaryColor)
        }
    }

    private fun roundOrInt(input: Double): String {
        val separatorFormat = if (thousandsSeparator) "," else ""
        return "%$separatorFormat.${decimalPlaces}f".format(input)
    }

    private fun StringBuilder.appendWithComma(string: String) = append(if (isNotEmpty()) ", $string" else string)

}