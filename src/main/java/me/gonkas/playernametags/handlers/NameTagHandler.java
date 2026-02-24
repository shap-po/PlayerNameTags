package me.gonkas.playernametags.handlers;

import me.gonkas.playernametags.util.Strings;
import me.gonkas.playernametags.util.TextType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.*;

import static me.gonkas.playernametags.PlayerNameTags.*;

public class NameTagHandler implements Listener {

    private static final HashMap<Player, ArrayList<String>> PLAYERNAMES = new HashMap<>(0);
    private static final HashMap<Player, Boolean> PLAYERNAMESTOGGLE = new HashMap<>(0);
    private static final HashMap<Player, ArmorStand> PLAYERSTANDS = new HashMap<>(0);

    private static final HashMap<String, Map<TextType, String>> OFFLINEPLAYERNAMES = new HashMap<>(0);
    private static final HashMap<String, Boolean> OFFLINEPLAYERTOGGLE = new HashMap<>(0);

    public static void load() {Bukkit.getOnlinePlayers().forEach(NameTagHandler::loadPlayer);}
    public static void unload() {
        Bukkit.getOnlinePlayers().forEach(NameTagHandler::unloadPlayer);

        consoleWarn("Saving all queued offline players' nametags...");
        OFFLINEPLAYERNAMES.forEach((p, v) -> createNameTagConfig(p, v, isHidden(p)));
        consoleInfo("Finished saving offline players' nametags.");
    }

    private static void loadPlayer(Player player) {

        File namefile = getNameFile(player);
        if (namefile == null) return;

        YamlConfiguration nametag = YamlConfiguration.loadConfiguration(namefile);

        String prefix = getPrefix(player.getName()).isEmpty() ? nametag.getString("prefix") : getPrefix(player.getName());
        String name = getName(player.getName()).isEmpty() ? nametag.getString("name") : getName(player.getName());
        String suffix = getSuffix(player.getName()).isEmpty() ? nametag.getString("suffix") : getSuffix(player.getName());
        boolean hidden = !OFFLINEPLAYERTOGGLE.containsKey(player.getName()) ? nametag.getBoolean("hidden") : isHidden(player.getName());

        OFFLINEPLAYERNAMES.remove(player.getName());
        OFFLINEPLAYERTOGGLE.remove(player.getName());

        if (prefix == null || name == null || suffix == null) {
            consoleError("Unable to load player %s's namefile! Creating backup...", player.getName());

            String filename = player.getUniqueId() + "_" + Instant.now().toString().split("\\.")[0].replaceAll(":", ".") + ".yml";
            try {Files.copy(namefile.toPath(), (new File(BACKUPSFOLDER, filename).toPath()), StandardCopyOption.REPLACE_EXISTING);}
            catch (IOException ex) {consoleError("Unable to create backup! Cancelling namefile load. Please create the file manually or restart the plugin."); return;}

            consoleInfo("Created backup successfully. Deleting name file...");
            if (!namefile.delete()) {consoleError("Unable to delete name file! Please delete it manually and restart the server."); return;}

            consoleInfo("Successfully deleted player %s's name file. Reloading player...", player.getName());
            loadPlayer(player);
            return;
        }

        createNameTag(player, prefix, name, suffix, hidden);
        consoleInfo("Creating Name Tag for player '%s'.", player.getName());
    }

    private static void unloadPlayer(Player player) {
        if (!hasName(player)) return;

        File namefile = new File(NAMETAGSFOLDER, player.getUniqueId() + ".yml");
        YamlConfiguration nametag = YamlConfiguration.loadConfiguration(namefile);

        nametag.set("version", NAMEFILEVERSION);
        nametag.set("prefix", getPrefix(player));
        nametag.set("name", getName(player));
        nametag.set("suffix", getSuffix(player));
        nametag.set("hidden", isNameTagToggled(player));

        consoleInfo("Attempting to save player name for '%s'.", player.getName());

        try {nametag.save(namefile);}
        catch (IOException e) {consoleError("Unable to save player name file!");}
        finally {consoleInfo("Successfully saved player name file.");}

        PLAYERNAMES.remove(player);
        PLAYERNAMESTOGGLE.remove(player);
        PLAYERSTANDS.get(player).remove();
        PLAYERSTANDS.remove(player);

        player.playerListName(Component.text(player.getName()));
        player.displayName(Component.text(player.getName()));
        TEAM.removePlayer(player);

        consoleInfo("Deleted name tag entity for player '%s'.", player.getName());
    }

