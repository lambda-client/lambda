package com.lambda.client.module.modules.render

import com.lambda.client.event.listener.listener
import com.lambda.client.mixin.extension.mapBossInfos
import com.lambda.client.mixin.extension.render
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.graphics.GlStateUtils
import net.minecraft.client.gui.BossInfoClient
import net.minecraft.client.gui.ScaledResolution
import net.minecraft.client.renderer.GlStateManager
import net.minecraft.entity.EntityLivingBase
import net.minecraft.util.ResourceLocation
import net.minecraftforge.client.event.RenderGameOverlayEvent
import org.lwjgl.opengl.GL11.*
import kotlin.math.abs
import kotlin.math.roundToInt

object BossStack : Module(
    name = "BossStack",
    description = "Modifies the boss health GUI to take up less space",
    category = Category.RENDER
) {
    private val mode by setting("Mode", BossStackMode.STACK)
    private val scale by setting("Scale", 1.0f, 0.1f..5.0f, 0.25f)
    private val censor by setting("Censor", false)

    @Suppress("unused")
    private enum class BossStackMode {
        REMOVE, MINIMIZE, STACK
    }

    private val texture = ResourceLocation("textures/gui/bars.png")
    private val bossInfoMap = LinkedHashMap<BossInfoClient, Int>()
    private val timer = TickTimer()

    init {
        listener<RenderGameOverlayEvent.Pre> {
            if (it.type != RenderGameOverlayEvent.ElementType.BOSSHEALTH) return@listener
            if (timer.tick(73L)) updateBossInfoMap()

            it.isCanceled = true
            drawHealthBar()
        }
    }

    private fun updateBossInfoMap() {
        bossInfoMap.clear()
        val bossInfoList = mc.ingameGUI.bossOverlay.mapBossInfos?.values ?: return

        when (mode) {
            BossStackMode.MINIMIZE -> {
                val closest = getMatchBoss(bossInfoList) ?: return
                bossInfoMap[closest] = -1
            }

            BossStackMode.STACK -> {
                val cacheMap = HashMap<String, ArrayList<BossInfoClient>>()

                for (bossInfo in bossInfoList) {
                    val list = cacheMap.getOrPut(if (censor) "Boss" else bossInfo.name.formattedText) { ArrayList() }
                    list.add(bossInfo)
                }

                for ((name, list) in cacheMap) {
                    val closest = getMatchBoss(list, if (censor) null else name) ?: continue
                    bossInfoMap[closest] = list.size
                }
            }

            else -> {
                // Do nothing
            }
        }
    }

    private fun getMatchBoss(list: Collection<BossInfoClient>, name: String? = null): BossInfoClient? {
        val closestBossHealth = getClosestBoss(name)?.let {
            it.health / it.maxHealth
        } ?: return null

        return list.minByOrNull {
            abs(it.percent - closestBossHealth)
        }
    }

    private fun getClosestBoss(name: String?) =
        mc.world?.loadedEntityList?.let {
            var closest = Float.MAX_VALUE
            var closestBoss: EntityLivingBase? = null

            for (entity in it) {
                if (entity !is EntityLivingBase) continue
                if (entity.isNonBoss) continue
                if (name != null && entity.displayName.formattedText != name) continue

                val dist = entity.getDistance(mc.player)
                if (dist >= closest) continue

                closest = dist
                closestBoss = entity
            }
            closestBoss
        }

    private fun drawHealthBar() {
        mc.profiler.startSection("bossHealth")

        val width = ScaledResolution(mc).scaledWidth
        var posY = 12

        GlStateUtils.blend(true)
        GlStateManager.tryBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE, GL_ZERO)
        glColor4f(1.0f, 1.0f, 1.0f, 1.0f)

        if (bossInfoMap.isNotEmpty()) for ((bossInfo, count) in bossInfoMap) {
            val posX = (width / scale / 2.0f - 91.0f).roundToInt()
            val text = (if (censor) "Boss" else bossInfo.name.formattedText) + if (count != -1) " x$count" else ""
            val textPosX = width / scale / 2.0f - mc.fontRenderer.getStringWidth(text) / 2.0f
            val textPosY = posY - 9.0f

            glScalef(scale, scale, 1.0f)
            mc.textureManager.bindTexture(texture)
            mc.ingameGUI.bossOverlay.render(posX, posY, bossInfo)
            mc.fontRenderer.drawStringWithShadow(text, textPosX, textPosY, 0xffffff)
            glScalef(1.0f / scale, 1.0f / scale, 1.0f)

            posY += 10 + mc.fontRenderer.FONT_HEIGHT
        }

        glColor4f(1.0f, 1.0f, 1.0f, 1.0f)
        mc.profiler.endSection()
    }
}
