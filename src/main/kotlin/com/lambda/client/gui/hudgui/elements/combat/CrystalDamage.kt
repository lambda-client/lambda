package com.lambda.client.gui.hudgui.elements.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.util.combat.CrystalUtils.calcCrystalDamage
import com.lambda.client.util.combat.CrystalUtils.getBestPlace
import com.lambda.client.util.combat.CrystalUtils.getPlaceInfo

internal object CrystalDamage : LabelHud(
    name = "CrystalDamage",
    category = Category.COMBAT,
    description = "Displays the potential damage inflicted by a crystal"
) {

    override fun SafeClientEvent.updateText() {
        val potentialSelf = calcCrystalDamage(getBestPlace(player, 10f)?.position, player)
        val potentialTarget = calcCrystalDamage(getPlaceInfo(CombatManager.target)?.position, CombatManager.target)

        displayText.add("Potential", secondaryColor)
        displayText.addLine("${potentialTarget.targetDamage}/${potentialSelf.selfDistance}", primaryColor)
    }
}