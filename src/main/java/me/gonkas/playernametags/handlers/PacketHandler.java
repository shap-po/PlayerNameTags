package me.gonkas.playernametags.handlers;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class PacketHandler implements PacketListener {
    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            handleChatMessage(event);
        } else if (event.getPacketType() == PacketType.Play.Server.SPAWN_ENTITY) {
            handleSpawnEntity(event);
        }
    }

    private static void handleChatMessage(PacketSendEvent event) {
        var replaced = false;
        var wrapper = new WrapperPlayServerSystemChatMessage(event);
        var message = wrapper.getMessage();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (message.toString().contains(player.getName())) {
                var nameWithoutFormatting = NameTagHandler.getFullName(player);
                nameWithoutFormatting = nameWithoutFormatting.replaceAll("§.", "");
                message = message.replaceText(
                    TextReplacementConfig.builder()
                        .match(player.getName())
                        .replacement(nameWithoutFormatting)
                        .build());
                replaced = true;
            }
        }

        if (replaced) {
            wrapper.setMessage(message);
            event.markForReEncode(true);
        }
    }

    private static void handleSpawnEntity(PacketSendEvent event) {
        var wrapper = new WrapperPlayServerSpawnEntity(event);
        if (wrapper.getEntityType() != EntityTypes.ARMOR_STAND) return;
        // do not notify clients about their own armor stands, so they won't see their own name
        if (isPlayerStand(event.getPlayer(), wrapper.getUUID().orElse(null))) {
            event.setCancelled(true);
        }
    }

    private static boolean isPlayerStand(@Nullable Player player, @Nullable UUID uuid) {
        if (player == null || uuid == null) return false;
        var playerStand = NameTagHandler.getArmorStand(player);
        if (playerStand == null) return false;
        return playerStand.getUniqueId().equals(uuid);
    }
}
