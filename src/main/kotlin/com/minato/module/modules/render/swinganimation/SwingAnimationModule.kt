@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation

import com.minato.Minato.mc
import com.minato.event.events.ButtonEvent
import com.minato.event.events.GuiEvent
import com.minato.event.events.HudRenderEvent
import com.minato.event.events.PlayerEvent
import com.minato.event.events.RenderEvent
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.event.listener.UnsafeListener.Companion.listenUnsafe
import com.minato.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.minato.gui.DearImGui
import com.minato.gui.dsl.ImGuiBuilder.buildLayout
import com.minato.module.Module
import com.minato.module.hud.HudTheme
import java.awt.Color
import com.minato.util.KeyCode
import com.minato.util.extension.tickDelta
import com.minato.module.modules.render.swinganimation.combo.SpearComboTracker
import com.minato.module.modules.render.swinganimation.combo.SpearStage
import com.minato.module.modules.render.swinganimation.kill.KillConfirmHud
import com.minato.module.modules.render.swinganimation.kill.KillDetector
import com.minato.module.modules.render.swinganimation.kill.KillEffectSequence
import com.minato.module.modules.render.swinganimation.kill.KillstreakTracker
import com.minato.module.modules.render.swinganimation.kill.LightningBoltRenderer
import com.minato.module.modules.render.swinganimation.kill.SoulParticleLayer
import com.minato.module.modules.render.swinganimation.kill.GroundShockwaveLayer
import com.minato.module.modules.render.swinganimation.quality.AutoQualityDetector
import com.minato.module.modules.render.swinganimation.trail.TrailRibbonMesh
import com.minato.module.modules.render.swinganimation.trail.WeaponAfterimageLayer
import com.minato.module.modules.render.swinganimation.trail.WeaponTipTracker
import com.minato.module.modules.render.swinganimation.impact.ImpactPunchEffect
import com.minato.module.modules.render.swinganimation.sprint.DashAfterimageLayer
import com.minato.module.modules.render.swinganimation.sprint.FootstepDustLayer
import com.minato.module.modules.render.swinganimation.sprint.SpeedLineLayer
import com.minato.module.modules.render.swinganimation.sprint.SpeedVignetteOverlay
import com.minato.module.modules.render.swinganimation.sprint.SprintTrailManager
import com.minato.module.modules.render.swinganimation.ui.PanelSettings
import com.minato.module.modules.render.swinganimation.ui.renderWeaponCheckPanel
import com.minato.module.tag.ModuleTag
import net.minecraft.util.math.Vec3d

/**
 * Swing Animation Module — tạo hiệu ứng trail vũ khí, vệt chém 3D,
 * glow, spark particles, afterimage khi swing + Kill Effect khi hạ gục.
 *
 * ### Kill Effect Lifecycle
 * 1. [KillDetector] → phát hiện kill từ [EntityEvent.Removal] với KILLED reason
 * 2. [KillstreakTracker] → cập nhật streak, kiểm tra escalation
 * 3. Spawn [KillEffectSequence] → render lightning/souls/shockwave/text trong ~1.2s
 * 4. Giới hạn 3 concurrent sequences
 */
