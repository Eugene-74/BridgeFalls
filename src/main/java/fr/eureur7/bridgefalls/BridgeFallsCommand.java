package fr.eureur7.bridgefalls;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntSupplier;
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

    @Subcommand("config debug")
    @CommandCompletion("true|false")
    public void onConfigDebug(CommandSender sender, @Optional String value) {
        handleBooleanConfig(sender, "debug", "debug", value);
    }

    @Subcommand("config allow-placing-unstable-blocks")
    @CommandCompletion("true|false")
    public void onConfigAllowUnstable(CommandSender sender, @Optional String value) {
        handleBooleanConfig(sender, "allow-placing-unstable-blocks", "allow-placing-unstable-blocks", value);
    }

    @Subcommand("config falling-block drop-item")
    @CommandCompletion("true|false")
    public void onConfigFallingDropItem(CommandSender sender, @Optional String value) {
        handleBooleanConfig(sender, "falling-block-drop-item", "falling-block-drop-item", value);
    }

    @Subcommand("config falling-block hurt-entities")
    @CommandCompletion("true|false")
    public void onConfigFallingHurtEntities(CommandSender sender, @Optional String value) {
        handleBooleanConfig(sender, "falling-block-hurt-entities", "falling-block-hurt-entities", value);
    }

    @Subcommand("config falling-block")
    @CommandCompletion("enable|disable")
    public void onConfigFallingEnabled(CommandSender sender, @Optional String value) {
        handleBooleanConfig(sender, "falling-block", "falling-block", value);
    }

    @Subcommand("config time-to-check")
    @CommandCompletion("20|40|100|200")
    public void onConfigTimeToCheck(CommandSender sender, String value) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        long ticks;
        try {
            ticks = Long.parseLong(value);
        } catch (NumberFormatException e) {
            Map<String, String> ph = new HashMap<>();
            ph.put("key", "time-to-check");
            ph.put("value", value);
            ph.put("min", "1");
            sender.sendMessage(plugin.getMessage("command.config.number-invalid", ph));
            return;
        }

        if (ticks < 1L) {
            Map<String, String> ph = new HashMap<>();
            ph.put("key", "time-to-check");
            ph.put("value", value);
            ph.put("min", "1");
            sender.sendMessage(plugin.getMessage("command.config.number-invalid", ph));
            return;
        }

        plugin.getConfig().set("time-to-check", ticks);
        plugin.saveConfig();
        plugin.reloadConfig();

        Map<String, String> ph = new HashMap<>();
        ph.put("key", "time-to-check");
        ph.put("value", String.valueOf(plugin.getTimeToCheckTicks()));
        sender.sendMessage(plugin.getMessage("command.config.number-updated", ph));
    }

    @Subcommand("config support-radius")
    @CommandCompletion("1|2|4|8|16|32")
    public void onConfigSupportRadius(CommandSender sender, String value) {
        handleIntConfig(sender, "support-radius", "support-radius", value, 1,
                () -> BridgeFallsPlugin.getInstance().getSupportRadius());
    }

    @Subcommand("config top-support-radius")
    @CommandCompletion("0|1|2|4|8|16")
    public void onConfigTopSupportRadius(CommandSender sender, String value) {
        handleIntConfig(sender, "top-support-radius", "top-support-radius", value, 0,
                () -> BridgeFallsPlugin.getInstance().getTopSupportRadius());
    }

    @Subcommand("config anchor-support-radius")
    @CommandCompletion("1|2|3|4|5|8")
    public void onConfigAnchorSupportRadius(CommandSender sender, String value) {
        handleIntConfig(sender, "anchor-support-radius", "anchor-support-radius", value, 1,
                () -> BridgeFallsPlugin.getInstance().getAnchorSupportRadius());
    }

    @Subcommand("config no-rest-vertical list")
    public void onListNoRestVertical(CommandSender sender) {
        handleListList(sender, "no-rest-blocks-vertical", "no-rest-blocks-vertical");
    }

    @Subcommand("config no-rest-vertical add")
    @CommandCompletion("@bf_no_rest_vertical_add")
    public void onAddNoRestVertical(CommandSender sender, String materialName) {
        handleMaterialListAdd(sender, "no-rest-blocks-vertical", "no-rest-blocks-vertical", materialName);
    }

    @Subcommand("config no-rest-vertical remove")
    @CommandCompletion("@bf_no_rest_vertical")
    public void onRemoveNoRestVertical(CommandSender sender, String materialName) {
        handleMaterialListRemove(sender, "no-rest-blocks-vertical", "no-rest-blocks-vertical", materialName);
    }

    @Subcommand("config no-rest-horizontal list")
    public void onListNoRestHorizontal(CommandSender sender) {
        handleListList(sender, "no-rest-blocks-horizontal", "no-rest-blocks-horizontal");
    }

    @Subcommand("config no-rest-horizontal add")
    @CommandCompletion("@bf_no_rest_horizontal_add")
    public void onAddNoRestHorizontal(CommandSender sender, String materialName) {
        handleMaterialListAdd(sender, "no-rest-blocks-horizontal", "no-rest-blocks-horizontal", materialName);
    }

    @Subcommand("config no-rest-horizontal remove")
    @CommandCompletion("@bf_no_rest_horizontal")
    public void onRemoveNoRestHorizontal(CommandSender sender, String materialName) {
        handleMaterialListRemove(sender, "no-rest-blocks-horizontal", "no-rest-blocks-horizontal", materialName);
    }

    @Subcommand("config always-stable list")
    public void onListAlwaysStable(CommandSender sender) {
        handleListList(sender, "always-stable-blocks", "always-stable-blocks");
    }

    @Subcommand("config always-stable add")
    @CommandCompletion("@bf_always_stable_add")
    public void onAddAlwaysStable(CommandSender sender, String materialName) {
        handleMaterialListAdd(sender, "always-stable-blocks", "always-stable-blocks", materialName);
    }

    @Subcommand("config always-stable remove")
    @CommandCompletion("@bf_always_stable")
    public void onRemoveAlwaysStable(CommandSender sender, String materialName) {
        handleMaterialListRemove(sender, "always-stable-blocks", "always-stable-blocks", materialName);
    }

    @Subcommand("config floating-support list")
    public void onListFloatingSupport(CommandSender sender) {
        handleListList(sender, "floating-support-blocks", "floating-support-blocks");
    }

    @Subcommand("config floating-support add")
    @CommandCompletion("@bf_floating_support_add")
    public void onAddFloatingSupport(CommandSender sender, String materialName) {
        handleMaterialListAdd(sender, "floating-support-blocks", "floating-support-blocks", materialName);
    }

    @Subcommand("config floating-support remove")
    @CommandCompletion("@bf_floating_support")
    public void onRemoveFloatingSupport(CommandSender sender, String materialName) {
        handleMaterialListRemove(sender, "floating-support-blocks", "floating-support-blocks", materialName);
    }

    @Subcommand("config instability-colors list")
    public void onListInstabilityColors(CommandSender sender) {
        handleListList(sender, "instability-colors", "instability-colors");
    }

    @Subcommand("config instability-colors add")
    public void onAddInstabilityColor(CommandSender sender, String color) {
        handleColorListAdd(sender, "instability-colors", "instability-colors", color);
    }

    @Subcommand("config instability-colors remove")
    @CommandCompletion("@bf_instability_colors")
    public void onRemoveInstabilityColor(CommandSender sender, String color) {
        handleStringListRemove(sender, "instability-colors", "instability-colors", color);
    }

    private void handleBooleanConfig(CommandSender sender, String path, String displayKey, String value) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        boolean current = plugin.getConfig().getBoolean(path, false);
        boolean newValue;

        if (value == null || value.isEmpty()) {
            newValue = !current; // toggle
        } else if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("enable")) {
            newValue = true;
        } else if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("disable")) {
            newValue = false;
        } else {
            Map<String, String> ph = new HashMap<>();
            ph.put("key", displayKey);
            sender.sendMessage(plugin.getMessage("command.config.boolean-invalid", ph));
            return;
        }

        plugin.getConfig().set(path, newValue);
        plugin.saveConfig();
        plugin.reloadConfig();

        Map<String, String> ph = new HashMap<>();
        ph.put("key", displayKey);
        ph.put("value", String.valueOf(newValue));
        sender.sendMessage(plugin.getMessage("command.config.boolean-updated", ph));
    }

    private void handleIntConfig(CommandSender sender, String path, String displayKey, String value, int min,
            IntSupplier runtimeValueSupplier) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        int parsed;
        try {
            parsed = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            Map<String, String> ph = new HashMap<>();
            ph.put("key", displayKey);
            ph.put("value", value);
            ph.put("min", String.valueOf(min));
            sender.sendMessage(plugin.getMessage("command.config.number-invalid", ph));
            return;
        }

        if (parsed < min) {
            Map<String, String> ph = new HashMap<>();
            ph.put("key", displayKey);
            ph.put("value", value);
            ph.put("min", String.valueOf(min));
            sender.sendMessage(plugin.getMessage("command.config.number-invalid", ph));
            return;
        }

        plugin.getConfig().set(path, parsed);
        plugin.saveConfig();
        plugin.reloadConfig();

        Map<String, String> ph = new HashMap<>();
        ph.put("key", displayKey);
        ph.put("value", String.valueOf(runtimeValueSupplier.getAsInt()));
        sender.sendMessage(plugin.getMessage("command.config.number-updated", ph));
    }

    private void handleMaterialListAdd(CommandSender sender, String path, String displayKey, String materialName) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        if (materialName == null) {
            materialName = "";
        }

        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("value", materialName);
            sender.sendMessage(plugin.getMessage("command.config.material-invalid", ph));
            return;
        }

        String canonical = material.name().toLowerCase();
        List<String> list = plugin.getConfig().getStringList(path);

        for (String entry : list) {
            if (entry.equalsIgnoreCase(canonical)) {
                Map<String, String> ph = new HashMap<>();
                ph.put("key", displayKey);
                ph.put("value", canonical);
                sender.sendMessage(plugin.getMessage("command.config.list-already", ph));
                return;
            }
        }

        list.add(canonical);
        plugin.getConfig().set(path, list);
        plugin.saveConfig();
        plugin.reloadConfig();

        Map<String, String> ph = new HashMap<>();
        ph.put("key", displayKey);
        ph.put("value", canonical);
        sender.sendMessage(plugin.getMessage("command.config.list-added", ph));
    }

    private void handleMaterialListRemove(CommandSender sender, String path, String displayKey, String materialName) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        if (materialName == null) {
            materialName = "";
        }

        Material material = Material.matchMaterial(materialName.toUpperCase());
        if (material == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("value", materialName);
            sender.sendMessage(plugin.getMessage("command.config.material-invalid", ph));
            return;
        }

        String canonical = material.name().toLowerCase();
        List<String> list = plugin.getConfig().getStringList(path);

        boolean removed = list.removeIf(entry -> entry.equalsIgnoreCase(canonical));
        if (!removed) {
            Map<String, String> ph = new HashMap<>();
            ph.put("key", displayKey);
            ph.put("value", canonical);
            sender.sendMessage(plugin.getMessage("command.config.list-not-found", ph));
            return;
        }

        plugin.getConfig().set(path, list);
        plugin.saveConfig();
        plugin.reloadConfig();

        Map<String, String> ph = new HashMap<>();
        ph.put("key", displayKey);
        ph.put("value", canonical);
        sender.sendMessage(plugin.getMessage("command.config.list-removed", ph));
    }

    private void handleListList(CommandSender sender, String path, String displayKey) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        List<String> list = plugin.getConfig().getStringList(path);
        String joined;
        if (list == null || list.isEmpty()) {
            joined = "(vide)";
        } else {
            joined = list.stream().map(String::toLowerCase).sorted().collect(Collectors.joining(", "));
        }

        Map<String, String> ph = new HashMap<>();
        ph.put("key", displayKey);
        ph.put("list", joined);
        sender.sendMessage(plugin.getMessage("command.config.list-current", ph));
    }

    private void handleColorListAdd(CommandSender sender, String path, String displayKey, String color) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        if (color == null) {
            color = "";
        }

        if (plugin.parseColor(color) == null) {
            Map<String, String> ph = new HashMap<>();
            ph.put("value", color);
            sender.sendMessage(plugin.getMessage("command.config.color-invalid", ph));
            return;
        }

        String canonical = color.toLowerCase();
        List<String> list = plugin.getConfig().getStringList(path);

        for (String entry : list) {
            if (entry.equalsIgnoreCase(canonical)) {
                Map<String, String> ph = new HashMap<>();
                ph.put("key", displayKey);
                ph.put("value", canonical);
                sender.sendMessage(plugin.getMessage("command.config.list-already", ph));
                return;
            }
        }

        list.add(canonical);
        plugin.getConfig().set(path, list);
        plugin.saveConfig();
        plugin.reloadConfig();

        Map<String, String> ph = new HashMap<>();
        ph.put("key", displayKey);
        ph.put("value", canonical);
        sender.sendMessage(plugin.getMessage("command.config.list-added", ph));
    }

    private void handleStringListRemove(CommandSender sender, String path, String displayKey, String value) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        if (value == null) {
            value = "";
        }

        String canonical = value.toLowerCase();
        List<String> list = plugin.getConfig().getStringList(path);

        boolean removed = list.removeIf(entry -> entry.equalsIgnoreCase(canonical));
        if (!removed) {
            Map<String, String> ph = new HashMap<>();
            ph.put("key", displayKey);
            ph.put("value", canonical);
            sender.sendMessage(plugin.getMessage("command.config.list-not-found", ph));
            return;
        }

        plugin.getConfig().set(path, list);
        plugin.saveConfig();
        plugin.reloadConfig();

        Map<String, String> ph = new HashMap<>();
        ph.put("key", displayKey);
        ph.put("value", canonical);
        sender.sendMessage(plugin.getMessage("command.config.list-removed", ph));
    }
}
