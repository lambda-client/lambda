package me.zeroeightsix.kami.gui.kami.theme.kami;

import me.zeroeightsix.kami.gui.kami.KamiGUI;
import me.zeroeightsix.kami.gui.rgui.component.container.Container;
import me.zeroeightsix.kami.gui.rgui.component.use.CheckButton;
import me.zeroeightsix.kami.gui.rgui.render.AbstractComponentUI;
import me.zeroeightsix.kami.gui.rgui.render.font.FontRenderer;

import static me.zeroeightsix.kami.util.ColourSet.*;
import static org.lwjgl.opengl.GL11.*;

/**
 * Created by 086 on 4/08/2017.
 */
public class RootCheckButtonUI<T extends CheckButton> extends AbstractComponentUI<CheckButton> {
    @Override
    public void renderComponent(CheckButton component, FontRenderer ff) {

        glColor4f(checkButtonBackgroundColour.getRed(), checkButtonBackgroundColour.getGreen(), checkButtonBackgroundColour.getBlue(), component.getOpacity());
        if (component.isToggled()) { // I don't know why the R in this one is separate, 086 wrote it that way
            glColor3f(checkButtonBackgroundColourOther, checkButtonBackgroundColour.getGreen(), checkButtonBackgroundColour.getBlue());
        }
        if (component.isHovered() || component.isPressed()) {
            glColor4f(checkButtonBackgroundColourHover.getRed(), checkButtonBackgroundColourHover.getGreen(), checkButtonBackgroundColourHover.getBlue(), component.getOpacity());
        }

        String text = component.getName();
        int c = component.isPressed() ? 0xaaaaaa : component.isToggled() ? 0xff3333 : 0xdddddd;
        if (component.isHovered())
            c = (c & 0x7f7f7f) << 1;

        glColor3f(1, 1, 1);
        glEnable(GL_TEXTURE_2D);
        KamiGUI.fontRenderer.drawString(component.getWidth() / 2 - KamiGUI.fontRenderer.getStringWidth(text) / 2, KamiGUI.fontRenderer.getFontHeight() / 2 - 2, c, text);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }

    @Override
    public void handleAddComponent(CheckButton component, Container container) {
        component.setWidth(KamiGUI.fontRenderer.getStringWidth(component.getName()) + 28);
        component.setHeight(KamiGUI.fontRenderer.getFontHeight() + 2);
    }
}
