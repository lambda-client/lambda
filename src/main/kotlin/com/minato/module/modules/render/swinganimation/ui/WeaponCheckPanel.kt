@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.ui

import com.lambda.imgui.ImGui
import com.lambda.imgui.flag.ImGuiTabBarFlags
import com.lambda.imgui.flag.ImGuiWindowFlags
import com.lambda.imgui.type.ImBoolean
import com.lambda.imgui.type.ImFloat
import com.lambda.imgui.type.ImInt
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.gui.dsl.ImGuiBuilder.button
import com.minato.gui.dsl.ImGuiBuilder.text
import com.minato.gui.dsl.ImGuiBuilder.textColored
import com.minato.gui.dsl.ImGuiBuilder.window
import com.minato.module.modules.render.swinganimation.QualityPreset
import com.minato.module.modules.render.swinganimation.WeaponType
import com.minato.module.modules.render.swinganimation.combo.SpearStage
import com.minato.module.modules.render.swinganimation.quality.AutoQualityDetector
import java.awt.Color

/**
 * Data class chứa toàn bộ settings cần thiết cho WeaponCheckPanel.
 */
data class PanelSettings(
    // Weapon detection
    val currentWeapon: WeaponType,
    // Quality
    val qualityPreset: QualityPreset,
    val isAutoQuality: Boolean,
    // Weapon toggles
    val swordEnabled: Boolean,
    val axeEnabled: Boolean,
    val maceEnabled: Boolean,
    val tridentEnabled: Boolean,
    val spearEnabled: Boolean,
    val bowEnabled: Boolean,
    val crossbowEnabled: Boolean,
    val fistEnabled: Boolean,
    // Trail appearance
    val trailWidth: Float,
    val trailLength: Float,
    // Custom trail colors
    val useCustomTrailColors: Boolean,
    val customTrailCoreColor: Color,
    val customTrailGlowColor: Color,
    val customTrailSparkColor: Color,
    val rimLightIntensity: Float,
    // HudTheme integration
    val useHudThemeForTrailColors: Boolean,
    val useHudThemeForKillColors: Boolean,
    val useHudThemeForSprintEffects: Boolean,
    // Quality properties
    val hasRimLight: Boolean,
    val hasChromaPulse: Boolean,
    // Spear combo
    val comboStage: Int,
    val showComboCounter: Boolean,
    // Kill effect
    val killEnabled: Boolean,
    val killLightning: Boolean,
    val killSouls: Boolean,
    val killShockwave: Boolean,
    val killVignette: Boolean,
    val killText: Boolean,
    val killStreakEscalation: Boolean,
    val killTriggerMobs: Boolean,
    val killReduceFlash: Boolean,
    // Kill lightning color settings
    val killCoreColor: Color,
    val killGlowColor: Color,
    val killUseGuiTheme: Boolean,
    // Sprint trail
    val sprintFootstepDust: Boolean,
    val sprintSpeedLines: Boolean,
    val sprintVignette: Boolean,
    val sprintDashAfterimage: Boolean,
    val sprintVignetteMaxOpacity: Float,
)

/**
 * Render the WeaponCheckPanel Quick Panel.
 */
fun ImGuiBuilder.renderWeaponCheckPanel(
    onSetTab: (Int) -> Unit,
    settings: PanelSettings,
    onSettingChange: (name: String, value: Any) -> Unit,
) {
    val qualityDisplay = if (settings.isAutoQuality) {
        val info = AutoQualityDetector.getFormattedInfo()
        info
    } else {
        settings.qualityPreset.name
    }

    val panelTitle = if (settings.currentWeapon != WeaponType.FIST) {
        "Weapon Check  [${settings.currentWeapon.name}]  Quality: $qualityDisplay"
    } else {
        "Weapon Check  [Empty/Block]  Quality: $qualityDisplay"
    }

    window(panelTitle, flags = ImGuiWindowFlags.AlwaysAutoResize or ImGuiWindowFlags.NoCollapse) {
        val tabNames = arrayOf("Weapon", "Movement", "Elimination")

        if (ImGui.beginTabBar("##panelTabBar", ImGuiTabBarFlags.None)) {
            if (ImGui.beginTabItem(tabNames[0])) {
                if (ImGui.isItemActivated()) onSetTab(0)
                renderWeaponTab(settings, onSettingChange)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem(tabNames[1])) {
                if (ImGui.isItemActivated()) onSetTab(1)
                renderMovementTab(settings, onSettingChange)
                ImGui.endTabItem()
            }
            if (ImGui.beginTabItem(tabNames[2])) {
                if (ImGui.isItemActivated()) onSetTab(2)
                renderEliminationTab(settings, onSettingChange)
                ImGui.endTabItem()
            }
            ImGui.endTabBar()
        }

        ImGui.separator()

        button("Reset", width = 80f) {
            onSettingChange("reset", true)
        }
        ImGui.sameLine()
        button("Toggle Enabled") {
            onSettingChange("toggleEnabled", true)
        }
    }
}

