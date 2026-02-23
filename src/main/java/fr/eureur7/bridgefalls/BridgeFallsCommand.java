package fr.eureur7.bridgefalls;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.Optional;
import org.bukkit.command.CommandSender;
import org.bukkit.Material;
import org.bukkit.GameMode;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;
import java.util.stream.Collectors;

@CommandAlias("bridgeFallsCommand|bfCommand|bf|bridgeFalls")
@CommandPermission("bridgefalls.admin")
public class BridgeFallsCommand extends BaseCommand {
    @Subcommand("help")
    public void onHelp(CommandSender sender) {
        sender.sendMessage("§6§m                  §r §6BridgeFalls Help §6§m                  §r");
        sender.sendMessage("");

        sender.sendMessage("§e=== Core Commands ===");
        sender.sendMessage("§7/bf enable §f- Enable the plugin");
        sender.sendMessage("§7/bf disable §f- Disable the plugin");
        sender.sendMessage("§7/bf reload §f- Reload configuration from disk");
        sender.sendMessage("");

        sender.sendMessage("§e=== Radius Configuration ===");
        sender.sendMessage("§7/bf config support-radius <value> §f- Set horizontal support search radius (min: 1)");
        sender.sendMessage("§7/bf config top-support-radius <value> §f- Set vertical support search radius (min: 0)");
        sender.sendMessage("§7/bf config anchor-support-radius <value> §f- Set anchor search radius (min: 1)");
        sender.sendMessage("");

        sender.sendMessage("§e=== Behavior Configuration ===");
        sender.sendMessage(
                "§7/bf config fall-delay-minutes <minutes> §f- Delay before unstable blocks fall (min: 0)");
        sender.sendMessage("§7/bf config time-to-check <ticks> §f- Check interval for unstable blocks (min: 1 tick)");
        sender.sendMessage("§7/bf config debug §f[true|false] §f- Toggle debug logging");
        sender.sendMessage(
                "§7/bf config allow-placing-unstable-blocks §f[true|false] §f- Allow placing unstable blocks");
        sender.sendMessage("§7/bf config falling-block §f[enable|disable] §f- Toggle falling physics");
        sender.sendMessage("§7/bf config falling-block drop-item §f[true|false] §f- Falling blocks drop items");
        sender.sendMessage(
                "§7/bf config falling-block hurt-entities §f[true|false] §f- Falling blocks damage entities");
        sender.sendMessage("");

        sender.sendMessage("§e=== Material Blacklists (No Support) ===");
        sender.sendMessage("§7/bf config no-rest-vertical list §f- View vertical non-support blocks");
        sender.sendMessage("§7/bf config no-rest-vertical add <material> §f- Add material to vertical blacklist");
        sender.sendMessage(
                "§7/bf config no-rest-vertical remove <material> §f- Remove material from vertical blacklist");
        sender.sendMessage("");
        sender.sendMessage("§7/bf config no-rest-horizontal list §f- View horizontal non-support blocks");
        sender.sendMessage("§7/bf config no-rest-horizontal add <material> §f- Add material to horizontal blacklist");
        sender.sendMessage(
                "§7/bf config no-rest-horizontal remove <material> §f- Remove material from horizontal blacklist");
        sender.sendMessage("");

        sender.sendMessage("§e=== Material Whitelists (Always Stable) ===");
        sender.sendMessage("§7/bf config always-stable list §f- View always-stable blocks");
        sender.sendMessage("§7/bf config always-stable add <material> §f- Add material to always-stable list");
        sender.sendMessage("§7/bf config always-stable remove <material> §f- Remove material from always-stable list");
        sender.sendMessage("§7/bf config always-stable-no-support list §f- View always-stable blocks with no support");
        sender.sendMessage(
                "§7/bf config always-stable-no-support add <material> §f- Add material to always-stable/no-support list");
        sender.sendMessage(
                "§7/bf config always-stable-no-support remove <material> §f- Remove material from always-stable/no-support list");
        sender.sendMessage("");

        sender.sendMessage("§e=== Floating Support (Water Blocks) ===");
        sender.sendMessage("§7/bf config floating-support list §f- View floating support blocks (e.g., lily pads)");
        sender.sendMessage("§7/bf config floating-support add <material> §f- Add material to floating support list");
        sender.sendMessage(
                "§7/bf config floating-support remove <material> §f- Remove material from floating support list");
        sender.sendMessage("");

        sender.sendMessage("§e=== Instability Colors ===");
        sender.sendMessage("§7/bf config instability-colors list §f- View instability indicator colors");
        sender.sendMessage("§7/bf config instability-colors add <color> §f- Add color (yellow, orange, red, etc.)");
        sender.sendMessage("§7/bf config instability-colors remove <color> §f- Remove color");
        sender.sendMessage("");

        sender.sendMessage("§e=== Disabled Gamemodes ===");
        sender.sendMessage(
                "§7/bf config disabled-gamemodes list §f- View gamemodes where stability is disabled");
        sender.sendMessage(
                "§7/bf config disabled-gamemodes add <gamemode> §f- Add gamemode to disabled list");
        sender.sendMessage(
                "§7/bf config disabled-gamemodes remove <gamemode> §f- Remove gamemode from disabled list");
        sender.sendMessage("");
    }

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

