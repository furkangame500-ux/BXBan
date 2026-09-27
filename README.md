# BXBan

BXBan, Minecraft sunucuları için Türkçe ve özelleştirilebilir bir ceza yönetim eklentisidir.

## Hedef uyumluluk
- Spigot / Paper / Bukkit tabanlı sunucular
- Tek JAR hedefi: Bukkit API'nin eski ortak yüzeyini kullanır
- Minimum hedef: Minecraft 1.11.2
- Modern hedef: Minecraft 26.3
- Java bytecode hedefi: Java 8; sunucunun kendi Java gereksinimi ayrıca geçerlidir.

> Çok geniş sürüm aralığında garanti, kullanılan ortak Bukkit API ile sınırlıdır. NMS veya sürüme özel sınıflar kullanılmaz.

## Komutlar
`/ban`, `/tempban`, `/unban`, `/banip`, `/unbanip`, `/kick`, `/mute`, `/unmute`, `/warn`, `/history`, `/banlist`, `/bxban reload`

Türkçe aliaslar da bulunur: `/yasakla`, `/geciciyasakla`, `/yasakkaldir`, `/ipyasakla`, `/at`, `/sustur`, `/susturmayikaldir`, `/uyar`, `/gecmis`, `/yasaklilar`.

## Derleme
Maven 3.9+ ile:

```bash
mvn clean package
```

Çıktı: `target/BXBan-1.0.0.jar`
