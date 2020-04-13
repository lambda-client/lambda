package me.zeroeightsix.kami.gui.kami.theme.kami;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.gui.kami.KamiGUI;
import me.zeroeightsix.kami.gui.kami.RenderHelper;
import me.zeroeightsix.kami.gui.kami.component.SettingsPanel;
import me.zeroeightsix.kami.gui.rgui.component.container.Container;
import me.zeroeightsix.kami.gui.rgui.component.use.CheckButton;
import me.zeroeightsix.kami.gui.rgui.render.AbstractComponentUI;
import me.zeroeightsix.kami.gui.rgui.render.font.FontRenderer;
import me.zeroeightsix.kami.gui.rgui.util.ContainerHelper;

import java.util.List;

import static me.zeroeightsix.kami.gui.kami.theme.kami.KamiGuiColors.GuiC;
import static me.zeroeightsix.kami.util.ColourConverter.rgbToInt;
import static me.zeroeightsix.kami.util.ColourConverter.toF;
import static org.lwjgl.opengl.GL11.*;

/**
 * Created by 086 on 4/08/2017.
 */
public class RootCheckButtonUI<T extends CheckButton> extends AbstractComponentUI<CheckButton> {
    @Override
    public void renderComponent(CheckButton component, FontRenderer ff) {

        glColor4f(toF(GuiC.bgColour.color.getRed()), toF(GuiC.bgColour.color.getGreen()), toF(GuiC.bgColour.color.getBlue()), component.getOpacity());
        if (component.isToggled()) { // red used to be a separate value, toF(229.5d)
            glColor3f(toF(GuiC.bgColour.color.getRed()), toF(GuiC.bgColour.color.getGreen()), toF(GuiC.bgColour.color.getBlue()));
        }
        if (component.isHovered() || component.isPressed()) {
            glColor4f(toF(GuiC.bgColourHover.color.getRed()), toF(GuiC.bgColourHover.color.getGreen()), toF(GuiC.bgColourHover.color.getBlue()), component.getOpacity());
        }

        String text = component.getName(); // on toggle, toggled, hovered enabled
        int c = component.isPressed() ?
                GuiC.buttonPressed.color.getRGB() : component.isToggled() ?
                GuiC.buttonIdleT.color.getRGB() :
                GuiC.buttonHoveredT.color.getRGB();
        if (component.isHovered()) {
            c = (c & GuiC.buttonHoveredN.color.getRGB()) << 1; // hovered text color
            if (component.hasDescription() && !isSettingsOpen()) {
                glDisable(GL_SCISSOR_TEST); // let it draw outside of the container
                glDepthRange(0, 0.01); // set render priority to the top

                RenderHelper.drawTooltip(component.getWidth() + 14, 0,
                        KamiGUI.fontRenderer.getStringWidth(component.getDescription() + 2), KamiGUI.fontRenderer.getFontHeight() + 6,
                        1.5f, 0.17F, 0.17F, 0.18F, 0.9F,
                        toF(GuiC.windowOutline.color.getRed()), toF(GuiC.windowOutline.color.getGreen()), toF(GuiC.windowOutline.color.getBlue()));
                RenderHelper.drawText(component.getWidth() + 17, component.getDescription(), rgbToInt(255, 255, 255));

                glEnable(GL_SCISSOR_TEST);
                glDepthRange(0, 1.0);
            }
        }

        glColor3f(1, 1, 1);
        glEnable(GL_TEXTURE_2D);
        KamiGUI.fontRenderer.drawString(component.getWidth() / 2 - KamiGUI.fontRenderer.getStringWidth(text) / 2, KamiGUI.fontRenderer.getFontHeight() / 2 - 2, c, text);
        glDisable(GL_TEXTURE_2D);
        glDisable(GL_BLEND);
    }

    @Override
    public void handleAddComponent(CheckButton component, Container container) {
        component.setWidth(KamiGUI.fontRenderer.getStringWidth(component.getName()) + 14);
        component.setHeight(KamiGUI.fontRenderer.getFontHeight() + 2);
    }

    private boolean isSettingsOpen() {
        List<SettingsPanel> panels = ContainerHelper.getAllChildren(SettingsPanel.class, KamiMod.getInstance().getGuiManager());
        for (SettingsPanel settingsPanel : panels) {
            if (settingsPanel.isVisible()) {
                return true;
            }
        }
        return false;
    }
}