object SwingAnimation : Module(
    name = "SwingAnimation",
    description = "Adds trail effects, particles and kill effects",
    tag = ModuleTag.RENDER,
    enabledByDefault = true,
) {
    // ========== SWING TRAIL SETTINGS ==========

    private var qualityPreset by setting("Quality Preset", QualityPreset.MEDIUM)
    private var isAutoQuality by setting("Auto Quality", true)
    private val maxEffectsSetting by setting("Max Effects", 20, 5..40, 1)

    private var swordEnabled by setting("Sword Trail", true)
    private var axeEnabled by setting("Axe Trail", true)
    private var maceEnabled by setting("Mace Effect", true)
    private var tridentEnabled by setting("Trident Trail", true)
    private var spearEnabled by setting("Spear Effect", true)
    private var bowEnabled by setting("Bow Effect", true)
    private var crossbowEnabled by setting("Crossbow Effect", true)
    private var fistEnabled by setting("Fist Trail", false)

    private var trailWidth by setting("Trail Width", 0.15f, 0.02f..0.6f, 0.01f)
    private var trailLength by setting("Trail Length", 1.2f, 0.5f..3.0f, 0.1f)

    // ── Custom Trail Colors ──
    private var useCustomTrailColors by setting("Custom Trail Colors", false, "Override weapon default colors with custom values")
    private var customTrailCoreColor by setting("Trail Core Color", Color(0xFF, 0xFF, 0xFF))
    private var customTrailGlowColor by setting("Trail Glow Color", Color(0xBE, 0xEF, 0xFF))
    private var customTrailSparkColor by setting("Trail Spark Color", Color(0xDF, 0xF7, 0xFF))
    // Rim light intensity multiplier
    private var rimLightIntensity by setting("Rim Light Intensity", 1.4f, 0.0f..3.0f, 0.1f)

    private val spearComboWindow by setting("Spear Combo Window (ms)", 600, 200..1500, 50)
    private val spearShowComboCounter by setting("Show Combo Counter", true)

    private var killEnabled by setting("Kill Effect", true)
    private var killLightning by setting("Kill Lightning", true)
    private var killSouls by setting("Kill Soul Particles", true)
    private var killShockwave by setting("Kill Shockwave", true)
    private var killCoreColor by setting("Kill Lightning Core Color", Color(0xDC, 0xEF, 0xFF))
    private var killGlowColor by setting("Kill Lightning Glow Color", Color(0xDC, 0xEF, 0xFF, 120))
    private var killUseGuiTheme by setting("Kill Lightning Use GUI Primary Color", false)
    private var killVignette by setting("Kill Vignette Pulse", true)
    private var killText by setting("Kill Confirm Text", true)
    private var killStreakEscalation by setting("Killstreak Escalation", true)
    private var killTriggerMobs by setting("Kill Effect on Mobs", false)
    private var killReduceFlash by setting("Reduce Flash (Kill)", false)

    private var sprintFootstepDust by setting("Footstep Dust", true)
    private var sprintSpeedLines by setting("Speed Lines", true)
    private var sprintVignette by setting("Speed Vignette", false)
    private var sprintDashAfterimage by setting("Dash Afterimage", false)
    private var sprintVignetteMaxOpacity by setting("Vignette Max Opacity", 0.15f, 0.0f..0.4f, 0.01f)

    private var impactPunchEnabled by setting("Impact Punch", true)
    private var impactPunchFov by setting("FOV Punch Intensity", 0.02f, 0.0f..0.05f, 0.005f)
    private var impactPunchFlash by setting("Flash Intensity", 0.12f, 0.0f..0.25f, 0.01f)
    private var impactPunchShake by setting("Shake Intensity", 0.015f, 0.0f..0.04f, 0.005f)

    // ── HudTheme Integration ──
    private var useHudThemeForTrailColors by setting("Use HUD Theme For Trails", false, "Trail colors automatically follow LIGHT/DARK theme")
    private var useHudThemeForKillColors by setting("Use HUD Theme For Kill Effects", false, "Kill lightning/effects follow LIGHT/DARK theme")
    private var useHudThemeForSprintEffects by setting("Use HUD Theme For Sprint Effects", false, "Sprint effects follow LIGHT/DARK theme")

    val sprintDustCount: Int get() = qualityPreset.soulParticles.coerceIn(4, 16) / 2
    val sprintLineCount: Int get() = qualityPreset.sparkCount.coerceIn(4, 20)
    val sprintGhostCount: Int get() = qualityPreset.afterimageCount.coerceIn(2, 8)

    private val effectsLock = Any()
    private val activeEffects = mutableListOf<ActiveSwingEffect>()
    private var renderSnapshot: List<ActiveSwingEffect> = emptyList()
    private val killEffects = mutableListOf<KillEffectSequence>()
    private val spearCombo = SpearComboTracker(comboWindowMs = spearComboWindow)
    private val maxEffects: Int get() = maxEffectsSetting.coerceIn(1, 40)

    // Object pools for GC pressure reduction
    private val swingEffectPool = ObjectPool<ActiveSwingEffect>(
        maxSize = 40,
        resetFn = { it.reset() },
    )
    private val killEffectPool = ObjectPool<KillEffectSequence>(
        maxSize = 8,
        resetFn = { it.soulParticles = null },
    )

    private var weaponCheckPanelOpen = false
    private var weaponCheckPanelTab = 0

    /** Effective preset — uses AutoQualityDetector when isAutoQuality is enabled */
    val effectiveQualityPreset: QualityPreset
        get() = if (isAutoQuality) AutoQualityDetector.effectivePreset else qualityPreset

    val historyPoints: Int get() = effectiveQualityPreset.historyPoints
    val subdivisions: Int get() = effectiveQualityPreset.subdivisions
    val glowLayers: Int get() = effectiveQualityPreset.glowLayers
    val sparkCount: Int get() = effectiveQualityPreset.sparkCount
    val afterimageCount: Int get() = effectiveQualityPreset.afterimageCount
    val hasRimLight: Boolean get() = effectiveQualityPreset.hasRimLight
    val hasChromaPulse: Boolean get() = effectiveQualityPreset.hasChromaPulse

    /**
     * Returns the effective trail colors — either weapon default or custom user colors.
     */
    val effectiveTrailColors: WeaponColorScheme
        get() {
            if (useCustomTrailColors) {
                return WeaponColorScheme(
                    core = customTrailCoreColor.rgb,
                    glow = customTrailGlowColor.rgb,
                    spark = customTrailSparkColor.rgb,
                )
            }
            // Fallback: will be resolved per-weapon in rendering
            return WeaponColorScheme(0xFFFFFFFF.toInt(), 0xFFBEEFFF.toInt(), 0xFFDFF7FF.toInt())
        }

    val effectiveRimLightIntensity: Float get() = rimLightIntensity

    // ── Theme-Aware Color Resolution ──

    /**
     * Returns theme-adapted trail colors when HudTheme integration is enabled.
     * Priority: useHudThemeForTrailColors > useCustomTrailColors > weapon defaults
     */
    val themeTrailColors: WeaponColorScheme?
        get() {
            if (!useHudThemeForTrailColors) return null
            val theme = HudTheme.current
            return WeaponColorScheme(
                core = theme.primaryTextColorInt,
                glow = theme.accentColor.rgb,
                spark = Color(
                    theme.accentColor.red,
                    theme.accentColor.green,
                    theme.accentColor.blue,
                    180
                ).rgb,
            )
        }

    /**
     * Resolves effective kill lightning colors considering all color sources.
     * Priority: useHudThemeForKillColors > killUseGuiTheme > killCoreColor/killGlowColor
     */
    val effectiveKillCoreColor: Color
        get() {
            if (useHudThemeForKillColors) {
                return HudTheme.current.dangerColor
            }
            return if (killUseGuiTheme) com.minato.gui.components.ClickGuiLayout.primaryColor else killCoreColor
        }

    val effectiveKillGlowColor: Color
        get() {
            if (useHudThemeForKillColors) {
                val theme = HudTheme.current
                return Color(theme.dangerColor.red, theme.dangerColor.green, theme.dangerColor.blue, 120)
            }
            return if (killUseGuiTheme) com.minato.gui.components.ClickGuiLayout.primaryColor else killGlowColor
        }

    /**
     * Theme-aware sprint effect accent color.
     */
    val themeSprintAccent: Color?
        get() = if (useHudThemeForSprintEffects) HudTheme.current.accentColor else null

    init {
        // ========== KILL DETECTOR ==========
        onEnable { KillDetector.triggerOnMobs = killTriggerMobs }
        KillDetector.onKill = { killPos, _, streak, isMob ->
            if (isEnabled && killEnabled) {
                synchronized(killEffects) {
                    while (killEffects.size >= KillEffectSequence.MAX_CONCURRENT) {
                        killEffects.removeFirst()
                    }

                    val label = if (killStreakEscalation) KillConfirmHud.formatStreakLabel(streak) else ""

                    val seq = killEffectPool.obtain {
                        KillEffectSequence(
                            killPosition = Vec3d(killPos.x, killPos.y, killPos.z),
                            streak = streak,
                            streakLabel = label,
                            reduceFlash = killReduceFlash,
                        )
                    }
                    // Nếu recycle, reset timing bằng cách re-assign start time
                    seq.reinit(
                        killPosition = Vec3d(killPos.x, killPos.y, killPos.z),
                        streak = streak,
                        streakLabel = label,
                        reduceFlash = killReduceFlash,
                    )

                    seq.lightningSegments = effectiveQualityPreset.lightningSegments
                    seq.soulParticleCount = effectiveQualityPreset.soulParticles
                    seq.soulParticles = SoulParticleLayer.ParticleData.generate(
                        seed = (killPos.x * 1000 + killPos.z).toLong(),
                        count = seq.soulParticleCount,
                    )

                    killEffects.add(seq)
                }
            }
        }

        onEnable {
            synchronized(effectsLock) {
                activeEffects.clear()
                renderSnapshot = emptyList()
            }
            synchronized(killEffects) { killEffects.clear() }
            AutoQualityDetector.reset()
            KillDetector.clear()
            KillstreakTracker.reset()
            spearCombo.reset()
            SprintTrailManager.reset()
            FootstepDustLayer.reset()
            SpeedLineLayer.reset()
            SpeedVignetteOverlay.reset()
            DashAfterimageLayer.reset()
            ImpactPunchEffect.reset()
        }

        onDisable {
            synchronized(effectsLock) {
                activeEffects.clear()
                swingEffectPool.clear()
                renderSnapshot = emptyList()
            }
            synchronized(killEffects) {
                killEffects.forEach { killEffectPool.free(it) }
                killEffects.clear()
                killEffectPool.clear()
            }
            SprintTrailManager.reset()
            FootstepDustLayer.reset()
            SpeedLineLayer.reset()
            SpeedVignetteOverlay.reset()
            DashAfterimageLayer.reset()
            ImpactPunchEffect.reset()
        }

        listen<PlayerEvent.Attack.Entity> {
            if (isEnabled && impactPunchEnabled) {
                ImpactPunchEffect.onHit()
            }
        }

        listen<PlayerEvent.SwingHand> { event ->
            if (!isEnabled) return@listen
            val player = mc.player ?: return@listen

            val mainHand = player.mainHandStack
            val weaponType = WeaponResolver.resolve(mainHand)
            if (!isWeaponEnabled(weaponType)) return@listen

            // Thông báo cho WeaponTipTracker để dùng elapsed-time tracking
            // (không phụ thuộc vào handSwinging set bởi ViewModel)
            WeaponTipTracker.onSwingStart()

            val comboStage: Int = if (weaponType == WeaponType.SPEAR) {
                spearCombo.comboWindowMs = spearComboWindow
                spearCombo.registerHit()
            } else {
                0
            }

            val isFallSmash = weaponType == WeaponType.MACE && player.fallDistance > 1.5f

            // Crit detection: falling, not in water (vanilla mechanics)
            val isCrit = weaponType.isMelee &&
                    player.fallDistance > 0f &&
                    !player.isOnGround &&
                    !player.isTouchingWater

            synchronized(effectsLock) {
                while (activeEffects.size >= maxEffects) {
                    val removed = activeEffects.removeFirst()
                    swingEffectPool.free(removed)
                }

                val ctx = SwingContext(
                    weaponType = weaponType,
                    hand = event.hand,
                    playerPos = player.pos,
                    yaw = player.yaw.toFloat(),
                    pitch = player.pitch.toFloat(),
                    useRemainingTicks = player.itemUseTimeLeft,
                    targetEntity = null,
                    hitResult = null,
                    fallDistance = player.fallDistance.toFloat(),
                    isCritical = isCrit,
                    comboStage = comboStage,
                    isFallSmash = isFallSmash,
                )

                val effect = swingEffectPool.obtain { ActiveSwingEffect(ctx, maxHistoryPoints = historyPoints) }
                effect.reinit(ctx)
                activeEffects.add(effect)
            }
        }

        listen<TickEvent.Render.Pre> {
            synchronized(effectsLock) {
                val iter = activeEffects.iterator()
                while (iter.hasNext()) {
                    val effect = iter.next()
                    if (!effect.tick()) {
                        iter.remove()
                        swingEffectPool.free(effect)
                    }
                }
            }
            synchronized(killEffects) {
                val iter = killEffects.iterator()
                while (iter.hasNext()) {
                    val seq = iter.next()
                    if (!seq.isAlive) {
                        iter.remove()
                        killEffectPool.free(seq)
                    }
                }
            }

            // Auto quality detection
            if (isAutoQuality) {
                AutoQualityDetector.tick(System.nanoTime())
            }

            if (isEnabled) {
                val player = mc.player ?: return@listen
                SprintTrailManager.update(player.isSprinting)

                val intensity = SprintTrailManager.speedIntensity
                val moveDir = if (intensity > 0.1f) {
                    Vec3d(-player.velocity.x, 0.0, -player.velocity.z).normalize()
                } else {
                    Vec3d.ZERO
                }

                FootstepDustLayer.tick(
                    footPos = player.pos.add(0.0, 0.1, 0.0),
                    moveDir = moveDir,
                    intensity = intensity,
                    qualityCount = sprintDustCount,
                    enabled = sprintFootstepDust,
                )
                SpeedLineLayer.tick(intensity = intensity, maxLines = sprintLineCount, enabled = sprintSpeedLines)
                SpeedVignetteOverlay.tick(intensity = intensity, maxOpacity = sprintVignetteMaxOpacity, enabled = sprintVignette)
                DashAfterimageLayer.tick(
                    position = player.pos, yaw = player.yaw, pitch = player.pitch,
                    intensity = intensity, maxGhosts = sprintGhostCount, enabled = sprintDashAfterimage,
                )
                ImpactPunchEffect.tick(
                    baseFovIntensity = impactPunchFov, baseFlashIntensity = impactPunchFlash,
                    baseShakeIntensity = impactPunchShake, enabled = impactPunchEnabled,
                )
            }
        }

        listen<RenderEvent.PreRenderWorld> {
            if (!isEnabled) return@listen
            val tDelta = mc.tickDelta.toFloat()
            WeaponTipTracker.update(tDelta)

            synchronized(effectsLock) {
                val player = mc.player ?: return@synchronized
                activeEffects.forEach { effect ->
                    if (effect.context.weaponType == WeaponType.MACE) return@forEach
                    val tipPos = WeaponTipTracker.getTipPosition(player, tDelta)
                    effect.addPoint(tipPos)
                }
                renderSnapshot = activeEffects.toList()
            }
        }

        // ========== 3D RENDERING via immediateRenderer (RenderBuilder DSL) ==========
        // Note: Listener ordering ensures module's SafeListener<PreRenderWorld> runs BEFORE this
        // UnsafeListener (registered by ImmediateRenderer). But as a safety net, we also refresh
        // renderSnapshot here directly from activeEffects under lock, eliminating any ordering
        // dependency. Without this, the first render frame after a swing would have only 1 trail
        // point (added by SafeListener) and renderTrail() would skip it (points.size < 2 check).
        immediateRenderer("SwingAnimation 3D") {
            if (!isEnabled) return@immediateRenderer

            // Refresh renderSnapshot directly from activeEffects — guarantees ordering independence
            synchronized(effectsLock) {
                renderSnapshot = activeEffects.toList()
            }

            // Performance guard: skip all rendering if nothing to draw
            if (renderSnapshot.isEmpty() && killEffects.isEmpty()
                && !sprintFootstepDust && !sprintDashAfterimage) return@immediateRenderer

            val player = mc.player ?: return@immediateRenderer
            val tDelta = mc.tickDelta.toFloat()
            val cameraPos = player.getCameraPosVec(tDelta)

            // Determine effective colors for this render pass:
            // Priority: HudTheme > custom colors > weapon defaults
            val hudThemeColors = themeTrailColors
            val customColorsForPass = when {
                hudThemeColors != null -> hudThemeColors
                useCustomTrailColors -> WeaponColorScheme(
                    core = customTrailCoreColor.rgb,
                    glow = customTrailGlowColor.rgb,
                    spark = customTrailSparkColor.rgb,
                )
                else -> null
            }
            val rimIntensity = effectiveRimLightIntensity

            // Swing trails + weapon afterimages
            TrailRibbonMesh.run {
                renderSnapshot.forEach { effect ->
                    // Weapon afterimage (ghost silhouettes dọc trail)
                    if (afterimageCount > 0 && effect.alpha > 0.3f && effect.context.weaponType.isMelee) {
                        WeaponAfterimageLayer.run {
                            this@immediateRenderer.render(
                                effect = effect,
                                cameraPos = cameraPos,
                                afterimageCount = afterimageCount,
                                width = trailWidth,
                            )
                        }
                    }
                    when (effect.context.weaponType) {
                        WeaponType.MACE -> {
                            this@immediateRenderer.renderRingWave(
                                center = player.pos.add(0.0, -0.5, 0.0),
                                cameraPos = cameraPos,
                                progress = effect.progress.coerceIn(0f, 1f),
                                alpha = effect.alpha,
                                segments = subdivisions * 4,
                                isFallSmash = effect.context.isFallSmash,
                            )
                        }
                        WeaponType.TRIDENT -> {
                            this@immediateRenderer.renderTridentTrail(
                                effect = effect,
                                cameraPos = cameraPos,
                                subdivisions = subdivisions,
                            )
                        }
                        WeaponType.SPEAR -> {
                            val stage = effect.context.comboStage
                            val stageInfo = when (stage) {
                                1 -> SpearStage.THRUST
                                2 -> SpearStage.SIDE_SWEEP
                                3 -> SpearStage.SPIN_SLASH
                                else -> null
                            }
                            if (stage == 3 && stageInfo != null) {
                                this@immediateRenderer.renderSpearSpinSlash(
                                    center = player.pos,
                                    cameraPos = cameraPos,
                                    progress = effect.progress.coerceIn(0f, 1f),
                                    radius = stageInfo.radius,
                                )
                            } else {
                                val trailW = if (stageInfo != null) trailWidth * (stageInfo.trailWidth / 0.12f) else trailWidth
                                this@immediateRenderer.renderTrail(
                                    effect = effect,
                                    weaponType = effect.context.weaponType,
                                    cameraPos = cameraPos,
                                    width = trailW,
                                    subdivisions = subdivisions,
                                    glowLayers = glowLayers,
                                    sparkCount = sparkCount,
                                    hasRimLight = hasRimLight,
                                    hasChromaPulse = hasChromaPulse && effect.context.isCritical,
                                    customColors = customColorsForPass,
                                    rimLightIntensity = rimIntensity,
                                )
                            }
                        }
                        else -> {
                            this@immediateRenderer.renderTrail(
                                effect = effect,
                                weaponType = effect.context.weaponType,
                                cameraPos = cameraPos,
                                width = trailWidth,
                                subdivisions = subdivisions,
                                glowLayers = glowLayers,
                                sparkCount = sparkCount,
                                hasRimLight = hasRimLight,
                                hasChromaPulse = hasChromaPulse && effect.context.isCritical,
                                customColors = customColorsForPass,
                                rimLightIntensity = rimIntensity,
                            )
                        }
                    }
                }
            }

            // Sprint 3D effects (dust + afterimages) — with theme-aware accent color
            val sprintAccent = themeSprintAccent
            if (sprintFootstepDust) {
                FootstepDustLayer.run { this@immediateRenderer.render(cameraPos, SprintTrailManager.speedIntensity, sprintAccent) }
            }
            if (sprintDashAfterimage) {
                DashAfterimageLayer.run { this@immediateRenderer.render(cameraPos, SprintTrailManager.speedIntensity, sprintAccent) }
            }

            // Kill effects (lightning, souls, shockwave)
            synchronized(killEffects) {
                killEffects.forEach { seq ->
                    if (killLightning) {
                        val branchCount = if (killStreakEscalation && seq.streak >= 3) 2
                                          else if (seq.streak >= 2) 1 else 0
                        val mainBolt = LightningBoltRenderer.generateBolt(
                            start = seq.killPosition.add(0.0, 15.0, 0.0),
                            end = seq.killPosition,
                            segments = seq.lightningSegments,
                            seed = System.nanoTime(),
                        )
                        val bolts = if (branchCount > 0) {
                            LightningBoltRenderer.generateForks(mainBolt, branchCount, System.nanoTime() + 1)
                        } else listOf(mainBolt)

                        val coreInt = effectiveKillCoreColor.rgb
                        val glowInt = effectiveKillGlowColor.rgb
                        LightningBoltRenderer.run { this@immediateRenderer.renderBolt(bolts, cameraPos, seq.lightningAlpha, coreInt, glowInt) }
                    }

                    if (killSouls && seq.isSoulActive) {
                        val particles = seq.soulParticles
                        if (particles != null) {
                            SoulParticleLayer.run { this@immediateRenderer.render(seq.killPosition, cameraPos, seq.soulProgress, particles) }
                        }
                    }

                    if (killShockwave && seq.isShockwaveActive) {
                        GroundShockwaveLayer.run { this@immediateRenderer.render(seq.killPosition, cameraPos, seq.shockwaveProgress, seq.shockwaveAlpha) }
                    }
                }
            }
        }

        // ========== 2D RENDERING via HudRenderEvent (DrawContext) ==========
        listen<HudRenderEvent> { event ->
            val window = mc.window
            val drawContext = event.context

            if (isEnabled && killEnabled) {
                synchronized(killEffects) {
                    killEffects.forEach { seq ->
                        if (killVignette && seq.isVignetteActive) {
                            KillConfirmHud.renderVignette(
                                seq.vignetteAlpha, window.width, window.height, killReduceFlash, drawContext,
                            )
                        }
                        if (killText && seq.textAlpha > 0f) {
                            KillConfirmHud.renderText(
                                seq.textAlpha, seq.streak, seq.streakLabel, window.width, window.height, drawContext,
                            )
                        }
                    }
                }
            }

            if (isEnabled) {
                val sprintAccent = themeSprintAccent
                if (sprintSpeedLines) SpeedLineLayer.render(window.width, window.height, drawContext, sprintAccent)
                if (sprintVignette) SpeedVignetteOverlay.render(window.width, window.height, drawContext)
                if (impactPunchEnabled) ImpactPunchEffect.renderFlash(window.width, window.height, drawContext)
            }

            // Spear combo counter
            if (isEnabled && spearEnabled && spearShowComboCounter) {
                val player = mc.player ?: return@listen
                val mainHand = player.mainHandStack
                if (WeaponResolver.resolve(mainHand) == WeaponType.SPEAR) {
                    val stage = spearCombo.currentStage
                    if (stage > 0 && !spearCombo.isTimedOut) {
                        val filled = "\u25CF"
                        val empty = "\u25CB"
                        val comboStr = buildString {
                            for (i in 1..3) {
                                append(if (i <= stage) filled else empty)
                                if (i < 3) append(" ")
                            }
                        }
                        val labelStr = SpearStage.getLabel(stage)
                        val text = "$comboStr  $labelStr"
                        val textRenderer = mc.textRenderer ?: return@listen
                        val textWidth = textRenderer.getWidth(text)
                        val textX = window.width / 2 - textWidth / 2
                        val textY = window.height / 2 + 20
                        drawContext.drawTextWithShadow(textRenderer, text, textX, textY, 0xE690EE90.toInt())
                    }
                }
            }
        }

        // ========== WEAPON CHECK PANEL ==========
        listenUnsafe<ButtonEvent.Keyboard.Press> { event ->
            if (event.keyCode == KeyCode.V.code && event.isPressed) {
                if (DearImGui.io.wantTextInput) return@listenUnsafe
                weaponCheckPanelOpen = !weaponCheckPanelOpen
            }
        }

        listenUnsafe<GuiEvent.NewImguiFrame> {
            if (!weaponCheckPanelOpen) return@listenUnsafe

            val player = mc.player
            val currentWeapon = if (player != null) WeaponResolver.resolve(player.mainHandStack) else WeaponType.FIST

            val panelSettings = PanelSettings(
                currentWeapon = currentWeapon,
                qualityPreset = qualityPreset,
                isAutoQuality = isAutoQuality,
                swordEnabled = swordEnabled, axeEnabled = axeEnabled, maceEnabled = maceEnabled,
                tridentEnabled = tridentEnabled, spearEnabled = spearEnabled,
                bowEnabled = bowEnabled, crossbowEnabled = crossbowEnabled, fistEnabled = fistEnabled,
                trailWidth = trailWidth, trailLength = trailLength,
                useCustomTrailColors = useCustomTrailColors,
                customTrailCoreColor = customTrailCoreColor,
                customTrailGlowColor = customTrailGlowColor,
                customTrailSparkColor = customTrailSparkColor,
                rimLightIntensity = rimLightIntensity,
                useHudThemeForTrailColors = useHudThemeForTrailColors,
                useHudThemeForKillColors = useHudThemeForKillColors,
                useHudThemeForSprintEffects = useHudThemeForSprintEffects,
                hasRimLight = hasRimLight, hasChromaPulse = hasChromaPulse,
                comboStage = spearCombo.currentStage, showComboCounter = spearShowComboCounter,
                killEnabled = killEnabled, killLightning = killLightning, killSouls = killSouls,
                killShockwave = killShockwave, killVignette = killVignette, killText = killText,
                killStreakEscalation = killStreakEscalation, killTriggerMobs = killTriggerMobs,
                killReduceFlash = killReduceFlash,
                killCoreColor = killCoreColor, killGlowColor = killGlowColor, killUseGuiTheme = killUseGuiTheme,
                sprintFootstepDust = sprintFootstepDust, sprintSpeedLines = sprintSpeedLines,
                sprintVignette = sprintVignette, sprintDashAfterimage = sprintDashAfterimage,
                sprintVignetteMaxOpacity = sprintVignetteMaxOpacity,
            )

            buildLayout {
                renderWeaponCheckPanel(
                    onSetTab = { weaponCheckPanelTab = it },
                    settings = panelSettings,
                    onSettingChange = { name, value -> handlePanelSettingChange(name, value) },
                )
            }
        }

        listen<RenderEvent.UpdateTarget> {
            synchronized(effectsLock) {
                activeEffects.clear()
                renderSnapshot = emptyList()
            }
            synchronized(killEffects) {
                killEffects.clear()
                KillstreakTracker.reset()
            }
        }
    }

    private fun isWeaponEnabled(type: WeaponType): Boolean = when (type) {
        WeaponType.SWORD -> swordEnabled
        WeaponType.AXE -> axeEnabled
        WeaponType.MACE -> maceEnabled
        WeaponType.TRIDENT -> tridentEnabled
        WeaponType.SPEAR -> spearEnabled
        WeaponType.BOW -> bowEnabled
        WeaponType.CROSSBOW -> crossbowEnabled
        WeaponType.FIST -> fistEnabled
    }

    private fun handlePanelSettingChange(name: String, value: Any) {
        when (name) {
            "swordEnabled" -> swordEnabled = value as Boolean
            "axeEnabled" -> axeEnabled = value as Boolean
            "maceEnabled" -> maceEnabled = value as Boolean
            "tridentEnabled" -> tridentEnabled = value as Boolean
            "spearEnabled" -> spearEnabled = value as Boolean
            "bowEnabled" -> bowEnabled = value as Boolean
            "crossbowEnabled" -> crossbowEnabled = value as Boolean
            "fistEnabled" -> fistEnabled = value as Boolean
            "trailWidth" -> trailWidth = value as Float
            "trailLength" -> trailLength = value as Float
            "qualityPreset" -> {
                qualityPreset = value as QualityPreset
                isAutoQuality = false  // Manual override disables auto
            }
            // Custom trail colors
            "useCustomTrailColors" -> useCustomTrailColors = value as Boolean
            "customTrailCoreColor" -> customTrailCoreColor = value as java.awt.Color
            "customTrailGlowColor" -> customTrailGlowColor = value as java.awt.Color
            "customTrailSparkColor" -> customTrailSparkColor = value as java.awt.Color
            "rimLightIntensity" -> rimLightIntensity = value as Float
            // HudTheme integration
            "useHudThemeForTrailColors" -> useHudThemeForTrailColors = value as Boolean
            "useHudThemeForKillColors" -> useHudThemeForKillColors = value as Boolean
            "useHudThemeForSprintEffects" -> useHudThemeForSprintEffects = value as Boolean
            // Kill effect
            "killEnabled" -> killEnabled = value as Boolean
            "killLightning" -> killLightning = value as Boolean
            "killSouls" -> killSouls = value as Boolean
            "killShockwave" -> killShockwave = value as Boolean
            "killVignette" -> killVignette = value as Boolean
            "killText" -> killText = value as Boolean
            "killStreakEscalation" -> killStreakEscalation = value as Boolean
            "killTriggerMobs" -> killTriggerMobs = value as Boolean
            "killReduceFlash" -> killReduceFlash = value as Boolean
            "killUseGuiTheme" -> killUseGuiTheme = value as Boolean
            "killCoreColor" -> killCoreColor = value as java.awt.Color
            "killGlowColor" -> killGlowColor = value as java.awt.Color
            "footstepDust" -> sprintFootstepDust = value as Boolean
            "speedLines" -> sprintSpeedLines = value as Boolean
            "speedVignette" -> sprintVignette = value as Boolean
            "dashAfterimage" -> sprintDashAfterimage = value as Boolean
            "vignetteMaxOpacity" -> sprintVignetteMaxOpacity = value as Float
            "impactPunchFov" -> impactPunchFov = value as Float
            "impactPunchFlash" -> impactPunchFlash = value as Float
            "impactPunchShake" -> impactPunchShake = value as Float
            "toggleEnabled" -> toggle()
            "reset" -> resetSettings()
            else -> {}
        }
    }
}
