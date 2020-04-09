package me.zeroeightsix.kami.gui.mc;

import net.minecraft.client.gui.*;

public class KamiGuiUpdateNotification extends GuiScreen {

    private final String title;
    private final String message;
    private final int singleOrMulti;

    public KamiGuiUpdateNotification(String title, String message, int singleOrMulti) {
        super();

        this.title = title;
        this.message = message;
        this.singleOrMulti = singleOrMulti;
    }

    @Override
    public void initGui() {
        super.initGui();

        this.buttonList.add(new GuiButton(0, this.width / 2 - 100, 140, "Download Latest"));
        this.buttonList.add(new GuiButton(1, this.width / 2 - 100, 240, "&4Use Current Version"));
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawGradientRect(0, 0, this.width, this.height, -12574688, -11530224);

        drawCenteredString(this.fontRenderer, this.title, this.width / 2, 70, 16777215);
        drawCenteredString(this.fontRenderer, this.message, this.width / 2, 95, 16777215);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    @Override
    protected void keyTyped(char typedChar, int keyCode) {}

    @Override
    protected void actionPerformed(GuiButton button) {
        if (singleOrMulti == 1) { // Single
            mc.displayGuiScreen(new GuiWorldSelection(new GuiMainMenu()));

            return;
        }

        mc.displayGuiScreen(new GuiMultiplayer(new GuiMainMenu()));
    }
}