private fun ImGuiBuilder.renderWeaponTab(
    settings: PanelSettings,
    onSettingChange: (String, Any) -> Unit,
) {
    val weaponName = settings.currentWeapon.name.lowercase().replaceFirstChar { it.uppercase() }
    text("Holding: ")
    ImGui.sameLine()
    textColored(weaponName, Color(130, 200, 255))

    if (settings.currentWeapon == WeaponType.SPEAR && settings.showComboCounter && settings.comboStage > 0) {
        val filled = "\u25CF"
        val empty = "\u25CB"
        val comboStr = buildString {
            for (i in 1..3) {
                append(if (i <= settings.comboStage) filled else empty)
                if (i < 3) append(" ")
            }
        }
        val labelStr = SpearStage.getLabel(settings.comboStage)
        ImGui.sameLine()
        ImGui.textColored(0.4f, 0.8f, 0.5f, 1.0f, "  $comboStr  $labelStr")
    }

    ImGui.separator()

    // Quality Preset — including AUTO mode
    text("Quality:")
    ImGui.sameLine()
    val presets = QualityPreset.entries.map { it.name }.toTypedArray()
    val displayName = if (settings.isAutoQuality) AutoQualityDetector.effectivePreset.name else settings.qualityPreset.name
    val displayIdx = (QualityPreset.entries.indexOfFirst { it.name == displayName }).coerceAtLeast(0)
    val currentPresetIdx = ImInt(displayIdx)
    if (ImGui.combo("##quality", currentPresetIdx, presets)) {
        val selectedPreset = QualityPreset.entries[currentPresetIdx.get()]
        onSettingChange("qualityPreset", selectedPreset)
    }

    // FPS & auto info
    if (settings.isAutoQuality) {
        val fps = AutoQualityDetector.averageFps
        val effectiveName = AutoQualityDetector.effectivePreset.name
        ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, "  AUTO → $effectiveName  ($fps FPS)")
    }

    ImGui.separator()

    text("Weapon Effects")
    drawCheckbox("Sword", settings.swordEnabled) { onSettingChange("swordEnabled", it) }
    drawCheckbox("Axe", settings.axeEnabled) { onSettingChange("axeEnabled", it) }
    drawCheckbox("Mace Ring", settings.maceEnabled) { onSettingChange("maceEnabled", it) }
    drawCheckbox("Trident", settings.tridentEnabled) { onSettingChange("tridentEnabled", it) }
    drawCheckbox("Spear", settings.spearEnabled) { onSettingChange("spearEnabled", it) }
    drawCheckbox("Bow", settings.bowEnabled) { onSettingChange("bowEnabled", it) }
    drawCheckbox("Crossbow", settings.crossbowEnabled) { onSettingChange("crossbowEnabled", it) }
    drawCheckbox("Fist", settings.fistEnabled) { onSettingChange("fistEnabled", it) }

    ImGui.separator()

    text("Trail Settings")
    val trailW = ImFloat(settings.trailWidth)
    if (ImGui.sliderFloat("Width", trailW.data, 0.02f, 0.6f, "%.2f")) {
        onSettingChange("trailWidth", trailW.get())
    }
    val trailL = ImFloat(settings.trailLength)
    if (ImGui.sliderFloat("Length", trailL.data, 0.5f, 3.0f, "%.1f")) {
        onSettingChange("trailLength", trailL.get())
    }
    val glowL = ImInt(3)
    if (ImGui.sliderInt("Bloom Layers", glowL.data, 1, 3)) {
        onSettingChange("bloomLayers", glowL.get())
    }
    val rimIntensity = ImFloat(1.4f)
    ImGui.sliderFloat("Rim Light Intensity", rimIntensity.data, 0.0f, 3.0f, "%.1f")

    ImGui.separator()

    drawCheckbox("Rim Light", settings.hasRimLight) { onSettingChange("rimLight", it) }
    drawCheckbox("Crit Chroma Pulse", settings.hasChromaPulse) { onSettingChange("chromaPulse", it) }

    ImGui.separator()

    // ── HudTheme Integration ──
    ImGui.textColored(0.4f, 0.7f, 1.0f, 1.0f, "HUD Theme Integration")
    drawCheckbox("Use Theme For Trails", settings.useHudThemeForTrailColors) { onSettingChange("useHudThemeForTrailColors", it) }
    if (settings.useHudThemeForTrailColors) ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, "  Trail colors follow LIGHT/DARK theme")

    ImGui.separator()

    // ── Custom Trail Colors ──
    drawCheckbox("Custom Trail Colors", settings.useCustomTrailColors) { onSettingChange("useCustomTrailColors", it) }

    if (settings.useCustomTrailColors && !settings.useHudThemeForTrailColors) {
        ImGui.indent(16f)
        text("Customize Trail Colors:")

        val coreFloats = settings.customTrailCoreColor.getColorComponents(null)
        val coreColFloats = floatArrayOf(coreFloats[0], coreFloats[1], coreFloats[2], 1.0f)
        if (ImGui.colorEdit4("Core", coreColFloats, com.lambda.imgui.flag.ImGuiColorEditFlags.NoAlpha)) {
            onSettingChange("customTrailCoreColor", java.awt.Color(coreColFloats[0], coreColFloats[1], coreColFloats[2]))
        }

        val glowFloats = settings.customTrailGlowColor.getColorComponents(null)
        val glowColFloats = floatArrayOf(glowFloats[0], glowFloats[1], glowFloats[2], 1.0f)
        if (ImGui.colorEdit4("Glow", glowColFloats, com.lambda.imgui.flag.ImGuiColorEditFlags.NoAlpha)) {
            onSettingChange("customTrailGlowColor", java.awt.Color(glowColFloats[0], glowColFloats[1], glowColFloats[2]))
        }

        val sparkFloats = settings.customTrailSparkColor.getColorComponents(null)
        val sparkColFloats = floatArrayOf(sparkFloats[0], sparkFloats[1], sparkFloats[2], 1.0f)
        if (ImGui.colorEdit4("Spark", sparkColFloats, com.lambda.imgui.flag.ImGuiColorEditFlags.NoAlpha)) {
            onSettingChange("customTrailSparkColor", java.awt.Color(sparkColFloats[0], sparkColFloats[1], sparkColFloats[2]))
        }

        val rim = com.lambda.imgui.type.ImFloat(settings.rimLightIntensity)
        if (ImGui.sliderFloat("Rim Light Intensity", rim.data, 0.0f, 3.0f, "%.1f")) {
            onSettingChange("rimLightIntensity", rim.get())
        }
        ImGui.unindent(16f)
    }

    if (!settings.useCustomTrailColors && !settings.useHudThemeForTrailColors) {
        ImGui.text("Default Colors:")
        drawColorButton("Start", Color(255, 255, 255))
        ImGui.sameLine()
        drawColorButton("Mid", Color(207, 239, 255))
        ImGui.sameLine()
        drawColorButton("End", Color(92, 140, 166))
    }
}

