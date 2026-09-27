# BXBan

BXBan, Minecraft sunucuları için tamamen Türkçe hazırlanmış bir ceza ve moderasyon eklentisidir.

## Özellikler

- `/ban <oyuncu> <sebep>` — Kalıcı yasak
- `/tempban <oyuncu> <süre> <sebep>` — Süreli yasak
- `/unban <oyuncu>` — Yasağı kaldırma
- `/banip <ip> <sebep>` — IP yasağı
- `/unbanip <ip>` — IP yasağını kaldırma
- `/kick <oyuncu> [sebep]` — Oyuncuyu atma
- `/warn <oyuncu> <sebep>` — Uyarı sistemi
- `/mute <oyuncu> <süre> <sebep>` — Sohbet susturma
- `/unmute <oyuncu>` — Susturmayı kaldırma
- `/history <oyuncu>` — Ceza geçmişi
- `/banlist` — Aktif yasaklar
- `/bxban reload` — Yapılandırmayı yenileme

## Süre biçimleri

`30s`, `10m`, `2h`, `7d`, `2w` kullanılabilir. Birleştirme de desteklenir: `1d12h`.

## Yasak ekranı

Yasaklanan oyuncuya özel bir ekran gösterilir. Ekranda sebep, süre, yetkili, itiraz adresi, Discord adresi ve ceza ID'si bulunur.

## Depolama

Veriler plugin klasöründeki YAML dosyalarında tutulur:

- `bans.yml`
- `ip-bans.yml`
- `mutes.yml`
- `history.yml`

## Logo

`logo.webp` ve `icon.png` proje logosu olarak eklenmiştir.

## Lisans

BXBan bu proje içinde sıfırdan geliştirilmiştir. Kullanılan logo kullanıcı tarafından sağlanmıştır.

---

## English Description

# BXBan

**BXBan** is a lightweight, customizable, and easy-to-use punishment management plugin designed for Minecraft servers. It provides a complete moderation system with bans, temporary bans, mutes, warnings, kicks, IP bans, punishment history, and a fully customizable punishment screen.

BXBan focuses on keeping server moderation simple, clean, and efficient while giving server owners full control over messages and punishment settings.

## ✨ Features

* 🔨 Permanent player bans
* ⏱️ Temporary bans with customizable durations
* 🌐 IP bans and IP unbans
* 🔓 Unban system
* 👢 Player kick system
* 🔇 Mute and unmute system
* ⚠️ Warning system
* 📜 Player punishment history
* 📋 Ban list
* 🆔 Punishment IDs
* 👤 Displays the moderator who issued the punishment
* 📝 Customizable punishment reasons
* 🖥️ Fully customizable ban screen
* 💬 Fully customizable messages
* 🇹🇷 Turkish language support
* ⚙️ Configuration file
* 🔄 Reload system
* 🔐 Permission-based commands
* 💾 Persistent punishment data
* 🎨 Customizable colors and formatting
* 🚀 Lightweight and optimized

## 🔨 Commands

```text
/ban <player> <reason>
/tempban <player> <duration> <reason>
/unban <player>
/banip <player> <reason>
/unbanip <player>
/kick <player> <reason>
/warn <player> <reason>
/mute <player> <duration> <reason>
/unmute <player>
/history <player>
/banlist
/bxban reload
```

## 🖥️ Custom Ban Screen

When a player is banned, BXBan can display a clean and informative screen containing:

```text
━━━━━━━━━━━━━━━━━━━━━━

       YASAKLANDINIZ

Sebep: Hile Kullanımı
Süre: 7 Gün
Yetkili: Moderator

Ceza ID: #123456

İtiraz: example.com/appeal
Discord: discord.gg/example

━━━━━━━━━━━━━━━━━━━━━━
```

Everything shown on the punishment screen can be customized through the configuration.

## ⚙️ Customization

BXBan provides configuration files that allow server owners to customize:

* Punishment messages
* Ban screen
* Reasons
* Durations
* Colors
* Prefixes
* Permission messages
* Reload messages
* Appeal information
* Discord information

## 🔐 Permissions

BXBan uses permissions to control access to moderation commands. This makes it easy to create different permissions for administrators, moderators, helpers, and other staff members.

## 🌍 Compatibility

BXBan is designed for Bukkit-based Minecraft servers and aims to support a wide range of Minecraft versions.

**Supported software:**

* Paper
* Spigot
* Bukkit

**Target version range:**
**Minecraft 1.16.5 → 26.x**

> Compatibility may vary depending on the Minecraft/server version and API changes between releases.

## 📦 Installation

1. Download the latest BXBan `.jar`.
2. Place it inside your server's `plugins` folder.
3. Restart your server.
4. Configure `plugins/BXBan/`.
5. Customize your messages and punishment screen.
6. Restart or use the reload command.

## 💙 Open Source

BXBan is open source. The source code is available for developers who want to inspect, modify, improve, or contribute to the project.

---

### Short Summary

**BXBan is a lightweight and customizable Minecraft punishment plugin featuring bans, tempbans, mutes, warnings, kicks, IP bans, punishment history, and a fully customizable punishment screen.**
