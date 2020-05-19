package me.zeroeightsix.kami.module.modules.misc;

import me.zero.alpine.listener.EventHandler;
import me.zero.alpine.listener.Listener;
import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.event.events.ChunkEvent;
import me.zeroeightsix.kami.module.Module;
import me.zeroeightsix.kami.setting.Setting;
import me.zeroeightsix.kami.setting.Settings;
import net.minecraft.client.Minecraft;
import net.minecraft.world.chunk.Chunk;
import org.apache.commons.lang3.SystemUtils;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Objects;

import static me.zeroeightsix.kami.util.MessageSendHelper.*;

/**
 * @author IronException
 * Moved to seperate module from ChunkFinder on 19/05/20
 */
@Module.Info(
        name = "WorldDownloader",
        description = "Downloads loaded chunks to load later!",
        category = Module.Category.MISC
)
public class WorldDownloader extends Module {
    private Setting<Save> saveSetting = register(Settings.e("Save", Save.OLD_CHUNKS));
    private Setting<SaveFormat> saveOption = register(Settings.e("Save Format", SaveFormat.EXTRA_FOLDER));
    private Setting<Boolean> saveInRegionFolder = register(Settings.b("Region Folder", false));
    private Setting<Boolean> alsoSaveNormalCoords = register(Settings.b("Save Normal Coords", false));
    private Setting<Boolean> closeFile = register(Settings.b("Close File", false));

    private LastSetting lastSetting = new LastSetting();
    private PrintWriter logWriter;

    public WorldDownloader() {
        super();

        closeFile.settingListener = setting -> {
            if (closeFile.getValue()) runCloseFile();
        };
    }

    @EventHandler
    public Listener<ChunkEvent> listener = new Listener<>(event -> {
        if (event.getPacket().isFullChunk() && (saveSetting.getValue().equals(Save.OLD_CHUNKS) || saveSetting.getValue().equals(Save.ALL_CHUNKS))) {
            saveNewChunk(event.getChunk());
        } else if (!event.getPacket().isFullChunk() && (saveSetting.getValue().equals(Save.NEW_CHUNKS) || saveSetting.getValue().equals(Save.ALL_CHUNKS))) {
            saveNewChunk(event.getChunk());
        }
    });

    private void runCloseFile() {
        closeFile.setValue(false);
        closeSettings();
        logWriter.close();
        sendChatMessage(getChatName() + "Saved file!");
    }

    @Override
    protected void onDisable() {
        logWriterClose();
        sendChatMessage(getChatName() + "Saved and cleared chunks!");
    }

    // needs to be synchronized so no data gets lost
    public void saveNewChunk(Chunk chunk) {
        saveNewChunk(testAndGetLogWriter(), getNewChunkInfo(chunk));
    }

    private String getNewChunkInfo(Chunk chunk) {
        String rV = String.format("%d,%d,%d", System.currentTimeMillis(), chunk.x, chunk.z);
        if (alsoSaveNormalCoords.getValue()) {
            rV += String.format(",%d,%d", chunk.x * 16 + 8, chunk.z * 16 + 8);
        }
        return rV;
    }

    private PrintWriter testAndGetLogWriter() {
        if (lastSetting.testChangeAndUpdate()) {
            logWriterClose();
            logWriterOpen();
        }
        return logWriter;
    }


    private void logWriterClose() {
        if (logWriter != null) {
            logWriter.close();
            logWriter = null;
            this.lastSetting = new LastSetting(); // what if the settings stay the same?
        }

    }

    private void logWriterOpen() {
        String filepath = getPath().toString();
        try {
            logWriter = new PrintWriter(new BufferedWriter(new FileWriter(filepath, true)), true);
            String head = "timestamp,ChunkX,ChunkZ";
            if (alsoSaveNormalCoords.getValue()) {
                head += ",x coordinate,z coordinate";
            }
            logWriter.println(head);
        } catch (Exception e) {
            e.printStackTrace();
            KamiMod.log.error(getChatName() + "some exception happened when trying to start the logging -> " + e.getMessage());
            sendErrorMessage(getChatName() + "onLogStart: " + e.getMessage());
        }
    }

