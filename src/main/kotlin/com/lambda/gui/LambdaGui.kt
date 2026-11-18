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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.unit.IntSize
import com.lambda.Lambda.LOG
import com.lambda.Lambda.mc
import com.lambda.core.Loadable
import com.lambda.event.events.ClientEvent
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.graphics.mc.LambdaRenderPipelines
import com.lambda.gui.LambdaGui.Content
import com.lambda.gui.LambdaGui.warmUp
import com.lambda.gui.clickgui.ClickGui
import com.lambda.gui.clickgui.Reveal
import com.lambda.gui.component.KeyboardFocus
import com.lambda.gui.component.TextFocus
import com.lambda.gui.core.GameClipboard
import com.lambda.gui.core.LocalObservations
import com.lambda.gui.core.Observations
import com.lambda.gui.core.Retained
import com.lambda.gui.core.SceneHost
import com.lambda.gui.core.SceneInput
import com.lambda.gui.core.awaitFrames
import com.lambda.gui.hud.Hud
import com.lambda.gui.hud.HudEditor
import com.lambda.gui.hud.HudLayer
import com.lambda.gui.render.GuiCompositor
import com.lambda.gui.render.ItemAtlas
import com.lambda.gui.settings.SettingsList
import com.lambda.gui.theme.LambdaTheme
import com.lambda.module.modules.client.AutoUpdater
import com.lambda.module.modules.client.Client
import com.lambda.module.modules.client.Style
import com.lambda.sound.LambdaSound
import com.lambda.sound.SoundHandler.play
import com.lambda.util.CommunicationUtils.warn
import net.minecraft.client.gui.cursor.Cursor
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.impl.Library
import kotlin.concurrent.thread

/**
 * Lambda's interface: the HUD drawn over the game, and the screens opened on top of it.
 *
 * All of it is one Compose scene that lives for as long as the client does. What it shows follows
 * [screen]; the Minecraft-side [GuiScreen] only exists to route input into it while one is open.
 */
object LambdaGui : Loadable {
    enum class Screen {
        ClickGui,
        HudEditor
    }

    /** The screen that is open, or null while only the HUD shows */
    var screen: Screen? by mutableStateOf(null)
        private set

    private var host: SceneHost? = null
    private var broken = false

    private val observations = Observations()

    /** What the click GUI observes, on its own because it stays composed while closed: see [Content] */
    private val clickGuiObservations = Observations()

    /** Whether the scene has had a frame since the screen closed, to put away what was open on it */
    private var settled = true

    /** Set for the one frame in which [warmUp] draws the click GUI without it being open */
    private var warmingUp by mutableStateOf(false)
    private var warmedUp = false

    internal val input: SceneInput? get() = host?.input

    /** Cursor the element under the pointer wants, or null for the default */
    internal val cursor: Cursor? get() = host?.platform?.cursor

    /** True while key presses belong to the interface: a text field is focused or a keybind is being recorded */
    val isCapturingKeys get() = TextFocus.isTyping || GuiScreen.keybindRecorder != null

    init {
        listenUnsafe<ClientEvent.Shutdown> { close() }
    }

    override fun load(): String {
        // Loading Skia's native library and listing the system fonts take a noticeable moment,
        // and neither needs the render thread. Doing them now keeps the first frame from paying.
        thread(name = "Lambda GUI preload", isDaemon = true) {
            runCatching {
                prepareSkiko()
                Library.staticLoad()
                FontMgr.default
            }.onFailure { LOG.warn("Could not preload the GUI's native library", it) }
        }
        // Registered this early, the compositor's shaders are compiled along with the game's own
        // while it loads, rather than on the frame the GUI first needs them
        LambdaRenderPipelines.GUI_COMPOSITE
        return "Loaded GUI"
    }

    fun toggle() {
        if (isCapturingKeys) return
        if (screen == null) open(Screen.ClickGui) else GuiScreen.close()
    }

    fun open(screen: Screen) {
        if (broken) {
            warn("Lambda's interface stopped after an error. Check the log, and restart the game to bring it back.")
            return
        }
        if (this.screen == null) {
            if (!GuiScreen.canOpenOver(mc.currentScreen)) return
            if (Client.clientSounds) LambdaSound.ModuleOn.play()
            GuiScreen.show()
            AutoUpdater.onGuiOpened()
        }
        this.screen = screen
    }

    /** Leaves the open screen: the HUD editor for the click GUI, the click GUI for the game */
    /**
     * What Escape closes before it leaves the screen, when nothing in the interface took the
     * key: set by whatever is open over a screen without being a popup. Returns whether it closed.
     */
    internal var onEscape: (() -> Boolean)? = null

    fun back() {
        if (screen == Screen.HudEditor) screen = Screen.ClickGui else GuiScreen.close()
    }