private fun ImGuiBuilder.renderMovementTab(
    settings: PanelSettings,
    onSettingChange: (String, Any) -> Unit,
) {
    text("Sprint Trail Settings")
    ImGui.separator()

    // HudTheme integration for sprint effects
    drawCheckbox("Use HUD Theme", settings.useHudThemeForSprintEffects) { onSettingChange("useHudThemeForSprintEffects", it) }
    if (settings.useHudThemeForSprintEffects) {
        ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, "  Sprint colors follow LIGHT/DARK theme")
    }
    ImGui.separator()

    drawCheckbox("Footstep Dust", settings.sprintFootstepDust) { onSettingChange("footstepDust", it) }
    drawCheckbox("Speed Lines", settings.sprintSpeedLines) { onSettingChange("speedLines", it) }
    drawCheckbox("Speed Vignette", settings.sprintVignette) { onSettingChange("speedVignette", it) }

    ImGui.separator()

    text("Vignette Max Opacity")
    val vigOpacity = ImFloat(settings.sprintVignetteMaxOpacity)
    if (ImGui.sliderFloat("##vignetteOpacity", vigOpacity.data, 0.0f, 0.4f, "%.2f")) {
        onSettingChange("vignetteMaxOpacity", vigOpacity.get())
    }

    drawCheckbox("Dash Afterimage", settings.sprintDashAfterimage) { onSettingChange("dashAfterimage", it) }
}

