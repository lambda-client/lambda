/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.gui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.IntSize
import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.config.settings.complex.KeybindSetting
import com.lambda.core.Loadable
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.ComposeEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.gui.clickgui.ClickGui
import com.lambda.gui.component.KeyboardFocus
import com.lambda.gui.component.TextFocus
import com.lambda.gui.core.ComposeSlot
import com.lambda.gui.core.GameClipboard
import com.lambda.gui.core.LocalObservations
import com.lambda.gui.core.Observations
import com.lambda.gui.core.Retained
import com.lambda.gui.core.SceneHost
import com.lambda.gui.core.SceneInput
import com.lambda.gui.hud.Hud
import com.lambda.gui.hud.HudEditor
import com.lambda.gui.hud.HudLayer
import com.lambda.gui.render.GuiCompositor
import com.lambda.gui.render.ItemAtlas
import com.lambda.gui.settings.SettingsList
import com.lambda.gui.theme.GuiInterrupt
import com.lambda.gui.theme.GuiTransition
import com.lambda.gui.theme.LambdaTheme
import com.lambda.gui.theme.Look
import com.lambda.gui.theme.Motion
import com.lambda.module.modules.client.AutoUpdater
import com.lambda.module.modules.client.Client
import com.lambda.module.modules.client.Style
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundHandler.play
import com.lambda.util.CommunicationUtils.warn
import kotlinx.coroutines.Job
import net.minecraft.client.gui.cursor.Cursor
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.impl.Library
import kotlin.concurrent.thread
import kotlin.math.abs

object LambdaGui : Loadable {
    private const val REFERENCE_HEIGHT = 540f
    private const val MAX_FAILURES = 3
    private const val FAILURE_WINDOW_NANOS = 60_000_000_000L

    enum class Screen {
        ClickGui,
        HudEditor
    }

    var screen: Screen? by mutableStateOf(null)
        private set

    private var host: SceneHost? = null
    private var failures = 0
    private var failedAt = 0L
    private var broken = false

    private val beneathHud = ComposeSlot { ComposeEvent.Hud.Pre() }
    private val aboveHud = ComposeSlot { ComposeEvent.Hud.Post() }
    private val beneathGui = ComposeSlot { ComposeEvent.Gui.Pre() }
    private val aboveGui = ComposeSlot { ComposeEvent.Gui.Post() }
    private var underlay: GraphicsLayer? = null
    private var layered by mutableStateOf(false)

    private var settled = true
    private var warmingUp by mutableStateOf(false)
    private var warmedUp = false

    private var shownPresence by mutableFloatStateOf(0f)
    private var animation: Job? = null
    var leaving by mutableStateOf(false)
        private set
    internal var restarts by mutableIntStateOf(0)
        private set
    internal val presence
        get() =
            if (warmingUp) 1f
            else shownPresence
    internal val transitions: Collection<GuiTransition>
        get() =
            if (leaving) Style.closeStyle
            else Style.openStyle
    internal val unfoldsOpen
        get() =
            Style.animations &&
                    Style.openAnimation &&
                    GuiTransition.Unfold in Style.openStyle
    private val showsClickGui
        get() = screen == Screen.ClickGui || leaving
    internal val foldsClosed
        get() = leaving && GuiTransition.Unfold in Style.closeStyle

    private val observations = Observations()
    private val clickGuiObservations = Observations()

    internal val input: SceneInput? get() = host?.input
    internal val cursor: Cursor? get() = host?.platform?.cursor
    internal var onEscape: (() -> Boolean)? = null

    val isCapturingKeys get() = TextFocus.isTyping || KeybindSetting.isRecording

    init {
        listenUnsafe<ClientEvent.Shutdown> { shutDown() }
    }

    override fun load(): String {
        thread(name = "Lambda GUI preload", isDaemon = true) {
            runCatching {
                prepareSkiko()
                Library.staticLoad()
                FontMgr.default
            }.onFailure { LOG.warn("Could not preload the GUI's native library", it) }
        }
        return "Loaded GUI"
    }

    fun toggle() {
        if (isCapturingKeys) return
        if (screen == null) open(Screen.ClickGui)
        else close()
    }

    fun close() {
        val animated =
            Style.animations && Style.closeAnimation && Style.closeStyle.isNotEmpty()
        if (screen == Screen.ClickGui && animated) {
            if (shownPresence < 1f) skipTo(1f)
            leaving = true
        }
        GuiScreen.close()
    }

    private fun skipTo(end: Float) {
        if (Style.interruptedAnimation != GuiInterrupt.Finish) return
        jumpTo(end)
        restarts++
    }

    private fun jumpTo(value: Float) {
        animation?.cancel()
        shownPresence = value
    }

    fun open(screen: Screen) {
        if (broken) {
            warn("Lambda's GUI stopped after repeated errors. Restart the game to bring it back.")
            return
        }
        if (this.screen == null) {
            if (!GuiScreen.canOpenOver(mc.currentScreen)) return
            if (leaving) skipTo(0f)
            else jumpTo(0f)
            if (Client.clientSounds) LambdaSound.ModuleOn.play()
            GuiScreen.show()
            AutoUpdater.onGuiOpened()
        }
        leaving = false
        this.screen = screen
    }

    fun back() {
        if (screen == Screen.HudEditor) screen = Screen.ClickGui
        else close()
    }

    internal fun onScreenRemoved() {
        if (screen == null) return
        if (Client.clientSounds) LambdaSound.ModuleOff.play()
        screen = null
        if (!leaving) jumpTo(0f)
        settled = false
        input?.release()
        host?.platform?.cursor = null
    }

