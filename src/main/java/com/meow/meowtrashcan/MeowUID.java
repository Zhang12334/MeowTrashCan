package com.meow.meowuid;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import me.clip.placeholderapi.PlaceholderAPI;

import java.util.concurrent.ConcurrentHashMap;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.net.HttpURLConnection;
import java.net.URL;

public class MeowUID extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {

    private Connection connection;
    private String host, database, username, password;
    private int port;
    private boolean enablePlugin;
    private long startingUid;
    private static final String TABLE_NAME = "player_uid";
    private final ConcurrentHashMap<String, Long> cache = new ConcurrentHashMap<>(); // 缓存

    private String startupMessage;
    private String shutdownMessage;
    private String nowusingversionMessage;
    private String checkingupdateMessage;
    private String checkfailedMessage;
    private String updateavailableMessage;
    private String updateurlMessage;
    private String oldversionmaycauseproblemMessage;
    private String nowusinglatestversionMessage;
    private String reloadedMessage;
    private String nopermissionMessage;
    private String usageMessage;
    private String CanNotConnectDatabaseMessage_include_reason;
    private String PluginDisabledMessage;
    private String InvalidUidMessage;
    private String playerMessage;
    private String uidfoudedMessage;
    private String CanNotFoundPlayeridMessage;
    private String playeridFoudedMessage;
    private String CanNotFoundPlayerUIDMessage;
    private String CanNotFoundPAPIMessage;
    private String RegistUIDMessage_a;
    private String RegistUIDMessage_b;

    @Override
    public void onEnable() {
        // bstats
        int pluginId = 24002;
        Metrics metrics = new Metrics(this, pluginId);        
        saveDefaultConfig();
        loadConfig();
        loadLanguage();
        // 检查插件是否启用
        if (!enablePlugin) {
            getLogger().info(PluginDisabledMessage);
            return; // 如果未启用插件，则停止后续操作
        }

        // 注册事件和命令
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("muid").setExecutor(this);
        getCommand("muid").setTabCompleter(this);

        // 连接数据库
        connectDatabase();

        // 注册自定义占位符
        if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new MeowUIDPlaceholderExpansion(this).register();  // 注册占位符扩展
        } else {
            getLogger().warning(CanNotFoundPAPIMessage);
        }

