package me.gonkas.playernametags.handlers;

import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PacketHandler implements PacketListener {
    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (event.getPacketType() == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            var wrapper = new WrapperPlayServerSystemChatMessage(event);
            var message = wrapper.getMessage();
            var replaced = false;

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (message.toString().contains(player.getName())) {
                    message = message.replaceText(
                        TextReplacementConfig.builder()
                            .match(player.getName())
                            .replacement(NameTagHandler.getFullName(player))
                            .build());
                    replaced = true;
                }
            }

            if (replaced) {
                wrapper.setMessage(message);
                event.markForReEncode(true);
            }
        }
    }
}
