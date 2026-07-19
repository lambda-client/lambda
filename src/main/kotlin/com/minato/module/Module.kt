
package com.minato.module

import com.minato.command.MinatoCommand
import com.minato.config.Config
import com.minato.config.ConfigCategory
import com.minato.config.automation.IMutableAutomationConfig
import com.minato.config.automation.MutableAutomationConfig
import com.minato.config.categories.ModuleCategory
import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.config.entries.Setting.Companion.onValueChangeUnsafe
import com.minato.config.settings.complex.Bind
import com.minato.config.settings.complex.KeybindSetting.Companion.onPress
import com.minato.config.settings.complex.KeybindSetting.Companion.onRelease
import com.minato.context.SafeContext
import com.minato.event.EventFlow.post
import com.minato.event.EventFlow.updateListenerSorting
import com.minato.event.Muteable
import com.minato.event.OwnerPriority
import com.minato.event.events.ClientEvent
import com.minato.event.events.ConnectionEvent
import com.minato.event.events.ModuleEvent
import com.minato.event.listener.Listener
import com.minato.event.listener.SafeListener
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.event.listener.UnsafeListener
import com.minato.module.modules.client.Client
import com.minato.module.tag.ModuleTag
import com.minato.sound.MinatoSound
import com.minato.sound.SoundHandler.play
import com.minato.util.KeyCode
import com.minato.util.Nameable

/**
 * A [Module] is a feature or tool for the utility mod.
 * It represents a [Config] component of the mod,
 * with its own set of behaviors and properties.
 *
 * Each [Module] has a [name], which is displayed in-game.
 * The [description] of the module is shown when hovering over
 * the [ModuleButton] in the GUI and in [MinatoCommand]s.
 * The [Module] can be associated with a [Set] of [ModuleTag]s to allow for
 * easier filtering and searching in the GUI.
 *
 * A [Module] can be activated by a [keybind], represented by a [KeyCode].
 * The default [keybind] is the key on which
 * the module will be activated by default.
 * If a module does not need to be activated by a key (like [ClickGui]),
 * the default [keybind] should not be set (using [KeyCode.Unbound]).
 *
 * [Module]s are [Config]s with [settingLayers] (see [EntryCore] for all setting types).
 * Example:
 * ```
 * private val foo by setting("Foo", true)
 * private val bar by setting("Bar", 0.0, 0.1..5.0, 0.1)
 * ```
 *
 * These settings are persisted in the `minato/config/modules.json` config file.
 * See [ModuleCategory.primary] and [ConfigCategory] for more details.
 *
 * In the `init` block, you can add hooks like [onEnable], [onDisable], [onToggle] and add listeners.
 *
 * Example:
 * ```
 * init {
 *     onEnable { // runs on module activation
 *         LOG.info("I was enabled!")
 *     }
 *
 *     onToggle { to ->
 *          LOG.info("Module enabled: ${to}")
 *     }
 *
 *     onDisable {
 *          LOG.info("I was disable!")
 *     }
 *
 *     listener<TickEvent.Pre> { event ->
 *         LOG.info("I've ticked!")
 *     }
 * }
 * ```
 *
 * [Listener]s are only triggered if:
 * - [Module] is [isEnabled], otherwise it [isMuted] (see [Muteable])
 * - [Module] was configured to [alwaysListening]
 * - [Listener] was configured to [Listener.alwaysListen]
 *
 * Example:
 * ```
 * val bind1 = setting("Keybind", KeyCode.A)
 * val bind2 = setting("Keybind", Bind(KeyCode.A.code, 0, -1))
 *
 * listen<KeyboardEvent.Press>(alwaysListen = true) { event ->
 *     if (!event.satisfies(bind1) || !event.satisfies(bind2)) return@listen
 *
 *     if (event.isPressed) toggle()
 *     else if (event.isReleased) disable()
 * }
 * ```
 *
 * When modules are toggled, enabled or disabled, the corresponding [ModuleEvent] is posted to the event bus before the module state is changed.
 *
 * See [SafeListener] and [UnsafeListener] for more details.
 */
