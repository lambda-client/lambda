package com.lambda.client.module.modules.render

import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.PredictionEntity
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.threads.safeListener
import net.minecraft.util.math.AxisAlignedBB
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent

/**
 * @author Doogie13
 * @since 26/01/2023
 */
object SelfPredict : Module(
    name = "SelfPredict",
    description = "Renders a prediction of your own future position",
    category = Category.RENDER
) {

    private val ticks = setting("Ticks Ahead", 2, 1..20, 1)
    private val stepHeight = setting("Step Height", .6f, 0f..3f, 0.01f)

    private val costSteps = setting("Cost Steps", false)
    private val stepCost1 = setting("Step Cost 1", 2, 0..10, 1, visibility = { costSteps.value })
    private val stepCost15 = setting("Step Cost 1.5", 6, 0..15, 1, visibility = { costSteps.value })
    private val stepCost2 = setting("Step Cost 2", 8, 0..20, 1, visibility = { costSteps.value })
    private val stepCost25 = setting("Step Cost 2.5", 10, 0..25, 1, visibility = { costSteps.value })

    private var bb : AxisAlignedBB = AxisAlignedBB(.0,.0,.0,.0,.0,.0)
    private var prevBB : AxisAlignedBB = AxisAlignedBB(.0,.0,.0,.0,.0,.0)

    private var prediction : PredictionEntity? = null

    init {
        
        safeListener<ClientTickEvent> {

            if (prediction == null)
                refresh()

            prediction?.let {

                it.travel()

                prevBB = bb
                bb = it.entityBoundingBox

            }

        }
        
        safeListener<RenderWorldEvent> {

            var newBB = AxisAlignedBB(bb.minX, bb.minY, bb.minZ, bb.maxX, bb.maxY, bb.maxZ)
            newBB = newBB.offset(bb.center.subtract(prevBB.center).scale(mc.renderPartialTicks.toDouble()))

            val renderer = ESPRenderer()

            renderer.aFilled = 64
            renderer.aOutline = 255
            renderer.thickness = 2f

            renderer.add(newBB, ColorHolder(170, 0, 170))

            renderer.render(true)
            
        }

        ticks.listeners.add { refresh() }
        stepHeight.listeners.add { refresh() }
        stepCost1.listeners.add { refresh() }
        stepCost15.listeners.add { refresh() }
        stepCost2.listeners.add { refresh() }
        stepCost25.listeners.add { refresh() }
        
    }

    private fun refresh() {

        if (prediction == null)
            prediction = PredictionEntity(mc.player)

        prediction?.let {

            it.profile.ticks = ticks.value
            it.profile.stepHeight = stepHeight.value

            it.profile.costSteps = costSteps.value
            it.profile.stepCost1 = stepCost1.value
            it.profile.stepCost15 = stepCost15.value
            it.profile.stepCost2 = stepCost2.value
            it.profile.stepCost25 = stepCost25.value

        }

    }
    
}