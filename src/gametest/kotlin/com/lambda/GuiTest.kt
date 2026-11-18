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

package com.lambda

import com.lambda.gui.GuiConfig
import com.lambda.gui.GuiScreen
import com.lambda.gui.LambdaGui
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.AutoUpdater
import com.lambda.module.modules.client.Style
import com.lambda.module.modules.hud.Armor
import com.lambda.module.modules.hud.Coordinates
import com.lambda.module.modules.hud.Fps
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screen.world.WorldCreator
import net.minecraft.client.input.KeyInput
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

/**
 * Walks through the interface the way a user would and takes a screenshot at every stop, so a
 * change to the GUI can be looked at without playing through it by hand. It fails if the
 * interface throws along the way, since that closes the screen the next step expects.
 */
@Suppress("UnstableApiUsage")
object GuiTest : FabricClientGameTest {
    private const val WIDTH = 1920
    private const val HEIGHT = 1080

    /** The interface is laid out for a screen 540 dp tall, see [LambdaGui] */
    private const val DENSITY = HEIGHT / 540.0

    override fun runTest(context: ClientGameTestContext) {
        context.input.resizeWindow(WIDTH, HEIGHT)
        context.waitTicks(5)

        // Over the title screen, where there is no world to draw a HUD for. The first time the
        // click GUI opens it offers the loader, which has to be answered before anything else.
        context.openClickGui()
        context.takeScreenshot("gui-0-loader-prompt")
        context.runOnClient<RuntimeException> { AutoUpdater.answerPrompt(accepted = false) }
        context.waitTicks(10)
        context.takeScreenshot("gui-1-title-screen")

        // That was the interface as it comes. The places clicked from here on are those of the
        // Minecraft font at its full size and of rows 13 dp tall, which is set here rather than
        // left to the defaults, so that the defaults can change without moving every click.
        context.runOnClient<RuntimeException> {
            Style.fontSize = 1f
            Style.elementHeight = 13f
        }
        context.waitTicks(5)

        // A left click on the first module of the first window toggles it
        val enabledBefore = context.enabledModules()
        context.click(dpX = 150.0, dpY = 115.0)
        check(context.enabledModules() != enabledBefore) { "Clicking a module did not toggle it" }
        context.takeScreenshot("gui-1-module-enabled")
        context.click(dpX = 150.0, dpY = 115.0)

        // A module is toggled the moment the button goes down, not when it is let go
        val enabledBeforePress = context.enabledModules()
        context.input.setCursorPos(150.0 * DENSITY, 115.0 * DENSITY)
        context.waitTicks(2)
        context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(3)
        check(context.enabledModules() != enabledBeforePress) { "A module was not toggled by the press" }
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(3)
        context.click(dpX = 150.0, dpY = 115.0)

        // A name too long for its window slides along while the pointer rests on it, and home after
        context.input.setCursorPos(240.0 * DENSITY, 193.0 * DENSITY)
        context.waitTicks(30)
        context.takeScreenshot("gui-1a-long-name-hovered")
        context.input.setCursorPos(240.0 * DENSITY, 60.0 * DENSITY)
        context.waitTicks(10)
        context.takeScreenshot("gui-1b-long-name-left")

        // A window goes exactly where it is dragged by its header...
        context.input.setCursorPos(620.0 * DENSITY, 101.0 * DENSITY)
        context.dragBy(3.5, 7.5)
        val free = context.windowPlacement("Network")
        check(abs(free.x - 576.5f) < 0.6f && abs(free.y - 101.5f) < 0.6f) { "A dragged window did not follow the pointer: $free" }

        // ...and with the snap key held it lines up with its neighbours instead
        context.input.holdKey(GLFW.GLFW_KEY_LEFT_SHIFT)
        context.waitTicks(2)
        context.dragBy(2.0, 2.0)
        context.input.releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT)
        val snapped = context.windowPlacement("Network")
        check(abs(snapped.x - 573f) < 0.1f) { "A window dragged with the snap key held did not snap back between its neighbours: $snapped" }