private fun ImGuiBuilder.renderEliminationTab(
    settings: PanelSettings,
    onSettingChange: (String, Any) -> Unit,
) {
    text("Elimination VFX")
    ImGui.separator()

    drawCheckbox("Enabled", settings.killEnabled) { onSettingChange("killEnabled", it) }
    drawCheckbox("Lightning Strike", settings.killLightning) { onSettingChange("killLightning", it) }
    drawCheckbox("Soul Particles", settings.killSouls) { onSettingChange("killSouls", it) }
    drawCheckbox("Ground Shockwave", settings.killShockwave) { onSettingChange("killShockwave", it) }
    drawCheckbox("Vignette Pulse", settings.killVignette) { onSettingChange("killVignette", it) }
    drawCheckbox("Kill Confirm Text", settings.killText) { onSettingChange("killText", it) }
    drawCheckbox("Killstreak Escalation", settings.killStreakEscalation) { onSettingChange("killStreakEscalation", it) }

    ImGui.separator()

    drawCheckbox("Trigger on Mobs", settings.killTriggerMobs) { onSettingChange("killTriggerMobs", it) }
    drawCheckbox("Reduce Flash", settings.killReduceFlash) { onSettingChange("killReduceFlash", it) }

    ImGui.separator()

    text("Streak Text Color:")
    drawColorButton("Text", Color(255, 215, 0))
    ImGui.sameLine()
    drawColorButton("Lightning", Color(220, 240, 255))

    ImGui.separator()
    ImGui.textColored(0.4f, 0.7f, 1.0f, 1.0f, "Theme & Colors")
    drawCheckbox("Use HUD Theme", settings.useHudThemeForKillColors) { onSettingChange("useHudThemeForKillColors", it) }
    if (settings.useHudThemeForKillColors) {
        ImGui.textColored(0.6f, 0.9f, 0.6f, 1.0f, "  Kill colors follow LIGHT/DARK theme")
    }
    if (!settings.useHudThemeForKillColors) {
        drawCheckbox("Use GUI Primary Color", settings.killUseGuiTheme) { onSettingChange("killUseGuiTheme", it) }
        text("Lightning Colors:")
        drawColorButton("Core", settings.killCoreColor)
        ImGui.sameLine()
        drawColorButton("Glow", settings.killGlowColor)
    }
}

private fun drawCheckbox(label: String, currentValue: Boolean, onChange: (Boolean) -> Unit) {
    val bool = ImBoolean(currentValue)
    if (ImGui.checkbox(label, bool)) {
        onChange(bool.get())
    }
}

private fun drawColorButton(label: String, color: Color) {
    val floats = color.getColorComponents(null)
    ImGui.colorButton(label, floats[0], floats[1], floats[2], 1.0f)
    if (ImGui.isItemHovered()) {
        ImGui.beginTooltip()
        ImGui.text("$label (#${Integer.toHexString(color.rgb and 0xFFFFFF).padStart(6, '0').uppercase()})")
        ImGui.endTooltip()
    }
}
