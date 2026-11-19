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

import com.lambda.config.entries.Setting
import com.lambda.context.Automated
import com.lambda.gui.GuiConfig
import com.lambda.gui.GuiScreen
import com.lambda.gui.LambdaGui
import com.lambda.module.ModuleRegistry
import com.lambda.module.modules.client.AutoUpdater
import com.lambda.module.modules.client.Style
import com.lambda.module.modules.hud.Armor
import com.lambda.module.modules.hud.Coordinates
import com.lambda.module.modules.hud.Fps
import com.lambda.module.modules.render.Bobbing
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext
import net.minecraft.client.gui.screen.world.WorldCreator
import net.minecraft.client.input.KeyInput
import org.lwjgl.glfw.GLFW
import kotlin.math.abs

@Suppress("UnstableApiUsage")
object GuiTest : FabricClientGameTest {
    private const val WIDTH = 1920
    private const val HEIGHT = 1080

    private const val DENSITY = HEIGHT / 540.0

    override fun runTest(context: ClientGameTestContext) {
        context.input.resizeWindow(WIDTH, HEIGHT)
        context.waitTicks(5)

        context.openClickGui()
        context.takeScreenshot("gui-0-loader-prompt")
        context.runOnClient<RuntimeException> { AutoUpdater.answerPrompt(accepted = false) }
        context.waitTicks(10)
        context.takeScreenshot("gui-1-title-screen")

        context.runOnClient<RuntimeException> {
            Style.set("fontSize", 1f)
            Style.set("elementHeight", 13f)
        }
        context.waitTicks(5)

        val enabledBefore = context.enabledModules()
        context.click(dpX = 150.0, dpY = 115.0)
        check(context.enabledModules() != enabledBefore) { "Clicking a module did not toggle it" }
        context.takeScreenshot("gui-1-module-enabled")
        context.click(dpX = 150.0, dpY = 115.0)

        val enabledBeforePress = context.enabledModules()
        context.input.setCursorPos(150.0 * DENSITY, 115.0 * DENSITY)
        context.waitTicks(2)
        context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(3)
        check(context.enabledModules() != enabledBeforePress) { "A module was not toggled by the press" }
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(3)
        context.click(dpX = 150.0, dpY = 115.0)

        context.input.setCursorPos(240.0 * DENSITY, 193.0 * DENSITY)
        context.waitTicks(30)
        context.takeScreenshot("gui-1a-long-name-hovered")
        context.input.setCursorPos(240.0 * DENSITY, 60.0 * DENSITY)
        context.waitTicks(10)
        context.takeScreenshot("gui-1b-long-name-left")

        context.input.setCursorPos(620.0 * DENSITY, 101.0 * DENSITY)
        context.dragBy(3.5, 7.5)
        val free = context.windowPlacement("Network")
        check(abs(free.x - 576.5f) < 0.6f && abs(free.y - 101.5f) < 0.6f) { "A dragged window did not follow the pointer: $free" }

        context.input.holdKey(GLFW.GLFW_KEY_LEFT_SHIFT)
        context.waitTicks(2)
        context.dragBy(2.0, 2.0)
        context.input.releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT)
        val snapped = context.windowPlacement("Network")
        check(snapped.inRow) { "A window dragged with the snap key held did not snap back between its neighbours: $snapped" }

        context.runOnClient<RuntimeException> { GuiConfig.set("showSnapLines", true) }
        context.input.setCursorPos(620.0 * DENSITY, 100.0 * DENSITY)
        context.waitTicks(2)
        context.input.holdKey(GLFW.GLFW_KEY_LEFT_SHIFT)
        context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        repeat(5) {
            context.input.moveCursor(3.0 * DENSITY, 30.0 * DENSITY)
            context.waitTick()
        }
        context.waitTicks(3)
        context.takeScreenshot("gui-1b1-window-snap-lines")
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.input.releaseKey(GLFW.GLFW_KEY_LEFT_SHIFT)
        context.waitTicks(2)
        val aligned = context.windowPlacement("Network")
        check(abs(aligned.x - 573f) < 0.6f) { "A window dragged down beside its place in the row did not stay in line with it: $aligned" }
        context.runOnClient<RuntimeException> {
            GuiConfig.set("showSnapLines", false)
            GuiConfig.resetWindows()
        }
        context.waitTicks(5)