    fun render() {
        if (broken) return
        val size = IntSize(mc.window.framebufferWidth, mc.window.framebufferHeight)
        if (size.width <= 0 || size.height <= 0) return
        val density = size.height / REFERENCE_HEIGHT * Style.scale

        if (!warmedUp) {
            warmedUp = true
            warmUp(size, density)
        }
        val showsGui = screen != null || leaving
        beneathHud.collect()
        aboveHud.collect()
        beneathGui.collect(showsGui)
        aboveGui.collect(showsGui)
        val showsHud = !beneathHud.isEmpty || !aboveHud.isEmpty || screen == null && Hud.shown.isNotEmpty()
        if (!showsGui && !showsHud && settled) return
        layered = showsGui && showsHud

        try {
            val host = host ?: createHost()
            settled = true

            observations.refresh()
            Look.refresh()
            if (screen == Screen.ClickGui) clickGuiObservations.refresh()
            val sceneChanged = host.advance(size, density)
            val itemsChanged = ItemAtlas.render()
            val redraw = sceneChanged || itemsChanged
            if (redraw) host.draw(underlay.takeIf { layered }, ItemAtlas::import)

            if (layered) host.underlayTexture?.let(GuiCompositor::blit)
            GuiCompositor.composite(
                host.texture ?: return,
                size.width,
                size.height,
                redraw,
                effects(density)
            )
        } catch (e: Throwable) {
            if (e is OutOfMemoryError) throw e
            fail(e)
        }
    }

    private fun fail(error: Throwable) {
        val now = System.nanoTime()
        if (now - failedAt > FAILURE_WINDOW_NANOS) failures = 0
        failedAt = now
        broken = ++failures >= MAX_FAILURES
        LOG.error(
            if (broken) "Lambda's interface failed again and was switched off"
            else "Lambda's interface failed and is being rebuilt",
            error
        )

        val failed = host
        host = null
        underlay = null
        ItemAtlas.release()
        runCatching { failed?.closeAndFree() }.onFailure {
            LOG.warn("Could not free the failed interface", it)
        }
        observations.active.clear()
        clickGuiObservations.active.clear()
        if (screen != null) GuiScreen.close()
        warn(
            if (broken) "Lambda's interface stopped after repeated errors. Check the log, and restart the game to bring it back."
            else "Lambda's interface hit an error and was restarted. Check the log for what went wrong."
        )
    }

    private fun warmUp(size: IntSize, density: Float) {
        val start = System.nanoTime()
        try {
            val host = createHost()
            warmingUp = true
            host.advance(size, density)
            host.draw()
            warmingUp = false
            host.advance(size, density)
            LOG.info("Prepared the GUI in ${(System.nanoTime() - start) / 1_000_000} ms")
        } catch (e: Throwable) {
            LOG.warn("Could not prepare the GUI ahead of its first use", e)
            warmingUp = false
            val failed = host
            host = null
            ItemAtlas.release()
            runCatching { failed?.closeAndFree() }
        }
    }

    private fun createHost(): SceneHost {
        prepareSkiko()
        return SceneHost(Motion) { Content() }.also { host = it }
    }

    @Composable
    private fun Content() {
        CompositionLocalProvider(
            LocalObservations provides observations,
            LocalClipboard provides GameClipboard
        ) {
            LambdaTheme {
                KeyboardFocus.Layer()
                Reveal.Expiry()
                Presence()

                Underlay {
                    beneathHud.Content()
                    if (screen == null) HudLayer()
                    aboveHud.Content()
                }
                beneathGui.Content()
                if (screen == Screen.HudEditor) HudEditor()
                Retained(
                    shown = showsClickGui || warmingUp,
                    clickGuiObservations
                ) {
                    ClickGui()
                }
                aboveGui.Content()
                if (warmingUp) WarmUp()
            }
        }
    }

    @Composable
    private fun Underlay(content: @Composable () -> Unit) {
        val layer = rememberGraphicsLayer()
        SideEffect { underlay = layer }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    if (layered) layer.record { this@drawWithContent.drawContent() }
                    else drawContent()
                },
        ) { content() }
    }

    @Composable
    private fun Presence() {
        val present = screen == Screen.ClickGui
        LaunchedEffect(present) {
            animation = coroutineContext[Job]
            val target =
                if (present) 1f
                else 0f
            val animated =
                if (present) Style.openAnimation && Style.openStyle.isNotEmpty()
                else leaving
            if (animated) {
                val spec = Motion.gui(abs(target - shownPresence))
                animate(shownPresence, target, animationSpec = spec) { value, _ ->
                    shownPresence = value
                }
            }
            else shownPresence = target
            if (!present) leaving = false
        }
    }

    @Composable
    private fun WarmUp() {
        SettingsList(Style.settingLayers)
    }

    private fun effects(density: Float): GuiCompositor.Effects {
        if (screen == null && !leaving) return GuiCompositor.Effects.NONE
        val strength =
            if (showsClickGui) presence
            else 1f
        return GuiCompositor.Effects(
            frost =
                if (Style.blur) Style.blurRadius * density * strength
                else 0f,
            glow =
                if (Style.glow && showsClickGui) Style.glowRadius * density
                else 0f,
            glowIntensity = Style.glowIntensity * strength,
            glowColorA = Style.glowColorA,
            glowColorB = Style.glowColorB,
            dim =
                if (Style.dimBackground && showsClickGui) Style.dimStrength * presence
                else 0f,
        )
    }

    private fun shutDown() {
        host?.close()
        host = null
    }

    private fun prepareSkiko() {
        if (System.getProperty("skiko.renderApi") != null) return
        System.setProperty("skiko.renderApi", "OPENGL")
        System.setProperty("skiko.macos.opengl.enabled", "true")
    }
}
