package me.zeroeightsix.kami.gui.kami.theme.kami;

import me.zeroeightsix.kami.gui.kami.KamiGUI;
import me.zeroeightsix.kami.gui.kami.RootSmallFontRenderer;
import me.zeroeightsix.kami.gui.kami.component.ColorizedCheckButton;
import me.zeroeightsix.kami.gui.rgui.component.use.CheckButton;
import me.zeroeightsix.kami.gui.rgui.render.font.FontRenderer;

import java.awt.*;

import static me.zeroeightsix.kami.util.ColourSet.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Created by 086 on 8/08/2017.
 */
public class RootColorizedCheckButtonUI extends RootCheckButtonUI<ColorizedCheckButton> {

    RootSmallFontRenderer ff = new RootSmallFontRenderer();

    public RootColorizedCheckButtonUI() { // why are these values all hardcoded screm aksdlksalkdlk
        checkButtonBackgroundColour = new Color(200, checkButtonBackgroundColour.getGreen(), checkButtonBackgroundColour.getBlue());
        checkButtonBackgroundColourHover = new Color(255, checkButtonBackgroundColourHover.getGreen(), checkButtonBackgroundColourHover.getBlue());
    }

    @Override
    public void renderComponent(CheckButton component, FontRenderer aa) {
        glColor4f(checkButtonBackgroundColour.getRed() / 255f, checkButtonBackgroundColour.getGreen() / 255f, checkButtonBackgroundColour.getBlue() / 255f, component.getOpacity());
        if (component.isHovered() || component.isPressed()) {
            glColor4f(checkButtonBackgroundColourHover.getRed() / 255f, checkButtonBackgroundColourHover.getGreen() / 255f, checkButtonBackgroundColourHover.getBlue() / 255f, component.getOpacity());
        }
        if (component.isToggled()) {
            glColor3f(checkButtonBackgroundColour.getRed() / 255f, checkButtonBackgroundColour.getGreen() / 255f, checkButtonBackgroundColour.getBlue() / 255f);
        }

//        RenderHelper.drawRoundedRectangle(0,0,component.getWidth(), component.getHeight(), 3f);
        glLineWidth(2.5f);
        glBegin(GL_LINES);
        {
            glVertex2d(0, component.getHeight());
            glVertex2d(component.getWidth(), component.getHeight());
        }
        glEnd();

        Color idleColour = component.isToggled() ? checkButtonIdleColourToggle : checkButtonIdleColourNormal;
        Color downColour = component.isToggled() ? checkButtonDownColourToggle : checkButtonDownColourNormal;

        glColor3f(1, 1, 1);
        glEnable(GL_TEXTURE_2D);
        ff.drawString(component.getWidth() / 2 - KamiGUI.fontRenderer.getStringWidth(component.getName()) / 2, 0, component.isPressed() ? downColour : idleColour, component.getName());
        glDisable(GL_TEXTURE_2D);
    }
}
