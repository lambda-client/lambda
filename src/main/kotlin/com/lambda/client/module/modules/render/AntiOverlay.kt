package com.lambda.client.module.modules.render

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import com.lambda.event.listener.listener
import net.minecraft.client.tutorial.TutorialSteps
import net.minecraft.init.MobEffects
import net.minecraftforge.client.event.RenderBlockOverlayEvent
import net.minecraftforge.client.event.RenderBlockOverlayEvent.OverlayType
import net.minecraftforge.client.event.RenderGameOverlayEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

object AntiOverlay : Module(
    name = "AntiOverlay",
    description = "Prevents rendering of fire, water and block texture overlays.",
    category = Category.RENDER
) {
    val hurtCamera by setting("Hurt Camera", true)
    private val fire by setting("Fire", true)
    private val water by setting("Water", true)
    private val blocks by setting("Blocks", true)
    private val portals by setting("Portals", true)
    private val blindness by setting("Blindness", true)
    private val nausea by setting("Nausea", true)
    val totems by setting("Totems", true)
    private val vignette by setting("Vignette", false)
    private val helmet by setting("Helmet", true)
    private val tutorial by setting("Tutorial", true)
    private val potionIcons by setting("Potion Icons", false)

    init {
        listener<RenderBlockOverlayEvent> {
            it.isCanceled = when (it.overlayType) {
                OverlayType.FIRE -> fire
                OverlayType.WATER -> water
                OverlayType.BLOCK -> blocks
                else -> it.isCanceled
            }
        }

        listener<RenderGameOverlayEvent.Pre> {
            it.isCanceled = when (it.type) {
                RenderGameOverlayEvent.ElementType.VIGNETTE -> vignette
                RenderGameOverlayEvent.ElementType.PORTAL -> portals
                RenderGameOverlayEvent.ElementType.HELMET -> helmet
                RenderGameOverlayEvent.ElementType.POTION_ICONS -> potionIcons
                else -> it.isCanceled
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (blindness) player.removeActivePotionEffect(MobEffects.BLINDNESS)
            if (nausea) player.removeActivePotionEffect(MobEffects.NAUSEA)
            if (tutorial) mc.gameSettings.tutorialStep = TutorialSteps.NONE
        }
    }
}