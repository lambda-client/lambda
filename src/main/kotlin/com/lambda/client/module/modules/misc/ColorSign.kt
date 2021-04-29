package com.lambda.client.module.modules.misc

import com.lambda.client.event.events.GuiEvent
import com.lambda.client.mixin.extension.editLine
import com.lambda.client.mixin.extension.tileSign
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.event.listener.listener
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.inventory.GuiEditSign
import net.minecraft.tileentity.TileEntitySign
import net.minecraft.util.text.TextComponentString
import java.io.IOException

internal object ColorSign : Module(
    name = "ColorSign",
    description = "Allows ingame coloring of text on signs",
    category = Category.MISC
) {
    init {
        listener<GuiEvent.Displayed> { event ->
            if (event.screen !is GuiEditSign) return@listener
            (event.screen as? GuiEditSign?)?.tileSign?.let { event.screen = LambdaGuiEditSign(it) }
        }
    }

    private class LambdaGuiEditSign(teSign: TileEntitySign) : GuiEditSign(teSign) {
        @Throws(IOException::class)
        override fun actionPerformed(button: GuiButton) {
            if (button.id == 0) {
                tileSign.signText[editLine] = TextComponentString(tileSign.signText[editLine].formattedText.replace("(§)(.)".toRegex(), "$1$1$2$2"))
            }
            super.actionPerformed(button)
        }

        @Throws(IOException::class)
        override fun keyTyped(typedChar: Char, keyCode: Int) {
            super.keyTyped(typedChar, keyCode)
            var s = (tileSign.signText[editLine] as TextComponentString).text
            s = s.replace('&', '§')
            tileSign.signText[editLine] = TextComponentString(s)
        }
    }
}