    private static void createNameTag(Player player, String prefix, String name, String suffix, boolean isNameTagHidden) {
        if (PLAYERSTANDS.containsKey(player)) {return;}

        Component nick = Component.text(prefix + (prefix.isEmpty() ? "" : " ") + name + (suffix.isEmpty() ? "" : " ") + suffix);

        ArmorStand stand = player.getWorld().spawn(player.getLocation(), ArmorStand.class);
        stand.customName(nick);
        stand.setCustomNameVisible(true);
        stand.setVisible(false);
        stand.setGravity(false);

        stand.getAttribute(Attribute.SCALE).setBaseValue(0.0625);

        if (!player.addPassenger(stand)) {consoleWarn("Unable to anchor armor stand onto player '%s'.", player.getName());}

        PLAYERSTANDS.put(player, stand);
        setFullName(player, prefix, name, suffix);
        forceConfigChanges(player);
        toggleNameTag(player, !isNameTagHidden);

        TEAM.addPlayer(player);
        player.playerListName(nick);
        player.displayName(nick);

        consoleInfo("Successfully created Name Tag '%s§r' for player '%s'.", name, player.getName());
    }

    public static void updateNameTag(Player player) {
        if (!PLAYERNAMES.containsKey(player)) return;

        forceConfigChanges(player);
        Component name = Component.text(getFullName(player));

        PLAYERSTANDS.get(player).customName(name);
        player.playerListName(name);
        player.displayName(name);

        consoleInfo("Updated name tag for player '%s' to '%s§r'.", player.getName(), getFullName(player));
    }

    // Force Runtime Config changes onto player's name.
    public static void forceConfigChanges(Player player) {
        List<String> name = List.of(getPrefix(player), getName(player), getSuffix(player));
        name.forEach(Strings::deformatText);

        // Resets the player's prefix, name, or suffx if any of them contain invalid characters or exceed the maximum character limit.
        if (Strings.textLength(name.getFirst()) > ConfigHandler.getMaxPrefixLength() || ConfigHandler.hasInvalidChars(name.getFirst())) setPrefix(player, "");
        if (Strings.textLength(name.get(1)) > ConfigHandler.getMaxNameLength() || ConfigHandler.hasInvalidChars(name.get(1))) setName(player, player.getName());
        if (Strings.textLength(name.getLast()) > ConfigHandler.getMaxSuffixLength() || ConfigHandler.hasInvalidChars(name.getLast())) setSuffix(player, "");
    }

    // ----------------------------------------------------------------------------------------------------------------------------------------------

    @EventHandler
    public static void onPlayerJoin(PlayerJoinEvent event) {
        if (!PLUGINISLOADED) return;
        Player player = event.getPlayer();

        loadPlayer(player);
        if (event.joinMessage() != null) {
            event.joinMessage(event.joinMessage().replaceText(
                    TextReplacementConfig.builder()
                            .match(player.getName())
                            .replacement(getFullName(player))
                            .build()));
        }
    }

    @EventHandler
    public static void onPlayerQuit(PlayerQuitEvent event) {
        if (!PLUGINISLOADED) return;
        Player player = event.getPlayer();

        if (event.quitMessage() != null) {
            event.quitMessage(event.quitMessage().replaceText(
                    TextReplacementConfig.builder()
                            .match(player.getName())
                            .replacement(getFullName(player))
                            .build()));
        }
        unloadPlayer(player);
    }

