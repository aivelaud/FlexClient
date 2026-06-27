package com.flex.client;

  import com.flex.client.module.ModuleManager;
  import net.minecraft.client.MinecraftClient;
  import net.minecraft.text.Text;

  import java.io.File;
  import java.nio.file.*;
  import java.util.*;

  /**
   * FlexClient CrashGuard — PojavLauncher çökme önleme sistemi.
   *
   * Nasıl çalışır:
   *  1. Başlangıç: kirli dosya varsa → aktif modüller kara listeye alınır.
   *  2. Her 100 tick: aktif modüller kirli dosyaya yazılır.
   *  3. Normal kapanış: dosya silinir.
   *  4. Runtime: runSafe() ile modül hataları yakalanır, oyun çökmez.
   */
  public class CrashGuard {

      private static final Set<String> blacklist = new LinkedHashSet<>();
      private static int  tickCounter  = 0;
      private static boolean initialized = false;
      private static Path crashFilePath = null;

      private static Path getCrashFile() {
          if (crashFilePath != null) return crashFilePath;
          try {
              MinecraftClient mc = MinecraftClient.getInstance();
              File runDir = (mc != null && mc.runDirectory != null) ? mc.runDirectory : new File(".");
              crashFilePath = runDir.toPath().resolve("flexclient_crash.dat");
          } catch (Exception e) {
              crashFilePath = Path.of("flexclient_crash.dat");
          }
          return crashFilePath;
      }

      public static void initialize() {
          if (initialized) return;
          initialized = true;
          checkPreviousCrash();
          Runtime.getRuntime().addShutdownHook(new Thread(() -> {
              try { Files.deleteIfExists(getCrashFile()); } catch (Exception ignored) {}
          }));
      }

      private static void checkPreviousCrash() {
          Path p = getCrashFile();
          if (!Files.exists(p)) return;
          try {
              List<String> lines = Files.readAllLines(p);
              if (!lines.isEmpty() && "DIRTY".equals(lines.get(0).trim())) {
                  for (int i = 1; i < lines.size(); i++) {
                      String mod = lines.get(i).trim();
                      if (!mod.isEmpty()) blacklist.add(mod);
                  }
                  for (String name : blacklist) ModuleManager.forceDisable(name);
              }
              Files.delete(p);
          } catch (Exception ignored) {}
      }

      /** Her oyun tick'inde çağrılır. */
      public static void tick() {
          if (!initialized) initialize();
          if (++tickCounter % 100 != 0) return;
          writeDirtyFile();
      }

      private static void writeDirtyFile() {
          try {
              StringBuilder sb = new StringBuilder("DIRTY\n");
              for (com.flex.client.module.Module m : ModuleManager.modules) {
                  if (m.isEnabled()) sb.append(m.getName()).append("\n");
              }
              Path p = getCrashFile();
              if (p.getParent() != null) Files.createDirectories(p.getParent());
              Files.writeString(p, sb.toString());
          } catch (Exception ignored) {}
      }

      /**
       * Modül bloğunu try-catch ile çalıştırır.
       * Hata fırlarsa sadece o modül devre dışı bırakılır.
       */
      public static void runSafe(String moduleName, Runnable action) {
          if (blacklist.contains(moduleName)) return;
          try {
              action.run();
          } catch (Throwable e) {
              onModuleCrash(moduleName, e);
          }
      }

      public static void onModuleCrash(String moduleName, Throwable e) {
          if (blacklist.contains(moduleName)) return;
          blacklist.add(moduleName);
          ModuleManager.forceDisable(moduleName);
          writeDirtyFile();
          try {
              MinecraftClient mc = MinecraftClient.getInstance();
              if (mc != null && mc.player != null) {
                  mc.player.sendMessage(Text.literal(
                      "§4[FlexClient] §c" + moduleName +
                      " §fhata verdi ve devre dışı bırakıldı."), false);
                  mc.player.sendMessage(Text.literal(
                      "§7  → GUI'den yeniden deneyebilirsiniz."), false);
              }
          } catch (Exception ignored) {}
      }

      public static boolean isSafe(String moduleName) { return !blacklist.contains(moduleName); }
      public static Set<String> getBlacklist() { return Collections.unmodifiableSet(blacklist); }

      /** Kara listeyi temizler — GUI'den çağrılabilir. */
      public static void clearBlacklist() {
          blacklist.clear();
          try { Files.deleteIfExists(getCrashFile()); } catch (Exception ignored) {}
          try {
              MinecraftClient mc = MinecraftClient.getInstance();
              if (mc != null && mc.player != null)
                  mc.player.sendMessage(Text.literal("§a[FlexClient] Kara liste temizlendi."), false);
          } catch (Exception ignored) {}
      }
  }
  