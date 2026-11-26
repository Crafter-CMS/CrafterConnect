package net.crafter.plugin.spigot;

import net.crafter.plugin.core.LanguageManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

/** Satın alma yanıtı mesajı (oyuncunun thread'inde çağrılır) */
final class SpigotMessages {
    private SpigotMessages() {}

    static void purchaseResponse(CrafterSpigotPlugin plugin, Player player, boolean success, String message) {
        LanguageManager lang = plugin.getLanguageManager();
        if (!success) {
            player.sendMessage(lang.getMessage("prefix") + lang.getMessage("purchase_error", message));
            return;
        }
        player.sendMessage(lang.getMessage("prefix") + lang.getMessage("purchase_success", message));
        TextComponent msg = new TextComponent(lang.getMessage("prefix") + lang.getMessage("click_to_view_chest"));
        TextComponent viewChest = new TextComponent(lang.getMessage("view_chest_button"));
        viewChest.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/crafter sandik"));
        viewChest.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(lang.getMessage("view_chest_hover")).create()));
        msg.addExtra(viewChest);
        player.spigot().sendMessage(msg);
    }
}
