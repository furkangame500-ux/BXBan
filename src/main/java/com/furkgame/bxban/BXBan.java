package com.furkgame.bxban;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class BXBan extends JavaPlugin implements Listener, CommandExecutor {
    private File bansFile, mutesFile, historyFile;
    private FileConfiguration bans, mutes, history;
    private FileConfiguration messages;
    private final Map<String, Long> muteCache = new HashMap<String, Long>();

    @Override public void onEnable() {
        saveDefaultConfig();
        setupFiles();
        saveResourceIfMissing("messages/messages.yml");
        loadAll();
        getServer().getPluginManager().registerEvents(this, this);
        String[] commands = {"ban","tempban","unban","banip","unbanip","kick","mute","unmute","warn","history","banlist","bxban"};
        for (String c : commands) if (getCommand(c) != null) getCommand(c).setExecutor(this);
        getLogger().info("BXBan 1.0.0 aktif. Türkçe ceza sistemi hazır.");
    }

    private void setupFiles() {
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        bansFile = new File(getDataFolder(), "bans.yml");
        mutesFile = new File(getDataFolder(), "mutes.yml");
        historyFile = new File(getDataFolder(), "history.yml");
        try { if (!bansFile.exists()) bansFile.createNewFile(); if (!mutesFile.exists()) mutesFile.createNewFile(); if (!historyFile.exists()) historyFile.createNewFile(); }
        catch (IOException e) { getLogger().severe("Veri dosyaları oluşturulamadı: " + e.getMessage()); }
    }

    private void saveResourceIfMissing(String path) { File f = new File(getDataFolder(), path); if (!f.exists()) saveResource(path, false); }
    private void loadAll() {
        bans = YamlConfiguration.loadConfiguration(bansFile);
        mutes = YamlConfiguration.loadConfiguration(mutesFile);
        history = YamlConfiguration.loadConfiguration(historyFile);
        messages = YamlConfiguration.loadConfiguration(new File(getDataFolder(), "messages/messages.yml"));
        muteCache.clear();
        ConfigurationSection s = mutes.getConfigurationSection("players");
        if (s != null) for (String key : s.getKeys(false)) muteCache.put(key.toLowerCase(), mutes.getLong("players." + key + ".expires", 0));
    }
    private void saveAll() { try { bans.save(bansFile); mutes.save(mutesFile); history.save(historyFile); } catch (IOException e) { getLogger().severe("Veriler kaydedilemedi: " + e.getMessage()); } }
    private String msg(String key, Map<String,String> vars) {
        String s = messages.getString(key, "&cMesaj bulunamadı: " + key);
        s = s.replace("%prefix%", messages.getString("prefix", ""));
        if (vars != null) for (Map.Entry<String,String> e : vars.entrySet()) s = s.replace("%" + e.getKey() + "%", e.getValue() == null ? "" : e.getValue());
        return ChatColor.translateAlternateColorCodes('&', s);
    }
    private void send(CommandSender sender, String key, Map<String,String> vars) { sender.sendMessage(msg(key, vars)); }
    private Map<String,String> v(String... a) { Map<String,String> m = new HashMap<String,String>(); for (int i=0;i+1<a.length;i+=2) m.put(a[i], a[i+1]); return m; }
    private String reason(String[] args, int from) { if (args.length <= from) return ""; StringBuilder b = new StringBuilder(); for (int i=from;i<args.length;i++) { if (i>from) b.append(' '); b.append(args[i]); } return b.toString(); }
    private String clean(String s) { return s == null ? "" : s.replace("\n", " ").replace("\r", " "); }

    @EventHandler public void onLogin(PlayerLoginEvent e) {
        String key = e.getPlayer().getUniqueId().toString();
        String ip = e.getAddress() == null ? "" : e.getAddress().getHostAddress();
        BanEntryData b = getBan(key);
        BanEntryData ipBan = getBan("ip:" + ip);
        if (b != null || ipBan != null) {
            BanEntryData x = b != null ? b : ipBan;
            if (x.expires > 0 && x.expires <= System.currentTimeMillis()) { removeBan(x.key); return; }
            String screen = buildBanScreen(x);
            e.disallow(PlayerLoginEvent.Result.KICK_BANNED, screen);
        }
    }

    @EventHandler public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();
        Long exp = muteCache.get(p.getName().toLowerCase());
        if (exp == null) return;
        if (exp > 0 && exp <= System.currentTimeMillis()) { muteCache.remove(p.getName().toLowerCase()); mutes.set("players." + p.getName(), null); saveAll(); return; }
        e.setCancelled(true);
        p.sendMessage(msg("chat-muted", v("duration", formatDuration(exp))));
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) { /* reserved for future cleanup */ }

    private String buildBanScreen(BanEntryData x) {
        if (!getConfig().getBoolean("ban-screen.enabled", true)) return msg("ban-created", v("player", x.name, "reason", x.reason));
        String duration = x.expires == 0 ? getConfig().getString("ban-screen.permanent-duration", "Kalıcı") : formatDuration(x.expires);
        List<String> lines = getConfig().getStringList("ban-screen.lines");
        StringBuilder out = new StringBuilder();
        for (String line : lines) {
            line = line.replace("%reason%", x.reason).replace("%duration%", duration).replace("%operator%", x.operator).replace("%id%", x.id).replace("%player%", x.name);
            out.append(ChatColor.translateAlternateColorCodes('&', line)).append('\n');
        }
        return out.toString();
    }

    private BanEntryData getBan(String key) {
        String base = "players." + key;
        if (!bans.contains(base)) return null;
        BanEntryData x = new BanEntryData(); x.key = key; x.name = bans.getString(base+".name", key); x.reason = bans.getString(base+".reason", "Belirtilmedi"); x.operator = bans.getString(base+".operator", "Sistem"); x.expires = bans.getLong(base+".expires", 0); x.id = bans.getString(base+".id", "?"); x.type = bans.getString(base+".type", "BAN"); return x;
    }
    private void putBan(String key, String name, String reason, long expires, String operator, String type) {
        String id = String.valueOf(Math.abs(UUID.randomUUID().getLeastSignificantBits()));
        String base = "players." + key;
        bans.set(base+".name", name); bans.set(base+".reason", clean(reason)); bans.set(base+".expires", expires); bans.set(base+".operator", operator); bans.set(base+".id", id); bans.set(base+".type", type); bans.set(base+".created", System.currentTimeMillis());
        addHistory(name, type, reason, operator, expires, id);
        saveAll();
    }
    private void removeBan(String key) { bans.set("players." + key, null); saveAll(); }
    private void addHistory(String name, String type, String reason, String operator, long expires, String id) {
        String path = "records." + System.currentTimeMillis() + ".";
        history.set(path+"name", name); history.set(path+"type", type); history.set(path+"reason", clean(reason)); history.set(path+"operator", operator); history.set(path+"expires", expires); history.set(path+"id", id); history.set(path+"time", System.currentTimeMillis());
    }
    private long parseDuration(String s) {
        if (s == null) return -1; s=s.toLowerCase(); if (s.equals("kalıcı") || s.equals("permanent") || s.equals("perm")) return 0;
        try { char unit=s.charAt(s.length()-1); long n=Long.parseLong(s.substring(0,s.length()-1)); switch(unit){case 's':return TimeUnit.SECONDS.toMillis(n);case 'm':return TimeUnit.MINUTES.toMillis(n);case 'h':return TimeUnit.HOURS.toMillis(n);case 'd':return TimeUnit.DAYS.toMillis(n);case 'w':return TimeUnit.DAYS.toMillis(n*7);default:return -1;} } catch(Exception e){ return -1; }
    }
    private String formatDuration(long expires) { if (expires == 0) return "Kalıcı"; long left=Math.max(0, expires-System.currentTimeMillis()); long d=TimeUnit.MILLISECONDS.toDays(left); left-=TimeUnit.DAYS.toMillis(d); long h=TimeUnit.MILLISECONDS.toHours(left); left-=TimeUnit.HOURS.toMillis(h); long m=TimeUnit.MILLISECONDS.toMinutes(left); left-=TimeUnit.MINUTES.toMillis(m); long s=TimeUnit.MILLISECONDS.toSeconds(left); if(d>0)return d+" gün "+h+" saat"; if(h>0)return h+" saat "+m+" dakika"; if(m>0)return m+" dakika "+s+" saniye"; return s+" saniye"; }
    private boolean protectedPlayer(Player p, CommandSender sender) { return p != null && getConfig().getBoolean("settings.protect-operators", true) && p.isOp() && !sender.isOp(); }
    private boolean perm(CommandSender s, String p) { if (s.hasPermission(p)) return true; send(s,"no-permission",null); return false; }
    private Player findPlayer(String name) { return Bukkit.getPlayerExact(name); }

    @Override public boolean onCommand(CommandSender s, Command c, String label, String[] a) {
        String n=c.getName().toLowerCase();
        if(n.equals("bxban")){ if(!perm(s,"bxban.reload"))return true; if(a.length==1&&a[0].equalsIgnoreCase("reload")){reloadConfig();loadAll();send(s,"reloaded",null);} else send(s,"usage",v("usage","/bxban reload")); return true; }
        if(n.equals("ban")) { if(!perm(s,"bxban.ban"))return true; if(a.length<2){send(s,"usage",v("usage","/ban <oyuncu> <sebep>"));return true;} return ban(s,a,false); }
        if(n.equals("tempban")){if(!perm(s,"bxban.tempban"))return true;if(a.length<3){send(s,"usage",v("usage","/tempban <oyuncu> <süre> <sebep>"));return true;}return ban(s,a,true);}
        if(n.equals("unban")){if(!perm(s,"bxban.unban"))return true;if(a.length<1){send(s,"usage",v("usage","/unban <oyuncu>"));return true;}String key=findKeyByName(a[0]);if(key==null){send(s,"not-banned",null);return true;}removeBan(key);send(s,"unbanned",v("player",a[0]));return true;}
        if(n.equals("banip")){if(!perm(s,"bxban.banip"))return true;if(a.length<2){send(s,"usage",v("usage","/banip <oyuncu> <sebep>"));return true;}Player p=findPlayer(a[0]);if(p==null||p.getAddress()==null){send(s,"player-not-found",v("player",a[0]));return true;}if(protectedPlayer(p,s)){send(s,"protected",null);return true;}String ip=p.getAddress().getAddress().getHostAddress();putBan("ip:"+ip,p.getName(),reason(a,1),0,s.getName(),"IPBAN");p.kickPlayer(buildBanScreen(getBan("ip:"+ip)));send(s,"ipbanned",v("player",p.getName()));return true;}
        if(n.equals("unbanip")){if(!perm(s,"bxban.unbanip"))return true;if(a.length<1){send(s,"usage",v("usage","/unbanip <ip>"));return true;}removeBan("ip:"+a[0]);send(s,"ipunbanned",v("ip",a[0]));return true;}
        if(n.equals("kick")){if(!perm(s,"bxban.kick"))return true;if(a.length<2){send(s,"usage",v("usage","/kick <oyuncu> <sebep>"));return true;}Player p=findPlayer(a[0]);if(p==null){send(s,"player-not-found",v("player",a[0]));return true;}if(protectedPlayer(p,s)){send(s,"protected",null);return true;}p.kickPlayer(ChatColor.translateAlternateColorCodes('&',"&c&lATILDINIZ\n\n&7Sebep: &f"+reason(a,1)));send(s,"kicked",v("player",p.getName()));return true;}
        if(n.equals("mute")){if(!perm(s,"bxban.mute"))return true;if(a.length<3){send(s,"usage",v("usage","/mute <oyuncu> <süre|kalıcı> <sebep>"));return true;}Player p=findPlayer(a[0]);if(p==null){send(s,"player-not-found",v("player",a[0]));return true;}if(protectedPlayer(p,s)){send(s,"protected",null);return true;}if(muteCache.containsKey(p.getName().toLowerCase())){send(s,"already-muted",null);return true;}long d=parseDuration(a[1]);if(d<0){send(s,"invalid-duration",null);return true;}long ex=d==0?0:System.currentTimeMillis()+d;muteCache.put(p.getName().toLowerCase(),ex);mutes.set("players."+p.getName()+".expires",ex);mutes.set("players."+p.getName()+".reason",reason(a,2));mutes.set("players."+p.getName()+".operator",s.getName());addHistory(p.getName(),"MUTE",reason(a,2),s.getName(),ex,String.valueOf(Math.abs(UUID.randomUUID().getLeastSignificantBits())));saveAll();send(s,"muted",v("player",p.getName()));return true;}
        if(n.equals("unmute")){if(!perm(s,"bxban.unmute"))return true;if(a.length<1){send(s,"usage",v("usage","/unmute <oyuncu>"));return true;}muteCache.remove(a[0].toLowerCase());mutes.set("players."+a[0],null);saveAll();send(s,"unmuted",v("player",a[0]));return true;}
        if(n.equals("warn")){if(!perm(s,"bxban.warn"))return true;if(a.length<2){send(s,"usage",v("usage","/warn <oyuncu> <sebep>"));return true;}Player p=findPlayer(a[0]);if(p==null){send(s,"player-not-found",v("player",a[0]));return true;}addHistory(p.getName(),"WARN",reason(a,1),s.getName(),0,String.valueOf(Math.abs(UUID.randomUUID().getLeastSignificantBits())));saveAll();send(s,"warned",v("player",p.getName()));p.sendMessage(msg("warned",v("player",p.getName())));return true;}
        if(n.equals("history")){if(!perm(s,"bxban.history"))return true;if(a.length<1){send(s,"usage",v("usage","/history <oyuncu>"));return true;}s.sendMessage(ChatColor.translateAlternateColorCodes('&',messages.getString("history-title").replace("%player%",a[0])));ConfigurationSection r=history.getConfigurationSection("records");boolean any=false;if(r!=null)for(String k:r.getKeys(false)){String name=r.getString(k+".name","");if(name.equalsIgnoreCase(a[0])){any=true;s.sendMessage(msg("history-line",v("id",r.getString(k+".id","?"),"type",r.getString(k+".type","?"),"reason",r.getString(k+".reason",""),"operator",r.getString(k+".operator",""),"duration",r.getLong(k+".expires",0)==0?"Kalıcı":formatDuration(r.getLong(k+".expires",0)))));}}if(!any)send(s,"history-empty",null);return true;}
        if(n.equals("banlist")){if(!perm(s,"bxban.banlist"))return true;s.sendMessage(ChatColor.translateAlternateColorCodes('&',messages.getString("banlist-header")));ConfigurationSection ps=bans.getConfigurationSection("players");boolean any=false;if(ps!=null)for(String k:ps.getKeys(false)){BanEntryData x=getBan(k);if(x!=null){if(x.expires>0&&x.expires<=System.currentTimeMillis()){removeBan(k);continue;}any=true;s.sendMessage(msg("banlist-line",v("id",x.id,"player",x.name,"reason",x.reason,"duration",x.expires==0?"Kalıcı":formatDuration(x.expires))));}}if(!any)send(s,"banlist-empty",null);return true;}
        return false;
    }

    private boolean ban(CommandSender s,String[] a,boolean temp){Player p=findPlayer(a[0]);if(p==null){send(s,"player-not-found",v("player",a[0]));return true;}if(protectedPlayer(p,s)){send(s,"protected",null);return true;}String key=p.getUniqueId().toString();if(getBan(key)!=null){send(s,"already-banned",null);return true;}long ex=0;if(temp){long d=parseDuration(a[1]);if(d<0){send(s,"invalid-duration",null);return true;}ex=d==0?0:System.currentTimeMillis()+d;}int from=temp?2:1;String r=reason(a,from);if(r.isEmpty()){send(s,"reason-required",null);return true;}if(r.length()>getConfig().getInt("settings.max-reason-length",120))r=r.substring(0,getConfig().getInt("settings.max-reason-length",120));putBan(key,p.getName(),r,ex,s.getName(),temp?"TEMPBAN":"BAN");BanEntryData x=getBan(key);p.kickPlayer(buildBanScreen(x));send(s,temp?"tempban-created":"ban-created",v("player",p.getName(),"reason",r,"duration",ex==0?"Kalıcı":formatDuration(ex)));if(getConfig().getBoolean("settings.notify-staff",true))notifyStaff(s,p,temp?"TEMPBAN":"BAN",r);return true;}
    private void notifyStaff(CommandSender actor, Player target, String type, String reason){for(Player p:Bukkit.getOnlinePlayers())if(p.hasPermission("bxban.ban"))p.sendMessage(msg("staff-notify",v("operator",actor.getName(),"player",target.getName(),"type",type,"reason",reason)));}
    private String findKeyByName(String name){ConfigurationSection ps=bans.getConfigurationSection("players");if(ps==null)return null;for(String k:ps.getKeys(false)){if(k.startsWith("ip:"))continue;if(bans.getString("players."+k+".name","").equalsIgnoreCase(name))return k;}return null;}
    private static class BanEntryData { String key,name,reason,operator,id,type; long expires; }
}
