package com.lambda.client.buildtools.pathfinding

import com.lambda.client.module.modules.client.BuildTools.goalRender
import com.lambda.client.util.BaritoneUtils

object BaritoneHelper {
    private var baritoneSettingAllowPlace = false
    private var baritoneSettingAllowBreak = false
    private var baritoneSettingRenderGoal = false
    private var baritoneSettingAllowInventory = false

    fun setupBaritone() {
        baritoneSettingAllowPlace = BaritoneUtils.settings?.allowPlace?.value ?: true
        baritoneSettingAllowBreak = BaritoneUtils.settings?.allowBreak?.value ?: true
        baritoneSettingRenderGoal = BaritoneUtils.settings?.renderGoal?.value ?: true
        baritoneSettingAllowInventory = BaritoneUtils.settings?.allowInventory?.value ?: true
        BaritoneUtils.settings?.allowPlace?.value = false
        BaritoneUtils.settings?.allowBreak?.value = false
        BaritoneUtils.settings?.renderGoal?.value = goalRender
        BaritoneUtils.settings?.allowInventory?.value = false
    }

    fun resetBaritone() {
        BaritoneUtils.settings?.allowPlace?.value = baritoneSettingAllowPlace
        BaritoneUtils.settings?.allowBreak?.value = baritoneSettingAllowBreak
        BaritoneUtils.settings?.renderGoal?.value = baritoneSettingRenderGoal
        BaritoneUtils.settings?.allowInventory?.value = baritoneSettingAllowInventory
    }
}