    @EventHandler
    public static void onWorldChange(PlayerTeleportEvent event) {
        Player player = event.getPlayer();
        Location to = event.getTo();

        if (to.getWorld() == player.getWorld()) {return;}

        ArmorStand stand = PLAYERSTANDS.get(player);
        stand.teleport(to);

        Bukkit.getScheduler().runTaskLater(INSTANCE, () -> {
            if (!player.addPassenger(stand)) {consoleWarn("Unable to anchor armor stand onto player '%s'.", player.getName());}
        }, 10);
    }

    @EventHandler
    public static void onPlayerCrouch(PlayerToggleSneakEvent event) {
        if (!PLUGINISLOADED) return;

        Player player = event.getPlayer();
        if (event.isCancelled() || player.isInvisible() || player.isFlying() || isNameTagToggled(player)) return;
        if (PLAYERSTANDS.containsKey(event.getPlayer())) setNameVisible(event.getPlayer(), event.getPlayer().isSneaking());
    }

    @EventHandler
    public static void onSpectatorMode(PlayerGameModeChangeEvent event) {
        if (!PLUGINISLOADED) return;

        Player player = event.getPlayer();
        if (event.isCancelled() || player.isInvisible() || isNameTagToggled(player)) return;
        if (PLAYERSTANDS.containsKey(player)) setNameVisible(player, event.getNewGameMode() != GameMode.SPECTATOR);
    }

    @EventHandler
    public static void onPlayerInvis(EntityPotionEffectEvent event) {
        if (!PLUGINISLOADED) return;

        Player player = event.getEntityType() == EntityType.PLAYER ? (Player) event.getEntity() : null;
        if (player == null) return;

        if (!PLAYERSTANDS.containsKey(player) || event.getModifiedType() != PotionEffectType.INVISIBILITY || isNameTagToggled(player)) return;

        if (event.getAction() == EntityPotionEffectEvent.Action.ADDED) setNameVisible((Player) event.getEntity(), false);
        else if (event.getAction() != EntityPotionEffectEvent.Action.CHANGED) setNameVisible((Player) event.getEntity(), true);
    }

    @EventHandler
    public static void onPlayerDeath(PlayerDeathEvent event) {
        if (!PLUGINISLOADED) return;
        Player player = event.getPlayer();

        if (event.deathMessage() != null) {
            event.deathMessage(event.deathMessage().replaceText(
                    TextReplacementConfig.builder()
                            .match(player.getName())
                            .replacement(getFullName(player))
                            .build()));
        }
    }

