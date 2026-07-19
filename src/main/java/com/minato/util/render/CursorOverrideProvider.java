
package com.minato.util.render;

import net.minecraft.client.gui.cursor.Cursor;

public interface CursorOverrideProvider {
    Cursor getCursorOverride(int mouseX, int mouseY);
}
