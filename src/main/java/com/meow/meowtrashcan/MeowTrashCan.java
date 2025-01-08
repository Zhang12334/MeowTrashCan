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

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MeowTrashCan extends JavaPlugin implements Listener {

    private final Map<UUID, Inventory> trashInventories = new HashMap<>();
    private final List<ItemStack> allTrashItems = new ArrayList<>(); // 内存中的垃圾物品列表
    private boolean useMySQL;
    private Connection connection;
    private Map<String, String> messages;
    private String storageType;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        storageType = getConfig().getString("storage", "json");
        useMySQL = storageType.equalsIgnoreCase("mysql");
        loadMessages();
        if (useMySQL) {
            setupMySQL();
        }
        loadTrashItems(); // 从数据库或 JSON 文件加载垃圾物品
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
        saveTrashItems(); // 保存垃圾物品到数据库或 JSON 文件
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
            getLogger().severe(messages.get("failed_connect_database"));
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
                // 获取所有存在页面的总数
                int totalPages = getTotalPages();
                if (totalPages > 0) {
                    int randomPage = (int) (Math.random() * totalPages); // 随机选一页
                    openDigInventory(player, randomPage); // 打开选中的页面
                } else {
                    openDigInventory(player, 0); // 空的则打开首个页面
                }
                break;
            case "reload":
                if (!player.hasPermission("meowtrashcan.reload")) {
                    player.sendMessage(messages.get("no_permission"));
                    return true;
                }
                reloadConfig();
                loadMessages();
                saveTrashItems();
                loadTrashItems();
                player.sendMessage(messages.get("reloaded"));
                break;
            default:
                player.sendMessage(messages.get("unknown_command"));
                break;
        }

        return true;
    }

    private void loadTrashItems() {
        allTrashItems.clear();
        if (useMySQL) {
            try (Statement statement = connection.createStatement();
                 ResultSet resultSet = statement.executeQuery("SELECT material, amount FROM trash_items")) {
                while (resultSet.next()) {
                    Material material = Material.getMaterial(resultSet.getString("material"));
                    int amount = resultSet.getInt("amount");
                    if (material != null) {
                        allTrashItems.add(new ItemStack(material, amount));
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            File file = new File(getDataFolder(), "trash_items.json");
            if (file.exists()) {
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] parts = line.split(",");
                        Material material = Material.getMaterial(parts[0]);
                        int amount = Integer.parseInt(parts[1]);
                        if (material != null) {
                            allTrashItems.add(new ItemStack(material, amount));
                        }
                    }
                } catch (IOException | NumberFormatException e) {
                    e.printStackTrace();
                }
            } 
        }
    }

    private void saveTrashItems() {
        if (useMySQL) {
            try (PreparedStatement clearStatement = connection.prepareStatement("DELETE FROM trash_items")) {
                clearStatement.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
                return;
            }

            try (PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO trash_items (material, amount) VALUES (?, ?)")) {
                for (ItemStack item : allTrashItems) {
                    insertStatement.setString(1, item.getType().toString());
                    insertStatement.setInt(2, item.getAmount());
                    insertStatement.addBatch();
                }
                insertStatement.executeBatch();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        } else {
            File file = new File(getDataFolder(), "trash_items.json");
            try (FileWriter writer = new FileWriter(file)) {
                for (ItemStack item : allTrashItems) {
                    writer.write(item.getType().toString() + "," + item.getAmount() + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Player player = (Player) event.getPlayer();
        Inventory closedInventory = event.getInventory();

        if (trashInventories.containsKey(player.getUniqueId()) && closedInventory.equals(trashInventories.get(player.getUniqueId()))) {
            for (ItemStack item : closedInventory.getContents()) {
                if (item != null && item.getType() != Material.PAPER) { // Prevent paper item from being added
                    allTrashItems.add(item);
                }
            }
            closedInventory.clear();
            saveTrashItems(); // 每次更新后保存
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // 检查是否是垃圾桶界面
        if (event.getView().getTitle().equals(ChatColor.YELLOW + messages.get("trashbin_flip"))) {
            event.setCancelled(true); // 取消默认点击行为

            int slot = event.getRawSlot(); // 获取点击的物品槽索引
            if (slot < 0 || slot >= 54) return; // 确保点击在有效范围内

            Inventory inventory = event.getInventory();
            Player player = (Player) event.getWhoClicked();

            if (slot == 45) { // 上一页按钮
                openDigInventory(player, getCurrentPage(inventory) - 1);
            } else if (slot == 53) { // 下一页按钮
                openDigInventory(player, getCurrentPage(inventory) + 1);
            } else if (slot == 49) { // 中间页数按钮
                openDigInventory(player, getCurrentPage(inventory)); //打开当前页的page，即刷新
            } else if (slot < 45) { // 点击物品槽
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                    // 检查物品是否在垃圾桶列表中
                    if (allTrashItems.contains(clickedItem)) {
                        // 尝试将物品加入玩家背包
                        HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(clickedItem);
                        if (remaining.isEmpty()) {
                            // 如果成功加入背包
                            inventory.setItem(slot, null); // 从界面移除物品
                            allTrashItems.remove(clickedItem); // 从垃圾列表移除
                            saveTrashItems(); // 保存垃圾列表
                        } else {
                            // 如果背包已满，提示玩家
                            player.sendMessage(ChatColor.RED + messages.get("inventory_full"));
                        }
                    } else {
                        // 物品不在垃圾桶中，刷新界面并提示
                        player.sendMessage(ChatColor.RED + messages.get("not_in_trashbin"));
                        openDigInventory(player, getCurrentPage(inventory)); // 刷新当前页
                    }
                }
            }
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
            messages.put("inventory_full", "Your inventory is full, you cannot pick up items!");
            messages.put("not_in_trashbin", "The item you picked up has been picked up by others, it has been refreshed for you!");
            messages.put("trashbin_null", "The trash bin is empty, please throw some items in it!");
            messages.put("last_page", "Previous page");
            messages.put("no_last_page", "No previous page");
            messages.put("next_page", "Next page");
            messages.put("no_next_page", "No next page");            
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
            messages.put("inventory_full", "你的背包已滿, 無法拾取物品!");
            messages.put("not_in_trashbin", "你拾取的物品已被他人拾取, 已為您刷新垃圾桶清單!");
            messages.put("trashbin_null", "垃圾桶为空!");
            messages.put("last_page", "上一頁");
            messages.put("no_last_page", "無上一頁");
            messages.put("next_page", "下一页");
            messages.put("no_next_page", "無下一頁");
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
            messages.put("inventory_full", "你的背包已满, 无法拾取物品!");
            messages.put("not_in_trashbin", "你拾取的物品已被他人拾取, 已为您刷新垃圾桶列表!");
            messages.put("trashbin_null", "垃圾桶为空");
            messages.put("last_page", "上一页");
            messages.put("no_last_page", "无上一页");
            messages.put("next_page", "下一页");
            messages.put("no_next_page", "无下一页");
        }
    }

    private void openTrashInventory(Player player) {
        Inventory trashInventory = Bukkit.createInventory(player, 54, ChatColor.GREEN + messages.get("trashbin_throw"));
        trashInventories.put(player.getUniqueId(), trashInventory);
        player.openInventory(trashInventory);
    }

    public int getTotalPages(int totalItems) {
        // 计算最大页数，每页最多显示45个项目
        return (totalItems == 0) ? 1 : (totalItems - 1) / 45 + 1;
    }

    private void openDigInventory(Player player, int page) {
        Inventory digInventory = Bukkit.createInventory(player, 54, ChatColor.YELLOW + messages.get("trashbin_flip"));

        int totalItems = allTrashItems.size();
        
        if (totalItems == 0) {
            // 如果垃圾桶为空，显示空的界面
            ItemStack emptyMessage = new ItemStack(Material.BARRIER);
            ItemMeta emptyMeta = emptyMessage.getItemMeta();
            if (emptyMeta != null) {
                emptyMeta.setDisplayName(ChatColor.RED + messages.get("trashbin_null"));
            }
            emptyMessage.setItemMeta(emptyMeta);
            for (int i = 0; i < 54; i++) {
                digInventory.setItem(i, emptyMessage);
            }
        } else {
            int maxPages = (totalItems == 0) ? 1 : (totalItems - 1) / 45 + 1;
            page = Math.min(Math.max(page, 0), maxPages - 1); // 限制页面范围

            int startIndex = page * 45; // 前45个用于物品展示
            for (int i = 0; i < 45 && startIndex + i < totalItems; i++) {
                digInventory.setItem(i, allTrashItems.get(startIndex + i));
            }

            // 设置底部灰色玻璃片
            ItemStack grayPane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta grayMeta = grayPane.getItemMeta();
            if (grayMeta != null) grayMeta.setDisplayName(" ");
            grayPane.setItemMeta(grayMeta);

            for (int i = 45; i < 54; i++) {
                digInventory.setItem(i, grayPane);
            }

            // 添加翻页按钮
            ItemStack previousButton = new ItemStack(page > 0 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            ItemMeta previousMeta = previousButton.getItemMeta();
            if (previousMeta != null) previousMeta.setDisplayName(ChatColor.GOLD + (page > 0 ? messages.get("last_page") : messages.get("no_last_page")));
            previousButton.setItemMeta(previousMeta);
            digInventory.setItem(45, previousButton);

            ItemStack nextButton = new ItemStack(page < maxPages - 1 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE);
            ItemMeta nextMeta = nextButton.getItemMeta();
            if (nextMeta != null) nextMeta.setDisplayName(ChatColor.GOLD + (page < maxPages - 1 ? messages.get("next_page") : messages.get("no_next_page")));
            nextButton.setItemMeta(nextMeta);
            digInventory.setItem(53, nextButton);

            // 页码指示器
            ItemStack pageIndicator = new ItemStack(Material.PAPER);
            ItemMeta pageMeta = pageIndicator.getItemMeta();
            if (pageMeta != null) pageMeta.setDisplayName(ChatColor.BLUE + messages.get("page") + ": " + (page + 1) + "/" + maxPages);
            pageIndicator.setItemMeta(pageMeta);
            digInventory.setItem(49, pageIndicator);
        }

        player.openInventory(digInventory);
    }


    private int getCurrentPage(Inventory inventory) {
        ItemStack pageIndicator = inventory.getItem(49);
        if (pageIndicator != null && pageIndicator.getType() == Material.PAPER) {
            String displayName = pageIndicator.getItemMeta().getDisplayName();
            String[] parts = ChatColor.stripColor(displayName).split(":");
            if (parts.length > 1) {
                try {
                    return Integer.parseInt(parts[1].split("/")[0].trim()) - 1;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return 0;
    }
}
