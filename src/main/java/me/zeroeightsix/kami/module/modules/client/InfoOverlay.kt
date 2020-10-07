package me.zeroeightsix.kami.module.modules.client

import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.setting.Setting
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.InfoCalculator
import me.zeroeightsix.kami.util.InfoCalculator.memory
import me.zeroeightsix.kami.util.InfoCalculator.ping
import me.zeroeightsix.kami.util.InventoryUtils
import me.zeroeightsix.kami.util.TimeUtils
import me.zeroeightsix.kami.util.TimeUtils.getFinalTime
import me.zeroeightsix.kami.util.color.ColorTextFormatting
import me.zeroeightsix.kami.util.color.ColorTextFormatting.ColourCode
import me.zeroeightsix.kami.util.math.MathUtils
import me.zeroeightsix.kami.util.math.MathUtils.round
import net.minecraft.client.Minecraft
import net.minecraft.util.text.TextFormatting
import java.util.*
import kotlin.math.max

@Module.Info(
        name = "InfoOverlay",
        category = Module.Category.CLIENT,
        description = "Configures the game information overlay",
        showOnArray = Module.ShowOnArray.OFF,
        alwaysEnabled = true
)
@Suppress("UNCHECKED_CAST")
object InfoOverlay : Module() {
    /* This is so horrible // TODO: FIX */
    private val page = register(Settings.enumBuilder(Page::class.java).withName("Page").withValue(Page.ONE))

    /* Page One */
    private val version = register(Settings.booleanBuilder("Version").withValue(true).withVisibility { page.value == Page.ONE }.build())
    private val username = register(Settings.booleanBuilder("Username").withValue(true).withVisibility { page.value == Page.ONE }.build())
    private val tps = register(Settings.booleanBuilder("TPS").withValue(true).withVisibility { page.value == Page.ONE }.build())
    private val fps = register(Settings.booleanBuilder("FPS").withValue(true).withVisibility { page.value == Page.ONE }.build())
    private val ping = register(Settings.booleanBuilder("Ping").withValue(false).withVisibility { page.value == Page.ONE }.build())
    private val server = register(Settings.booleanBuilder("ServerBrand").withValue(false).withVisibility { page.value == Page.ONE }.build())
    private val durability = register(Settings.booleanBuilder("ItemDamage").withValue(false).withVisibility { page.value == Page.ONE }.build())
    private val biome = register(Settings.booleanBuilder("Biome").withValue(false).withVisibility { page.value == Page.ONE }.build())
    private val memory = register(Settings.booleanBuilder("RAMUsed").withValue(false).withVisibility { page.value == Page.ONE }.build())
    private val timerSpeed = register(Settings.booleanBuilder("TimerSpeed").withValue(false).withVisibility { page.value == Page.ONE }.build())

    /* Page Two */
    private val totems = register(Settings.booleanBuilder("Totems").withValue(false).withVisibility { page.value == Page.TWO }.build())
    private val endCrystals = register(Settings.booleanBuilder("EndCrystals").withValue(false).withVisibility { page.value == Page.TWO }.build())
    private val expBottles = register(Settings.booleanBuilder("EXPBottles").withValue(false).withVisibility { page.value == Page.TWO }.build())
    private val godApples = register(Settings.booleanBuilder("GodApples").withValue(false).withVisibility { page.value == Page.TWO }.build())

