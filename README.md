# FlexClient 4.0

**Minecraft 1.20.1 Fabric Hack Client**

---

## Kurulum

1. Fabric Loader `>=0.14.22` ve Fabric API `0.83.0+1.20.1` yükle
2. `flexclient-4.0.jar` dosyasını `.minecraft/mods/` klasörüne at
3. Oyunu başlat → **Sağ Shift** ile ClickGUI aç

---

## Kontroller

| Tuş | Eylem |
|-----|-------|
| **Sağ Shift** | ClickGUI aç/kapat |
| **Modül Keybind** | Modülü toggle et |

---

## Modüller

### ⚔️ Combat
| Modül | Açıklama |
|-------|----------|
| KillAura | Yakın entitelere otomatik saldırı (Single/Multi/Switch) |
| CrystalAura | End Crystal otomatik yerleştir & patlat (Smart/Suicide/Safe) |
| AimAssist | Hedeflere yumuşak nişan |
| TriggerBot | Crosshair'daki hedefe otomatik atış |
| Velocity | Geri itilme miktarını ayarla |
| AntiKnockback | Geri itilmeyi tamamen iptal et |
| Criticals | Her vuruşu kritik yap (Jump/Packet/Always) |
| Reach | Etkileşim menzilini artır |
| AutoTotem | Ölüm anında totemi offhand'e taşı ✨ |
| AutoGap | Düşük canda golden apple ye |
| AutoWeapon | En iyi silahı seç |
| AutoArmor | En iyi zırhı giy |
| BowAimbot | Yay çekerken hedefe otomatik nişan 🆕 |
| Surround | Etrafına obsidyen/blok yerleştir 🆕 |
| ClickAura | Tıklarken hedefe otomatik saldırı 🆕 |

### 🏃 Movement
| Modül | Açıklama |
|-------|----------|
| Fly | Serbestçe uç (Vanilla/Packet/Creative) |
| Speed | Hızı artır (Strafe/Ground/YPort) |
| Sprint | Sürekli koş |
| NoFall | Düşme hasarını engelle |
| BunnyHop | Zıplayarak hız kazanma |
| LongJump | Çok uzağa zıpla |
| Step | Yüksek bloklara direkt çık |
| Jesus | Su/lav üzerinde yürü |
| Scaffold | Ayak altına blok döşe |
| SafeWalk | Kenarlardan düşme |
| NoSlow | Yemek/ok çekerken yavaşlama |
| AntiVoid | Void'e düşmeyi engelle |
| Parkour | Otomatik zıpla |
| ElytraFly | Elitra ile stabil uçuş |
| Spider | Duvarlara tırman |
| HighJump | Çok yüksek zıpla |
| AntiAFK | AFK atılmayı engelle |
| FastLadder | Merdivende çok hızlı tırman |
| Clip | Bloklardan geç |
| VClip | Dikey bloklardan geç |
| BetterSprint | Sprint tüm yönlere 🆕 |
| Blink | Paketleri biriktir, toplu gönder 🆕 |
| PacketFly | Paket tabanlı uçuş 🆕 |

### 🎮 Player
| Modül | Açıklama |
|-------|----------|
| AutoEat | Otomatik yemek ye |
| Regen | Canı yenile |
| FastPlace | Blok yerleşimini hızlandır |
| SpeedMine | Blok kırma hızını artır |
| AutoTool | En iyi aleti seç |
| VeinMiner | Tüm cevher damarını kır |
| AutoLog | Düşük canda çıkış yap |
| AntiHunger | Açlığı sıfırla |
| InvWalk | Envanter açıkken yürü |
| Nuker | Etraftaki blokları otomatik kır |
| ChestStealer | Sandıktan otomatik eşya al |
| Multitask | Madencilik sırasında yemek ye |

### 🎨 Render
| Modül | Açıklama |
|-------|----------|
| Xray | Cevher/sandık/spawner göster |
| Fullbright | Karanlıkta tam parlaklık |
| ESP | Oyuncular/moblar etrafında kutu (Box/Outline/Corner) |
| Tracers | Oyunculara/sandıklara çizgi |
| StorageESP | Sandık/barrel/shulker vurgula |
| HoleESP | Obsidyen/bedrock deliklerini vurgula ✨ |
| NameTags | Oyuncu isimlerini büyük göster |
| Chams | Düşmanları duvar arkasından göster |
| NoHurtCam | Hasar alınca ekran titremesini engelle |
| Zoom | Yakınlaştır |
| HandView | El animasyonlarını özelleştir |
| FreeLook | Bakış açısını serbestçe döndür |
| NoRender | Gereksiz efektleri kaldır |

### 🌍 World
| Modül | Açıklama |
|-------|----------|
| Timer | Oyun hızını değiştir (0.5x – 10x) 🆕 |

### ⚙️ Misc
| Modül | Açıklama |
|-------|----------|
| AutoReconnect | Sunucudan atılınca yeniden bağlan 🆕 |

---

## 4.0 Yenilikleri

- ✅ **ConfigManager** — JSON config sistemi. Tüm ayarlar ve keybind'ler `.minecraft/config/flexclient/config.json` dosyasına kaydedilir
- ✅ **Keybind Sistemi** — Her modüle tuş atanabilir, oyun içi toggle çalışır
- ✅ **HoleESP** — Obsidyen/bedrock deliklerini renk kodlamalı vurgular (yeşil=bedrock, mavi=obsidyen)
- ✅ **AutoTotemMixin** — Gelişmiş mixin; offhand'de totem yokken envanterdeki ilk totemi paket ile anında taşır
- ✅ **VelocityMixin** — Sunucu taraflı geri itilme paketlerini intercept eder
- ✅ **Yeni Modüller** — Timer, BowAimbot, Surround, AutoReconnect, BetterSprint, Blink, PacketFly, ClickAura
- ✅ **CrashGuard** — Çöken modülü devre dışı bırakır, oyunu korur

---

## Derleme

```bash
./gradlew build
```

Çıktı: `build/libs/flexclient-4.0.0.jar`

---

## Stack

- Minecraft **1.20.1**
- Fabric Loader `0.15.11`
- Fabric API `0.83.0+1.20.1`
- Java 17
- Yarn Mappings `1.20.1+build.10`
