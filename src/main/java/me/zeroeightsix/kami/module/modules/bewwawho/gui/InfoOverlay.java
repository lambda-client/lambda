package me.zeroeightsix.kami.module.modules.bewwawho.gui;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import me.zeroeightsix.kami.util.bewwawho.InfoCalculator;
import me.zeroeightsix.kami.util.zeroeightysix.ColourUtils;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;

/**
 * @author S-B99
 * Created by S-B99 on 04/12/19
 * Updated by S-B99 on 22/01/20
 */
@Module.Info(name = "InfoOverlay", category = Module.Category.GUI, description = "Configures game information overlay", showOnArray = Module.ShowOnArray.OFF)
public class InfoOverlay extends Module {

    public Setting<Boolean> version = register(Settings.b("Version", true));
    public Setting<Boolean> username = register(Settings.b("Username", true));
    public Setting<Boolean> tps = register(Settings.b("TPS", true));
    public Setting<Boolean> fps = register(Settings.b("FPS", true));
    public Setting<Boolean> speed = register(Settings.b("Speed", true));
    public Setting<Boolean> ping = register(Settings.b("Ping", false));
    public Setting<Boolean> durability = register(Settings.b("Durability", true));
    public Setting<Boolean> memory = register(Settings.b("Memory", false));
    public Setting<SpeedUnit> speedUnit = register(Settings.e("Speed Unit", SpeedUnit.KmH));
    public Setting<ColourCode> firstColour = register(Settings.e("First Colour", ColourCode.WHITE));
    public Setting<ColourCode> secondColour = register(Settings.e("Second Colour", ColourCode.BLUE));

    public enum SpeedUnit {
        MpS, KmH
    }

    private enum ColourCode {
        BLACK, DARK_BLUE, DARK_GREEN, DARK_AQUA, DARK_RED, DARK_PURPLE, GOLD, GREY, DARK_GREY, BLUE, GREEN, AQUA, RED, LIGHT_PURPLE, YELLOW, WHITE
    }

    public boolean useUnitKmH() {
        return speedUnit.getValue().equals(SpeedUnit.KmH);
    }

    public String unitType(SpeedUnit s) {
        switch (s) {
            case MpS: return "m/s";
            case KmH: return "km/h";
            default: return "Invalid unit type (mps or kmh)";
        }
    }

    private String textColour(ColourCode c) {
        switch (c) {
            case BLACK: return ColourUtils.ColourCodesMinecraft.BLACK_CC;
            case DARK_BLUE: return ColourUtils.ColourCodesMinecraft.DARK_BLUE_CC;
            case DARK_GREEN: return ColourUtils.ColourCodesMinecraft.DARK_GREEN_CC;
            case DARK_AQUA: return ColourUtils.ColourCodesMinecraft.DARK_AQUA_CC;
            case DARK_RED: return ColourUtils.ColourCodesMinecraft.DARK_RED_CC;
            case DARK_PURPLE: return ColourUtils.ColourCodesMinecraft.DARK_PURPLE_CC;
            case GOLD: return ColourUtils.ColourCodesMinecraft.GOLD_CC;
            case GREY: return ColourUtils.ColourCodesMinecraft.GREY_CC;
            case DARK_GREY: return ColourUtils.ColourCodesMinecraft.DARK_GREY_CC;
            case BLUE: return ColourUtils.ColourCodesMinecraft.BLUE_CC;
            case GREEN: return ColourUtils.ColourCodesMinecraft.GREEN_CC;
            case AQUA: return ColourUtils.ColourCodesMinecraft.AQUA_CC;
            case RED: return ColourUtils.ColourCodesMinecraft.RED_CC;
            case LIGHT_PURPLE: return ColourUtils.ColourCodesMinecraft.LIGHT_PURPLE_CC;
            case YELLOW: return ColourUtils.ColourCodesMinecraft.YELLOW_CC;
            case WHITE: return ColourUtils.ColourCodesMinecraft.WHITE_CC;

            default: return "";
        }
    }

    public ArrayList<String> infoContents() {
        ArrayList<String> infoContents = new ArrayList<>();
        if (version.getValue()) {
            infoContents.add(textColour(firstColour.getValue()) + KamiMod.KAMI_KANJI + textColour(secondColour.getValue()) + " " + KamiMod.MODVER);
        }
        if (username.getValue()) {
            infoContents.add(textColour(firstColour.getValue()) + "Welcome " + textColour(secondColour.getValue()) + " " + mc.player.getName() + "!");
        }
        if (tps.getValue()) {
            infoContents.add(textColour(firstColour.getValue()) + InfoCalculator.tps() + textColour(secondColour.getValue()) + " tps");
        }
        if (fps.getValue()) {
            infoContents.add(textColour(firstColour.getValue()) + Minecraft.debugFPS + textColour(secondColour.getValue()) + " fps");
        }
        if (speed.getValue()) {
            infoContents.add(textColour(firstColour.getValue()) + InfoCalculator.speed() + textColour(secondColour.getValue()) + " " + unitType(speedUnit.getValue()));
        }
        if (ping.getValue()) {
            infoContents.add(textColour(firstColour.getValue()) + InfoCalculator.ping() + textColour(secondColour.getValue()) + " ms");
        }
        if (durability.getValue()) {
            infoContents.add(textColour(firstColour.getValue()) + InfoCalculator.dura() + textColour(secondColour.getValue()) + " dura");
        }
        if (memory.getValue()) {
            infoContents.add(textColour(firstColour.getValue()) + InfoCalculator.memory() + textColour(secondColour.getValue()) + "mB free");
        }
        return infoContents;
    }

    public void onDisable() { this.enable(); }
}
