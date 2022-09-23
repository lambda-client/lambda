package com.lambda.client.gui.hudgui.elements.client

import com.lambda.client.commons.extension.sumByFloat
import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.gui.hudgui.HudElement
import com.lambda.client.module.AbstractModule
import com.lambda.client.module.ModuleManager
import com.lambda.client.util.AsyncCachedValue
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.graphics.font.HAlign
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.graphics.font.VAlign
import com.lambda.client.util.threads.safeAsyncListener
import net.minecraft.client.renderer.GlStateManager
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.max

internal object Bindings : HudElement(
    name = "Bindings",
    category = Category.CLIENT,
    description = "Display current module keybindings",
    enabledByDefault = false
) {
    private val sortingMode by setting("Sorting Mode", SortingMode.LENGTH)
    private val disabledColor by setting("Disabled Color", ColorHolder(255, 255, 255), false)
    private val enabledColor by setting("Enabled Color", ColorHolder(0, 255, 0), false)

    private var cacheWidth = 20.0f
    private var cacheHeight = 20.0f
    override val hudWidth: Float get() = cacheWidth
    override val hudHeight: Float get() = cacheHeight

    @Suppress("UNUSED")
    private enum class SortingMode(
        override val displayName: String,
        val comparator: Comparator<AbstractModule>
    ) : DisplayEnum {
        LENGTH("Length", compareByDescending { it.textLine.getWidth() }),
        ALPHABET("Alphabet", compareBy { it.name }),
        CATEGORY("Category", compareBy { it.category.ordinal })
    }

    private var modulesWithBindings: List<AbstractModule> = emptyList()
    private val lineHeight = FontRenderAdapter.getFontHeight() + 2.0f

    init {
        relativePosX = -2.0f
        relativePosY = 2.0f
        dockingH = HAlign.RIGHT

        safeAsyncListener<TickEvent.ClientTickEvent> {event ->
            if (event.phase != TickEvent.Phase.END) return@safeAsyncListener
            // this isn't terribly efficient, consider creating events for editing bindings and module toggle state
            modulesWithBindings = sortedModuleList
                .filter { it.bind.value.isEmpty.not() }
            cacheWidth = modulesWithBindings.maxOfOrNull {
                it.textLine.getWidth() + 4.0f
            } ?: 20.0f
            cacheHeight = max(modulesWithBindings.sumByFloat { lineHeight }, 20.0f)
        }
    }

    override fun renderHud(vertexHelper: VertexHelper) {
        super.renderHud(vertexHelper)
        GlStateManager.pushMatrix()
        GlStateManager.translate(width / scale * dockingH.multiplier, 0.0f, 0.0f)
        if (dockingV == VAlign.BOTTOM) {
            GlStateManager.translate(0.0f, height / scale - (FontRenderAdapter.getFontHeight() + 2.0f), 0.0f)
        }

        drawModuleList()

        GlStateManager.popMatrix()
    }

    private fun drawModuleList() {
        for (module in modulesWithBindings) {
            GlStateManager.pushMatrix()
            val textLine = module.textLine
            val textWidth = textLine.getWidth()
            val stringPosX = textWidth * dockingH.multiplier
            val margin = 2.0f * dockingH.offset
            GlStateManager.translate(-stringPosX - margin, 0.0f, 0.0f)
            textLine.drawLine(1.0f, true, HAlign.LEFT, FontRenderAdapter.useCustomFont)
            GlStateManager.popMatrix()
            var yOffset = lineHeight
            if (dockingV == VAlign.BOTTOM) yOffset *= -1.0f
            GlStateManager.translate(0.0f, yOffset, 0.0f)
        }
    }

    private val AbstractModule.textLine get() = this.newTextLine()

    private val sortedModuleList: List<AbstractModule> by AsyncCachedValue(1L, TimeUnit.SECONDS) {
        ModuleManager.modules
            .filter { it.category != com.lambda.client.module.Category.CLIENT}
            .sortedWith(this.sortingMode.comparator)
    }

    private fun AbstractModule.newTextLine() =
        TextComponent.TextLine(" ").apply {
            val lineColor: ColorHolder = if (isEnabled) enabledColor else disabledColor
            add(TextComponent.TextElement(name, lineColor))
            add(TextComponent.TextElement("[" + bind.value.toString() + "]", lineColor))
            if (dockingH == HAlign.RIGHT) reverse()
        }

}