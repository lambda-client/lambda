package me.zeroeightsix.kami.gui.kami;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.command.Command;
import me.zeroeightsix.kami.gui.rgui.GUI;
import me.zeroeightsix.kami.gui.rgui.component.container.use.Frame;
import me.zeroeightsix.kami.gui.rgui.component.container.use.Scrollpane;
import me.zeroeightsix.kami.gui.rgui.component.listen.MouseListener;
import me.zeroeightsix.kami.gui.rgui.component.listen.TickListener;
import me.zeroeightsix.kami.gui.rgui.component.use.CheckButton;
import me.zeroeightsix.kami.gui.rgui.component.use.Label;
import me.zeroeightsix.kami.gui.rgui.render.theme.Theme;
import me.zeroeightsix.kami.gui.rgui.util.ContainerHelper;
import me.zeroeightsix.kami.gui.rgui.util.Docking;
import me.zeroeightsix.kami.gui.kami.component.ActiveModules;
import me.zeroeightsix.kami.gui.kami.component.SettingsPanel;
import me.zeroeightsix.kami.gui.kami.theme.kami.KamiTheme;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.util.ColourHolder;
import me.zeroeightsix.kami.util.LagCompensator;
import me.zeroeightsix.kami.util.Pair;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityEgg;
import net.minecraft.entity.projectile.EntitySnowball;
import net.minecraft.entity.projectile.EntityWitherSkull;

import java.util.*;

/**
 * Created by 086 on 25/06/2017.
 */
public class KamiGUI extends GUI {

    public static final RootFontRenderer fontRenderer = new RootFontRenderer(1);
    public Theme theme;

    public static ColourHolder primaryColour = new ColourHolder(29,29,29);

    public KamiGUI() {
        super(new KamiTheme());
        theme = getTheme();
    }

    @Override
    public void drawGUI() {
        super.drawGUI();
    }