        // Resetting the layout forgets every placement, which puts the windows back in their row
        context.runOnClient<RuntimeException> { GuiConfig.resetWindows() }
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, RuntimeException> { GuiConfig.windows.isEmpty() }) { "Resetting the layout left a window placed" }

        // A right click pops its settings out beside it, over the next window, which blurs behind it
        context.click(dpX = 150.0, dpY = 115.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-2-module-settings")

        // Right clicking another module straight away has one panel fold while the other opens
        context.input.setCursorPos(150.0 * DENSITY, 128.0 * DENSITY)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(1)
        context.takeScreenshot("gui-2a-switching-settings")
        context.waitTicks(10)

        // KillAura sorts its settings into tabs and groups: a group unfolds into a darker block,
        // and the page slides over when another tab is picked
        context.click(dpX = 150.0, dpY = 193.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)

        // What every module has is kept off its own settings, on pages the strip at the top turns to
        context.click(dpX = 298.5, dpY = 198.0)
        context.takeScreenshot("gui-2c2-module-page")
        context.input.setCursorPos(340.0 * DENSITY, 198.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(1)
        context.takeScreenshot("gui-2c3-page-turning")
        context.waitTicks(10)
        context.takeScreenshot("gui-2c1-automation-page")
        context.input.setCursorPos(261.0 * DENSITY, 247.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(1)
        context.takeScreenshot("gui-2c0-group-unfolding")
        context.waitTicks(10)
        context.takeScreenshot("gui-2c-group")
        context.click(dpX = 261.0, dpY = 247.0)
        context.click(dpX = 236.5, dpY = 198.0)

        // A setting is changed when the button is let go: pressed on a toggle and let go off it, nothing changes
        context.input.setCursorPos(250.0 * DENSITY, 231.0 * DENSITY)
        context.waitTicks(2)
        context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(3)
        context.takeScreenshot("gui-2g3-setting-pressed")
        context.input.setCursorPos(250.0 * DENSITY, 150.0 * DENSITY)
        context.waitTicks(2)
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(3)
        context.takeScreenshot("gui-2g4-setting-click-cancelled")

        // An enum lists its options over the panel, without moving the settings under it
        context.input.setCursorPos(320.0 * DENSITY, 273.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(1)
        context.takeScreenshot("gui-2g0-dropdown-unfolding")
        context.waitTicks(10)
        context.takeScreenshot("gui-2g-dropdown")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(1)
        context.takeScreenshot("gui-2g1-dropdown-folding")
        context.waitTicks(2)
        context.takeScreenshot("gui-2g2-dropdown-folded-in")
        context.waitTicks(3)

        // The pages a module sorts its settings into are picked from the band over them
        context.click(dpX = 296.0, dpY = 216.0)
        context.takeScreenshot("gui-2d0-tab-list")
        context.input.setCursorPos(296.0 * DENSITY, 245.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(1)
        context.takeScreenshot("gui-2d-tab-changing")
        context.waitTicks(10)
        context.takeScreenshot("gui-2e-tab-changed")

        // A collection is edited in a window of its own, each entry with its icon
        context.input.setCursorPos(360.0 * DENSITY, 260.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(1)
        context.takeScreenshot("gui-2h0-collection-unfolding")
        context.waitTicks(10)
        context.takeScreenshot("gui-2h-collection")
        context.input.typeChars("stone")
        context.waitTicks(5)
        context.takeScreenshot("gui-2h2-collection-searched")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(1)
        context.takeScreenshot("gui-2h1-collection-folding")
        context.waitTicks(2)
        context.takeScreenshot("gui-2h3-collection-folded-in")
        context.waitTicks(3)

        // Settings too long for the room under their module scroll, and the top edge of the
        // panel then has a grip that drags it taller, up past the module
        context.click(dpX = 340.0, dpY = 198.0)
        // Unfolded from the last up, so none moves before it is clicked
        for (y in listOf(289.0, 275.0, 261.0, 247.0)) context.click(dpX = 261.0, dpY = y)
        context.takeScreenshot("gui-2i-scrolling")

        // The wheel sets the list off fast and lets it glide to rest
        context.input.setCursorPos(290.0 * DENSITY, 400.0 * DENSITY)
        context.waitTicks(2)
        context.input.scroll(-2.0)
        context.waitTicks(1)
        context.takeScreenshot("gui-2i1-wheel-gliding")
        context.waitTicks(15)
        context.takeScreenshot("gui-2i2-wheel-settled")
        context.input.scroll(10.0)
        context.waitTicks(15)
        context.input.setCursorPos(317.0 * DENSITY, 189.0 * DENSITY)
        context.waitTicks(2)
        context.dragBy(0.0, -60.0)
        context.takeScreenshot("gui-2j-extended")

        // However far it is dragged, the panel stops under the menu bar, its grip still in reach
        context.dragBy(0.0, -300.0)
        context.takeScreenshot("gui-2k-extended-to-the-bar")

        // Folding the category away takes the settings hanging off it along
        context.click(dpX = 150.0, dpY = 101.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-2f-category-folded")
        context.click(dpX = 150.0, dpY = 101.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)

        // The settings of the Style module hold the colour pickers
        context.click(dpX = 800.0, dpY = 180.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-2b-style-settings")

        // The first of the buttons opens a menu, which Escape closes without closing the GUI
        context.click(dpX = 20.0, dpY = 7.0)
        context.takeScreenshot("gui-3-menu")

        // With a menu and one nested in it open, a single click on another entry of the bar
        // closes both and opens that entry's menu
        context.click(dpX = 30.0, dpY = 39.0)
        context.click(dpX = 190.0, dpY = 7.0)
        context.takeScreenshot("gui-3b-menu-to-menu")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)

        // A menu closed with one nested in it open takes that one with it as it folds
        context.click(dpX = 20.0, dpY = 7.0)
        context.click(dpX = 30.0, dpY = 39.0)
        context.waitTicks(10)
        context.takeScreenshot("gui-3c-submenu")
        context.input.setCursorPos(700.0 * DENSITY, 450.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.takeScreenshot("gui-3d-submenu-swallowed")
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.takeScreenshot("gui-3e-submenu-swallowed")
        context.waitTicks(10)

        // Closed with a menu open, the GUI comes back as it was left, minus the menu
        context.click(dpX = 20.0, dpY = 7.0)
        context.closeGui()
        context.openClickGui()
        context.takeScreenshot("gui-3a-reopened")

        // The search in the menu bar lists what matches as it is typed...
        context.click(dpX = 480.0, dpY = 7.0)
        context.input.typeChars("scaff")
        context.waitTicks(10)
        context.takeScreenshot("gui-4-search")

        // ...and Enter shows where the first result is: the module is lit in its window
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(8)
        context.takeScreenshot("gui-4a-search-module")
        context.waitTicks(40)

        // The arrow keys and Tab move the mark through the results, and Enter takes the marked one
        context.click(dpX = 480.0, dpY = 7.0)
        context.input.typeChars("auto")
        context.waitTicks(1)
        context.takeScreenshot("gui-4a1-search-unfolding")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_DOWN)
        context.input.pressKey(GLFW.GLFW_KEY_TAB)
        context.waitTicks(5)
        context.takeScreenshot("gui-4a2-search-third-marked")
        context.input.pressKey(GLFW.GLFW_KEY_UP)
        context.waitTicks(5)
        context.takeScreenshot("gui-4a3-search-second-marked")
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(8)
        context.takeScreenshot("gui-4a4-search-marked-chosen")
        context.waitTicks(40)

        // A setting has the settings of its module opened on its tab, and is lit there
        context.click(dpX = 480.0, dpY = 7.0)
        context.input.typeChars("targeting range")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(15)
        context.takeScreenshot("gui-4b-search-setting")

        // A list of blocks shows each with its icon, drawn by Minecraft
        context.click(dpX = 480.0, dpY = 7.0)
        context.input.typeChars("surround")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(15)
        context.click(dpX = 150.0, dpY = 219.5, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-4c-block-settings")
        context.click(dpX = 350.0, dpY = 242.0)
        context.takeScreenshot("gui-4d-block-list")

        // Lists that scroll have a scrollbar on their right, like every other
        context.input.setCursorPos(400.0 * DENSITY, 346.0 * DENSITY)
        context.waitTicks(2)
        context.input.scroll(-5.0)
        context.waitTicks(15)
        context.takeScreenshot("gui-4e-block-list-scrolled")

        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)
        context.takeScreenshot("gui-4f-block-list-shut")

        // A setting that is one block shows it on a chip, with its icon
        context.click(dpX = 480.0, dpY = 7.0)
        context.input.typeChars("pavement material")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(15)
        context.takeScreenshot("gui-4g-block-setting")
        context.closeGui()

        val singleplayer = context.worldBuilder()
            .adjustSettings { it.gameMode = WorldCreator.Mode.CREATIVE }
            .create()
        singleplayer.clientWorld.waitForChunksDownload()
        // A stack, a worn piece and an enchanted one: the count, the damage bar and the glint are Minecraft's
        singleplayer.server.runCommand("/item replace entity @p armor.head with minecraft:stone 32")
        singleplayer.server.runCommand("/item replace entity @p armor.chest with minecraft:netherite_chestplate[minecraft:damage=500]")
        singleplayer.server.runCommand("/item replace entity @p armor.legs with minecraft:diamond_leggings[minecraft:enchantments={protection:4}]")
        singleplayer.server.runCommand("/item replace entity @p armor.feet with minecraft:golden_boots[minecraft:damage=40]")

        context.runOnClient<RuntimeException> {
            Armor.enable()
            Coordinates.enable()
            Fps.enable()
        }
        context.waitTicks(20)
        context.takeScreenshot("gui-5-hud-in-game")

        // In game the GUI is opened the way a player does, with its keybind
        context.input.pressKey(GLFW.GLFW_KEY_Y)
        context.waitTicks(2)
        context.takeScreenshot("gui-6-0-click-gui-opening")
        context.waitTicks(18)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == LambdaGui.Screen.ClickGui }) { "The keybind did not open the click GUI" }
        context.takeScreenshot("gui-6-click-gui-in-game")

        // A tooltip that is up when the GUI closes goes with it, and is not back when it reopens
        context.input.setCursorPos(300.0 * DENSITY, 400.0 * DENSITY)
        context.waitTicks(2)
        context.input.setCursorPos(150.0 * DENSITY, 115.0 * DENSITY)
        context.waitTicks(40)
        context.takeScreenshot("gui-6a-tooltip")
        context.input.pressKey(GLFW.GLFW_KEY_Y)
        context.waitTicks(10)
        context.takeScreenshot("gui-6b-closed-with-tooltip-up")
        context.input.pressKey(GLFW.GLFW_KEY_Y)
        context.waitTicks(10)
        context.takeScreenshot("gui-6c-reopened")

        // While a text field has the keyboard, the GUI's keybind is a letter like any other
        context.click(dpX = 480.0, dpY = 7.0)
        context.input.pressKey(GLFW.GLFW_KEY_Y)
        context.waitTicks(5)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == LambdaGui.Screen.ClickGui }) { "Typing the keybind into a text field closed the GUI" }
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.isCapturingKeys }) { "A focused text field did not hold on to the keys" }
        context.input.pressKey(GLFW.GLFW_KEY_BACKSPACE)
        context.waitTicks(2)

        // The HUD editor is reached from the click GUI's button, and dragging an element moves it
        context.click(dpX = 924.0, dpY = 528.0)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == LambdaGui.Screen.HudEditor }) { "The HUD Editor button did not open the editor" }
        check(context.computeOnClient<Boolean, RuntimeException> { !LambdaGui.isCapturingKeys }) { "Keys stayed captured after the focused text field went away" }
        context.input.setCursorPos(20.0 * DENSITY, 44.0 * DENSITY)
        context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        repeat(10) {
            context.input.moveCursor(30.0 * DENSITY, 15.0 * DENSITY)
            context.waitTick()
        }
        context.takeScreenshot("gui-7-hud-editor-dragging")
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(2)

        // Dragged with the snap key held, the element lines up with the screen and its neighbours
        context.input.holdKey(GLFW.GLFW_KEY_LEFT_SHIFT)
        context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        repeat(10) {
            context.input.moveCursor(-29.0 * DENSITY, 2.0 * DENSITY)
            context.waitTick()
        }
        context.takeScreenshot("gui-7a-hud-editor-snapping")
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.input.releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT)
        context.waitTicks(2)

        // Right click on the empty screen offers the hidden elements, on an element its own menu.
        // The second right click comes while the first menu is still open: it closes that one
        // and opens its own, in one go.
        context.click(dpX = 700.0, dpY = 400.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-8-hud-editor-menu")

        // A right click elsewhere with the menu open unfolds a new one there
        context.input.setCursorPos(500.0 * DENSITY, 300.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(1)
        context.takeScreenshot("gui-8a-hud-editor-menu-reopening")
        context.waitTicks(10)
        context.takeScreenshot("gui-8b-hud-editor-menu-reopened")

        // How the editor is used is told by an entry of that menu, to whoever rests the pointer on it
        context.input.setCursorPos(513.0 * DENSITY, 427.5 * DENSITY)
        context.waitTicks(20)
        context.takeScreenshot("gui-8c-hud-editor-help")
        context.click(dpX = 150.0, dpY = 108.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-9-hud-element-menu")
        // Pressed and let go a few frames apart, the way a hand does it
        context.input.setCursorPos(175.0 * DENSITY, 129.0 * DENSITY)
        context.waitTicks(2)
        context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(3)
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(10)
        context.takeScreenshot("gui-9a-hud-element-settings")

        // What opens out of that panel in turn: a colour picker and a list of options
        context.click(dpX = 342.0, dpY = 135.0)
        context.takeScreenshot("gui-9b-hud-element-colour")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)
        context.click(dpX = 312.0, dpY = 208.5)
        context.takeScreenshot("gui-9c-hud-element-options")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)

        // The panel is dragged by any part of it that is not a control, here the room beside its buttons
        context.input.setCursorPos(170.0 * DENSITY, 268.0 * DENSITY)
        context.waitTicks(2)
        context.dragBy(150.0, 60.0)
        context.takeScreenshot("gui-9d-hud-settings-dragged")

        // A click off the panel closes it, shrinking into its middle, and the editor stays
        context.input.setCursorPos(700.0 * DENSITY, 450.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(1)
        context.takeScreenshot("gui-9e-hud-settings-closing")
        context.waitTicks(10)
        context.takeScreenshot("gui-9f-hud-settings-closed")
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == LambdaGui.Screen.HudEditor }) { "Closing the settings panel left the HUD editor" }

        // Escape leaves the editor for the click GUI, and the click GUI for the game
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == LambdaGui.Screen.ClickGui }) { "Escape did not return from the HUD editor to the click GUI" }
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == null }) { "Escape did not close the click GUI" }

        // A panel closed with a menu open on one of its settings takes the menu with it as it folds
        context.openClickGui()
        context.click(dpX = 150.0, dpY = 193.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(10)
        context.click(dpX = 320.0, dpY = 273.0)
        context.waitTicks(10)
        context.takeScreenshot("gui-10a-panel-with-menu")
        context.input.setCursorPos(700.0 * DENSITY, 450.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(1)
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.takeScreenshot("gui-10b-menu-folding-in-panel")
        context.waitTicks(1)
        context.takeScreenshot("gui-10c-menu-folding-in-panel")
        context.waitTicks(15)
        context.takeScreenshot("gui-10d-panel-closed")

        // Settings kept open come back once the categories have unfolded, not riding their row down
        context.runOnClient<RuntimeException> { GuiConfig.keepSettingsOpen = true }
        context.click(dpX = 150.0, dpY = 193.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(10)
        context.closeGui()
        context.runOnClient<RuntimeException> { LambdaGui.open(LambdaGui.Screen.ClickGui) }
        context.waitTicks(2)
        context.takeScreenshot("gui-11a-kept-settings-categories-unfolding")
        context.waitTicks(4)
        context.takeScreenshot("gui-11b-kept-settings-unfolding")
        context.waitTicks(15)
        context.takeScreenshot("gui-11c-kept-settings-open")
        context.runOnClient<RuntimeException> { GuiConfig.keepSettingsOpen = false }
        context.closeGui()

        // However many pages a config has, the band that picks one is a single row
        context.openClickGui()
        context.click(dpX = 520.0, dpY = 180.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(10)
        context.takeScreenshot("gui-11d-settings-of-many-tabs")
        context.click(dpX = 728.0, dpY = 185.0)
        context.waitTicks(10)
        context.click(dpX = 650.0, dpY = 216.0)
        context.takeScreenshot("gui-11e-many-tabs-listed")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)
        context.closeGui()

        // The glow is in the colours of the theme in use: the dark one's here, the light one's below
        context.runOnClient<RuntimeException> { Style.glow = true }
        context.openClickGui()
        context.takeScreenshot("gui-11f-glow-dark")
        context.closeGui()

        // The light theme, picked the way a player picks it, in the settings of Style
        context.openClickGui()
        context.click(dpX = 800.0, dpY = 180.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(10)
        context.click(dpX = 730.0, dpY = 201.0)
        context.waitTicks(10)
        context.click(dpX = 730.0, dpY = 243.0)
        context.waitTicks(10)
        context.takeScreenshot("gui-12a-light-theme")
        context.click(dpX = 150.0, dpY = 193.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(10)
        context.takeScreenshot("gui-12b-light-theme-settings")
        context.closeGui()
        context.runOnClient<RuntimeException> { Style.glow = false }

        // Another font, picked the same way: the interface keeps its proportions
        context.openClickGui()
        context.click(dpX = 800.0, dpY = 180.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(10)
        context.click(dpX = 730.0, dpY = 215.0)
        context.waitTicks(10)
        context.click(dpX = 730.0, dpY = 243.0)
        context.waitTicks(10)
        context.takeScreenshot("gui-13-fira-sans")
        context.closeGui()

        // Smaller text and taller modules, set on their sliders: the rows follow both
        context.openClickGui()
        context.click(dpX = 800.0, dpY = 180.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(10)
        context.input.setCursorPos(700.0 * DENSITY, 243.0 * DENSITY)
        context.waitTicks(2)
        context.dragBy(-30.0, 0.0)
        context.input.setCursorPos(680.0 * DENSITY, 257.0 * DENSITY)
        context.waitTicks(2)
        context.dragBy(40.0, 0.0)
        context.waitTicks(10)
        context.takeScreenshot("gui-14-font-size-and-module-height")
        context.closeGui()

        // The whole of it from the keyboard. Tab puts the focus on the first thing there is, the
        // header of the first window, and the arrows take it across to the third and down its list
        context.openClickGui()
        context.input.pressKey(GLFW.GLFW_KEY_TAB)
        context.waitTicks(5)
        context.takeScreenshot("gui-15a-keyboard-first-focus")
        context.input.pressKey(GLFW.GLFW_KEY_RIGHT)
        context.input.pressKey(GLFW.GLFW_KEY_RIGHT)
        context.input.pressKey(GLFW.GLFW_KEY_DOWN)
        context.input.pressKey(GLFW.GLFW_KEY_DOWN)
        context.waitTicks(5)
        context.takeScreenshot("gui-15b-keyboard-on-module")

        // Enter is a click on it, and Shift with Enter a right click: its settings open beside it
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(5)
        context.takeScreenshot("gui-15c-keyboard-toggled")
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.pressKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_SHIFT)
        context.waitTicks(15)
        context.input.pressKey(GLFW.GLFW_KEY_RIGHT)
        context.waitTicks(5)
        context.takeScreenshot("gui-15d-keyboard-into-settings")
        context.input.pressKey(GLFW.GLFW_KEY_DOWN)
        context.input.pressKey(GLFW.GLFW_KEY_DOWN)
        context.waitTicks(5)
        context.takeScreenshot("gui-15e-keyboard-down-settings")
        context.input.pressKey(GLFW.GLFW_KEY_RIGHT)
        context.input.pressKey(GLFW.GLFW_KEY_RIGHT)
        context.waitTicks(5)
        context.takeScreenshot("gui-15f-keyboard-right-twice")

        // A menu of the bar is its own layer of focus: Enter opens it, the arrows move inside it
        context.pressKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(5)
        context.input.typeChars("zoo")
        context.waitTicks(10)
        context.takeScreenshot("gui-15g-keyboard-search")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)
        repeat(5) { context.pressKey(GLFW.GLFW_KEY_TAB, GLFW.GLFW_MOD_SHIFT) }
        context.waitTicks(5)
        context.takeScreenshot("gui-15h-keyboard-on-menu-bar")
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_DOWN)
        context.input.pressKey(GLFW.GLFW_KEY_DOWN)
        context.waitTicks(5)
        context.takeScreenshot("gui-15i-keyboard-in-menu")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(10)
        context.takeScreenshot("gui-15j-keyboard-menu-closed")
        context.closeGui()

        singleplayer.close()
    }

    private fun ClientGameTestContext.openClickGui() {
        runOnClient<RuntimeException> { LambdaGui.open(LambdaGui.Screen.ClickGui) }
        waitTicks(20)
        check(computeOnClient<Boolean, RuntimeException> { LambdaGui.screen != null }) { "The click GUI did not open" }
    }

    /** A key pressed with modifiers held, which the test's own input leaves out of the key it sends */
    private fun ClientGameTestContext.pressKey(key: Int, modifiers: Int) {
        runOnClient<RuntimeException> {
            GuiScreen.keyPressed(KeyInput(key, 0, modifiers))
            GuiScreen.keyReleased(KeyInput(key, 0, modifiers))
        }
        waitTicks(1)
    }

    private fun ClientGameTestContext.closeGui() {
        check(computeOnClient<Boolean, RuntimeException> { LambdaGui.screen != null }) { "The interface closed itself, see the log" }
        runOnClient<RuntimeException> { it.setScreen(null) }
        waitTicks(5)
    }

    private fun ClientGameTestContext.click(dpX: Double, dpY: Double, button: Int = GLFW.GLFW_MOUSE_BUTTON_LEFT) {
        input.setCursorPos(dpX * DENSITY, dpY * DENSITY)
        waitTicks(2)
        input.pressMouse(button)
        waitTicks(10)
    }

    /** Drags from where the pointer is, by a distance in dp, in steps a frame apart as a hand would */
    private fun ClientGameTestContext.dragBy(dpX: Double, dpY: Double) {
        input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        repeat(5) {
            input.moveCursor(dpX / 5 * DENSITY, dpY / 5 * DENSITY)
            waitTick()
        }
        input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        waitTicks(2)
    }

    private fun ClientGameTestContext.windowPlacement(category: String): GuiConfig.WindowPlacement =
        computeOnClient<GuiConfig.WindowPlacement, RuntimeException> {
            checkNotNull(GuiConfig.windows[category]) { "The $category window was never moved" }
        }

    private fun ClientGameTestContext.enabledModules(): Int =
        computeOnClient<Int, RuntimeException> { ModuleRegistry.modules.count { it.isEnabled } }
}
