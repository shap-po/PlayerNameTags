package me.gonkas.playernametags.handlers;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.List;

import static me.gonkas.playernametags.PlayerNameTags.CONFIG;

public class ConfigHandler {

    private static String VALIDCHARS;
    private static int MAXNAMELENGTH;
    private static int MAXPREFIXLENGTH;
    private static int MAXSUFFIXLENGTH;
    private static boolean ALLOWCOLORS;
    private static boolean ALLOWFORMATTING;
    private static boolean JOINTEAM;

    private static final String VALIDCHARSPATH = "valid-name-characters";
    private static final String MAXNAMELENGTHPATH = "max-name-length";
    private static final String MAXPREFIXLENGTHPATH = "max-prefix-length";
    private static final String MAXSUFFIXLENGTHPATH = "max-suffix-length";
    private static final String ALLOWCOLORSPATH = "enable-colors";
    private static final String ALLOWFORMATTINGPATH = "enable-formatting";
    private static final String JOINTEAMPATH = "join-team";

    private static final String DEFAULTCHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_.,+-/*!?' ";
    private static final int DEFAULTNAMELENGTH = 16;
    private static final int DEFAULTPREFIXLENGTH = 8;
    private static final int DEFAULTSUFFIXLENGTH = 8;
    private static final boolean DEFAULTALLOWCOLORS = true;
    private static final boolean DEFAULTALLOWFORMATTING = true;
    private static final boolean DEFAULTJOINTEAM = true;

    public static void load() {
        fixConfigPaths(CONFIG);

        VALIDCHARS = CONFIG.getString(VALIDCHARSPATH);
        MAXNAMELENGTH = CONFIG.getInt(MAXNAMELENGTHPATH);
        MAXPREFIXLENGTH = CONFIG.getInt(MAXPREFIXLENGTHPATH);
        MAXSUFFIXLENGTH = CONFIG.getInt(MAXSUFFIXLENGTHPATH);
        ALLOWCOLORS = CONFIG.getBoolean(ALLOWCOLORSPATH);
        ALLOWFORMATTING = CONFIG.getBoolean(ALLOWFORMATTINGPATH);
        JOINTEAM = CONFIG.getBoolean(JOINTEAMPATH);
    }
    
    public static void unload() {
        CONFIG.set(VALIDCHARSPATH, VALIDCHARS);
        CONFIG.set(MAXNAMELENGTHPATH, MAXNAMELENGTH);
        CONFIG.set(MAXPREFIXLENGTHPATH, MAXPREFIXLENGTH);
        CONFIG.set(MAXSUFFIXLENGTHPATH, MAXSUFFIXLENGTH);
        CONFIG.set(ALLOWCOLORSPATH, ALLOWCOLORS);
        CONFIG.set(JOINTEAMPATH, JOINTEAM);
    }

    // Fixes any used config paths that are broken or missing.
    private static void fixConfigPaths(FileConfiguration config) {
        if (!pathIsString(config, VALIDCHARSPATH)) config.set(VALIDCHARSPATH, DEFAULTCHARS);
        if (!pathIsInteger(config, MAXNAMELENGTHPATH)) config.set(MAXNAMELENGTHPATH, DEFAULTNAMELENGTH);
        if (!pathIsInteger(config, MAXPREFIXLENGTHPATH)) config.set(MAXPREFIXLENGTHPATH, DEFAULTPREFIXLENGTH);
        if (!pathIsInteger(config, MAXSUFFIXLENGTHPATH)) config.set(MAXSUFFIXLENGTHPATH, DEFAULTSUFFIXLENGTH);
        if (!pathIsBoolean(config, ALLOWCOLORSPATH)) config.set(ALLOWCOLORSPATH, DEFAULTALLOWCOLORS);
        if (!pathIsBoolean(config, ALLOWFORMATTINGPATH)) config.set(ALLOWFORMATTINGPATH, DEFAULTALLOWFORMATTING);
        if (!pathIsBoolean(config, JOINTEAMPATH)) config.set(JOINTEAMPATH, DEFAULTJOINTEAM);
    }

    private static boolean pathIsString(FileConfiguration config, String path) {return config.contains(path) && config.get(path) instanceof String;}
    private static boolean pathIsInteger(FileConfiguration config, String path) {return config.contains(path) && config.get(path) instanceof Integer;}
    private static boolean pathIsBoolean(FileConfiguration config, String path) {return config.contains(path) && config.get(path) instanceof Boolean;}
    private static boolean pathIsStringList(FileConfiguration config, String path) {return config.contains(path) && config.get(path) instanceof List<?> && config.get(path).getClass() == String.class;}

    public static String getValidChars() {return VALIDCHARS;}
    public static void setValidChars(String chars) {VALIDCHARS = chars;}
    public static String resetValidChars() {VALIDCHARS = DEFAULTCHARS; return VALIDCHARS;}
    public static boolean hasInvalidChars(String chars) {
        if (VALIDCHARS.isEmpty()) return false;
        for (char c : chars.toCharArray()) {if (!ConfigHandler.getValidChars().contains(String.valueOf(c)) && c != '§') return true;}
        return false;
    }

    public static int getMaxNameLength() {return MAXNAMELENGTH;}
    public static void setMaxNameLength(int num) {MAXNAMELENGTH = num;}
    public static int resetMaxNameLength() {MAXNAMELENGTH = DEFAULTNAMELENGTH; return MAXNAMELENGTH;}

    public static int getMaxPrefixLength() {return MAXPREFIXLENGTH;}
    public static void setMaxPrefixLength(int num) {MAXPREFIXLENGTH = num;}
    public static int resetMaxPrefixLength() {MAXPREFIXLENGTH = DEFAULTPREFIXLENGTH; return MAXPREFIXLENGTH;}

    public static int getMaxSuffixLength() {return MAXSUFFIXLENGTH;}
    public static void setMaxSuffixLength(int num) {MAXSUFFIXLENGTH = num;}
    public static int resetMaxSuffixLength() {MAXSUFFIXLENGTH = DEFAULTSUFFIXLENGTH; return MAXSUFFIXLENGTH;}

    public static boolean getAllowColors() {return ALLOWCOLORS;}
    public static void setAllowColors(boolean value) {ALLOWCOLORS = value;}
    public static boolean resetAllowColors() {ALLOWCOLORS = DEFAULTALLOWCOLORS; return ALLOWCOLORS;}

    public static boolean getAllowFormatting() {return ALLOWFORMATTING;}
    public static void setAllowFormatting(boolean value) {ALLOWFORMATTING = value;}
    public static boolean resetAllowFormatting() {ALLOWFORMATTING = DEFAULTALLOWFORMATTING; return ALLOWFORMATTING;}

    public static boolean getJoinTeam() {return JOINTEAM;}
    public static void setJoinTeam(boolean value) {JOINTEAM = value;}
    public static boolean resetJoinTeam() {JOINTEAM = DEFAULTJOINTEAM; return JOINTEAM;}

    public static String getFormattingCharacters() {
        StringBuilder builder = new StringBuilder();
        if (getAllowColors()) {builder.append("0123456789abcdef");}
        if (getAllowFormatting()) {builder.append("klmnor");}
        return builder.toString();
    }
}
