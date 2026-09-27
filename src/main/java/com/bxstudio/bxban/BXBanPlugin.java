package com.bxstudio.bxban;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BXBanPlugin extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final Map<UUID, Punishment> bans = new ConcurrentHashMap<>();
    private final Map<String, Punishment> ipBans = new ConcurrentHashMap<>();
    private final Map<UUID, Punishment> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, List<HistoryEntry>> histories = new ConcurrentHashMap<>();

    private File bansFile, ipBansFile, mutesFile, historyFile;
    private FileConfiguration bansCfg, ipBansCfg, mutesCfg, historyCfg;
    private FileConfiguration messages;
    private final Pattern durationPart = Pattern.compile("(?i)(\\d+)(s|m|h|d|w)");
    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")
            .withZone(ZoneId.systemDefault());

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("messages.yml", false);
        loadFiles();
        getServer().getPluginManager().registerEvents(this, this);

        String[] commands = {"ban", "tempban", "unban", "banip", "unbanip", "kick", "warn", "mute", "unmute", "history", "banlist", "bxban"};
        for (String name : commands) {
            if (getCommand(name) != null) {
                getCommand(name).setExecutor(this);
                getCommand(name).setTabCompleter(this);
            }
        }

        getLogger().info("BXBan başarıyla etkinleştirildi. Türkçe ceza sistemi hazır!");
    }

    @Override
    public void onDisable() {
        saveFiles();
    }

    private void loadFiles() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Plugin klasörü oluşturulamadı.");
        }
        bansFile = new File(getDataFolder(), "bans.yml");
        ipBansFile = new File(getDataFolder(), "ip-bans.yml");
        mutesFile = new File(getDataFolder(), "mutes.yml");
        historyFile = new File(getDataFolder(), "history.yml");
        createIfMissing(bansFile);
        createIfMissing(ipBansFile);
        createIfMissing(mutesFile);
        createIfMissing(historyFile);

        bansCfg = YamlConfiguration.loadConfiguration(bansFile);
        ipBansCfg = YamlConfiguration.loadConfiguration(ipBansFile);
        mutesCfg = YamlConfiguration.loadConfiguration(mutesFile);
        historyCfg = YamlConfiguration.loadConfiguration(historyFile);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml"));

        loadPunishments();
    }

    private void createIfMissing(File file) {
        if (!file.exists()) {
            try { if (!file.createNewFile()) getLogger().warning("Dosya oluşturulamadı: " + file.getName()); }
            catch (IOException e) { getLogger().severe("Dosya oluşturulamadı: " + file.getName()); }
        }
    }

    private void loadPunishments() {
        bans.clear(); ipBans.clear(); mutes.clear(); histories.clear();
        for (String key : bansCfg.getKeys(false)) {
            Punishment p = readPunishment(bansCfg, key);
            if (p != null && (p.expiresAt == 0 || p.expiresAt > System.currentTimeMillis())) bans.put(UUID.fromString(key), p);
        }
        for (String key : ipBansCfg.getKeys(false)) {
            Punishment p = readPunishment(ipBansCfg, key);
            if (p != null && (p.expiresAt == 0 || p.expiresAt > System.currentTimeMillis())) ipBans.put(key, p);
        }
        for (String key : mutesCfg.getKeys(false)) {
            Punishment p = readPunishment(mutesCfg, key);
            if (p != null && (p.expiresAt == 0 || p.expiresAt > System.currentTimeMillis())) mutes.put(UUID.fromString(key), p);
        }
        for (String key : historyCfg.getKeys(false)) {
            UUID uuid;
            try { uuid = UUID.fromString(key); } catch (IllegalArgumentException ex) { continue; }
            List<HistoryEntry> list = new ArrayList<>();
            for (Map<?, ?> map : historyCfg.getMapList(key)) {
                list.add(new HistoryEntry(
                        map.getOrDefault("id", 0).toString(),
                        map.getOrDefault("type", "Bilinmiyor").toString(),
                        map.getOrDefault("reason", "Belirtilmedi").toString(),
                        map.getOrDefault("staff", "Bilinmiyor").toString(),
                        parseLong(map.get("time"), System.currentTimeMillis())
                ));
            }
            histories.put(uuid, list);
        }
    }

    private Punishment readPunishment(FileConfiguration cfg, String key) {
        String reason = cfg.getString(key + ".reason", "Belirtilmedi");
        String staff = cfg.getString(key + ".staff", "Bilinmiyor");
        long expires = cfg.getLong(key + ".expires", 0);
        long created = cfg.getLong(key + ".created", System.currentTimeMillis());
        String id = cfg.getString(key + ".id", "-");
        return new Punishment(id, reason, staff, created, expires);
    }

    private void saveFiles() {
        savePunishments(bansCfg, bansFile, bans);
        savePunishments(ipBansCfg, ipBansFile, ipBans);
        savePunishments(mutesCfg, mutesFile, mutes);
        historyCfg.getKeys(false).forEach(k -> historyCfg.set(k, null));
        for (Map.Entry<UUID, List<HistoryEntry>> entry : histories.entrySet()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (HistoryEntry h : entry.getValue()) {
                rows.add(Map.of("id", h.id, "type", h.type, "reason", h.reason, "staff", h.staff, "time", h.time));
            }
            historyCfg.set(entry.getKey().toString(), rows);
        }
        saveYaml(bansCfg, bansFile); saveYaml(ipBansCfg, ipBansFile); saveYaml(mutesCfg, mutesFile); saveYaml(historyCfg, historyFile);
    }

    private void savePunishments(FileConfiguration cfg, File file, Map<?, Punishment> map) {
        cfg.getKeys(false).forEach(k -> cfg.set(k, null));
        for (Map.Entry<?, Punishment> entry : map.entrySet()) {
            String key = entry.getKey().toString();
            Punishment p = entry.getValue();
            cfg.set(key + ".id", p.id); cfg.set(key + ".reason", p.reason); cfg.set(key + ".staff", p.staff);
            cfg.set(key + ".created", p.createdAt); cfg.set(key + ".expires", p.expiresAt);
        }
        saveYaml(cfg, file);
    }

    private void saveYaml(FileConfiguration cfg, File file) {
        try { cfg.save(file); } catch (IOException e) { getLogger().warning("Kaydetme hatası: " + file.getName()); }
    }

    @EventHandler
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        Punishment ban = bans.get(uuid);
        if (isExpired(ban)) { bans.remove(uuid); ban = null; }
        if (ban != null) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(event.getName(), ban));
            return;
        }
        String ip = event.getAddress() == null ? "" : event.getAddress().getHostAddress();
        Punishment ipBan = ipBans.get(ip);
        if (isExpired(ipBan)) { ipBans.remove(ip); ipBan = null; }
        if (ipBan != null) event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banScreen(event.getName(), ipBan));
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) { cleanupExpired(event.getPlayer().getUniqueId()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { cleanupExpired(event.getPlayer().getUniqueId()); }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Punishment mute = mutes.get(event.getPlayer().getUniqueId());
        if (isExpired(mute)) { mutes.remove(event.getPlayer().getUniqueId()); return; }
        if (mute != null) {
            event.setCancelled(true);
            send(event.getPlayer(), "chat-blocked", Map.of("remaining", formatRemaining(mute.expiresAt), "reason", mute.reason));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (!sender.hasPermission(command.getPermission() == null ? "" : command.getPermission())) {
            send(sender, "no-permission", Map.of()); return true;
        }
        switch (name) {
            case "ban" -> doBan(sender, args, false);
            case "tempban" -> doBan(sender, args, true);
            case "unban" -> doUnban(sender, args);
            case "banip" -> doBanIp(sender, args);
            case "unbanip" -> doUnbanIp(sender, args);
            case "kick" -> doKick(sender, args);
            case "warn" -> doWarn(sender, args);
            case "mute" -> doMute(sender, args);
            case "unmute" -> doUnmute(sender, args);
            case "history" -> doHistory(sender, args);
            case "banlist" -> doBanList(sender);
            case "bxban" -> doAdmin(sender, args);
            default -> send(sender, "usage", Map.of("usage", "/" + label));
        }
        return true;
    }

    private void doBan(CommandSender sender, String[] args, boolean temporary) {
        if ((temporary && args.length < 3) || (!temporary && args.length < 2)) {
            send(sender, "usage", Map.of("usage", temporary ? "/tempban <oyuncu> <süre> <sebep>" : "/ban <oyuncu> <sebep>")); return;
        }
        Player online = Bukkit.getPlayerExact(args[0]);
        UUID uuid = findUuid(args[0]);
        if (uuid == null) { send(sender, "player-not-found", Map.of("player", args[0])); return; }
        if (sender instanceof Player p && p.getUniqueId().equals(uuid)) { send(sender, "self-action", Map.of()); return; }
        String durationText = temporary ? args[1] : "Kalıcı";
        long expires = temporary ? parseDuration(args[1]) : 0;
        if (temporary && expires < 0) { send(sender, "invalid-duration", Map.of()); return; }
        String reason = join(args, temporary ? 2 : 1);
        Punishment p = new Punishment(nextId(), reason, sender.getName(), System.currentTimeMillis(), expires);
        bans.put(uuid, p); addHistory(uuid, new HistoryEntry(p.id, temporary ? "Süreli Yasak" : "Yasak", reason, sender.getName(), p.createdAt)); saveFiles();
        if (online != null) online.kickPlayer(banScreen(online.getName(), p));
        String key = temporary ? "tempban-success" : "ban-success";
        send(sender, key, Map.of("player", args[0], "duration", formatRemaining(expires), "reason", reason));
        broadcast(temporary ? "broadcast-tempban" : "broadcast-ban", Map.of("player", args[0], "süre", formatRemaining(expires), "reason", reason));
    }

    private void doUnban(CommandSender sender, String[] args) {
        if (args.length < 1) { send(sender, "usage", Map.of("usage", "/unban <oyuncu>")); return; }
        UUID uuid = findUuid(args[0]);
        if (uuid == null) { send(sender, "player-not-found", Map.of("player", args[0])); return; }
        if (!bans.containsKey(uuid)) { send(sender, "not-banned", Map.of("player", args[0])); return; }
        bans.remove(uuid); addHistory(uuid, new HistoryEntry(nextId(), "Yasak Kaldırma", "Yasak kaldırıldı", sender.getName(), System.currentTimeMillis())); saveFiles();
        send(sender, "unban-success", Map.of("player", args[0]));
        broadcast("broadcast-unban", Map.of("player", args[0]));
    }

    private void doBanIp(CommandSender sender, String[] args) {
        if (args.length < 2) { send(sender, "usage", Map.of("usage", "/banip <ip> <sebep>")); return; }
        String ip = args[0];
        if (!isValidIp(ip)) { send(sender, "invalid-duration", Map.of()); return; }
        String reason = join(args, 1);
        Punishment p = new Punishment(nextId(), reason, sender.getName(), System.currentTimeMillis(), 0);
        ipBans.put(ip, p); saveFiles();
        for (Player player : Bukkit.getOnlinePlayers()) {
            InetAddress addr = player.getAddress() == null ? null : player.getAddress().getAddress();
            if (addr != null && ip.equals(addr.getHostAddress())) player.kickPlayer(banScreen(player.getName(), p));
        }
        send(sender, "ipban-success", Map.of("ip", ip));
    }

    private void doUnbanIp(CommandSender sender, String[] args) {
        if (args.length < 1) { send(sender, "usage", Map.of("usage", "/unbanip <ip>")); return; }
        if (ipBans.remove(args[0]) == null) { send(sender, "not-banned", Map.of("player", args[0])); return; }
        saveFiles(); send(sender, "ipunban-success", Map.of("ip", args[0]));
    }

    private void doKick(CommandSender sender, String[] args) {
        if (args.length < 1) { send(sender, "usage", Map.of("usage", "/kick <oyuncu> [sebep]")); return; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { send(sender, "player-not-found", Map.of("player", args[0])); return; }
        if (sender instanceof Player p && p.getUniqueId().equals(target.getUniqueId())) { send(sender, "self-action", Map.of()); return; }
        String reason = args.length > 1 ? join(args, 1) : "Kural ihlali";
        target.kickPlayer(color("&c&lSUNUCUDAN ATILDINIZ\\n\\n&7Sebep: &f" + reason + "\\n&7Yetkili: &f" + sender.getName()));
        send(sender, "kick-success", Map.of("player", target.getName()));
    }

    private void doWarn(CommandSender sender, String[] args) {
        if (args.length < 2) { send(sender, "usage", Map.of("usage", "/warn <oyuncu> <sebep>")); return; }
        UUID uuid = findUuid(args[0]); if (uuid == null) { send(sender, "player-not-found", Map.of("player", args[0])); return; }
        String reason = join(args, 1); addHistory(uuid, new HistoryEntry(nextId(), "Uyarı", reason, sender.getName(), System.currentTimeMillis())); saveFiles();
        Player target = Bukkit.getPlayer(uuid); if (target != null) target.sendMessage(color("&e&l⚠ UYARI &8» &7" + reason));
        send(sender, "warn-success", Map.of("player", args[0], "reason", reason));
    }

    private void doMute(CommandSender sender, String[] args) {
        if (args.length < 3) { send(sender, "usage", Map.of("usage", "/mute <oyuncu> <süre> <sebep>")); return; }
        UUID uuid = findUuid(args[0]); if (uuid == null) { send(sender, "player-not-found", Map.of("player", args[0])); return; }
        long expires = parseDuration(args[1]); if (expires < 0) { send(sender, "invalid-duration", Map.of()); return; }
        String reason = join(args, 2); Punishment p = new Punishment(nextId(), reason, sender.getName(), System.currentTimeMillis(), expires);
        mutes.put(uuid, p); addHistory(uuid, new HistoryEntry(p.id, "Susturma", reason, sender.getName(), p.createdAt)); saveFiles();
        Player target = Bukkit.getPlayer(uuid); if (target != null) send(target, "muted", Map.of("remaining", formatRemaining(expires), "reason", reason));
        send(sender, "mute-success", Map.of("player", args[0], "duration", formatRemaining(expires), "reason", reason));
    }

    private void doUnmute(CommandSender sender, String[] args) {
        if (args.length < 1) { send(sender, "usage", Map.of("usage", "/unmute <oyuncu>")); return; }
        UUID uuid = findUuid(args[0]); if (uuid == null) { send(sender, "player-not-found", Map.of("player", args[0])); return; }
        if (mutes.remove(uuid) == null) { send(sender, "not-banned", Map.of("player", args[0])); return; }
        addHistory(uuid, new HistoryEntry(nextId(), "Susturma Kaldırma", "Susturma kaldırıldı", sender.getName(), System.currentTimeMillis())); saveFiles();
        send(sender, "unmute-success", Map.of("player", args[0]));
    }

    private void doHistory(CommandSender sender, String[] args) {
        if (args.length < 1) { send(sender, "usage", Map.of("usage", "/history <oyuncu>")); return; }
        UUID uuid = findUuid(args[0]); if (uuid == null) { send(sender, "player-not-found", Map.of("player", args[0])); return; }
        send(sender, "history-header", Map.of("player", args[0]));
        List<HistoryEntry> list = histories.getOrDefault(uuid, Collections.emptyList());
        if (list.isEmpty()) { send(sender, "history-empty", Map.of()); return; }
        list.stream().sorted(Comparator.comparingLong((HistoryEntry h) -> h.time).reversed()).limit(20).forEach(h ->
                send(sender, "history-entry", Map.of("id", h.id, "type", h.type, "reason", h.reason, "staff", h.staff, "date", dateFormatter.format(Instant.ofEpochMilli(h.time)))));
    }

    private void doBanList(CommandSender sender) {
        send(sender, "banlist-header", Map.of());
        if (bans.isEmpty() && ipBans.isEmpty()) { send(sender, "banlist-empty", Map.of()); return; }
        bans.forEach((uuid, p) -> {
            OfflinePlayer op = Bukkit.getOfflinePlayer(uuid);
            send(sender, "banlist-entry", Map.of("id", p.id, "player", op.getName() == null ? uuid.toString() : op.getName(), "type", p.expiresAt == 0 ? "Kalıcı" : "Süreli", "remaining", formatRemaining(p.expiresAt), "reason", p.reason));
        });
        ipBans.forEach((ip, p) -> send(sender, "banlist-entry", Map.of("id", p.id, "player", ip, "type", "IP", "remaining", formatRemaining(p.expiresAt), "reason", p.reason)));
    }

    private void doAdmin(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) { reloadConfig(); messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages.yml")); send(sender, "reload", Map.of()); return; }
        send(sender, "usage", Map.of("usage", "/bxban reload"));
    }

    private UUID findUuid(String name) {
        Player online = Bukkit.getPlayerExact(name); if (online != null) return online.getUniqueId();
        for (OfflinePlayer p : Bukkit.getOfflinePlayers()) if (p.getName() != null && p.getName().equalsIgnoreCase(name)) return p.getUniqueId();
        return null;
    }

    private void cleanupExpired(UUID uuid) {
        boolean changed = false;
        Punishment b = bans.get(uuid); if (isExpired(b)) { bans.remove(uuid); changed = true; }
        Punishment m = mutes.get(uuid); if (isExpired(m)) { mutes.remove(uuid); changed = true; }
        if (changed) saveFiles();
    }

    private boolean isExpired(Punishment p) { return p != null && p.expiresAt > 0 && p.expiresAt <= System.currentTimeMillis(); }

    private long parseDuration(String text) {
        if (text == null) return -1;
        if (text.equalsIgnoreCase("permanent") || text.equalsIgnoreCase("perm") || text.equalsIgnoreCase("kalıcı")) return 0;
        Matcher matcher = durationPart.matcher(text);
        long total = 0; int end = 0;
        while (matcher.find()) {
            if (matcher.start() != end) return -1;
            long n; try { n = Long.parseLong(matcher.group(1)); } catch (NumberFormatException e) { return -1; }
            long mult = switch (matcher.group(2).toLowerCase(Locale.ROOT)) { case "s" -> 1000L; case "m" -> 60000L; case "h" -> 3600000L; case "d" -> 86400000L; case "w" -> 604800000L; default -> 0; };
            try { total = Math.addExact(total, Math.multiplyExact(n, mult)); } catch (ArithmeticException e) { return -1; }
            end = matcher.end();
        }
        if (end != text.length() || total <= 0) return -1;
        return System.currentTimeMillis() + total;
    }

    private String formatRemaining(long expiresAt) {
        if (expiresAt == 0) return "Kalıcı";
        long ms = Math.max(0, expiresAt - System.currentTimeMillis());
        Duration d = Duration.ofMillis(ms); long days = d.toDays(); long hours = d.minusDays(days).toHours(); long minutes = d.minusDays(days).minusHours(hours).toMinutes();
        if (days > 0) return days + " gün " + hours + " saat";
        if (hours > 0) return hours + " saat " + minutes + " dakika";
        if (minutes > 0) return minutes + " dakika";
        return Math.max(1, d.getSeconds()) + " saniye";
    }

    private String nextId() { return Long.toString(System.currentTimeMillis()); }
    private long parseLong(Object value, long fallback) { try { return Long.parseLong(String.valueOf(value)); } catch (Exception e) { return fallback; } }
    private String join(String[] args, int from) { StringBuilder b = new StringBuilder(); for (int i = from; i < args.length; i++) { if (i > from) b.append(' '); b.append(args[i]); } return b.toString(); }

    private String banScreen(String player, Punishment p) {
        if (!getConfig().getBoolean("ban-screen.enabled", true)) return color("&cYasaklandınız! &7Sebep: &f" + p.reason);
        StringBuilder out = new StringBuilder();
        for (String line : getConfig().getStringList("ban-screen.lines")) {
            out.append(replace(line, Map.of("player", player, "reason", p.reason, "duration", formatRemaining(p.expiresAt), "staff", p.staff, "server", getConfig().getString("server-name", "Sunucu"), "appeal", getConfig().getString("appeal-url", "Ayarlanmadı"), "discord", getConfig().getString("discord-url", "Ayarlanmadı"), "id", p.id))).append("\\n");
        }
        return color(out.toString());
    }

    private void send(CommandSender sender, String key, Map<String, String> placeholders) {
        String raw = messages.getString(key, "&cMesaj bulunamadı: " + key);
        sender.sendMessage(color(replace(raw, placeholders)));
    }

    private void broadcast(String key, Map<String, String> placeholders) { Bukkit.broadcastMessage(color(replace(messages.getString(key, ""), placeholders))); }

    private String replace(String text, Map<String, String> values) { String result = text; for (Map.Entry<String, String> e : values.entrySet()) result = result.replace("%" + e.getKey() + "%", e.getValue()); return result; }
    private String color(String text) { return ChatColor.translateAlternateColorCodes('&', text.replace("\\n", "\n")); }

    private boolean isValidIp(String ip) {
        try { InetAddress.getByName(ip); return ip.chars().filter(c -> c == '.').count() == 3 || ip.contains(":"); } catch (Exception e) { return false; }
    }

    private void addHistory(UUID uuid, HistoryEntry entry) { histories.computeIfAbsent(uuid, k -> new ArrayList<>()).add(entry); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("bxban") && args.length == 1) return List.of("reload");
        if (List.of("ban", "tempban", "unban", "kick", "warn", "mute", "unmute", "history").contains(command.getName().toLowerCase(Locale.ROOT)) && args.length == 1) {
            List<String> names = new ArrayList<>(); for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName()); return names;
        }
        if (command.getName().equalsIgnoreCase("tempban") && args.length == 2) return List.of("30m", "1h", "1d", "7d", "30d");
        if (command.getName().equalsIgnoreCase("mute") && args.length == 2) return List.of("10m", "1h", "1d", "7d");
        return Collections.emptyList();
    }

    private record Punishment(String id, String reason, String staff, long createdAt, long expiresAt) {}
    private record HistoryEntry(String id, String type, String reason, String staff, long time) {}
}
