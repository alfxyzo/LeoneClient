package dev.alfxyz.leoneclient;

import dev.alfxyz.leoneclient.utils.*;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.io.*;
import java.util.ArrayList;
import java.util.regex.Pattern;

public class Config {
    private static final ModContainer mod = FabricLoader.getInstance()
            .getModContainer("leoneclient")
            .orElseThrow(NullPointerException::new);
    public static final String version = mod.getMetadata().getVersion().getFriendlyString();
    public static File configFile = new File(FabricLoader.getInstance().getConfigDir().toString() + File.separator + "leoneclient.cfg");

    
    public static Boolean isLatestModVersion() throws IOException {
        BufferedReader brTest = new BufferedReader(new FileReader(configFile));
        String text = brTest.readLine();
        brTest.close();

        return text != null && text.contains(version);
    }

    
    public static String getValueFromConfig(String value) {
        String data = null;
        try {
            BufferedReader file = new BufferedReader(new FileReader(configFile));
            String line;

            while ((line = file.readLine()) != null) {
                if (line.contains(value)) {
                    
                    
                    data = line.replace(value + ": ", "");
                    file.close();
                    return data;
                }
            }

            file.close();
        } catch (Exception e) {
            LeoneClient.log.info("Problem reading file.");
        }

        
        if (data == null) {
            LeoneClient.log.info("bad!!! missing " + value);
            delete();
            create();
            return "true";
        }

        return data;
    }

    
    public static void setValueFromConfig(String value, String data) {
        try {
            ArrayList<String> lines = getConfig();

            if (lines == null) {
                LeoneClient.log.info("Problem reading leoneclient config!!! Error!!!");
                return;
            }

            
            for (int i = 0; i < lines.size(); i++) {
                String line = lines.get(i);

                
                if (!line.contains("#") && line.length() > 1 && line.contains(":")) {
                    
                    if (value.equals(line.substring(0, line.indexOf(":")))) {
                        
                        lines.set(i, value + ": " + data);
                        break;
                    }
                }
            }

            
            FileWriter writer = new FileWriter(configFile);

            
            for (String line : lines) {
                writer.write(line + System.lineSeparator());
            }

            writer.close();
        } catch (Exception e) {
            LeoneClient.log.info("Problem writing file. " + e);
        }
    }

    
    public static ArrayList<Pattern> getListOfRegex() {
        try {
            ArrayList<String> lines = getConfig();

            if (lines == null) {
                LeoneClient.log.info("Problem reading leoneclient config!!! Error!!!");
                return new ArrayList<>();
            }

            
            if (!lines.contains("# Each line below is regex for ChatPhraseFilter to use.")) {
                LeoneClient.log.info("bad!!! missing regex for chat phrase filter");
                delete();
                create();
                return new ArrayList<>();
            }

            ArrayList<Pattern> linesOfRegex = new ArrayList<>();

            
            int commentLocation = lines.indexOf("# Each line below is regex for ChatPhraseFilter to use.") + 1;
            for (int i = commentLocation; i < lines.size(); i++) {
                if (!lines.get(i).isEmpty()) {
                    String edit = lines.get(i).replace("\n", "");
                    linesOfRegex.add(Pattern.compile(edit));
                }
            }

            if (linesOfRegex.isEmpty()) return new ArrayList<>();

            return linesOfRegex;
        } catch (Exception e) {
            LeoneClient.log.info("Problem reading file. " + e);
            return new ArrayList<>();
        }
    }

    
    public static void addRegex(String regex) {
        try {
            FileWriter writer = new FileWriter(configFile, true);

            writer.write("\n" + regex);
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    public static void removeRegex(String regex) {
        try {
            ArrayList<String> lines = getConfig();

            if (lines == null) {
                LeoneClient.log.info("Problem reading leoneclient config!!! Error!!!");
                return;
            }

            
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).equals(regex)) {
                    lines.remove(i);
                    break;
                }
            }

            
            FileWriter writer = new FileWriter(configFile);

            
            for (String line : lines) {
                writer.write(line + System.lineSeparator());
            }

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    
    private static ArrayList<String> getConfig() {
        try {
            BufferedReader bufferedReader = new BufferedReader(new FileReader(configFile));
            ArrayList<String> lines = new ArrayList<>();

            String l;

            while ((l = bufferedReader.readLine()) != null) {
                lines.add(l);
            }
            bufferedReader.close();

            return lines;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void delete() {
        if (configFile.delete())
            LeoneClient.log.info("Config file has been successfully deleted.");
        else
            LeoneClient.log.info("Error! Config file couldn't be deleted!");
    }

    
    public static void create() {
        try {
            
            if (configFile.createNewFile()) {
                writeDefaultConfig();
                LeoneClient.log.info("New config created.");
                return;
            }

            LeoneClient.log.info("Config already exists.");

            
            if (!isLatestModVersion()) {
                LeoneClient.log.info("Config is outdated!"); 
                delete();
                create();
                return;
            }

            
            BlockLobbyAds.toggled.set(Boolean.parseBoolean(getValueFromConfig(BlockLobbyAds.name)));
            BlockMinehutAds.toggled.set(Boolean.parseBoolean(getValueFromConfig(BlockMinehutAds.name)));

            BlockLobbyMapAds.toggled.set(Boolean.parseBoolean(getValueFromConfig(BlockLobbyMapAds.name)));



        } catch (IOException e) {
            LeoneClient.log.error("Error! LeoneClient couldn't create a config! ");
            e.printStackTrace();
        }
    }

    
    private static void writeDefaultConfig() throws IOException {
        FileWriter w = new FileWriter(configFile, true);
        w.write("# LeoneClient v" + version + " by alfxyz | Config" + System.lineSeparator());
        w.write("# Hey! " + System.lineSeparator());
        w.write(System.lineSeparator());
        w.write(BlockLobbyAds.name + ": true" + System.lineSeparator());
        w.write(BlockMinehutAds.name + ": true" + System.lineSeparator());
        w.write(BlockLobbyMapAds.name + ": true" + System.lineSeparator());
        w.write(System.lineSeparator());
        w.write("# Each line below is regex for ChatPhraseFilter to use." + System.lineSeparator());
        w.write("this_is_a_example" + System.lineSeparator());
        w.write("\\nMinehut ");
        w.close();
    }
}