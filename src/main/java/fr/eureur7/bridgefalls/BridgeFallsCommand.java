package fr.eureur7.bridgefalls;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@CommandAlias("bridgeFallsCommand|bfCommand|bf|bridgeFalls")
@CommandPermission("bridgefalls.admin")
public class BridgeFallsCommand extends BaseCommand {
    @Subcommand("reload")
    public void onReload(CommandSender sender) {
        BridgeFallsPlugin.getInstance().reloadConfig();
        sender.sendMessage(BridgeFallsPlugin.getInstance().getMessage("command.reload"));
    }

    @Subcommand("enable")
    public void onEnable(CommandSender sender) {
        BridgeFallsPlugin.getInstance().setBridgeFallsEnabled(true);
        sender.sendMessage(BridgeFallsPlugin.getInstance().getMessage("command.enable"));
    }

    @Subcommand("disable")
    public void onDisable(CommandSender sender) {
        BridgeFallsPlugin.getInstance().setBridgeFallsEnabled(false);
        sender.sendMessage(BridgeFallsPlugin.getInstance().getMessage("command.disable"));
    }

    @Subcommand("list-nonstruct")
    public void onListNonStruct(CommandSender sender) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        Set<Material> vertical = plugin.getNoRestBlocksVertical();
        Set<Material> horizontal = plugin.getNoRestBlocksHorizontal();

        String verticalList = vertical.stream()
                .map(m -> m.name().toLowerCase())
                .sorted()
                .collect(Collectors.joining(", "));

        String horizontalList = horizontal.stream()
                .map(m -> m.name().toLowerCase())
                .sorted()
                .collect(Collectors.joining(", "));

        Map<String, String> placeholders = new HashMap<>();

        placeholders.put("list", verticalList);
        sender.sendMessage(BridgeFallsPlugin.getInstance().getMessage("command.list-nonstruct.vertical", placeholders));

        placeholders.put("list", horizontalList);
        sender.sendMessage(
                BridgeFallsPlugin.getInstance().getMessage("command.list-nonstruct.horizontal", placeholders));
    }

    @Subcommand("list-struct")
    public void onListStruct(CommandSender sender) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        Set<Material> nonStructVertical = plugin.getNoRestBlocksVertical();
        Set<Material> nonStructHorizontal = plugin.getNoRestBlocksHorizontal();

        // On affiche surtout la différence de logique, pas tous les matériaux
        // possibles.
        String nonStructVerticalList = nonStructVertical.stream()
                .map(m -> m.name().toLowerCase()).sorted().collect(Collectors.joining(", "));
        String nonStructHorizontalList = nonStructHorizontal.stream()
                .map(m -> m.name().toLowerCase()).sorted().collect(Collectors.joining(", "));

        sender.sendMessage(BridgeFallsPlugin.getInstance().getMessage("command.list-struct.vertical-info"));
        sender.sendMessage(BridgeFallsPlugin.getInstance().getMessage("command.list-struct.horizontal-info"));

        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("list", nonStructVerticalList);
        sender.sendMessage(
                BridgeFallsPlugin.getInstance().getMessage("command.list-struct.nonstruct-vertical", placeholders));

        placeholders.put("list", nonStructHorizontalList);
        sender.sendMessage(
                BridgeFallsPlugin.getInstance().getMessage("command.list-struct.nonstruct-horizontal", placeholders));
    }
}
