package com.lambda.client.module.modules.render

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.color.ColorConverter.rgbToHex
import com.lambda.client.util.color.ColorHolder
import com.lambda.event.listener.listener
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.awt.Color

internal object EnchantColor : Module(
    name = "EnchantColor",
    category = Category.RENDER,
    description = "Change the color of enchanted items"
) {
    val rainbow by setting("Rainbow", true)
    private val rainbowSpeed by setting("Rainbow Speed", 2, 1..50, 1, { rainbow })
    val colorSetting by setting("Color", (ColorHolder(255, 0, 255)), true, { rainbow })

    private var rgb = 0
    private var hue = 0.01f
    var rainbowA = 0
    var rainbowR = 0
    var rainbowG = 0
    var rainbowB = 0

    init {
        listener<TickEvent.ClientTickEvent> {
            updateRainbow()
        }
    }
    fun getNormalValue(): Int {
        return rgbToHex(colorSetting.r, colorSetting.g, colorSetting.b, colorSetting.a)
    }

    fun getRainbowValue(): Int {
        return rgbToHex(rainbowR, rainbowG, rainbowB, rainbowA)
    }

    private fun updateRainbow() {
        rgb = Color.HSBtoRGB(hue, 1F, 1F)
        rainbowA = rgb ushr 24 and 0xFF
        rainbowR = rgb ushr 16 and 0xFF
        rainbowG = rgb ushr 8 and 0xFF
        rainbowB = rgb and 0xFF
        hue += rainbowSpeed / 1000f
        if (hue > 1) hue -= 1f
    }
}