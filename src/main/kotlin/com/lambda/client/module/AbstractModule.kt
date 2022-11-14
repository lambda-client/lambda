package com.lambda.client.module

import com.lambda.client.commons.interfaces.Alias
import com.lambda.client.commons.interfaces.Nameable
import com.lambda.client.event.LambdaEventBus
import com.lambda.client.event.events.ModuleToggleEvent
import com.lambda.client.gui.clickgui.LambdaClickGui
import com.lambda.client.module.modules.client.ClickGUI
import com.lambda.client.setting.configs.NameableConfig
import com.lambda.client.setting.settings.AbstractSetting
import com.lambda.client.setting.settings.SettingRegister
import com.lambda.client.setting.settings.impl.number.IntegerSetting
import com.lambda.client.setting.settings.impl.other.BindSetting
import com.lambda.client.setting.settings.impl.primitive.BooleanSetting
import com.lambda.client.util.Bind
import com.lambda.client.util.text.MessageSendHelper
import net.minecraft.client.Minecraft

@Suppress("UNCHECKED_CAST")
abstract class AbstractModule(
    override val name: String,
    override val alias: Array<String> = emptyArray(),
    val category: Category,
    val description: String,
    val modulePriority: Int = -1,
    var alwaysListening: Boolean = false,
    val showOnArray: Boolean = true,
    val alwaysEnabled: Boolean = false,
    val enabledByDefault: Boolean = false,
    private val config: NameableConfig<out Nameable>
) : Nameable, Alias, SettingRegister<Nameable> by config as NameableConfig<Nameable> {

    val bind = BindSetting("Bind", Bind(), { !alwaysEnabled }).also(::addSetting)
    private val enabled = BooleanSetting("Enabled", false, { false }).also(::addSetting)
    private val visible = BooleanSetting("Visible", showOnArray).also(::addSetting)
    private val default = BooleanSetting("Default", false, { settingList.isNotEmpty() }).also(::addSetting)
    val priorityForGui = IntegerSetting("Priority In GUI", 0, 0..1000, 50, { ClickGUI.sortBy.value == ClickGUI.SortByOptions.CUSTOM }, fineStep = 1).also(::addSetting)
    val clicks = IntegerSetting("Clicks", 0, 0..Int.MAX_VALUE, 1, { false }).also(::addSetting) // Not nice, however easiest way to save it.

    val fullSettingList get() = (config as NameableConfig<Nameable>).getSettings(this)
    val settingList: List<AbstractSetting<*>> get() = fullSettingList.filter { it != bind && it != enabled && it != visible && it != default && it != clicks }

    val isEnabled: Boolean get() = enabled.value || alwaysEnabled
    val isDisabled: Boolean get() = !isEnabled
    var isPaused = false
    val chatName: String get() = "[${name}]"
    val isVisible: Boolean get() = visible.value

    private fun addSetting(setting: AbstractSetting<*>) {
        (config as NameableConfig<Nameable>).addSettingToConfig(this, setting)
    }

    internal fun postInit() {
        enabled.value = enabledByDefault || alwaysEnabled
        if (alwaysListening) LambdaEventBus.subscribe(this)
    }

    fun toggle() {
        enabled.value = !enabled.value
        isPaused = false
        if (enabled.value) clicks.value++
    }

    fun enable() {
        clicks.value++
        enabled.value = true
    }

    fun disable() {
        enabled.value = false
    }

    fun pause() {
        isPaused = true
        LambdaEventBus.unsubscribe(this)
    }

    fun unpause() {
        isPaused = false
        LambdaEventBus.subscribe(this)
    }

    open fun isActive(): Boolean {
        return isEnabled || alwaysListening
    }

    open fun getHudInfo(): String {
        return ""
    }

    protected fun onEnable(block: (Boolean) -> Unit) {
        enabled.valueListeners.add { _, input ->
            if (input) {
                block(true)
            }
        }
    }

    protected fun onDisable(block: (Boolean) -> Unit) {
        enabled.valueListeners.add { _, input ->
            if (!input) {
                block(false)
            }
        }
    }

    protected fun onToggle(block: (Boolean) -> Unit) {
        enabled.valueListeners.add { _, input ->
            block(input)
        }
    }

    init {
        enabled.consumers.add { prev, input ->
            val enabled = alwaysEnabled || input

            if (prev != input && !alwaysEnabled) {
                LambdaEventBus.post(ModuleToggleEvent(this))
            }

            if (enabled || alwaysListening) {
                LambdaEventBus.subscribe(this)
            } else {
                LambdaEventBus.unsubscribe(this)
            }

            enabled
        }

        default.valueListeners.add { _, it ->
            if (it) {
                settingList.forEach { it.resetValue() }
                default.value = false
                MessageSendHelper.sendChatMessage("$chatName Set to defaults!")
            }
        }

        priorityForGui.listeners.add { LambdaClickGui.reorderModules() }

        // clicks is deliberately not re-organised when changed.
    }

    protected companion object {
        val mc: Minecraft = Minecraft.getMinecraft()
    }
}
