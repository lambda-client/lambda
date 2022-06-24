package com.lambda.client.gui.hudgui.elements.client

import com.lambda.client.commons.extension.sumByFloat
import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.gui.hudgui.HudElement
import com.lambda.client.module.AbstractModule
import com.lambda.client.module.ModuleManager
import com.lambda.client.util.AsyncCachedValue
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.TimedFlag
import com.lambda.client.util.color.ColorConverter
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.AnimationUtils
import com.lambda.client.util.graphics.VertexHelper
import com.lambda.client.util.graphics.font.FontRenderAdapter
import com.lambda.client.util.graphics.font.HAlign
import com.lambda.client.util.graphics.font.TextComponent
import com.lambda.client.util.graphics.font.VAlign
import com.lambda.client.util.threads.safeAsyncListener
import net.minecraft.client.renderer.GlStateManager
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.awt.Color
import kotlin.math.max

internal object ModuleList : HudElement(
    name = "ModuleList",
    category = Category.CLIENT,
    description = "List of enabled modules",
    enabledByDefault = true
) {

    private val sortingMode by setting("Sorting Mode", SortingMode.LENGTH)
    private val showInvisible by setting("Show Invisible", false)
    private val rainbow by setting("Rainbow", true)
    private val rainbowLength by setting("Rainbow Length", 10.0f, 1.0f..20.0f, 0.5f, { rainbow })
    private val indexedHue by setting("Indexed Hue", 0.5f, 0.0f..1.0f, 0.05f, { rainbow })
    private val saturation by setting("Saturation", 1.0f, 0.0f..1.0f, 0.05f, { rainbow })
    private val brightness by setting("Brightness", 1.0f, 0.0f..1.0f, 0.05f, { rainbow })
    private val primary by setting("Primary Color", ColorHolder(155, 144, 255), false)
    private val secondary by setting("Secondary Color", ColorHolder(255, 255, 255), false)

    @Suppress("UNUSED")
    private enum class SortingMode(
        override val displayName: String,
        val comparator: Comparator<AbstractModule>
    ) : DisplayEnum {
        LENGTH("Length", compareByDescending { it.textLine.getWidth() }),
        ALPHABET("Alphabet", compareBy { it.name }),
        CATEGORY("Category", compareBy { it.category.ordinal })
    }

    private var cacheWidth = 20.0f
    private var cacheHeight = 20.0f
    override val hudWidth: Float get() = cacheWidth
    override val hudHeight: Float get() = cacheHeight

    private val textLineMap = HashMap<AbstractModule, TextComponent.TextLine>()

    private val sortedModuleList by AsyncCachedValue(1L, TimeUnit.SECONDS) {
        ModuleManager.modules.sortedWith(sortingMode.comparator)
    }

    private var prevToggleMap = emptyMap<AbstractModule, TimedFlag<Boolean>>()
    private val toggleMap by AsyncCachedValue(1L, TimeUnit.SECONDS) {
        ModuleManager.modules
            .associateWith { prevToggleMap[it] ?: TimedFlag(false) }
            .also { prevToggleMap = it }
    }

    init {
        safeAsyncListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.END) return@safeAsyncListener

            for ((module, timedFlag) in toggleMap) {
                val state = module.isEnabled && (module.isVisible || showInvisible)
                if (timedFlag.value != state) timedFlag.value = state

                if (timedFlag.progress <= 0.0f) continue
                textLineMap[module] = module.newTextLine()
            }

            cacheWidth = sortedModuleList.maxOfOrNull {
                if (toggleMap[it]?.value == true) it.textLine.getWidth() + 4.0f
                else 20.0f
            }?.let {
                max(it, 20.0f)
            } ?: 20.0f

            cacheHeight = max(toggleMap.values.sumByFloat { it.displayHeight }, 20.0f)
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
        val lengthMs = rainbowLength * 1000.0f
        val timedHue = System.currentTimeMillis() % lengthMs.toLong() / lengthMs

        var index = 0

        for (module in sortedModuleList) {
            val timedFlag = toggleMap[module] ?: continue
            val progress = timedFlag.progress

            if (progress <= 0.0f) continue

            GlStateManager.pushMatrix()

            val textLine = module.textLine
            val textWidth = textLine.getWidth()
            val animationXOffset = textWidth * dockingH.offset * (1.0f - progress)
            val stringPosX = textWidth * dockingH.multiplier
            val margin = 2.0f * dockingH.offset

            GlStateManager.translate(animationXOffset - stringPosX - margin, 0.0f, 0.0f)

            if (rainbow) {
                val hue = timedHue + indexedHue * 0.05f * index++
                val color = ColorConverter.hexToRgb(Color.HSBtoRGB(hue, saturation, brightness))
                module.newTextLine(color).drawLine(progress, true, HAlign.LEFT, FontRenderAdapter.useCustomFont)
            } else {
                textLine.drawLine(progress, true, HAlign.LEFT, FontRenderAdapter.useCustomFont)
            }

            GlStateManager.popMatrix()
            var yOffset = timedFlag.displayHeight
            if (dockingV == VAlign.BOTTOM) yOffset *= -1.0f
            GlStateManager.translate(0.0f, yOffset, 0.0f)
        }
    }

    private val AbstractModule.textLine
        get() = textLineMap.getOrPut(this) {
            this.newTextLine()
        }

    private fun AbstractModule.newTextLine(color: ColorHolder = primary) =
        TextComponent.TextLine(" ").apply {
            add(TextComponent.TextElement(name, color))
            getHudInfo().let {
                if (it.isNotBlank()) add(TextComponent.TextElement(it, secondary))
            }
            if (dockingH == HAlign.RIGHT) reverse()
        }

    private val TimedFlag<Boolean>.displayHeight
        get() = (FontRenderAdapter.getFontHeight() + 2.0f) * progress

    private val TimedFlag<Boolean>.progress
        get() = if (value) {
            AnimationUtils.exponentInc(AnimationUtils.toDeltaTimeFloat(lastUpdateTime), 200.0f)
        } else {
            AnimationUtils.exponentDec(AnimationUtils.toDeltaTimeFloat(lastUpdateTime), 200.0f)
        }

    init {
        relativePosX = -2.0f
        relativePosY = 2.0f
        dockingH = HAlign.RIGHT
    }

}