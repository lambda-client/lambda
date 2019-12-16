package me.zeroeightsix.kami.module.modules.bewwawho.util;

import com.google.gson.Gson;
import me.zeroeightsix.kami.KamiMod;

import javax.net.ssl.HttpsURLConnection;
import java.io.InputStreamReader;
import java.net.URL;

/***
 * @author S-B99
 */
public class Donator {

    public static Donator INSTANCE;
    public DonatorUser[] donatorUsers;

    public class DonatorUser {
        public String uuid;
    }

    public Donator() {
        INSTANCE = this;
        try {
            HttpsURLConnection connection = (HttpsURLConnection) new URL("https://raw.githubusercontent.com/S-B99/KAMI/features-master/assets/capes.json").openConnection();
            connection.connect();
            this.donatorUsers = new Gson().fromJson(new InputStreamReader(connection.getInputStream()), DonatorUser[].class);
            connection.disconnect();
        } catch (Exception e) {
            KamiMod.log.error("Failed to load capes");
            e.printStackTrace();
        }
    }
}