        context.input.setCursorPos(620.0 * DENSITY, 100.0 * DENSITY)
        context.waitTicks(2)
        context.dragBy(0.0, -95.0)
        val raised = context.windowPlacement("Network")
        context.takeScreenshot("gui-1c-window-dragged-to-the-top")
        check(abs(raised.y - 15f) < 0.6f) { "A window dragged to the top of the screen did not stop under the menu bar: $raised" }

        context.runOnClient<RuntimeException> { GuiConfig.resetWindows() }
        context.waitTicks(2)
        check(context.computeOnClient<Boolean, RuntimeException> { GuiConfig.windows.isEmpty() }) { "Resetting the layout left a window placed" }

        context.click(dpX = 150.0, dpY = 115.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-2-module-settings")

        context.input.setCursorPos(150.0 * DENSITY, 128.0 * DENSITY)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(1)
        context.takeScreenshot("gui-2a-switching-settings")
        context.waitTicks(10)

        context.click(dpX = 150.0, dpY = 193.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)

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

        context.click(dpX = 296.0, dpY = 216.0)
        context.takeScreenshot("gui-2d0-tab-list")
        context.input.setCursorPos(296.0 * DENSITY, 245.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(1)
        context.takeScreenshot("gui-2d-tab-changing")
        context.waitTicks(10)
        context.takeScreenshot("gui-2e-tab-changed")

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

        context.setClipboard("")
        context.pressKey(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL)
        context.pressKey(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(3)
        check(context.clipboard() == "stone") { "Copying from a text field in a dialog left the clipboard as '${context.clipboard()}'" }
        context.setClipboard("glass")
        context.pressKey(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL)
        context.pressKey(GLFW.GLFW_KEY_V, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(3)
        context.setClipboard("")
        context.pressKey(GLFW.GLFW_KEY_A, GLFW.GLFW_MOD_CONTROL)
        context.pressKey(GLFW.GLFW_KEY_C, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(3)
        check(context.clipboard() == "glass") { "Pasting into a text field in a dialog gave '${context.clipboard()}'" }
        context.takeScreenshot("gui-2h4-collection-pasted")

        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(1)
        context.takeScreenshot("gui-2h1-collection-folding")
        context.waitTicks(2)
        context.takeScreenshot("gui-2h3-collection-folded-in")
        context.waitTicks(3)

        context.click(dpX = 340.0, dpY = 198.0)
        for (y in listOf(289.0, 275.0, 261.0, 247.0)) context.click(dpX = 261.0, dpY = y)
        context.takeScreenshot("gui-2i-scrolling")

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

        context.dragBy(0.0, -300.0)
        context.takeScreenshot("gui-2k-extended-to-the-bar")

        context.click(dpX = 150.0, dpY = 101.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-2f-category-folded")
        context.click(dpX = 150.0, dpY = 101.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)

        context.click(dpX = 800.0, dpY = 180.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-2b-style-settings")

        context.click(dpX = 20.0, dpY = 7.0)
        context.takeScreenshot("gui-3-menu")

        context.click(dpX = 30.0, dpY = 39.0)
        context.click(dpX = 190.0, dpY = 7.0)
        context.takeScreenshot("gui-3b-menu-to-menu")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)

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

        context.click(dpX = 20.0, dpY = 7.0)
        context.closeGui()
        context.openClickGui()
        context.takeScreenshot("gui-3a-reopened")

        context.click(dpX = 480.0, dpY = 7.0)
        context.input.typeChars("scaff")
        context.waitTicks(10)
        context.takeScreenshot("gui-4-search")

        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(8)
        context.takeScreenshot("gui-4a-search-module")
        context.waitTicks(40)

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

        context.click(dpX = 480.0, dpY = 7.0)
        context.input.typeChars("targeting range")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(15)
        context.takeScreenshot("gui-4b-search-setting")

        context.click(dpX = 480.0, dpY = 7.0)
        context.input.typeChars("surround")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(15)
        context.click(dpX = 150.0, dpY = 219.5, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-4c-block-settings")
        context.click(dpX = 350.0, dpY = 242.0)
        context.takeScreenshot("gui-4d-block-list")

        context.input.setCursorPos(400.0 * DENSITY, 346.0 * DENSITY)
        context.waitTicks(2)
        context.input.scroll(-5.0)
        context.waitTicks(15)
        context.takeScreenshot("gui-4e-block-list-scrolled")

        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)
        context.takeScreenshot("gui-4f-block-list-shut")

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

        context.input.pressKey(GLFW.GLFW_KEY_Y)
        context.waitTicks(2)
        context.takeScreenshot("gui-6-0-click-gui-opening")
        context.waitTicks(18)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == LambdaGui.Screen.ClickGui }) { "The keybind did not open the click GUI" }
        context.takeScreenshot("gui-6-click-gui-in-game")

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

        context.click(dpX = 480.0, dpY = 7.0)
        context.input.pressKey(GLFW.GLFW_KEY_Y)
        context.waitTicks(5)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == LambdaGui.Screen.ClickGui }) { "Typing the keybind into a text field closed the GUI" }
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.isCapturingKeys }) { "A focused text field did not hold on to the keys" }
        context.input.pressKey(GLFW.GLFW_KEY_BACKSPACE)
        context.waitTicks(2)

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

