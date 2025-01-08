package com.meow.meowuid;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;
import com.meow.meowuid.MeowUID;

public class MeowUIDPlaceholderExpansion extends PlaceholderExpansion {

    private final MeowUID plugin;

    // 构造函数，传入插件实例
    public MeowUIDPlaceholderExpansion(MeowUID plugin) {
        this.plugin = plugin;
    }

    // 返回扩展标识符
    @Override
    public String getIdentifier() {
        return "meowuid";  // PAPI中的标识符
    }

    // 返回作者名
    @Override
    public String getAuthor() {
        return "Zhang1233";  // 插件作者名
    }

    // 返回插件版本
    @Override
    public String getVersion() {
        return plugin.getDescription().getVersion();  // 返回当前插件版本
    }

    // 处理占位符请求
    @Override
    public String onPlaceholderRequest(Player player, String params) {
        String playerId = player.getName();
        return plugin.getPlayerUID(playerId);  // 返回
    }
}
