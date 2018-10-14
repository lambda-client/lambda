package me.zeroeightsix.kami;

import me.zero.alpine.EventBus;
import me.zero.alpine.EventManager;
import me.zeroeightsix.kami.command.CommandManager;
import me.zeroeightsix.kami.event.ForgeEventProcessor;
import me.zeroeightsix.kami.gui.kami.KamiGUI;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.module.ModuleManager;
import me.zeroeightsix.kami.setting.config.Configuration;
import me.zeroeightsix.kami.util.Friends;
import me.zeroeightsix.kami.util.LagCompensator;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;

/**
 * Created by 086 on 7/11/2017.
 */
@Mod(modid = KamiMod.MODID, name = KamiMod.MODNAME, version = KamiMod.MODVER)
public class KamiMod {

    public static final String MODID = "kami";
    public static final String MODNAME = "KAMI";
    public static final String MODVER = "b9";

    public static final String KAMI_HIRAGANA = "\u304B\u307F";
    public static final String KAMI_KATAKANA = "\u30AB\u30DF";
    public static final String KAMI_KANJI = "\u795E";

    private static final String KAMI_CONFIG_NAME_DEFAULT = "KAMIConfig.json";

    public static final Logger log = LogManager.getLogger("KAMI");

    public static final EventBus EVENT_BUS = new EventManager();

    @Mod.Instance
    private static KamiMod INSTANCE;

    public KamiGUI guiManager;
    public CommandManager commandManager;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {

    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        KamiMod.log.info("\n\nInitializing KAMI " + MODVER);

        ModuleManager.initialize();

        ModuleManager.getModules().stream().filter(module -> module.alwaysListening).forEach(EVENT_BUS::subscribe);
        MinecraftForge.EVENT_BUS.register(new ForgeEventProcessor());
        LagCompensator.INSTANCE = new LagCompensator();

        Wrapper.init();

        guiManager = new KamiGUI();
        guiManager.initializeGUI();

        commandManager = new CommandManager();

        File file = new File("KAMISettings.json");
        Friends.initFriends();
        if (file.exists()) {

        }
        KamiMod.log.info("Settings loaded");

        // After settings loaded, we want to let the enabled modules know they've been enabled (since the setting is done through reflection)
        ModuleManager.getModules().stream().filter(Module::isEnabled).forEach(Module::enable);

        KamiMod.log.info("KAMI Mod initialized!\n");
    }

    private static String getConfigName() {
        File configNameFile = new File("KAMILastConfig.txt");
        String kamiConfigName = KAMI_CONFIG_NAME_DEFAULT;
        try(BufferedReader reader = new BufferedReader(new FileReader(configNameFile))) {
            kamiConfigName = reader.readLine();
            if (!isFilenameValid(kamiConfigName)) kamiConfigName = KAMI_CONFIG_NAME_DEFAULT;
        } catch (FileNotFoundException e) {
            try(BufferedWriter writer = new BufferedWriter(new FileWriter(configNameFile))) {
                writer.write(KAMI_CONFIG_NAME_DEFAULT);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return kamiConfigName;
    }

    public static void loadConfiguration() {
        try {
            loadConfigurationUnsafe();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void loadConfigurationUnsafe() throws IOException {
        String kamiConfigName = getConfigName();
        File kamiConfig = new File(kamiConfigName);
        if (!kamiConfig.exists()) return;
        Configuration.loadConfiguration(kamiConfig);
    }

    public static void saveConfiguration() {
        try {
            saveConfigurationUnsafe();
        }catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void saveConfigurationUnsafe() throws IOException {
        File outputFile = new File(getConfigName());
        if (!outputFile.exists())
            outputFile.createNewFile();
        Configuration.saveConfiguration(outputFile);
        ModuleManager.getModules().forEach(Module::destroy);
    }

    public static boolean isFilenameValid(String file) {
        File f = new File(file);
        try {
            f.getCanonicalPath();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static KamiMod getInstance() {
        return INSTANCE;
    }

    public KamiGUI getGuiManager() {
        return guiManager;
    }

    public CommandManager getCommandManager() {
        return commandManager;
    }
}