    @Subcommand("config fall-delay-minutes")
    @CommandCompletion("0|1|5|10|15|30|60")
    public void onConfigFallDelayMinutes(CommandSender sender, String value) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        double minutes;
        try {
            minutes = Double.parseDouble(value);
        } catch (NumberFormatException e) {
            Map<String, String> ph = new HashMap<>();
            ph.put("key", "fall-delay-minutes");
            ph.put("value", value);
            ph.put("min", "0");
            sender.sendMessage(plugin.getMessage("command.config.number-invalid", ph));
            return;
        }

        if (minutes < 0.0D) {
            Map<String, String> ph = new HashMap<>();
            ph.put("key", "fall-delay-minutes");
            ph.put("value", value);
            ph.put("min", "0");
            sender.sendMessage(plugin.getMessage("command.config.number-invalid", ph));
            return;
        }

        plugin.getConfig().set("fall-delay-minutes", minutes);
        plugin.saveConfig();
        plugin.reloadConfig();

        Map<String, String> ph = new HashMap<>();
        ph.put("key", "fall-delay-minutes");
        ph.put("value", String.valueOf(plugin.getConfig().getDouble("fall-delay-minutes", 0.0D)));
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

    @Subcommand("config always-stable-no-support list")
    public void onListAlwaysStableNoSupport(CommandSender sender) {
        handleListList(sender, "always-stable-blocks-but-with-no-support",
                "always-stable-blocks-but-with-no-support");
    }

    @Subcommand("config always-stable-no-support add")
    @CommandCompletion("@bf_always_stable_no_support_add")
    public void onAddAlwaysStableNoSupport(CommandSender sender, String materialName) {
        handleMaterialListAdd(sender, "always-stable-blocks-but-with-no-support",
                "always-stable-blocks-but-with-no-support", materialName);
    }

    @Subcommand("config always-stable-no-support remove")
    @CommandCompletion("@bf_always_stable_no_support")
    public void onRemoveAlwaysStableNoSupport(CommandSender sender, String materialName) {
        handleMaterialListRemove(sender, "always-stable-blocks-but-with-no-support",
                "always-stable-blocks-but-with-no-support", materialName);
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

    @Subcommand("config disabled-gamemodes list")
    public void onListDisabledGamemodes(CommandSender sender) {
        handleListList(sender, "disabled-gamemodes", "disabled-gamemodes");
    }

    @Subcommand("config disabled-gamemodes add")
    @CommandCompletion("@bf_disabled_gamemodes_add")
    public void onAddDisabledGamemode(CommandSender sender, String gamemodeName) {
        handleGamemodeListAdd(sender, "disabled-gamemodes", "disabled-gamemodes", gamemodeName);
    }

    @Subcommand("config disabled-gamemodes remove")
    @CommandCompletion("@bf_disabled_gamemodes")
    public void onRemoveDisabledGamemode(CommandSender sender, String gamemodeName) {
        handleGamemodeListRemove(sender, "disabled-gamemodes", "disabled-gamemodes", gamemodeName);
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

    private void handleGamemodeListAdd(CommandSender sender, String path, String displayKey, String gamemodeName) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        if (gamemodeName == null) {
            gamemodeName = "";
        }

        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(gamemodeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            Map<String, String> ph = new HashMap<>();
            ph.put("value", gamemodeName);
            sender.sendMessage(plugin.getMessage("command.config.material-invalid", ph));
            return;
        }

        String canonical = gameMode.name().toLowerCase();
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

    private void handleGamemodeListRemove(CommandSender sender, String path, String displayKey, String gamemodeName) {
        BridgeFallsPlugin plugin = BridgeFallsPlugin.getInstance();

        if (gamemodeName == null) {
            gamemodeName = "";
        }

        GameMode gameMode;
        try {
            gameMode = GameMode.valueOf(gamemodeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            Map<String, String> ph = new HashMap<>();
            ph.put("value", gamemodeName);
            sender.sendMessage(plugin.getMessage("command.config.material-invalid", ph));
            return;
        }

        String canonical = gameMode.name().toLowerCase();
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
