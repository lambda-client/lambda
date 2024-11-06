package com.lambda.newgui.impl.clickgui

import com.lambda.config.AbstractSetting
import com.lambda.module.modules.client.NewCGui
import com.lambda.newgui.component.HAlign
import com.lambda.newgui.component.layout.Layout
import com.lambda.newgui.component.window.Window
import com.lambda.util.math.Vec2d

/**
 * A base class for setting layouts.
 */
abstract class SettingLayout <V : Any, T: AbstractSetting<V>> (
    owner: Layout,
    setting: T,
    expandable: Boolean = false
) : Window( // going to use window to easily implement expandable settings (such as color picker)
    owner,
    setting.name,
    Vec2d.ZERO, Vec2d.ZERO,
    false, false,
    if (expandable) Minimizing.Relative else Minimizing.Disabled,
    false,
    AutoResize.ForceEnabled,
    true
) {
    init {
        overrideSize(owner::renderWidth, NewCGui::settingsHeight)
        minimized = true

        with(titleBar.textField) {
            text = setting.name
            bold = false
            textHAlignment = HAlign.LEFT

            onUpdate {
                scale = NewCGui.fontScale * 0.92
            }
        }

        children.removeAll(listOf(titleBarRect, contentRect, outlineRect))
        if (!expandable) children.remove(content)
    }
}