package me.zeroeightsix.kami.module.modules.gui;

import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;

import static me.zeroeightsix.kami.command.Command.sendDisableMessage;
import static me.zeroeightsix.kami.util.ColourConverter.rgbToInt;
import static me.zeroeightsix.kami.util.InfoCalculator.isNumberEven;
import static me.zeroeightsix.kami.util.InfoCalculator.reverseNumber;

/**
 * @author S-B99
 * Created by S-B99 on 20/03/20
 */
@Module.Info(name = "ActiveModules", category = Module.Category.GUI, description = "Configures ActiveModules Colour", showOnArray = Module.ShowOnArray.OFF)
public class ActiveModules extends Module {
    public Setting<Mode> mode = register(Settings.e("Mode", Mode.RAINBOW));
    private Setting<Integer> rainbowSpeed = register(Settings.integerBuilder().withName("Speed F").withValue(30).withMinimum(0).withMaximum(100).withVisibility(v -> mode.getValue().equals(Mode.RAINBOW)).build());
    public Setting<Integer> saturationR = register(Settings.integerBuilder().withName("Saturation R").withValue(117).withMinimum(0).withMaximum(255).withVisibility(v -> mode.getValue().equals(Mode.RAINBOW)).build());
    public Setting<Integer> brightnessR = register(Settings.integerBuilder().withName("Brightness R").withValue(255).withMinimum(0).withMaximum(255).withVisibility(v -> mode.getValue().equals(Mode.RAINBOW)).build());
    public Setting<Integer> hueC = register(Settings.integerBuilder().withName("Hue C").withValue(178).withMinimum(0).withMaximum(255).withVisibility(v -> mode.getValue().equals(Mode.CUSTOM)).build());
    public Setting<Integer> saturationC = register(Settings.integerBuilder().withName("Saturation C").withValue(156).withMinimum(0).withMaximum(255).withVisibility(v -> mode.getValue().equals(Mode.CUSTOM)).build());
    public Setting<Integer> brightnessC = register(Settings.integerBuilder().withName("Brightness C").withValue(255).withMinimum(0).withMaximum(255).withVisibility(v -> mode.getValue().equals(Mode.CUSTOM)).build());
    private Setting<Boolean> alternate = register(Settings.booleanBuilder().withName("Alternate").withValue(true).withVisibility(v -> mode.getValue().equals(Mode.INFO_OVERLAY)).build());

    public static int getCategoryColour(Module module) {
        switch (module.getCategory()) {
            case CHAT: return rgbToInt(245, 66, 66);
            case COMBAT: return rgbToInt(245, 135, 66);
            case EXPERIMENTAL: return rgbToInt(245, 66, 129);
            case GUI: return rgbToInt(245, 203, 66);
            case RENDER: return rgbToInt(194, 245, 66);
            case PLAYER: return rgbToInt(66, 245, 126);
            case MOVEMENT: return rgbToInt(66, 182, 245);
            case MISC: return rgbToInt(170, 66, 245);
            default: return rgbToInt(139, 100, 255);
        }
    }

    public int getInfoColour(int position) {
        if (!alternate.getValue()) return getSecondInfoColourFromSettings();
        else {
            if (isNumberEven(position)) {
                return getFirstInfoColourFromSettings();
            } else {
                return getSecondInfoColourFromSettings();
            }
        }
    }

    //TODO: fix this dogshit code with the ColourTextFormatting class
    private int getFirstInfoColourFromSettings() {
        InfoOverlay infoOverlay = (InfoOverlay) ModuleManager.getModuleByName("InfoOverlay");
        switch (infoOverlay.firstColour.getValue()) {
            case BLACK: return rgbToInt(0,0, 0);
            case DARK_BLUE: return rgbToInt(0, 0, 170);
            case DARK_GREEN: return rgbToInt(0, 170, 0);
            case DARK_AQUA: return rgbToInt(0, 170, 170);
            case DARK_RED: return rgbToInt(170, 0, 0);
            case DARK_PURPLE: return rgbToInt(170, 0, 170);
            case GOLD: return rgbToInt(255, 170, 0);
            case GRAY: return rgbToInt(170, 170, 0);
            case DARK_GRAY: return rgbToInt(85, 85, 85);
            case BLUE: return rgbToInt(85, 85, 255);
            case GREEN: return rgbToInt(85, 255, 85);
            case AQUA: return rgbToInt(85, 225, 225);
            case RED: return rgbToInt(255, 85, 85);
            case LIGHT_PURPLE: return rgbToInt(255, 85, 255);
            case YELLOW: return rgbToInt(255, 255, 85);
            case WHITE: return rgbToInt(255, 255, 255);
        }
        return rgbToInt(155, 144, 255);
    }

    private int getSecondInfoColourFromSettings() {
        InfoOverlay infoOverlay = (InfoOverlay) ModuleManager.getModuleByName("InfoOverlay");
        switch (infoOverlay.secondColour.getValue()) {
            case BLACK: return rgbToInt(0,0, 0);
            case DARK_BLUE: return rgbToInt(0, 0, 170);
            case DARK_GREEN: return rgbToInt(0, 170, 0);
            case DARK_AQUA: return rgbToInt(0, 170, 170);
            case DARK_RED: return rgbToInt(170, 0, 0);
            case DARK_PURPLE: return rgbToInt(170, 0, 170);
            case GOLD: return rgbToInt(255, 170, 0);
            case GRAY: return rgbToInt(170, 170, 0);
            case DARK_GRAY: return rgbToInt(85, 85, 85);
            case BLUE: return rgbToInt(85, 85, 255);
            case GREEN: return rgbToInt(85, 255, 85);
            case AQUA: return rgbToInt(85, 225, 225);
            case RED: return rgbToInt(255, 85, 85);
            case LIGHT_PURPLE: return rgbToInt(255, 85, 255);
            case YELLOW: return rgbToInt(255, 255, 85);
            case WHITE: return rgbToInt(255, 255, 255);
        }
        return rgbToInt(155, 144, 255);
    }

    public int getRainbowSpeed() {
        if (rainbowSpeed.getValue() == 0) return 10000000; // if 0 basically just never change the color
        int rSpeed = reverseNumber(rainbowSpeed.getValue(), 1, 100);
        if (rSpeed == 0) return 1; // can't divide by 0
        else return rSpeed;
    }

    public enum Mode { RAINBOW, CATEGORY, CUSTOM, INFO_OVERLAY }
    public void onDisable() { sendDisableMessage(getName()); }
}
