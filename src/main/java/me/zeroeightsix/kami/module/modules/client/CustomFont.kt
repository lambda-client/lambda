package me.zeroeightsix.kami.module.modules.client

import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.command.Command
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Setting
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.graphics.font.KamiFontRenderer
import me.zeroeightsix.kami.util.text.MessageSendHelper

@Module.Info(
        name = "CustomFont",
        description = "Use the better font instead of the stupid Minecraft font",
        showOnArray = Module.ShowOnArray.OFF,
        category = Module.Category.CLIENT,
        enabledByDefault = true
)
object CustomFont : Module() {
    private const val DEFAULT_FONT_NAME = "Roboto"

    val fontName = register(Settings.stringBuilder("FontName").withValue(DEFAULT_FONT_NAME).withRestriction { KamiFontRenderer.availableFonts.contains(it) })
    val size = register(Settings.floatBuilder("Size").withValue(1f).withRange(0.5f, 2f).withStep(0.05f))
    val gap = register(Settings.floatBuilder("Gap").withValue(0f).withRange(-5f, 5f).withStep(0.05f))
    val lineSpace = register(Settings.floatBuilder("LineSpace").withValue(0f).withRange(-5f, 5f).withStep(0.05f))

    val isDefaultFont get() = fontName.value.equals(DEFAULT_FONT_NAME, true)

    override fun onToggle() {
        MessageSendHelper.sendChatMessage(
                "Changed font! Run \n" +
                        "&7${Command.commandPrefix.value}config save\n" +
                        "&7${Command.commandPrefix.value}config reload\n" +
                        "&f if it's not sizing correctly"
        )
        if (isDefaultFont) {
            MessageSendHelper.sendChatMessage("You can run &7${Command.commandPrefix.value}set $originalName ${fontName.name} <Font Name> (Case sensitive) to change custom font")
        }
    }

    init {
        fontName.settingListener = Setting.SettingListeners {
            if (Thread.currentThread() == KamiMod.MAIN_THREAD) KamiFontRenderer.reloadFonts()
        }
    }
}