    @Override
    public void initializeGUI() {
        HashMap<Module.Category, Pair<Scrollpane, SettingsPanel>> categoryScrollpaneHashMap = new HashMap<>();
        for (Module module : ModuleManager.getModules()){
            if (module.getCategory().isHidden()) continue;
            Module.Category moduleCategory = module.getCategory();
            if (!categoryScrollpaneHashMap.containsKey(moduleCategory)){
                Stretcherlayout stretcherlayout = new Stretcherlayout(1);
                stretcherlayout.setComponentOffsetWidth(0);
                Scrollpane scrollpane = new Scrollpane(getTheme(), stretcherlayout, 300, 260);
                scrollpane.setMaximumHeight(180);
                categoryScrollpaneHashMap.put(moduleCategory, new Pair<>(scrollpane, new SettingsPanel(getTheme(), null)));
            }

            Pair<Scrollpane, SettingsPanel> pair = categoryScrollpaneHashMap.get(moduleCategory);
            Scrollpane scrollpane = pair.getKey();
            CheckButton checkButton = new CheckButton(module.getName());
            checkButton.setToggled(module.isEnabled());

            checkButton.addTickListener(() -> checkButton.setToggled(module.isEnabled()));
            checkButton.addMouseListener(new MouseListener() {
                @Override
                public void onMouseDown(MouseButtonEvent event) {
                    if (event.getButton() == 1){ // Right click
                        pair.getValue().setModule(module);
                        pair.getValue().setX(event.getX()+checkButton.getX());
                        pair.getValue().setY(event.getY()+checkButton.getY());
                    }
                }

                @Override
                public void onMouseRelease(MouseButtonEvent event) {

                }

                @Override
                public void onMouseDrag(MouseButtonEvent event) {

                }

                @Override
                public void onMouseMove(MouseMoveEvent event) {

                }

                @Override
                public void onScroll(MouseScrollEvent event) {

                }
            });
            checkButton.addPoof(new CheckButton.CheckButtonPoof<CheckButton, CheckButton.CheckButtonPoof.CheckButtonPoofInfo>() {
                @Override
                public void execute(CheckButton component, CheckButtonPoofInfo info) {
                    if (info.getAction().equals(CheckButton.CheckButtonPoof.CheckButtonPoofInfo.CheckButtonPoofInfoAction.TOGGLE)){
                        module.setEnabled(checkButton.isToggled());
                    }
                }
            });
            scrollpane.addChild(checkButton);
        }

        int x = 10;
        int y = 10;
        int nexty = y;
        for (Map.Entry<Module.Category, Pair<Scrollpane, SettingsPanel>> entry : categoryScrollpaneHashMap.entrySet()){
            Stretcherlayout stretcherlayout = new Stretcherlayout(1);
            stretcherlayout.COMPONENT_OFFSET_Y = 1;
            Frame frame = new Frame(getTheme(), stretcherlayout, entry.getKey().getName());
            Scrollpane scrollpane = entry.getValue().getKey();
            frame.addChild(scrollpane);
            frame.addChild(entry.getValue().getValue());
            scrollpane.setOriginOffsetY(0);
            scrollpane.setOriginOffsetX(0);
            frame.setCloseable(false);

            frame.setX(x);
            frame.setY(y);

            addChild(frame);

            nexty = Math.max(y + frame.getHeight() + 10, nexty);
            x += frame.getWidth() + 10;
            if (x > Wrapper.getMinecraft().displayWidth / 1.2f){
                y = nexty;
                nexty = y;
            }
        }

        this.addMouseListener(new MouseListener() {
            private boolean isBetween(int min, int val, int max){
                return !(val > max || val < min);
            }

            @Override
            public void onMouseDown(MouseButtonEvent event) {
                List<SettingsPanel> panels = ContainerHelper.getAllChildren(SettingsPanel.class, KamiGUI.this);
                for (SettingsPanel settingsPanel : panels){
                    if (!settingsPanel.isVisible()) continue;
                    int[] real = GUI.calculateRealPosition(settingsPanel);
                    int pX = event.getX() - real[0];
                    int pY = event.getY() - real[1];
                    if (!isBetween(0, pX, settingsPanel.getWidth()) || !isBetween(0, pY, settingsPanel.getHeight()))
                        settingsPanel.setVisible(false);
                }
            }

            @Override
            public void onMouseRelease(MouseButtonEvent event) {

            }

            @Override
            public void onMouseDrag(MouseButtonEvent event) {

            }

            @Override
            public void onMouseMove(MouseMoveEvent event) {

            }

            @Override
            public void onScroll(MouseScrollEvent event) {

            }
        });

        ArrayList<Frame> frames = new ArrayList<>();

        Frame frame = new Frame(getTheme(), new Stretcherlayout(1), "Active modules");
        frame.setCloseable(false);
        frame.addChild(new ActiveModules());
        frame.setPinneable(true);
        frames.add(frame);

        frame = new Frame(getTheme(), new Stretcherlayout(1), "Info");
        frame.setCloseable(false);
        frame.setPinneable(true);
        Label information = new Label("");
        information.setShadow(true);
        information.addTickListener(() -> {
            information.setText("");
            information.addLine("\u00A7b" + KamiMod.KAMI_KANJI + "\u00A73 " + KamiMod.MODVER);
            information.addLine("\u00A7b" + Math.round(LagCompensator.INSTANCE.getTickRate()) + Command.SECTIONSIGN() +  "3 tps");
            information.addLine("\u00A7b" + Wrapper.getMinecraft().debugFPS + Command.SECTIONSIGN() +  "3 fps");

//            information.addLine("[&3" + Sprint.getSpeed() + "km/h&r]");

        });
        frame.addChild(information);
        information.setFontRenderer(fontRenderer);
        frames.add(frame);

        frame = new Frame(getTheme(), new Stretcherlayout(1), "Text Radar");
        Label list = new Label("");
        list.addTickListener(new TickListener() {
            @Override
            public void onTick() {
                if (!list.isVisible()) return;
                list.setText("");

                Minecraft mc = Wrapper.getMinecraft();

                if (mc.player == null) return;
                List<EntityPlayer> entityList = mc.world.playerEntities;

                Map<String, Integer> players = new HashMap<>();
                for(Entity e : entityList){
                    if (e.getName().equals(mc.player.getName())) continue;
                    players.put(e.getName(), (int)mc.player.getDistance(e));
                }

                if (players.isEmpty()){
                    list.setText("");
                    return;
                }

                players = sortByValue(players);

                for (Map.Entry<String, Integer> player : players.entrySet()){
                    list.addLine("\u00A77" + player.getKey() + " \u00A78" + player.getValue());
                }

//                list.getParent().setHeight(list.getLines().length * (list.getTheme().getFontRenderer().getFontHeight()+1) + 3);
            }
        });
        frame.setCloseable(false);
        frame.setPinneable(true);
        list.setShadow(true);
        frame.addChild(list);
        list.setFontRenderer(fontRenderer);
        frames.add(frame);

        frame = new Frame(getTheme(), new Stretcherlayout(1), "Entities");
        Label entityLabel = new Label("");
        frame.setCloseable(false);
        entityLabel.addTickListener(new TickListener() {
            Minecraft mc = Wrapper.getMinecraft();

            @Override
            public void onTick() {
                if (mc.player == null || !entityLabel.isVisible()) return;
                List<Entity> entityList = mc.world.loadedEntityList;
                if (entityList.size()<=1){
                    entityLabel.setText("");
                    return;
                }

                Map<String, Integer> entityMap = new HashMap<>();
                List<Entity> copy = new ArrayList<>(entityList);
                for (Entity e : copy) {
                    if (e instanceof EntityPlayer) continue;

                    String name = e.getName();

                    int add = 1;

                    if (e instanceof EntityItem){
                        name = Command.SECTIONSIGN() + "3" + ((EntityItem) e).getItem().getItem().getItemStackDisplayName(((EntityItem) e).getItem());
                        add = ((EntityItem) e).getItem().getCount();
                    }
                    if (e instanceof EntityWitherSkull){
                        name = Command.SECTIONSIGN() + "8" + "Wither skull";
                    }
                    if (e instanceof EntityEnderCrystal){
                        name = Command.SECTIONSIGN() + "d" + "End crystal";
                    }
                    if (e instanceof EntityEnderPearl){
                        name = "Thrown ender pearl";
                    }
                    if (e instanceof EntityMinecart){
                        name = "Minecart";
                    }
                    if (e instanceof EntityItemFrame){
                        name = "Item frame";
                    }
                    if (e instanceof EntityEgg){
                        name = "Thrown egg";
                    }
                    if (e instanceof EntitySnowball){
                        name = "Thrown snowball";
                    }

                    int count = entityMap.containsKey(name) ? entityMap.get(name) : 0;
                    entityMap.put(name,count+add);
                }

                entityMap = sortByValue(entityMap);

                entityLabel.setText("");
                for (Map.Entry<String, Integer> entry : entityMap.entrySet()){
                    entityLabel.addLine(Command.SECTIONSIGN() + "7"+entry.getKey() + " " + Command.SECTIONSIGN() + "8x" + entry.getValue());
                }

//                entityLabel.getParent().setHeight(entityLabel.getLines().length * (entityLabel.getTheme().getFontRenderer().getFontHeight()+1) + 3);
            }
        });
        frame.addChild(entityLabel);
        frame.setPinneable(true);
        entityLabel.setShadow(true);
        entityLabel.setFontRenderer(fontRenderer);
        frames.add(frame);

        frame = new Frame(getTheme(), new Stretcherlayout(1), "Coordinates");
        frame.setCloseable(false);
        frame.setPinneable(true);
        Label coordsLabel = new Label("");
        coordsLabel.addTickListener(new TickListener() {
            Minecraft mc = Minecraft.getMinecraft();
            @Override
            public void onTick() {
                boolean inHell = (mc.world.getBiome(mc.player.getPosition()).getBiomeName().equals("Hell"));
                //" " + Command.SECTIONSIGN() + "7" +  + " / " +  + " / " + (Math.floor(mc.player.posZ*10)/10)

                int posX = (int) mc.player.posX;
                int posY = (int) mc.player.posY;
                int posZ = (int) mc.player.posZ;

                float f = !inHell ? 0.125f : 8;
                int hposX = (int) (mc.player.posX * f);
                int hposZ = (int) (mc.player.posZ * f);

                coordsLabel.setText(String.format(" %sf%d%s7, %sf%d%s7, %sf%d %s7(%sf%d%s7, %sf%d%s7, %sf%d%s7)",
                        Command.SECTIONSIGN(),
                        posX,
                        Command.SECTIONSIGN(),
                        Command.SECTIONSIGN(),
                        posY,
                        Command.SECTIONSIGN(),
                        Command.SECTIONSIGN(),
                        posZ,
                        Command.SECTIONSIGN(),
                        Command.SECTIONSIGN(),
                        hposX,
                        Command.SECTIONSIGN(),
                        Command.SECTIONSIGN(),
                        posY,
                        Command.SECTIONSIGN(),
                        Command.SECTIONSIGN(),
                        hposZ,
                        Command.SECTIONSIGN()
                        ));
            }
        });
        frame.addChild(coordsLabel);
        coordsLabel.setFontRenderer(fontRenderer);
        coordsLabel.setShadow(true);
        frame.setHeight(20);
        frames.add(frame);

        for (Frame frame1 : frames){
            frame1.setX(x);
            frame1.setY(y);

            nexty = Math.max(y + frame1.getHeight() + 10, nexty);
            x += frame1.getWidth() + 10;
            if (x*DisplayGuiScreen.getScale() > Wrapper.getMinecraft().displayWidth / 1.2f){
                y = nexty;
                nexty = y;
                x = 10;
            }

            addChild(frame1);
        }
    }

    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map )
    {
        List<Map.Entry<K, V>> list =
                new LinkedList<>( map.entrySet() );
        Collections.sort(list, Comparator.comparing(o -> (o.getValue())));

        Map<K, V> result = new LinkedHashMap<K, V>();
        for (Map.Entry<K, V> entry : list)
        {
            result.put( entry.getKey(), entry.getValue() );
        }
        return result;
    }

    @Override
    public void destroyGUI() {
        kill();
    }

    private static final int DOCK_OFFSET = 0;
    public static void dock(Frame component) {
        Docking docking = component.getDocking();
        if (docking.isTop())
            component.setY(DOCK_OFFSET);
        if (docking.isBottom())
            component.setY((Wrapper.getMinecraft().displayHeight / DisplayGuiScreen.getScale()) - component.getHeight() - DOCK_OFFSET);
        if (docking.isLeft())
            component.setX(DOCK_OFFSET);
        if (docking.isRight())
            component.setX((Wrapper.getMinecraft().displayWidth / DisplayGuiScreen.getScale()) - component.getWidth() - DOCK_OFFSET);
    }
}
