package com.lambda.client.gui.hudgui.elements.combat

import com.lambda.client.commons.utils.MathUtils
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.util.Quad
import com.lambda.client.util.combat.CrystalUtils.canPlaceCollide
import kotlin.math.max

internal object CrystalDamage : LabelHud(
    name = "CrystalDamage",
    category = Category.COMBAT,
    description = "Display the max potential damage and the current damage to you and target"
) {

    private var prevDamages = Quad(0.0f, 0.0f, 0.0f, 0.0f)

    override fun SafeClientEvent.updateText() {
        val placeList = CombatManager.placeMap
        val crystalList = CombatManager.crystalMap.values

        var potentialTarget = 0.0f
        var potentialSelf = 0.0f
        for ((pos, calculation) in placeList) {
            if (!canPlaceCollide(pos)) continue
            potentialTarget = max(calculation.targetDamage, potentialTarget)
            potentialSelf = max(calculation.selfDamage, potentialSelf)
        }

        var currentTarget = 0.0f
        var currentSelf = 0.0f
        for (calculation in crystalList) {
            currentTarget = max(calculation.targetDamage, currentTarget)
            currentSelf = max(calculation.selfDamage, currentSelf)
        }

        val quad = Quad(potentialTarget, potentialSelf, currentTarget, currentSelf)
        potentialTarget = calcAndRound(prevDamages.first, potentialTarget)
        potentialSelf = calcAndRound(prevDamages.second, potentialSelf)
        currentTarget = calcAndRound(prevDamages.third, currentTarget)
        currentSelf = calcAndRound(prevDamages.fourth, currentSelf)

        displayText.add("Potential", secondaryColor)
        displayText.addLine("$potentialTarget/$potentialSelf", primaryColor)
        displayText.add("Current", secondaryColor)
        displayText.add("$currentTarget/$currentSelf", primaryColor)
        prevDamages = quad
    }

    private fun calcAndRound(prev: Float, curr: Float) = MathUtils.round(max(prev, curr), 1).toFloat()

}