@Suppress("unused")
abstract class Module(
    name: String,
    val description: String = "",
    val tag: ModuleTag,
    private val alwaysListening: Boolean = false,
    enabledByDefault: Boolean = false,
    modulePriority: Int = 0,
    defaultKeybind: Bind = Bind.EMPTY,
    autoDisable: Boolean = false
) : Nameable, Muteable, OwnerPriority, Config(name, ModuleCategory),
    IMutableAutomationConfig by MutableAutomationConfig()
{
    private val isEnabledSetting = setting("Enabled", enabledByDefault) { false }
    val prioritySetting = setting("Module Priority", modulePriority, -100..100, 1, "Priority over other modules") { false }
		.onValueChangeUnsafe { _, to -> ownerPriority = to }
    override var ownerPriority = modulePriority
        set(value) {
            val oldVal = field
            field = value
            if (value != oldVal) updateListenerSorting()
        }
    val keybindSetting = setting("Keybind", defaultKeybind, alwaysListening = true) { false }
        .onPress { toggle() }
        .onRelease { if (disableOnRelease) disable() }
    val disableOnReleaseSetting = setting("Disable On Release", false) { false }
    val drawSetting = setting("Draw", true, "Draws the module in the module list hud element") { false }
    val showInClickGui = setting("Show In ClickGui", true, "Shows the module in the ClickGui layout") { false }

    var isEnabled by isEnabledSetting
        private set
    val isDisabled get() = !isEnabled

    val keybind by keybindSetting
    val disableOnRelease by disableOnReleaseSetting
    val draw by drawSetting

    override val isMuted: Boolean
        get() = !isEnabled && !alwaysListening

    init {
        onEnableUnsafe { if (Client.clientSounds) MinatoSound.ModuleOn.play() }
        onDisableUnsafe { if (Client.clientSounds) MinatoSound.ModuleOff.play() }

        listen<ClientEvent.Shutdown> { if (autoDisable) disable() }
        listen<ClientEvent.Startup> { if (autoDisable) disable() }
        listen<ConnectionEvent.Disconnect> { if (autoDisable) disable() }
    }

    @DslMarker
    private annotation class ModuleDsl

    @ModuleDsl
    fun enable() {
        ModuleEvent.Enabled(this@Module).post()
        isEnabled = true
    }

    @ModuleDsl
    fun disable() {
        ModuleEvent.Disabled(this@Module).post()
        isEnabled = false
    }

    @ModuleDsl
    fun toggle() {
        ModuleEvent.Toggle(this@Module, !isEnabled).post()
        if (isEnabled) disable() else enable()
    }

    @ModuleDsl
    fun onEnable(block: SafeContext.() -> Unit) {
        isEnabledSetting.onValueChange { from, to ->
            if (!from && to) block()
        }
    }

    @ModuleDsl
    fun onDisable(block: SafeContext.() -> Unit) {
        isEnabledSetting.onValueChange { from, to ->
            if (from && !to) block()
        }
    }

    @ModuleDsl
    fun onToggle(block: SafeContext.(to: Boolean) -> Unit) {
        isEnabledSetting.onValueChange { from, to ->
            if (from != to) block(to)
        }
    }

    @ModuleDsl
    fun onEnableUnsafe(block: () -> Unit) {
        isEnabledSetting.onValueChangeUnsafe { from, to ->
            if (!from && to) block()
        }
    }

    @ModuleDsl
    fun onDisableUnsafe(block: () -> Unit) {
        isEnabledSetting.onValueChangeUnsafe { from, to ->
            if (from && !to) block()
        }
    }

    @ModuleDsl
    fun onToggleUnsafe(block: (to: Boolean) -> Unit) {
        isEnabledSetting.onValueChangeUnsafe { from, to ->
            if (from != to) block(to)
        }
    }
}