    @EventHandler
    public static void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!PLUGINISLOADED) return;

        Bukkit.getScheduler().runTaskLater(INSTANCE, () -> {
            if (event.getPlayer().addPassenger(PLAYERSTANDS.get(event.getPlayer()))) {consoleWarn("Unable to anchor armor stand onto player '%s'.", event.getPlayer().getName());}
        }, 10);
    }

    // ----------------------------------------------------------------------------------------------------------------------------------------------

    public static String getPrefix(Player player) {return hasPrefix(player) ? PLAYERNAMES.get(player).getFirst() : "";}
    public static boolean hasPrefix(Player player) {return PLAYERNAMES.containsKey(player) && !PLAYERNAMES.get(player).getFirst().isEmpty();}
    public static void setPrefix(Player player, String prefix) {
        if (PLAYERNAMES.containsKey(player)) PLAYERNAMES.get(player).set(0, prefix);
        else {PLAYERNAMES.put(player, new ArrayList<>(List.of(prefix, "", "")));}
        updateNameTag(player);
    }

    public static String getName(Player player) {return hasName(player) ? PLAYERNAMES.get(player).get(1) : player.getName();}
    public static String getTrueName(Player player) {return Strings.deformatText(getName(player));}
    public static boolean hasName(Player player) {return PLAYERNAMES.containsKey(player) && !PLAYERNAMES.get(player).get(1).isEmpty();}
    public static void setName(Player player, String name) {
        if (PLAYERNAMES.containsKey(player)) PLAYERNAMES.get(player).set(1, name);
        else {PLAYERNAMES.put(player, new ArrayList<>(List.of("", name, "")));}
        updateNameTag(player);
    }

    public static String getSuffix(Player player) {return hasSuffix(player) ? PLAYERNAMES.get(player).getLast() : "";}
    public static boolean hasSuffix(Player player) {return PLAYERNAMES.containsKey(player) && !PLAYERNAMES.get(player).getLast().isEmpty();}
    public static void setSuffix(Player player, String suffix) {
        if (PLAYERNAMES.containsKey(player)) PLAYERNAMES.get(player).set(2, suffix);
        else {PLAYERNAMES.put(player, new ArrayList<>(List.of("", "", suffix)));}
        updateNameTag(player);
    }

    public static String getFullName(Player player) {
        StringBuilder fullname = new StringBuilder(getName(player));
        if (hasPrefix(player)) fullname.insert(0, getPrefix(player) + " ");
        if (hasSuffix(player)) fullname.append(" ").append(getSuffix(player));
        return fullname.toString();
    }
    public static void setFullName(Player player, String prefix, String name, String suffix) {setPrefix(player, prefix); setName(player, name); setSuffix(player, suffix);}

    public static boolean isNameVisible(Player player) {return PLAYERSTANDS.containsKey(player) ? PLAYERSTANDS.get(player).isCustomNameVisible() : player.isInvisible();}
    public static void setNameVisible(Player player, boolean bool) {if (PLAYERSTANDS.containsKey(player)) PLAYERSTANDS.get(player).setCustomNameVisible(bool);}

    public static boolean isNameTagToggled(Player player) {return PLAYERNAMESTOGGLE.containsKey(player) && !PLAYERNAMESTOGGLE.get(player);}
    public static void toggleNameTag(Player player, boolean bool) {
        if (PLAYERNAMESTOGGLE.containsKey(player)) {PLAYERNAMESTOGGLE.replace(player, bool); setNameVisible(player, bool);}
        else {PLAYERNAMESTOGGLE.put(player, bool); setNameVisible(player, bool);}
    }
    public static void toggleNameTag(Player player) {toggleNameTag(player, !PLAYERNAMESTOGGLE.get(player));}

    // ----------------------------------------------------------------------------------------------------------------------------------------------

    public static File getNameFile(OfflinePlayer player) {
        File file = new File(NAMETAGSFOLDER, player.getUniqueId() + ".yml");
        if (!file.exists()) {
            try {file.createNewFile(); if (player instanceof Player) {createNameTagConfig(player.getPlayer());}}
            catch (IOException e) {consoleError("Unable to create player %s's namefile!", player.getName()); return null;}
        } return file;
    }
    public static boolean hasNameFile(OfflinePlayer player) {return (new File(NAMETAGSFOLDER, player.getUniqueId() + ".yml")).exists();}

    public static String getFromFile(OfflinePlayer player, TextType text) {
        if (!hasNameFile(player)) return null;

        YamlConfiguration nametag = YamlConfiguration.loadConfiguration(getNameFile(player));
        return switch (text) {
            case NAME -> nametag.getString("name");
            case PREFIX -> nametag.getString("prefix");
            case SUFFIX -> nametag.getString("suffix");
        };
    }

    public static void createNameTagConfig(Player player) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(getNameFile(player));
        config.set("prefix", "");
        config.set("name", player.getName());
        config.set("suffix", "");
        config.set("hidden", false);
        saveNameTagConfig(player, config);
    }
    public static void createNameTagConfig(String offline_player_name, Map<TextType, String> nametag, boolean hidden) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(offline_player_name);
        YamlConfiguration config = YamlConfiguration.loadConfiguration(getNameFile(player));
        config.set("prefix", nametag.get(TextType.PREFIX) != null ? nametag.get(TextType.PREFIX) : "");
        config.set("name", nametag.get(TextType.NAME) != null ? nametag.get(TextType.NAME) : player.getName() + "§r");
        config.set("suffix", nametag.get(TextType.SUFFIX) != null ? nametag.get(TextType.SUFFIX) : "");
        config.set("hidden", hidden);
        saveNameTagConfig(player, config);
    }
    public static void saveNameTagConfig(OfflinePlayer player, YamlConfiguration config) {try {config.save(getNameFile(player));} catch (IOException e) {consoleError("Unable to save player %s's namefile!", player.getName());}}

    public static String getPrefix(String offline_player_name) {
        if (!OFFLINEPLAYERNAMES.containsKey(offline_player_name) || !OFFLINEPLAYERNAMES.get(offline_player_name).containsKey(TextType.PREFIX)) return "";
        return OFFLINEPLAYERNAMES.get(offline_player_name).get(TextType.PREFIX);
    }
    public static String getName(String offline_player_name) {
        if (!OFFLINEPLAYERNAMES.containsKey(offline_player_name) || !OFFLINEPLAYERNAMES.get(offline_player_name).containsKey(TextType.NAME)) return "";
        return OFFLINEPLAYERNAMES.get(offline_player_name).get(TextType.NAME);
    }
    public static String getSuffix(String offline_player_name) {
        if (!OFFLINEPLAYERNAMES.containsKey(offline_player_name) || !OFFLINEPLAYERNAMES.get(offline_player_name).containsKey(TextType.SUFFIX)) return "";
        return OFFLINEPLAYERNAMES.get(offline_player_name).get(TextType.SUFFIX);
    }
    public static boolean isHidden(String offline_player_name) {return OFFLINEPLAYERTOGGLE.containsKey(offline_player_name) && OFFLINEPLAYERTOGGLE.get(offline_player_name);}

    public static String getFullName(String offline_player_name) {
        StringBuilder fullname = new StringBuilder(getName(offline_player_name));
        if (!getPrefix(offline_player_name).isEmpty()) fullname.insert(0, getPrefix(offline_player_name) + " ");
        if (!getSuffix(offline_player_name).isEmpty()) fullname.append(" ").append(getSuffix(offline_player_name));
        return fullname.toString();
    }

    public static void setPrefix(String offline_player_name, String prefix) {
        if (OFFLINEPLAYERNAMES.containsKey(offline_player_name)) {
            if (OFFLINEPLAYERNAMES.get(offline_player_name).containsKey(TextType.PREFIX)) {OFFLINEPLAYERNAMES.get(offline_player_name).replace(TextType.PREFIX, prefix);}
            else {OFFLINEPLAYERNAMES.get(offline_player_name).put(TextType.PREFIX, prefix);}
        } else {OFFLINEPLAYERNAMES.put(offline_player_name, new HashMap<>(Map.of(TextType.PREFIX, prefix)));}
    }
    public static void setName(String offline_player_name, String name) {
        if (OFFLINEPLAYERNAMES.containsKey(offline_player_name)) {
            if (OFFLINEPLAYERNAMES.get(offline_player_name).containsKey(TextType.NAME)) {OFFLINEPLAYERNAMES.get(offline_player_name).replace(TextType.NAME, name);}
            else {OFFLINEPLAYERNAMES.get(offline_player_name).put(TextType.NAME, name);}
        } else {OFFLINEPLAYERNAMES.put(offline_player_name, new HashMap<>(Map.of(TextType.NAME, name)));}
    }
    public static void setSuffix(String offline_player_name, String suffix) {
        if (OFFLINEPLAYERNAMES.containsKey(offline_player_name)) {
            if (OFFLINEPLAYERNAMES.get(offline_player_name).containsKey(TextType.SUFFIX)) {OFFLINEPLAYERNAMES.get(offline_player_name).replace(TextType.SUFFIX, suffix);}
            else {OFFLINEPLAYERNAMES.get(offline_player_name).put(TextType.SUFFIX, suffix);}
        } else {OFFLINEPLAYERNAMES.put(offline_player_name, new HashMap<>(Map.of(TextType.SUFFIX, suffix)));}
    }
    public static void setHidden(String offline_player_name, boolean hidden) {
        if (OFFLINEPLAYERTOGGLE.containsKey(offline_player_name)) {OFFLINEPLAYERTOGGLE.replace(offline_player_name, hidden);}
        else {OFFLINEPLAYERTOGGLE.put(offline_player_name, hidden);}
    }
}