    /* Page Three */
    private val decimalPlaces = register(Settings.integerBuilder("DecimalPlaces").withValue(2).withMinimum(0).withMaximum(10).withVisibility { page.value == Page.THREE }.build())
    private val speed = register(Settings.booleanBuilder("Speed").withValue(true).withVisibility { page.value == Page.THREE }.build())
    private val averageSpeedTime = register(Settings.floatBuilder("AverageSpeedTime(s)").withValue(1f).withRange(0f, 5f).withVisibility { page.value == Page.THREE && speed.value }.build())
    private val speedUnit = register(Settings.enumBuilder(SpeedUnit::class.java).withName("SpeedUnit").withValue(SpeedUnit.KMH).withVisibility { page.value == Page.THREE && speed.value }.build()) as Setting<SpeedUnit>
    private val time = register(Settings.booleanBuilder("Time").withValue(true).withVisibility { page.value == Page.THREE }.build())
    val timeTypeSetting = register(Settings.enumBuilder(TimeUtils.TimeType::class.java).withName("TimeFormat").withValue(TimeUtils.TimeType.HHMMSS).withVisibility { page.value == Page.THREE && time.value }.build()) as Setting<TimeUtils.TimeType>
    val timeUnitSetting = register(Settings.enumBuilder(TimeUtils.TimeUnit::class.java).withName("TimeUnit").withValue(TimeUtils.TimeUnit.H12).withVisibility { page.value == Page.THREE && time.value }.build()) as Setting<TimeUtils.TimeUnit>
    val doLocale = register(Settings.booleanBuilder("TimeShowAM/PM").withValue(true).withVisibility { page.value == Page.THREE && time.value && timeUnitSetting.value == TimeUtils.TimeUnit.H12 }.build())
    val firstColor = register(Settings.enumBuilder(ColourCode::class.java).withName("FirstColour").withValue(ColourCode.WHITE).withVisibility { page.value == Page.THREE }.build()) as Setting<ColourCode>
    val secondColor = register(Settings.enumBuilder(ColourCode::class.java).withName("SecondColour").withValue(ColourCode.BLUE).withVisibility { page.value == Page.THREE }.build()) as Setting<ColourCode>

    private enum class Page {
        ONE, TWO, THREE
    }

    private val s = "\ud83d\uddff"

    @Suppress("UNUSED")
    private enum class SpeedUnit(val displayName: String) {
        MPS("m/s"),
        KMH("km/h")
        // No retarded imperial unit here
    }

    private val speedList = LinkedList<Double>()

    override fun onUpdate() {
        updateSpeedList()
    }

    fun infoContents(): ArrayList<String> {
        val infoContents = ArrayList<String>()
        for (setting in settingList) {
            if (setting.value != true) continue // make sure it is a Boolean setting and enabled

            setting.infoMap()?.let {
                infoContents.add(first() + it.replace(s, second()))
            }
        }
        return infoContents
    }

    private fun Setting<*>.infoMap() = when (this) {
        version -> "${KamiMod.KAMI_KANJI} ${s}${KamiMod.VER_SMALL}"
        username -> "Welcome ${mc.session.username}!"
        time -> getFinalTime(setToText(secondColor.value), setToText(firstColor.value), timeUnitSetting.value, timeTypeSetting.value, doLocale.value)
        tps -> "${InfoCalculator.tps(decimalPlaces.value)} ${s}tps"
        fps -> "${Minecraft.debugFPS} ${s}fps"
        speed -> "${calcSpeed(decimalPlaces.value)} ${s}${speedUnit.value.displayName}"
        timerSpeed -> "${round(50f / mc.timer.tickLength, decimalPlaces.value)} ${s}x"
        ping -> "${ping()} ${s}ms"
        server -> mc.player.serverBrand
        durability -> "${InfoCalculator.heldItemDurability()} ${s}dura"
        biome -> "${mc.world.getBiome(mc.player.position).biomeName} ${s}biome"
        memory -> "${memory()} ${s}mB free"
        totems -> "${InventoryUtils.countItemAll(449)} ${s}totems"
        endCrystals -> "${InventoryUtils.countItemAll(426)} ${s}crystals"
        expBottles -> "${InventoryUtils.countItemAll(384)} ${s}exp"
        godApples -> "${InventoryUtils.countItemAll(322)} ${s}gaps"
        else -> null
    }

    private fun first(): String {
        return setToText(firstColor.value).toString()
    }

    private fun second(): String {
        return setToText(secondColor.value).toString()
    }

    private fun setToText(colourCode: ColourCode): TextFormatting {
        return ColorTextFormatting.toTextMap[colourCode]!!
    }

    fun calcSpeedWithUnit(place: Int) = "${calcSpeed(place)} ${speedUnit.value.displayName}"

    private fun calcSpeed(place: Int): Double {
        val averageSpeed = if (speedList.isEmpty()) 0.0 else (speedList.sum() / speedList.size.toDouble())
        return MathUtils.round(averageSpeed, place)
    }

    private fun updateSpeedList() {
        val speed = InfoCalculator.speed(speedUnit.value == SpeedUnit.KMH)
        if (speed > 0.0 || mc.player.ticksExisted % 4 == 0) speedList.add(speed) // Only adding it every 4 ticks if speed is 0
        else speedList.poll()
        while (speedList.size > max((averageSpeedTime.value * 20).toInt(), 1)) speedList.poll()
    }
}