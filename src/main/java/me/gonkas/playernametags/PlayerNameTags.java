package me.gonkas.playernametags;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import me.gonkas.playernametags.commands.ConfigCommand;
import me.gonkas.playernametags.commands.NameTagCommand;
import me.gonkas.playernametags.handlers.ConfigHandler;
import me.gonkas.playernametags.handlers.NameTagHandler;
import me.gonkas.playernametags.handlers.PacketHandler;
import me.gonkas.playernametags.util.Strings;
import me.gonkas.playernametags.util.TextType;
import org.bukkit.Bukkit;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.UUID;

public final class PlayerNameTags extends JavaPlugin {

    public static PlayerNameTags INSTANCE;
    public static boolean PLUGINISLOADED = false;

    public static ConsoleCommandSender CONSOLE;
    private static final String PLUGINPREFIX = "[PlayerNameTags/";
    private static final String ERRORPREFIX = "§c" + PLUGINPREFIX + "ERROR] ";
    private static final String INFOPREFIX = "§7" + PLUGINPREFIX + "INFO] ";
    private static final String WARNPREFIX = "§e" + PLUGINPREFIX + "WARN] ";

    public static File PLUGINFOLDER = new File("plugins/PlayerNameTags");
    public static File BACKUPSFOLDER = new File(PLUGINFOLDER, "backups");
    public static File NAMETAGSFOLDER = new File(PLUGINFOLDER, "nametags");

    public static FileConfiguration CONFIG;
    public static final int NAMEFILEVERSION = 1;

    public static ScoreboardManager SCOREBOARDMANAGER;
    public static Scoreboard MAINSCOREBOARD;
    public static Team TEAM;

    @Override
    public void onEnable() {
        INSTANCE = this;

        saveDefaultConfig();
        reloadConfig();
        CONFIG = getConfig();

        CONSOLE = Bukkit.getConsoleSender();

        if (!PLUGINFOLDER.exists()) {PLUGINFOLDER.mkdir();}
        if (!BACKUPSFOLDER.exists()) {BACKUPSFOLDER.mkdir();}
        if (!NAMETAGSFOLDER.exists()) {NAMETAGSFOLDER.mkdir();}

        getCommand("pntconfig").setExecutor(new ConfigCommand());
        getCommand("nametag").setExecutor(new NameTagCommand());

        if (!CONFIG.getBoolean("enable-plugin")) {consoleError("Plugin is disabled! Use '/pntconfig set enable-plugin true' to enable the plugin!"); return;}
        load();
    }

    public static void load() {
        if (PLUGINISLOADED) return;

        consoleInfo("Loading plugin...");

        ConfigHandler.load();
        updateNameFileSystem();

        SCOREBOARDMANAGER = Bukkit.getScoreboardManager();
        MAINSCOREBOARD = SCOREBOARDMANAGER.getMainScoreboard();

        TEAM = MAINSCOREBOARD.getTeam("PlayerNameTags");
        if (TEAM == null) {TEAM = MAINSCOREBOARD.registerNewTeam("PlayerNameTags");}
        Bukkit.getOnlinePlayers().forEach(p -> TEAM.addPlayer(p));
        TEAM.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
        TEAM.setCanSeeFriendlyInvisibles(false);

        NameTagHandler.load();

        Bukkit.getPluginManager().registerEvents(new NameTagHandler(), INSTANCE);

        PacketEvents.getAPI().getEventManager().registerListener(new PacketHandler(), PacketListenerPriority.NORMAL);

        consoleInfo("Loaded successfully.");
        PLUGINISLOADED = true;
    }

    @Override
    public void onDisable() {unload();}

    public static void unload() {
        if (!PLUGINISLOADED) return;

        NameTagHandler.unload();
        ConfigHandler.unload();
        TEAM.unregister();

        INSTANCE.saveConfig();
        PLUGINISLOADED = false;
    }

    public static void enable() {
        CONFIG.set("enable-plugin", true);
        INSTANCE.saveConfig();
        load();
    }

    public static void disable() {
        consoleError("Disabling plugin! Use '/pntconfig enable-plugin true' to re-enable the plugin!");
        CONFIG.set("enable-plugin", false);
        unload();
    }

    public static void consoleError(String message, String... args) {CONSOLE.sendMessage(String.format(ERRORPREFIX + message, (Object[]) args));}
    public static void consoleInfo(String message, String... args) {CONSOLE.sendMessage(String.format(INFOPREFIX + message, (Object[]) args));}
    public static void consoleWarn(String message, String... args) {CONSOLE.sendMessage(String.format(WARNPREFIX + message, (Object[]) args));}

    private static boolean requestNamefileBackup = false;
    private static void updateNameFileSystem() {
        File old_namefile = new File(PLUGINFOLDER, "player_names.yml");
        if (!old_namefile.exists()) return;

        consoleWarn("Old namefile detected! Updating to new nametag file system...");
        YamlConfiguration nametags = YamlConfiguration.loadConfiguration(old_namefile);

        nametags.getValues(false).forEach((path, value) -> {
            try {UUID.fromString(path);} catch (IllegalArgumentException e) {return;}

            File new_namefile = new File(NAMETAGSFOLDER, path + ".yml");
            YamlConfiguration nametag = YamlConfiguration.loadConfiguration(new_namefile);

            if (!nametags.contains(path + ".prefix") || Strings.formatText(nametags.getString(path + ".prefix"), TextType.PREFIX) == null) {nametag.set("prefix", "");}
            else {nametag.set("prefix", nametags.getString(path + ".prefix"));}

            if (!nametags.contains(path + ".name") || Strings.formatText(nametags.getString(path + ".name"), TextType.NAME) == null) {nametag.set("name", Bukkit.getOfflinePlayer(UUID.fromString(path)).getName() + "§r");}
            else {nametag.set("name", nametags.getString(path + ".name"));}

            if (!nametags.contains(path + ".suffix") || Strings.formatText(nametags.getString(path + ".suffix"), TextType.SUFFIX) == null) {nametag.set("suffix", "");}
            else {nametag.set("suffix", nametags.getString(path + ".suffix"));}

            if (!nametags.contains(path + ".hidden") || !(nametags.get(path + ".hidden") instanceof Boolean)) {nametag.set("hidden", false);}
            else {nametag.set("hidden", nametags.getBoolean(path + ".hidden"));}

            try {nametag.save(new_namefile);} catch (IOException ignored) {requestNamefileBackup = true;}
        });

        if (requestNamefileBackup) {
            consoleError("Some nametags were not updated properly! Creating old system backup...");

            File namefile_backups_folder = new File(BACKUPSFOLDER, "old_namefiles");
            if (!namefile_backups_folder.exists()) {namefile_backups_folder.mkdir();}

            String filename = "namefile_" + Instant.now().toString().split("\\.")[0].replaceAll(":", ".") + ".yml";
            try {Files.copy(old_namefile.toPath(), (new File(namefile_backups_folder, filename).toPath()), StandardCopyOption.REPLACE_EXISTING);}
            catch (IOException ex) {consoleError("Unable to create backup! Cancelling namefile deletion. Please update manually or delete the file."); return;}

            consoleWarn("Created backup successfully. Deleting old namefile...");
        }

        if (!old_namefile.delete()) {consoleError("Unable to delete old namefile! Please delete it manually.");}
        else {consoleInfo("Successfully deleted old namefile");}
    }
}