        // 启动时的版本检查
        getLogger().info(startupMessage);
        String currentVersion = getDescription().getVersion();
        getLogger().info(nowusingversionMessage + " v" + currentVersion);
        getLogger().info(checkingupdateMessage);
        new BukkitRunnable() {
            @Override
            public void run() {
                check_update();
            }
        }.runTaskAsynchronously(this);
    }

    @Override
    public void onDisable() {
        // 关闭数据库连接
        disconnectDatabase();
    }

    public String getPlayerUID(String playerId) {
        // FindUID 方法返回 Long 类型的玩家 UID
        Long playeruid_tmp_l = FindUID(playerId);
        
        // 检查 playeruid_tmp_l 是否为 null
        if (playeruid_tmp_l == null) {
            return "Unknown UID";  // 如果没有找到 UID，返回 "Unknown UID"
        } else {
            // 将 Long 转换为 String
            String playeruid_tmp_s = String.valueOf(playeruid_tmp_l);
            return playeruid_tmp_s;  // 返回字符串类型的 UID
        }
    }

    // 加载配置文件
    private void loadConfig() {
        enablePlugin = getConfig().getBoolean("enable-plugin", false);
        startingUid = getConfig().getLong("starting_uid", 1000000);
        host = getConfig().getString("mysql.host", "localhost");
        port = getConfig().getInt("mysql.port", 3306);
        database = getConfig().getString("mysql.database", "meow_uid");
        username = getConfig().getString("mysql.username", "root");
        password = getConfig().getString("mysql.password", "");
    }
    private void loadLanguage() {
        FileConfiguration config = getConfig();
        String language = config.getString("language", "zh_cn");

        if ("zh_cn".equalsIgnoreCase(language)) {
            // 中文消息
            startupMessage = "MeowUID 已加载!";
            shutdownMessage = "MeowUID 已卸载!";
            nowusingversionMessage = "当前使用版本:";
            checkingupdateMessage = "正在检查更新...";
            checkfailedMessage = "检查更新失败，请检查你的网络状况!";
            updateavailableMessage = "发现新版本:";
            updateurlMessage = "新版本下载地址:";
            oldversionmaycauseproblemMessage = "旧版本可能会导致问题，请尽快更新!";
            nowusinglatestversionMessage = "您正在使用最新版本!";
            reloadedMessage = "配置文件已重载!";
            nopermissionMessage = "你没有权限执行此命令!";
            usageMessage = "用法:";
            CanNotConnectDatabaseMessage_include_reason = "无法连接到数据库:";
            PluginDisabledMessage = "插件目前未启用, 请检查你的配置文件!";
            InvalidUidMessage = "无效的 UID 格式!";
            playerMessage = "玩家";
            uidfoudedMessage = "的 UID 是:";
            CanNotFoundPlayeridMessage = "找不到此玩家的 UID :";
            playeridFoudedMessage = "对应的玩家 ID 是:";
            CanNotFoundPlayerUIDMessage = "找不到此 UID 对应的玩家:";
            CanNotFoundPAPIMessage = "未找到 PlaceholderAPI, 无法使用变量查询玩家 UID !";
            RegistUIDMessage_a = "已为玩家";
            RegistUIDMessage_b = "注册UID:";
        } else if ("en_us".equalsIgnoreCase(language)) {
            // English Message
            startupMessage = "MeowUID has been loaded!";
            shutdownMessage = "MeowUID has been unloaded!";
            nowusingversionMessage = "Currently using version:";
            checkingupdateMessage = "Checking for updates...";
            checkfailedMessage = "Update check failed, please check your network status!";
            updateavailableMessage = "New version available:";
            updateurlMessage = "Download URL for new version:";
            oldversionmaycauseproblemMessage = "Old versions may cause problems, please update as soon as possible!";
            nowusinglatestversionMessage= "You are currently using the latest version!";
            reloadedMessage = "Config file has been reloaded!";
            nopermissionMessage = "You do not have permission to execute this command!";
            usageMessage = "Usage:";
            CanNotConnectDatabaseMessage_include_reason = "Failed to connect to the database:";
            PluginDisabledMessage = "The plugin is currently disabled, please check your configuration file!";
            InvalidUidMessage = "Invalid UID format!";
            playerMessage = "Player";
            uidfoudedMessage = " has UID:";
            CanNotFoundPlayeridMessage = "Could not find the UID for player:";
            playeridFoudedMessage = "The ID of the user is:";
            CanNotFoundPlayerUIDMessage = "Could not find the player with this UID:";
            CanNotFoundPAPIMessage = "Could not find PlaceholderAPI, unable to use variables to query player UID !";
            RegistUIDMessage_a = "Registered UID for player";
            RegistUIDMessage_b = ":";
        } else if ("zh_tc".equalsIgnoreCase(language)) {
            // 繁体中文消息
            startupMessage = "MeowUID 已加载!";
            shutdownMessage = "MeowUID 已卸载!";
            nowusingversionMessage = "目前使用版本:";
            checkingupdateMessage = "正在檢查更新...";
            checkfailedMessage = "檢查更新失敗，請檢查你的網絡狀態!";
            updateavailableMessage = "發現新版本:";
            updateurlMessage= "新版本下載網址:";
            oldversionmaycauseproblemMessage = "舊版本可能會導致問題，請盡快更新!";
            nowusinglatestversionMessage = "您正在使用最新版本!";
            reloadedMessage = "配置文件已重载!";
            nopermissionMessage = "你没有权限执行此命令!";
            usageMessage = "用法:";
            CanNotConnectDatabaseMessage_include_reason = "無法連接到數據庫:";
            PluginDisabledMessage = "插件目前未啟用，請檢查你的配置文件!";
            InvalidUidMessage = "無效的 UID 格式!";
            playerMessage = "玩家";
            uidfoudedMessage = "的 UID 是:";
            CanNotFoundPlayeridMessage = "找不到此玩家的 UID :";
            playeridFoudedMessage = "對應的玩家 ID 是:";
            CanNotFoundPlayerUIDMessage = "找不到此 UID 對應的玩家:";
            CanNotFoundPAPIMessage = "未找到 PlaceholderAPI, 無法使用變量查詢玩家 UID !";
            RegistUIDMessage_a = "已為玩家";
            RegistUIDMessage_b = "註冊UID:";
        }
    }

    // 数据库连接
    private void connectDatabase() {
        try {
            if (connection != null && !connection.isClosed()) return;
            String url = "jdbc:mysql://" + host + ":" + port + "/" + database + "?autoReconnect=true";
            connection = DriverManager.getConnection(url, username, password);
            
            // 创建表格如果不存在
            PreparedStatement ps = connection.prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + TABLE_NAME + " (" +
                "player_id VARCHAR(16) PRIMARY KEY, " +
                "uid BIGINT NOT NULL)"
            );
            ps.executeUpdate();
            ps.close();
        } catch (SQLException e) {
            getLogger().severe(CanNotConnectDatabaseMessage_include_reason + " " + e.getMessage());
        }
    }

    private void disconnectDatabase() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 处理玩家进入事件
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!enablePlugin) return; // 如果插件未启用，则不执行任何逻辑

        Player player = event.getPlayer();
        String playerId = player.getName();

        new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    PreparedStatement ps = connection.prepareStatement(
                        "SELECT uid FROM " + TABLE_NAME + " WHERE player_id = ?"
                    );
                    ps.setString(1, playerId);
                    ResultSet rs = ps.executeQuery();

                    if (!rs.next()) {
                        // 为新玩家分配 UID
                        long newUid = getNextAvailableUid();
                        PreparedStatement insert = connection.prepareStatement(
                            "INSERT INTO " + TABLE_NAME + " (player_id, uid) VALUES (?, ?)"
                        );
                        insert.setString(1, playerId);
                        insert.setLong(2, newUid);
                        insert.executeUpdate();
                        insert.close();
                        getLogger().info(RegistUIDMessage_a + " " + playerId + " " + RegistUIDMessage_b + newUid);
                    }
                    rs.close();
                    ps.close();
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }.runTaskAsynchronously(this); //异步
    }

    // 获取下一个可用的 UID
    private long getNextAvailableUid() {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT MAX(uid) AS max_uid FROM " + TABLE_NAME
            );
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                long maxUid = rs.getLong("max_uid");
                rs.close();
                ps.close();
                return (maxUid >= startingUid) ? maxUid + 1 : startingUid + 1;
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return startingUid + 1;
    }

    // 处理命令
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!enablePlugin) {
            sender.sendMessage(PluginDisabledMessage);
            return true;
        }

        if (command.getName().equalsIgnoreCase("muid")) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("find")) {
                if (args[1].equalsIgnoreCase("id") && args.length == 3) {
                    String playerId = args[2];
                    findUidById(sender, playerId);
                } else if (args[1].equalsIgnoreCase("uid") && args.length == 3) {
                    try {
                        long uid = Long.parseLong(args[2]);
                        findIdByUid(sender, uid);
                    } catch (NumberFormatException e) {
                        sender.sendMessage(InvalidUidMessage);
                    }
                } else {
                    sender.sendMessage(usageMessage + " /muid find <id/uid> <PlayerID / PlayerUID>");
                }
                return true;
            } else if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadConfig();
                cache.clear(); // 清空缓存
                sender.sendMessage(reloadedMessage);
                return true;
            }
        }
        return false;
    }

    // 根据玩家名查找 UID
    private void findUidById(CommandSender sender, String playerId) {
        Long uid = FindUID(playerId);  // 修改为 Long 类型
        if (uid != null) {
            sender.sendMessage(playerMessage + " " + playerId + " " + uidfoudedMessage + " " + uid);
        } else {
            sender.sendMessage(CanNotFoundPlayeridMessage + " " + playerId);
        }
    }

    // 查找指定 playerId 的 UID
    public Long FindUID(String playerId) {
        // 从缓存中获取
        if (cache.containsKey(playerId)) {
            return cache.get(playerId);
        }

        // 如果缓存没有，查询数据库
        Long uid = null;
        try {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT uid FROM " + TABLE_NAME + " WHERE player_id = ?"
            );
            ps.setString(1, playerId);
            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                uid = rs.getLong("uid"); // 获取 UID
                cache.put(playerId, uid); // 存入缓存
            }

            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return uid; // 如果没有找到，返回 null
    }

    // 根据 UID 查找玩家名
    private void findIdByUid(CommandSender sender, long uid) {
        try {
            PreparedStatement ps = connection.prepareStatement(
                "SELECT player_id FROM " + TABLE_NAME + " WHERE uid = ?"
            );
            ps.setLong(1, uid);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String playerId = rs.getString("player_id");
                sender.sendMessage("UID " + uid + " " + playeridFoudedMessage + " " + playerId);
            } else {
                sender.sendMessage(CanNotFoundPlayerUIDMessage + " " + uid);
            }
            rs.close();
            ps.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // 提供 TAB 补全
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!enablePlugin) return null; // 如果插件未启用，禁用补全

        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("find");
            completions.add("reload");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("find")) {
            completions.add("id");
            completions.add("uid");
        }
        return completions;
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
        // 仓库地址：https://github.com/Zhang12334/MeowUID
        String latestVersionUrl = "https://github.com/Zhang12334/MeowUID/releases/latest";
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
                getLogger().warning(checkfailedMessage);
                return;
            }
            // 比较版本号
            if (isVersionGreater(latestVersion, currentVersion)) {
                // 如果有新版本，则提示新版本
                getLogger().warning(updateavailableMessage + " v" + latestVersion);
                // 提示下载地址（latest release地址）
                getLogger().warning(updateurlMessage + " https://github.com/Zhang12334/MeowUID/releases/latest");
                getLogger().warning(oldversionmaycauseproblemMessage);
            } else {
                getLogger().info(nowusinglatestversionMessage);
            }
        } catch (Exception e) {
            getLogger().warning(checkfailedMessage);
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
}
