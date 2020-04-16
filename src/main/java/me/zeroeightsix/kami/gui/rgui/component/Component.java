package me.zeroeightsix.kami.gui.rgui.component;

import me.zeroeightsix.kami.gui.rgui.component.container.Container;
import me.zeroeightsix.kami.gui.rgui.component.listen.*;
import me.zeroeightsix.kami.gui.rgui.poof.IPoof;
import me.zeroeightsix.kami.gui.rgui.poof.PoofInfo;
import me.zeroeightsix.kami.gui.rgui.render.ComponentUI;
import me.zeroeightsix.kami.gui.rgui.render.theme.Theme;

import java.util.ArrayList;

/**
 * Created by 086 on 25/06/2017.
 */
public interface Component {
    // Getters for location and size
    int getX(); // Relative to its parent.

    // Setters for location and size
    void setX(int x);

    int getY();

    void setY(int y);

    int getWidth();

    void setWidth(int width);

    int getHeight();

    void setHeight(int height);

    int getMinimumWidth();

    Component setMinimumWidth(int width);

    int getMaximumWidth();

    Component setMaximumWidth(int width);

    int getMinimumHeight();

    Component setMinimumHeight(int height);

    int getMaximumHeight();

    Component setMaximumHeight(int height);

    float getOpacity();

    void setOpacity(float opacity);

    boolean doAffectLayout();

    void setAffectLayout(boolean flag);

    Container getParent();

    void setParent(Container parent);

    boolean liesIn(Component container);

    boolean isVisible();

    void setVisible(boolean visible);

    boolean isFocused();

    void setFocused(boolean focus);

    ComponentUI getUI();

    Theme getTheme();

    void setTheme(Theme theme);

    boolean isHovered();

    boolean isPressed();

    ArrayList<MouseListener> getMouseListeners();

    void addMouseListener(MouseListener listener);

    ArrayList<RenderListener> getRenderListeners();

    void addRenderListener(RenderListener listener);

    ArrayList<KeyListener> getKeyListeners();

    void addKeyListener(KeyListener listener);

    ArrayList<UpdateListener> getUpdateListeners();

    void addUpdateListener(UpdateListener listener);

    ArrayList<TickListener> getTickListeners();

    void addTickListener(TickListener listener);

    void addPoof(IPoof poof);

    void callPoof(Class<? extends IPoof> target, PoofInfo info);

    int getPriority(); // The higher, the more prioritized.

    void kill();
}
