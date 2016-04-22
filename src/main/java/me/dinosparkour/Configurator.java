package me.dinosparkour;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Configurator {

    private static final File config = new File("config.json");

    /**
     * Get the contents of the config file if it exists, or create a new one if it doesn't
     *
     * @return JSONObject config
     */
    public static JSONObject getConfig() {
        if(!config.exists()) {
            try {
                Configurator.create();

            } catch (IOException ex) {
                ex.printStackTrace();
            }

            System.out.println("Created a configuration file. Please fill the login credentials!");
            System.exit(0);
        }

        JSONObject object = null;
        try {
            object = Configurator.load();

        } catch (IOException ex) {
            ex.printStackTrace();
        }

        return object;
    }

    /**
     * Create a new config file
     *
     * @throws IOException
     */
    private static void create() throws IOException {
        Files.write(Paths.get(config.getPath()),
                new JSONObject()
                        .put("appId", "")
                        .put("token", "")
                        .put("prefix", "-")
                        .toString(4).getBytes());
    }

    /**
     * Load the contents of the config file, or regenerate it if it's missing a value
     * Used internally by the configurator
     * @return JSONObject config
     * @throws IOException
     */
    private static JSONObject load() throws IOException {
        JSONObject object = new JSONObject(new String(Files.readAllBytes(Paths.get(config.getPath())), "UTF-8"));

        if (object.has("appId") && object.has("token") && object.has("prefix"))
            return object;

        Configurator.create();
        System.err.println("The config file was missing a value! Regenerating..");
        System.exit(1);

        return null;
    }
}