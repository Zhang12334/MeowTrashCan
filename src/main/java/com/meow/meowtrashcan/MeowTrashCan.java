package com.meow.meowtrashcan;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MeowTrashCan extends JavaPlugin implements Listener {

    private final Map<UUID, Inventory> trashInventories = new HashMap<>();
    private final List<ItemStack> allTrashItems = new ArrayList<>();
    private boolean useMySQL;
    private Connection connection;
    private Map<String, String> messages;
    private String storageType;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        storageType = getConfig().getString("storage", "json");
        useMySQL = storageType.equalsIgnoreCase("mysql");

        if (useMySQL) {
            setupMySQL();
        }

        loadMessages();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (useMySQL && connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private void setupMySQL() {
        String host = getConfig().getString("mysql.host");
        int port = getConfig().getInt("mysql.port");
        String database = getConfig().getString("mysql.database");
        String username = getConfig().getString("mysql.username");
        String password = getConfig().getString("mysql.password");

        try {
            connection = DriverManager.getConnection(
                    "jdbc:mysql://" + host + ":" + port + "/" + database,
                    username,
                    password
            );
            connection.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS trash_items (id INT AUTO_INCREMENT PRIMARY KEY, material VARCHAR(255), amount INT)"
            );
        } catch (SQLException e) {
            e.printStackTrace();
            getLogger().severe("Could not connect to MySQL database.");
        }
    }

    private void loadMessages() {
        messages = new HashMap<>();

        String language = getConfig().getString("language", "zh_cn");
        if (language.equalsIgnoreCase("en")) {//英文消息
            messages.put("only_players", ChatColor.RED + "Only players can use this command.");
            messages.put("usage", ChatColor.RED + "Usage: /meowtrashcan <throw|flip|reload>");
            messages.put("no_permission", ChatColor.RED + "You don't have permission to perform this action.");
            messages.put("reloaded", ChatColor.GREEN + "MeowTrashCan configuration reloaded.");
            messages.put("unknown_command", ChatColor.RED + "Unknown command. Usage: /meowtrashcan <throw|flip|reload>");
            messages.put("failed_connect_database", ChatColor.RED + "Could not connect to MySQL database.");
            messages.put("page", "Now page");
            messages.put("trashbin_throw", "Trash Bin - Throw");
            messages.put("trashbin_flip", "Trash Bin - Dig");
        } else if (language.equalsIgnoreCase("zh_tc")) {//繁体消息
            messages.put("only_players", ChatColor.RED + "只有玩家能使用這個指令!");
            messages.put("usage", ChatColor.RED + "用法：/meowtrashcan <throw|flip|reload>");
            messages.put("no_permission", ChatColor.RED + "你沒有權限執行此操作!");
            messages.put("reloaded", ChatColor.GREEN + "MeowTrashCan 配置已重載");
            messages.put("unknown_command", ChatColor.RED + "未知的指令, 用法：/meowtrashcan <throw|flip|reload>");
            messages.put("failed_connect_database", ChatColor.RED + "無法連接到MySQL數據庫!");
            messages.put("page", "當前頁數");
            messages.put("trashbin_throw", "丟垃圾");
            messages.put("trashbin_flip", "翻垃圾");
        } else if (language.equalsIgnoreCase("zh_cn")) {//简体消息
            messages.put("only_players", ChatColor.RED + "只有玩家可以使用此命令!");
            messages.put("usage", ChatColor.RED + "用法：/meowtrashcan <throw|flip|reload>");
            messages.put("no_permission", ChatColor.RED + "你没有权限执行此操作!");
            messages.put("reloaded", ChatColor.GREEN + "MeowTrashCan 配置已重新加载");
            messages.put("unknown_command", ChatColor.RED + "未知命令, 用法：/meowtrashcan <throw|flip|reload>");
            messages.put("failed_connect_database", ChatColor.RED + "无法连接到MySQL数据库!");
            messages.put("page", "当前页数");
            messages.put("trashbin_throw", "丢垃圾");
            messages.put("trashbin_flip", "翻垃圾");
        }
    }


    private void openTrashInventory(Player player) {
        Inventory trashInventory = Bukkit.createInventory(player, 54, ChatColor.GREEN + messages.get("trashbin_throw"));
        trashInventories.put(player.getUniqueId(), trashInventory);
        player.openInventory(trashInventory);
    }

    private void openDigInventory(Player player) {
        Inventory digInventory = Bukkit.createInventory(player, 54, ChatColor.YELLOW + messages.get("trashbin_flip"));
        int totalItems = allTrashItems.size();

        if (totalItems > 0) {
            int randomPage = ThreadLocalRandom.current().nextInt((totalItems + 53) / 54); // Use 54 instead of 27
            int startIndex = randomPage * 54;

            for (int i = 0; i < 54 && startIndex + i < totalItems; i++) {
                digInventory.setItem(i, allTrashItems.get(startIndex + i));
            }

            ItemStack pageIndicator = new ItemStack(Material.PAPER);
            ItemMeta meta = pageIndicator.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.BLUE + messages.get("page") + " " + (randomPage + 1));
                pageIndicator.setItemMeta(meta);
            }
            digInventory.setItem(53, pageIndicator); // Update to place page indicator in last slot
        }

        player.openInventory(digInventory);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Inventory closedInventory = event.getInventory();

        if (trashInventories.containsKey(player.getUniqueId()) && closedInventory.equals(trashInventories.get(player.getUniqueId()))) {
            for (ItemStack item : closedInventory.getContents()) {
                if (item != null && item.getType() != Material.PAPER) { // Prevent paper item from being added
                    allTrashItems.add(item);
                    if (useMySQL) {
                        saveItemToDatabase(item);
                    } else {
                        saveItemToJson(item);
                    }
                }
            }
            closedInventory.clear();
            updateTrashInventories();
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getView().getTitle().equals(ChatColor.YELLOW + messages.get("trashbin_flip"))) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();

            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                event.getWhoClicked().getInventory().addItem(clickedItem);
                // Remove the item from the trash bin
                event.getView().getTopInventory().remove(clickedItem);
                updateTrashInventories(); // Update inventory after item removal
            }
        }
    }

    private void updateTrashInventories() {
        // Update all players with the new trash inventory contents
        for (UUID playerId : trashInventories.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null && player.isOnline()) {
                openTrashInventory(player); // Reopen trash inventory to update contents
            }
        }
    }

    private void saveItemToDatabase(ItemStack item) {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO trash_items (material, amount) VALUES (?, ?)"
        )) {
            statement.setString(1, item.getType().toString());
            statement.setInt(2, item.getAmount());
            statement.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void saveItemToJson(ItemStack item) {
        try {
            File file = new File(getDataFolder(), "trash_items.json");
            if (!file.exists()) {
                file.createNewFile();
            }

            FileWriter writer = new FileWriter(file, true);
            writer.write(item.getType().toString() + "," + item.getAmount() + "\n");
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.get("only_players"));
            return true;
        }

        Player player = (Player) sender;

        if (args.length < 1) {
            player.sendMessage(messages.get("usage"));
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "throw":
                if (!player.hasPermission("meowtrashcan.throw")) {
                    player.sendMessage(messages.get("no_permission"));
                    return true;
                }
                openTrashInventory(player);
                break;
            case "flip":
                if (!player.hasPermission("meowtrashcan.flip")) {
                    player.sendMessage(messages.get("no_permission"));
                    return true;
                }
                openDigInventory(player);
                break;
            case "reload":
                if (!player.hasPermission("meowtrashcan.reload")) {
                    player.sendMessage(messages.get("no_permission"));
                    return true;
                }
                reloadConfig();
                loadMessages();
                player.sendMessage(messages.get("reloaded"));
                break;
            default:
                player.sendMessage(messages.get("unknown_command"));
                break;
        }

        return true;
    }
}