    /** Called by [GuiScreen] when Minecraft takes it down, whatever the reason */
    internal fun onScreenRemoved() {
        if (screen == null) return
        if (Client.clientSounds) LambdaSound.ModuleOff.play()
        screen = null
        settled = false
        input?.release()
        host?.platform?.cursor = null
    }

    /**
     * Draws the interface over the frame. Called once per frame, after the game and its own GUI
     * have been drawn.
     */
    fun render() {
        if (broken) return
        val size = IntSize(mc.window.framebufferWidth, mc.window.framebufferHeight)
        if (size.width <= 0 || size.height <= 0) return
        val density = size.height / REFERENCE_HEIGHT * Style.scale

        if (!warmedUp) {
            warmedUp = true
            warmUp(size, density)
        }
        if (screen == null && Hud.shown.isEmpty() && settled) return

        try {
            val host = host ?: createHost()
            settled = true

            observations.refresh()
            if (screen == Screen.ClickGui) clickGuiObservations.refresh()
            val sceneChanged = host.advance(size, density)
            val itemsChanged = ItemAtlas.render()
            val redraw = sceneChanged || itemsChanged
            if (redraw) host.draw(beforeScene = ItemAtlas::import)

            GuiCompositor.composite(host.texture ?: return, size.width, size.height, redraw, effects(density))
        } catch (e: Throwable) {
            // A scene that threw is in no state to draw the next frame; stop rather than throw every frame
            broken = true
            LOG.error("Lambda's interface failed and was switched off", e)
            if (screen != null) GuiScreen.close()
        }
    }

    /**
     * Builds the interface and draws the click GUI once without showing it. This runs on the
     * game's first frame, which is spent behind the loading screen, and takes everything that
     * makes a first time slow with it: loading Compose's and Skia's classes, composing the
     * click GUI, creating Skia's context and render target, compiling its shaders and
     * rasterising the glyphs. Without it, all of that would freeze the game for a second the
     * moment the GUI is first opened.
     */
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
            // Nothing is lost but the head start: the real thing starts over with a scene of its own
            LOG.warn("Could not prepare the GUI ahead of its first use", e)
            warmingUp = false
            runCatching { close() }
            host = null
        }
    }

    private fun createHost(): SceneHost {
        prepareSkiko()
        return SceneHost { Content() }.also { host = it }
    }

    @Composable
    private fun Content() {
        CompositionLocalProvider(LocalObservations provides observations, LocalClipboard provides GameClipboard) {
            LambdaTheme {
                KeyboardFocus.Layer()
                // What the search pointed at is lit for a moment, wherever it is shown
                val revealed = Reveal.module ?: Reveal.setting
                LaunchedEffect(revealed) {
                    if (revealed != null) {
                        awaitFrames(Reveal.MILLIS)
                        Reveal.clear()
                    }
                }

                // The HUD is for the game, and for other screens if the user says so. The click
                // GUI never has it: it would only sit under the windows and get in their way.
                when (screen) {
                    Screen.HudEditor -> HudEditor()
                    Screen.ClickGui -> Unit
                    null -> HudLayer()
                }
                // Composed once and kept, so that opening the click GUI only has to show it
                Retained(shown = screen == Screen.ClickGui || warmingUp, clickGuiObservations) { ClickGui() }
                if (warmingUp) WarmUp()
            }
        }
    }

    /** What [warmUp] draws besides the click GUI itself: the settings, which open later */
    @Composable
    private fun WarmUp() {
        SettingsList(Style.settingLayers)
    }

    /**
     * The game is only frosted behind an open screen, and only the click GUI glows: the HUD on
     * its own has to cost nothing, and the HUD editor shows the HUD as it looks in game.
     */
    private fun effects(density: Float) = when (screen) {
        null -> GuiCompositor.Effects.NONE
        else -> GuiCompositor.Effects(
            frost = if (Style.blur) Style.blurRadius * density else 0f,
            glow = if (Style.glow && screen == Screen.ClickGui) Style.glowRadius * density else 0f,
            glowIntensity = Style.glowIntensity,
            glowColorA = Style.glowColorA,
            glowColorB = Style.glowColorB,
        )
    }

    private fun close() {
        host?.close()
        host = null
    }

    /** Skia will not create an OpenGL context on macOS unless told to before it loads */
    private fun prepareSkiko() {
        if (System.getProperty("skiko.renderApi") != null) return
        System.setProperty("skiko.renderApi", "OPENGL")
        System.setProperty("skiko.macos.opengl.enabled", "true")
    }

    /**
     * The interface is laid out for a screen this many dp tall and scaled to fit the real one.
     * At 1080p that makes a dp two pixels, the size of a unit of Minecraft's GUI at scale 2.
     */
    private const val REFERENCE_HEIGHT = 540f
}
