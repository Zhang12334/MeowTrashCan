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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import java.lang.reflect.Method;
import net.minecraft.nbt.NBTTagCompound;
import java.lang.reflect.InvocationTargetException;

import java.io.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;


public class MeowTrashCan extends JavaPlugin implements Listener {

    private final Map<UUID, Inventory> trashInventories = new HashMap<>();
    private final List<ItemStack> allTrashItems = new ArrayList<>(); // 内存中的垃圾物品列表
    private boolean useMySQL;
    private Connection connection;
    private Map<String, String> messages;
    private String storageType;

    @Override
    public void onEnable() {
 
        // bstats
        int pluginId = 24401;
        Metrics metrics = new Metrics(this, pluginId);
        // 读配置        
        saveDefaultConfig();
        storageType = getConfig().getString("storage", "json");
        useMySQL = storageType.equalsIgnoreCase("mysql");
        loadMessages();
        if (useMySQL) {
            setupMySQL();
        }
        loadTrashItems(); // 从数据库或 JSON 文件加载垃圾物品
        // 更新检查部分
        getLogger().info(messages.get("startupMessage"));
        String currentVersion = getDescription().getVersion();
        getLogger().info(messages.get("nowusingversionMessage") + " v" + currentVersion);
        getLogger().info(messages.get("checkingUpdateMessage"));
        new BukkitRunnable() {
            @Override
            public void run() {
                check_update();
            }
        }.runTaskAsynchronously(this);
        // 注册事件
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        saveTrashItems(); // 保存垃圾物品到数据库或 JSON 文件
        if (useMySQL && connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    // 检查更新方法
    private void check_update() {
        // 获取当前版本号
        String currentVersion = getDescription().getVersion();
        // github加速地址，挨个尝试
        String[] githubUrls = {
            "https://ghp.ci/",
            "https://raw.fastgit.org/",
            ""
            //最后使用源地址
        };
        // 获取 github release 最新版本号作为最新版本
        // 仓库地址：https://github.com/Zhang12334/MeowTrashCan
        String latestVersionUrl = "https://github.com/Zhang12334/MeowTrashCan/releases/latest";
        // 获取版本号
        try {
            String latestVersion = null;
            for (String url : githubUrls) {
                HttpURLConnection connection = (HttpURLConnection) new URL(url + latestVersionUrl).openConnection();
                connection.setInstanceFollowRedirects(false); // 不自动跟随重定向
                int responseCode = connection.getResponseCode();
                if (responseCode == 302) { // 如果 302 了
                    String redirectUrl = connection.getHeaderField("Location");
                    if (redirectUrl != null && redirectUrl.contains("tag/")) {
                        // 从重定向URL中提取版本号
                        latestVersion = extractVersionFromUrl(redirectUrl);
                        break; // 找到版本号后退出循环
                    }
                }
                connection.disconnect();
                if (latestVersion != null) {
                    break; // 找到版本号后退出循环
                }
            }
            if (latestVersion == null) {
                getLogger().warning(messages.get("checkfailedMessage"));
                return;
            }
            // 比较版本号
            if (isVersionGreater(latestVersion, currentVersion)) {
                // 如果有新版本，则提示新版本
                getLogger().warning(messages.get("updateavailableMessage") + " v" + latestVersion);
                // 提示下载地址（latest release地址）
                getLogger().warning(messages.get("updateurlMessage") + " https://github.com/Zhang12334/MeowTrashCan/releases/latest");
                getLogger().warning(messages.get("oldversionmaycauseproblemMessage"));
            } else {
                getLogger().info(messages.get("nowusinglatestversionMessage"));
            }
        } catch (Exception e) {
            getLogger().warning(messages.get("checkfailedMessage"));
        }
    }

    // 版本比较
    private boolean isVersionGreater(String version1, String version2) {
        String[] v1Parts = version1.split("\\.");
        String[] v2Parts = version2.split("\\.");
        for (int i = 0; i < Math.max(v1Parts.length, v2Parts.length); i++) {
            int v1Part = i < v1Parts.length ? Integer.parseInt(v1Parts[i]) : 0;
            int v2Part = i < v2Parts.length ? Integer.parseInt(v2Parts[i]) : 0;
            if (v1Part > v2Part) {
                return true;
            } else if (v1Part < v2Part) {
                return false;
            }
        }
        return false;
    }
    
    private String extractVersionFromUrl(String url) {
        // 解析 302 URL 中的版本号
        int tagIndex = url.indexOf("tag/");
        if (tagIndex != -1) {
            int endIndex = url.indexOf('/', tagIndex + 4);
            if (endIndex == -1) {
                endIndex = url.length();
            }
            return url.substring(tagIndex + 4, endIndex);
        }
        return null;
    }

    private void setupMySQL() {
        String host = getConfig().getString("mysql.host");
        int port = getConfig().getInt("mysql.port");
        String database = getConfig().getString("mysql.database");
        String username = getConfig().getString("mysql.username");
        String password = getConfig().getString("mysql.password");

        // 添加 autoReconnect 参数
        String url = String.format(
                "jdbc:mysql://%s:%d/%s?autoReconnect=true&autoReconnectForPools=true&useSSL=false&serverTimezone=UTC",
                host, port, database
        );

        try {
            connection = DriverManager.getConnection(url, username, password);
            
            // 创建表时添加 nbt_data 字段来存储物品的 NBT 数据
            connection.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS trash_items (" +
                    "id INT AUTO_INCREMENT PRIMARY KEY, " +
                    "nbt_data TEXT NOT NULL" +  // 使用 TEXT 类型来存储 NBT 数据
                    ")"
            );
            getLogger().info(messages.get("connect_database_successful"));
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
            case "dig":
                if (!player.hasPermission("meowtrashcan.dig")) {
                    player.sendMessage(messages.get("no_permission"));
                    return true;
                }
                loadTrashItems();
                // 获取所有存在页面的总数
                int totalItems = allTrashItems.size();
                int totalPages = getTotalPages(totalItems);
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

public void saveTrashItems() {
    try {
        String query = "DELETE FROM trash_items";  // 清除表中的旧数据
        try (PreparedStatement statement = connection.prepareStatement(query)) {
            statement.executeUpdate();
        }

        // 遍历所有的垃圾物品并保存 NBT 数据
        for (ItemStack item : allTrashItems) {
            String nbtData = getNBTData(item); // 使用反射获取 NBT 数据
            try (PreparedStatement insertStatement = connection.prepareStatement("INSERT INTO trash_items (nbt_data) VALUES (?)")) {
                insertStatement.setString(1, nbtData); // 将 NBT 数据存入数据库
                insertStatement.executeUpdate();
            }
        }
    } catch (SQLException | ReflectiveOperationException e) {
        e.printStackTrace();
    }
}

    public void loadTrashItems() {
        allTrashItems.clear();
        try (Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT nbt_data FROM trash_items")) {
            while (resultSet.next()) {
                String nbtData = resultSet.getString("nbt_data");
                ItemStack item = getItemStackFromNBT(nbtData); // 使用反射将 NBT 数据转换为 ItemStack
                if (item != null) {
                    allTrashItems.add(item); // 将物品添加到 allTrashItems
                }
            }
        } catch (SQLException | ReflectiveOperationException e) {
            e.printStackTrace();
        }
    }

    // 获取 NBT 数据
    private String getNBTData(ItemStack item) throws ReflectiveOperationException {
        Class<?> CraftItemStack = Class.forName("org.bukkit.craftbukkit.v1_20_R4.inventory.CraftItemStack");
        Class<?> NBTTagCompound = Class.forName("net.minecraft.nbt.NBTTagCompound");
        Class<?> ItemStack = Class.forName("net.minecraft.world.item.ItemStack");

        Method asNMSCopy = CraftItemStack.getMethod("asNMSCopy", ItemStack.class);
        Method save = ItemStack.getMethod("save", NBTTagCompound);

        Object nmsItem = asNMSCopy.invoke(null, item);
        Object nbt = save.invoke(nmsItem, NBTTagCompound.getDeclaredConstructor().newInstance());

        return nbt.toString();
    }

    // 从 NBT 数据中恢复 ItemStack
    private ItemStack getItemStackFromNBT(String nbtData) throws ReflectiveOperationException {
        Class<?> CraftItemStack = Class.forName("org.bukkit.craftbukkit.v1_20_R4.inventory.CraftItemStack");
        Class<?> ItemStack = Class.forName("net.minecraft.world.item.ItemStack");

        Method parse = Class.forName("net.minecraft.nbt.MojangsonParser").getMethod("a", String.class);
        Method createStack = ItemStack.getMethod("a", NBTTagCompound.class);
        Method asBukkitCopy = CraftItemStack.getMethod("asBukkitCopy", ItemStack.class);

        Object nmsItem = asBukkitCopy.invoke(null, createStack.invoke(null, parse.invoke(null, nbtData)));
        return (ItemStack) nmsItem;
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
                // 如果点击的是空气、屏障或无物品则不进行操作
                if (clickedItem != null && clickedItem.getType() != Material.AIR && clickedItem.getType() != Material.BARRIER) {
                    loadTrashItems();
                    // 检查物品是否在垃圾桶列表中
                    if (allTrashItems.contains(clickedItem)) {
                        // 尝试将物品加入玩家背包
                        HashMap<Integer, ItemStack> remaining = player.getInventory().addItem(clickedItem);
                        if (remaining.isEmpty()) {
                            // 如果成功加入背包
                            inventory.setItem(slot, null); // 从界面移除物品
                            allTrashItems.remove(clickedItem); // 从垃圾列表移除
                            saveTrashItems(); // 保存垃圾列表
                            openDigInventory(player, getCurrentPage(inventory)); // 刷新当前页
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
            messages.put("usage", ChatColor.RED + "Usage: /mtc <throw|dig|reload>");
            messages.put("no_permission", ChatColor.RED + "You don't have permission to perform this action.");
            messages.put("reloaded", ChatColor.GREEN + "MeowTrashCan configuration reloaded.");
            messages.put("unknown_command", ChatColor.RED + "Unknown command. Usage: /mtc <throw|dig|reload>");
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
            messages.put("connect_database_successful", "Connected to MySQL database successfully!");
            messages.put("startupMessage", "MeowTrashCan has loaded!");
            messages.put("shutdownMessage", "MeowTrashCan has been unloaded!");
            messages.put("nowusingversionMessage", "Currently using version:");
            messages.put("checkingUpdateMessage", "Checking for updates...");
            messages.put("checkfailedMessage", "Failed to check for updates. Please check your network connection!");
            messages.put("updateavailableMessage", "New version available:");
            messages.put("updateurlMessage", "New version download URL:");
            messages.put("oldversionmaycauseproblemMessage", "The old version may cause problems. Please update as soon as possible!");
            messages.put("nowusinglatestversionMessage", "You are using the latest version!");

        } else if (language.equalsIgnoreCase("zh_tc")) {//繁体消息
            messages.put("only_players", ChatColor.RED + "只有玩家能使用這個指令!");
            messages.put("usage", ChatColor.RED + "用法：/mtc <throw|dig|reload>");
            messages.put("no_permission", ChatColor.RED + "你沒有權限執行此操作!");
            messages.put("reloaded", ChatColor.GREEN + "MeowTrashCan 配置已重載");
            messages.put("unknown_command", ChatColor.RED + "未知的指令, 用法：/mtc <throw|dig|reload>");
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
            messages.put("connect_database_successful", "資料庫連線成功!");
            messages.put("startupMessage", "MeowTrashCan 已加載!");
            messages.put("shutdownMessage", "MeowTrashCan 已卸載!");
            messages.put("nowusingversionMessage", "當前使用版本:");
            messages.put("checkingUpdateMessage", "正在檢查更新...");
            messages.put("checkfailedMessage", "檢查更新失敗，請檢查您的網絡狀況!");
            messages.put("updateavailableMessage", "發現新版本:");
            messages.put("updateurlMessage", "新版本下載地址:");
            messages.put("oldversionmaycauseproblemMessage", "舊版本可能會導致問題，請盡快更新!");
            messages.put("nowusinglatestversionMessage", "您正在使用最新版本!");

        } else if (language.equalsIgnoreCase("zh_cn")) {//简体消息
            messages.put("only_players", ChatColor.RED + "只有玩家可以使用此命令!");
            messages.put("usage", ChatColor.RED + "用法：/mtc <throw|dig|reload>");
            messages.put("no_permission", ChatColor.RED + "你没有权限执行此操作!");
            messages.put("reloaded", ChatColor.GREEN + "MeowTrashCan 配置已重新加载");
            messages.put("unknown_command", ChatColor.RED + "未知命令, 用法：/mtc <throw|dig|reload>");
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
            messages.put("connect_database_successful", "数据库连接成功!");
            messages.put("startupMessage", "MeowTrashCan 已加载!");
            messages.put("shutdownMessage", "MeowTrashCan 已卸载!");
            messages.put("nowusingversionMessage", "当前使用版本:");
            messages.put("checkingUpdateMessage", "正在检查更新...");
            messages.put("checkfailedMessage", "检查更新失败，请检查你的网络状况!");
            messages.put("updateavailableMessage", "发现新版本:");
            messages.put("updateurlMessage", "新版本下载地址:");
            messages.put("oldversionmaycauseproblemMessage", "旧版本可能会导致问题，请尽快更新!");
            messages.put("nowusinglatestversionMessage", "您正在使用最新版本!");          
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
        loadTrashItems();
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