    private Path getPath() {
        /* code from baritone (https://github.com/cabaletta/baritone/blob/master/src/main/java/baritone/cache/WorldProvider.java)
         */
        File file = null;
        int dimension = mc.player.dimension;

        // If there is an integrated server running (Aka Singleplayer) then do magic to find the world save file
        if (mc.isSingleplayer()) {
            try {
                file = Objects.requireNonNull(mc.getIntegratedServer()).getWorld(dimension).getChunkSaveLocation();
            } catch (Exception e) {
                e.printStackTrace();
                KamiMod.log.error("some exception happened when getting canonicalFile -> " + e.getMessage());
                sendErrorMessage(getChatName() + "onGetPath: " + e.getMessage());
            }

            // Gets the "depth" of this directory relative the the game's run directory, 2 is the location of the world
            if (Objects.requireNonNull(file).toPath().relativize(mc.gameDir.toPath()).getNameCount() != 2) {
                // subdirectory of the main save directory for this world

                file = file.getParentFile();
            }

        } else { // Otherwise, the server must be remote...
            file = makeMultiplayerDirectory().toFile();
        }

        // We will actually store the world data in a subfolder: "DIM<id>"
        if (dimension != 0) { // except if it's the overworld
            file = new File(file, "DIM" + dimension);
        }

        // maybe we want to save it in region folder
        if (saveInRegionFolder.getValue()) {
            file = new File(file, "region");
        }

        file = new File(file, "newChunkLogs");


        String date = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
        file = new File(file, mc.getSession().getUsername() + "_" + date + ".csv"); // maybe dont safe the name actually. But I also dont want to make another option...

        Path rV = file.toPath();
        try {
            if (!Files.exists(rV)) { // ovsly always...
                Files.createDirectories(rV.getParent());
                Files.createFile(rV);
            }
        } catch (IOException e) {
            e.printStackTrace();
            KamiMod.log.error("some exception happened when trying to make the file -> " + e.getMessage());
            sendErrorMessage(getChatName() + "onCreateFile: " + e.getMessage());
        }
        return rV;
    }

    private Path makeMultiplayerDirectory() {
        File rV = Minecraft.getMinecraft().gameDir;
        String folderName;
        switch (saveOption.getValue()) {
            case LITE_LOADER_WDL: // make folder structure like liteLoader
                folderName = Objects.requireNonNull(mc.getCurrentServerData()).serverName;

                rV = new File(rV, "saves");
                rV = new File(rV, folderName);
                break;
            case NHACK_WDL: // make folder structure like nhack-insdustries
                folderName = getNHackInetName();

                rV = new File(rV, "config");
                rV = new File(rV, "wdl-saves");
                rV = new File(rV, folderName);

                // extra because name might be different
                if (!rV.exists()) {
                    sendWarningMessage(getChatName() + "nhack wdl directory doesnt exist: " + folderName);
                    sendWarningMessage(getChatName() + "creating the directory now. It is recommended to update the ip");
                }
                break;
            default: // make folder structure in .minecraft
                folderName = Objects.requireNonNull(mc.getCurrentServerData()).serverName + "-" + mc.getCurrentServerData().serverIP;
                if (SystemUtils.IS_OS_WINDOWS) {
                    folderName = folderName.replace(":", "_");
                }

                rV = new File(rV, "KAMI_NewChunks");
                rV = new File(rV, folderName);
        }

        return rV.toPath();
    }

    private String getNHackInetName() {
        String folderName = Objects.requireNonNull(mc.getCurrentServerData()).serverIP;
        if (SystemUtils.IS_OS_WINDOWS) {
            folderName = folderName.replace(":", "_");
        }
        if (hasNoPort(folderName)) {
            folderName += "_25565"; // if there is no port then we have to manually include the standard port..
        }
        return folderName;
    }

    private boolean hasNoPort(String ip) {
        if (!ip.contains("_")) {
            return true;
        }

        String[] sp = ip.split("_");
        String ending = sp[sp.length - 1];
        // if it is numeric it means it might be a port...
        return !isInteger(ending);
    }

    private boolean isInteger(String s) {
        try {
            Integer.parseInt(s);
        } catch (NumberFormatException | NullPointerException e) {
            return false;
        }
        return true;
    }

    private void saveNewChunk(PrintWriter log, String data) {
        log.println(data);
    }

    private class LastSetting {

        SaveFormat lastSaveFormat;
        boolean lastInRegion;
        boolean lastSaveNormal;
        int dimension;
        String ip;

        public boolean testChangeAndUpdate() {
            if (testChange()) {
                // so we dont have to do this process again next time
                update();
                return true;
            }
            return false;
        }

        public boolean testChange() {
            // these somehow include the test whether its null
            if (saveOption.getValue() != lastSaveFormat) {
                return true;
            }
            if (saveInRegionFolder.getValue() != lastInRegion) {
                return true;
            }
            if (alsoSaveNormalCoords.getValue() != lastSaveNormal) {
                return true;
            }
            if (dimension != mc.player.dimension) {
                return true;
            }
            // strings need equals + this way because could be null
            return !Objects.requireNonNull(mc.getCurrentServerData()).serverIP.equals(ip);
        }

        private void update() {
            lastSaveFormat = saveOption.getValue();
            lastInRegion = saveInRegionFolder.getValue();
            lastSaveNormal = alsoSaveNormalCoords.getValue();
            dimension = mc.player.dimension;
            ip = Objects.requireNonNull(mc.getCurrentServerData()).serverIP;
        }
    }

    private enum SaveFormat {
        EXTRA_FOLDER, LITE_LOADER_WDL, NHACK_WDL
    }

    private enum Save {
        NEW_CHUNKS, OLD_CHUNKS, ALL_CHUNKS
    }
}
