package me.zeroeightsix.kami.gui.kami.theme.kami;

import me.zeroeightsix.kami.gui.kami.RenderHelper;
import me.zeroeightsix.kami.gui.kami.component.SettingsPanel;
import me.zeroeightsix.kami.gui.rgui.render.AbstractComponentUI;
import me.zeroeightsix.kami.gui.rgui.render.font.FontRenderer;
import org.lwjgl.opengl.GL11;

/**
 * Created by 086 on 16/12/2017.
 */
public class KamiSettingsPanelUI extends AbstractComponentUI<SettingsPanel> {

    @Override
    public void renderComponent(SettingsPanel component, FontRenderer fontRenderer) {
        super.renderComponent(component, fontRenderer);

        GL11.glLineWidth(2.0F);
        GL11.glColor4f(0.17F, 0.17F, 0.18F, 0.9F);

        RenderHelper.drawFilledRectangle(0.0F, 0.0F, (float) component.getWidth(), (float) component.getHeight());
        GL11.glColor3f(.60f, .56f, 1.00f);
        GL11.glLineWidth(1.5F);
        RenderHelper.drawRectangle(0.0F, 0.0F, (float) component.getWidth(), (float) component.getHeight());
    }
}
