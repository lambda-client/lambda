package me.zeroeightsix.kami.util;

import net.minecraft.util.text.TextFormatting;

import java.awt.*;

public class ColourTextFormatting {
    public enum ColourEnum {
        BLACK(TextFormatting.BLACK),
        DARK_BLUE(TextFormatting.DARK_BLUE),
        DARK_GREEN(TextFormatting.DARK_GREEN),
        DARK_AQUA(TextFormatting.DARK_AQUA),
        DARK_RED(TextFormatting.DARK_RED),
        DARK_PURPLE(TextFormatting.DARK_PURPLE),
        GOLD(TextFormatting.GOLD),
        GRAY(TextFormatting.GRAY),
        DARK_GRAY(TextFormatting.DARK_GRAY),
        BLUE(TextFormatting.BLUE),
        GREEN(TextFormatting.GREEN),
        AQUA(TextFormatting.AQUA),
        RED(TextFormatting.RED),
        LIGHT_PURPLE(TextFormatting.LIGHT_PURPLE),
        YELLOW(TextFormatting.YELLOW),
        WHITE(TextFormatting.WHITE),
        BLACK_C(new Color(0,0, 0)),
        DARK_BLUE_C(new Color(0, 0, 170)),
        DARK_GREEN_C(new Color(0, 170, 0)),
        DARK_AQUA_C(new Color(0, 170, 170)),
        DARK_RED_C(new Color(170, 0, 0)),
        DARK_PURPLE_C(new Color(170, 0, 170)),
        GOLD_C(new Color(255, 170, 0)),
        GRAY_C(new Color(170, 170, 0)),
        DARK_GRAY_C(new Color(85, 85, 85)),
        BLUE_C(new Color(85, 85, 255)),
        GREEN_C(new Color(85, 255, 85)),
        AQUA_C(new Color(85, 225, 225)),
        RED_C(new Color(255, 85, 85)),
        LIGHT_PURPLE_C(new Color(255, 85, 255)),
        YELLOW_C(new Color(255, 255, 85)),
        WHITE_C(new Color(255, 255, 255));

        public TextFormatting val;
        public Color colorLocal;

        private ColourEnum(TextFormatting val) {
            this.val = val;
        }

        private ColourEnum(Color colorLocal) {
            this.colorLocal = colorLocal;
        }
    }

}