        context.click(dpX = 700.0, dpY = 400.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-8-hud-editor-menu")

        context.input.setCursorPos(500.0 * DENSITY, 300.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(1)
        context.takeScreenshot("gui-8a-hud-editor-menu-reopening")
        context.waitTicks(10)
        context.takeScreenshot("gui-8b-hud-editor-menu-reopened")

        context.input.setCursorPos(513.0 * DENSITY, 427.5 * DENSITY)
        context.waitTicks(20)
        context.takeScreenshot("gui-8c-hud-editor-help")
        context.click(dpX = 150.0, dpY = 108.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.takeScreenshot("gui-9-hud-element-menu")
        context.input.setCursorPos(175.0 * DENSITY, 129.0 * DENSITY)
        context.waitTicks(2)
        context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(3)
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(10)
        context.takeScreenshot("gui-9a-hud-element-settings")

        context.click(dpX = 342.0, dpY = 151.0)
        context.takeScreenshot("gui-9b-hud-element-colour")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)
        context.click(dpX = 312.0, dpY = 224.5)
        context.takeScreenshot("gui-9c-hud-element-options")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)

        context.click(dpX = 311.0, dpY = 135.0)
        context.waitTicks(10)
        context.takeScreenshot("gui-9c1-hud-element-module-page")
        context.click(dpX = 200.0, dpY = 135.0)
        context.waitTicks(10)

        context.input.setCursorPos(170.0 * DENSITY, 284.0 * DENSITY)
        context.waitTicks(2)
        context.dragBy(150.0, 60.0)
        context.takeScreenshot("gui-9d-hud-settings-dragged")

        context.input.setCursorPos(700.0 * DENSITY, 450.0 * DENSITY)
        context.waitTicks(2)
        context.input.pressMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(1)
        context.takeScreenshot("gui-9e-hud-settings-closing")
        context.waitTicks(10)
        context.takeScreenshot("gui-9f-hud-settings-closed")
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == LambdaGui.Screen.HudEditor }) { "Closing the settings panel left the HUD editor" }

        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == LambdaGui.Screen.ClickGui }) { "Escape did not return from the HUD editor to the click GUI" }
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == null }) { "Escape did not close the click GUI" }

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

        context.runOnClient<RuntimeException> { GuiConfig.set("keepSettingsOpen", true) }
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
        context.runOnClient<RuntimeException> { GuiConfig.set("keepSettingsOpen", false) }
        context.closeGui()

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

        context.runOnClient<RuntimeException> {
            Style.set("inlineSettings", true)
            GuiConfig.resetWindows()
        }
        context.openClickGui()
        context.input.pressKey(GLFW.GLFW_KEY_TAB)
        context.input.pressKey(GLFW.GLFW_KEY_DOWN)
        context.pressKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_SHIFT)
        context.waitTicks(3)
        context.takeScreenshot("gui-11g-inline-settings-dropping")
        context.waitTicks(15)
        context.takeScreenshot("gui-11h-inline-settings")
        context.pressKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(5)
        context.input.typeChars("targeting range")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(40)
        context.takeScreenshot("gui-11i-inline-settings-with-tabs")
        context.pressKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(5)
        context.input.typeChars("window gap")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(40)
        context.takeScreenshot("gui-11j-inline-settings-with-groups")

        context.runOnClient<RuntimeException> { Style.set("multipleSettings", true) }
        context.pressKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(5)
        context.input.typeChars("targeting range")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(40)
        context.takeScreenshot("gui-11k-inline-settings-of-two")
        context.runOnClient<RuntimeException> { Style.set("inlineSettings", false) }
        context.waitTicks(20)
        context.takeScreenshot("gui-11l-panels-of-two")
        context.runOnClient<RuntimeException> { Style.set("multipleSettings", false) }
        context.waitTicks(20)
        context.takeScreenshot("gui-11m-multiple-off-both-stay")
        context.pressKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(5)
        context.input.typeChars("magnitude")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(40)
        context.takeScreenshot("gui-11n-opening-one-closes-the-rest")
        context.closeGui()

        context.runOnClient<RuntimeException> { Style.set("glow", true) }
        context.openClickGui()
        context.takeScreenshot("gui-11f-glow-dark")
        context.closeGui()

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
        context.runOnClient<RuntimeException> { Style.set("glow", false) }

        context.openClickGui()
        context.click(dpX = 800.0, dpY = 180.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(10)
        context.click(dpX = 730.0, dpY = 215.0)
        context.waitTicks(10)
        context.click(dpX = 730.0, dpY = 243.0)
        context.waitTicks(10)
        context.takeScreenshot("gui-13-fira-sans")
        context.closeGui()

        context.openClickGui()
        context.click(dpX = 800.0, dpY = 180.0, button = GLFW.GLFW_MOUSE_BUTTON_RIGHT)
        context.waitTicks(10)
        context.input.setCursorPos(700.0 * DENSITY, 243.0 * DENSITY)
        context.waitTicks(2)
        context.input.holdMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        repeat(5) {
            context.input.moveCursor(3.0 * DENSITY, 0.0)
            context.waitTick()
        }
        context.waitTicks(5)
        context.takeScreenshot("gui-14a-scale-held")
        check(context.computeOnClient<Float, RuntimeException> { Style.scale } == 1f) { "The scale changed while its bar was still held" }
        context.input.releaseMouse(GLFW.GLFW_MOUSE_BUTTON_LEFT)
        context.waitTicks(10)
        context.takeScreenshot("gui-14b-scale-applied")
        check(context.computeOnClient<Float, RuntimeException> { Style.scale } > 1f) { "The scale was not applied when its bar was let go" }
        context.runOnClient<RuntimeException> { Style.set("scale", 1.5f) }
        context.waitTicks(15)
        context.takeScreenshot("gui-14c-scale-largest")
        context.runOnClient<RuntimeException> { Style.set("scale", 1f) }
        context.waitTicks(10)

        context.input.setCursorPos(700.0 * DENSITY, 229.0 * DENSITY)
        context.waitTicks(2)
        context.dragBy(-30.0, 0.0)
        context.runOnClient<RuntimeException> { Style.set("elementHeight", 20f) }
        context.waitTicks(10)
        context.takeScreenshot("gui-14-font-size-and-module-height")
        check(context.computeOnClient<Float, RuntimeException> { Style.fontSize } < 0.85f) { "Dragging the font size bar did not make text smaller" }
        check(context.computeOnClient<Float, RuntimeException> { Style.scale } == 1f) { "The scale was left changed" }

        context.runOnClient<RuntimeException> { Style.set("scale", 1.5f) }
        context.waitTicks(15)
        context.pressKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(5)
        context.input.typeChars("magnitude")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(45)
        context.takeScreenshot("gui-14d-panel-beside-row")
        context.input.setCursorPos(200.0 * DENSITY * 1.5, 200.0 * DENSITY * 1.5)
        context.waitTicks(2)
        context.input.scroll(-30.0)
        context.waitTicks(25)
        context.takeScreenshot("gui-14e-panel-row-scrolled-away")
        context.runOnClient<RuntimeException> { Style.set("scale", 1f) }
        context.waitTicks(10)
        context.closeGui()

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
        check(context.bobbingSpeed() == 1.02) { "Two steps right did not take the slider from 1.0 to 1.02" }
        context.input.pressKey(GLFW.GLFW_KEY_LEFT)
        context.input.pressKey(GLFW.GLFW_KEY_LEFT)
        context.waitTicks(5)
        check(context.bobbingSpeed() == 1.0) { "Two steps left did not bring the slider back to exactly 1.0" }

        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(5)
        context.input.typeChars("5")
        context.waitTicks(5)
        context.takeScreenshot("gui-15f1-number-being-typed")
        check(context.bobbingSpeed() == 1.0) { "A number was applied while it was still being typed" }
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(5)
        check(context.bobbingSpeed() == 1.05) { "A typed number was not applied on Enter" }

        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(5)
        context.input.typeChars("9")
        context.waitTicks(5)
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)
        check(context.bobbingSpeed() == 1.05) { "Escape did not discard a typed number" }
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen != null }) { "Escape in a number field closed the interface" }

        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(5)
        repeat(4) { context.input.pressKey(GLFW.GLFW_KEY_BACKSPACE) }
        context.input.typeChars("7")
        context.waitTicks(5)
        check(context.bobbingSpeed() == 1.05) { "Clearing a number field changed the setting before it was done" }
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(5)
        context.takeScreenshot("gui-15f2-number-clamped")
        check(context.bobbingSpeed() == 2.0) { "A typed number beyond the range was not brought to its end" }
        context.runOnClient<RuntimeException> { Bobbing.set("speed", 1.0) }
        context.waitTicks(5)

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

        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(10)
        repeat(7) { context.input.pressKey(GLFW.GLFW_KEY_DOWN) }
        context.waitTicks(5)
        context.takeScreenshot("gui-15k-keyboard-menu-reopened")
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(10)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == null }) {
            "A menu closed from the keyboard did not open again from it, to have its Close GUI chosen"
        }

        context.runOnClient<RuntimeException> {
            Style.set("windowRounding", 9f)
            Style.set("panelRounding", 0f)
            Style.set("outlineWidth", 1.5f)
            Style.set("windowWidth", 110f)
            Style.set("windowGap", 8f)
            Style.set("modulePadding", 10f)
            Style.set("panelPadding", 10f)
            Style.set("settingSpacing", 4f)
            Style.set("panelAnimations", false)
        }
        context.openClickGui()
        context.takeScreenshot("gui-16a-restyled")
        context.input.pressKey(GLFW.GLFW_KEY_TAB)
        context.input.pressKey(GLFW.GLFW_KEY_DOWN)
        context.pressKey(GLFW.GLFW_KEY_ENTER, GLFW.GLFW_MOD_SHIFT)
        context.waitTicks(1)
        context.takeScreenshot("gui-16b-no-panel-animation")
        context.closeGui()
        context.runOnClient<RuntimeException> {
            Style.set("windowRounding", 3f)
            Style.set("panelRounding", 5f)
            Style.set("outlineWidth", 0.5f)
            Style.set("windowWidth", 90f)
            Style.set("windowGap", 2f)
            Style.set("modulePadding", 4f)
            Style.set("panelPadding", 4f)
            Style.set("settingSpacing", 1f)
            Style.set("panelAnimations", true)
        }

        context.runOnClient<RuntimeException> {
            GuiConfig.windows = mapOf(
                "Movement" to GuiConfig.WindowPlacement(204.6f, 94.3f, false),
                "Render" to GuiConfig.WindowPlacement(297f, 94f, true),
                "World" to GuiConfig.WindowPlacement(500f, 200f, false),
            )
        }
        context.openClickGui()
        context.pressKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(5)
        context.input.typeChars("window gap")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(60)
        context.takeScreenshot("gui-17a-spacing-default")
        context.runOnClient<RuntimeException> { Style.set("windowGap", 12f) }
        context.waitTicks(10)
        context.takeScreenshot("gui-17b-window-gap")
        context.runOnClient<RuntimeException> { Style.set("windowGap", 2f); Style.set("windowTop", 40f) }
        context.waitTicks(10)
        context.takeScreenshot("gui-17c-window-top")
        context.runOnClient<RuntimeException> { Style.set("windowTop", 94f); Style.set("modulePadding", 12f) }
        context.waitTicks(10)
        context.takeScreenshot("gui-17d-module-padding")
        context.runOnClient<RuntimeException> { Style.set("modulePadding", 4f); Style.set("panelPadding", 12f) }
        context.waitTicks(10)
        context.takeScreenshot("gui-17e-panel-padding")
        context.runOnClient<RuntimeException> { Style.set("panelPadding", 4f); Style.set("settingSpacing", 6f) }
        context.waitTicks(10)
        context.takeScreenshot("gui-17f-setting-spacing")
        context.runOnClient<RuntimeException> { Style.set("settingSpacing", 1f); Style.set("groupIndent", 12f) }
        context.waitTicks(10)
        context.takeScreenshot("gui-17g-group-indent")
        context.runOnClient<RuntimeException> { Style.set("groupIndent", 5f); Style.set("windowWidth", 120f) }
        context.waitTicks(10)
        context.takeScreenshot("gui-17h-window-width")
        context.runOnClient<RuntimeException> { Style.set("windowWidth", 90f) }
        context.waitTicks(10)
        context.takeScreenshot("gui-17i-spacing-restored")
        context.closeGui()

        context.runOnClient<RuntimeException> { GuiConfig.resetWindows() }
        context.openClickGui()
        context.pressKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(5)
        context.input.typeChars("emboss")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(60)
        context.takeScreenshot("gui-18a-dependent-hidden")
        context.runOnClient<RuntimeException> { Style.set("glow", true) }
        context.waitTicks(15)
        context.takeScreenshot("gui-18b-dependent-shown")
        context.runOnClient<RuntimeException> { Style.set("glow", false) }
        context.waitTicks(15)
        context.takeScreenshot("gui-18c-dependent-hidden-again")
        context.runOnClient<RuntimeException> { Style.set("animations", false) }
        context.waitTicks(5)
        context.runOnClient<RuntimeException> { Style.set("glow", true) }
        context.waitTicks(15)
        context.takeScreenshot("gui-18d-dependent-shown-unanimated")
        context.runOnClient<RuntimeException> { Style.set("glow", false) }
        context.waitTicks(15)
        context.takeScreenshot("gui-18e-dependent-hidden-unanimated")
        context.runOnClient<RuntimeException> { Style.set("animations", true) }
        context.closeGui()

        context.openClickGui()
        context.pressKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(5)
        context.input.typeChars("rebreak mode")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(60)
        context.takeScreenshot("gui-19a-dependent-enabled")
        context.setRebreak(false)
        context.waitTicks(15)
        context.takeScreenshot("gui-19b-dependent-disabled")
        context.setRebreak(true)
        context.waitTicks(15)
        context.takeScreenshot("gui-19c-dependent-enabled-again")
        context.closeGui()

        context.openClickGui()
        context.pressKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(5)
        context.input.typeChars("airplace")
        context.waitTicks(15)
        context.takeScreenshot("gui-20-search-same-name")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(5)

        context.pressKey(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL)
        context.waitTicks(5)
        context.input.typeChars("keep settings open")
        context.waitTicks(10)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(45)
        context.takeScreenshot("gui-20a-config-dialog")
        context.input.pressKey(GLFW.GLFW_KEY_ESCAPE)
        context.waitTicks(10)
        context.closeGui()

        context.openClickGui()
        context.failInterface()
        context.waitTicks(10)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == null }) { "An error in the interface left its screen open" }
        context.takeScreenshot("gui-21a-hud-after-an-error")
        context.openClickGui()
        val enabledAfterError = context.enabledModules()
        context.input.pressKey(GLFW.GLFW_KEY_TAB)
        context.input.pressKey(GLFW.GLFW_KEY_RIGHT)
        context.input.pressKey(GLFW.GLFW_KEY_RIGHT)
        context.input.pressKey(GLFW.GLFW_KEY_DOWN)
        context.input.pressKey(GLFW.GLFW_KEY_DOWN)
        context.waitTicks(5)
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(5)
        context.takeScreenshot("gui-21b-click-gui-after-an-error")
        check(context.enabledModules() != enabledAfterError) { "The interface rebuilt after an error did not take input" }
        context.input.pressKey(GLFW.GLFW_KEY_ENTER)
        context.waitTicks(5)
        context.closeGui()

        context.failInterface()
        context.failInterface()
        context.runOnClient<RuntimeException> { LambdaGui.open(LambdaGui.Screen.ClickGui) }
        context.waitTicks(10)
        check(context.computeOnClient<Boolean, RuntimeException> { LambdaGui.screen == null }) { "The interface went on opening after failing three times in a row" }

        singleplayer.close()
    }

    private fun ClientGameTestContext.openClickGui() {
        runOnClient<RuntimeException> { LambdaGui.open(LambdaGui.Screen.ClickGui) }
        waitTicks(20)
        check(computeOnClient<Boolean, RuntimeException> { LambdaGui.screen != null }) { "The click GUI did not open" }
    }

    private fun ClientGameTestContext.pressKey(key: Int, modifiers: Int) {
        runOnClient<RuntimeException> {
            GuiScreen.keyPressed(KeyInput(key, 0, modifiers))
            GuiScreen.keyReleased(KeyInput(key, 0, modifiers))
        }
        waitTicks(1)
    }

    private fun ClientGameTestContext.setRebreak(to: Boolean) = runOnClient<RuntimeException> {
        (ModuleRegistry.modules.first { it.name == "PacketMine" } as Automated).breakConfig.set("rebreak", to)
    }

    private fun ClientGameTestContext.failInterface() = runOnClient<RuntimeException> {
        LambdaGui.javaClass.getDeclaredMethod("fail", Throwable::class.java)
            .apply { isAccessible = true }
            .invoke(LambdaGui, IllegalStateException("An error made up by the GUI test"))
    }

    private fun ClientGameTestContext.clipboard(): String = computeOnClient<String, RuntimeException> { it.keyboard.clipboard }

    private fun ClientGameTestContext.setClipboard(text: String) = runOnClient<RuntimeException> { it.keyboard.clipboard = text }

    private fun ClientGameTestContext.bobbingSpeed(): Double = computeOnClient<Double, RuntimeException> { Bobbing.speed }

    @Suppress("UNCHECKED_CAST")
    private fun Any.set(property: String, value: Any) {
        val delegate = javaClass.getDeclaredField(property + "\$delegate").apply { isAccessible = true }
        (delegate.get(this) as Setting<Any>).value = value
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
