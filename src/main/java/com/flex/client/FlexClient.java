package com.flex.client;

import com.flex.client.config.ConfigManager;
import com.flex.client.gui.ClickGui;
import com.flex.client.gui.CrashGuardScreen;
import com.flex.client.module.Module;
import com.flex.client.module.ModuleManager;
import com.flex.client.render.ESPRenderer;
import com.flex.client.xray.AntiXrayBypass;
import com.flex.client.xray.ChunkOreScanner;
import com.flex.client.xray.XrayHUD;
import com.flex.client.xray.XrayRealOreRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientChunkEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import java.lang.reflect.Method;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtInt;
import net.minecraft.nbt.NbtString;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlexClient implements ClientModInitializer {

    public static final String MOD_ID  = "flexclient";
    public static final String VERSION = "4.0";
    public static FlexClient INSTANCE;

    private static final int GUI_KEY    = GLFW.GLFW_KEY_RIGHT_SHIFT;
    private static boolean wasGuiKey    = false;
    private static boolean wasMouseDown = false;
    private static final Map<Integer, Boolean> keybindState = new HashMap<>();

    private static int worldCopyCooldown = 0;
    private static int pasteCooldown = 0;
    private static final List<NbtCompound> copiedBlocks = new ArrayList<>();
    private static final List<NbtCompound> copiedEntities = new ArrayList<>();
    private static boolean mixRunning = false;
    private static int mixPhase = 0;

    // ── Paste state ──────────────────────────────────────────────────
    private static int pasteOriginX = 0;
    private static int pasteOriginY = 0;
    private static int pasteOriginZ = 0;
    private static int pasteIndex = 0;
    private static String pasteFileName = null;
    private static boolean pasteLoaded = false;

    private static final int BTN_X = 4, BTN_Y = 4, BTN_W = 72, BTN_H = 14;

    @Override
    public void onInitializeClient() {
        INSTANCE = this;
        ModuleManager.init();
        ConfigManager.load();   // ← config JSON'dan ayarları yükle
        CrashGuard.initialize();

        // ── Kapanışta config kaydet ──────────────────────────────────────
        Runtime.getRuntime().addShutdownHook(new Thread(ConfigManager::save));

        // ── Sunucuya bağlanınca ─────────────────────────────────────────
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            ChunkOreScanner.clearAll();
            AntiXrayBypass.reset();
            client.execute(() -> {
                if (!CrashGuard.getBlacklist().isEmpty()) {
                    // Önceki oturumda çöken modüller var — uyarı ekranı göster
                    client.setScreen(new CrashGuardScreen());
                } else {
                    client.setScreen(new ClickGui());
                }
            });
        });

        // ── Sunucudan ayrılınca ─────────────────────────────────────────
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ChunkOreScanner.clearAll();
            AntiXrayBypass.reset();
            ConfigManager.save();   // ← ayrılırken config kaydet
        });

        // ── Chunk kaldırılınca: cache temizle ───────────────────────────
        ClientChunkEvents.CHUNK_UNLOAD.register((world, chunk) -> {
            ChunkOreScanner.onChunkUnloaded(chunk.getPos());
            AntiXrayBypass.invalidateChunk(chunk.getPos());
        });

        // ── WorldRender: ESP + Tracers + StorageESP + XrayGerçekCevher ─
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            try { ESPRenderer.render(context); } catch (Exception ignored) {}
            try { XrayRealOreRenderer.render(context); } catch (Exception ignored) {}
        });

        // ── HUD Render ─────────────────────────────────────────────────
        HudRenderCallback.EVENT.register((DrawContext ctx, float tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.currentScreen != null || client.world == null) return;

            renderWatermark(ctx, client);
            renderModuleList(ctx, client);
            renderNameTags(ctx, client, tickDelta);

            XrayHUD.render(ctx, tickDelta);
            XrayHUD.renderYLevelGuide(ctx, client.textRenderer,
                client.getWindow().getScaledWidth(),
                client.player != null ? client.player.getBlockPos().getY() : 0);
        });

        // ── Client Tick ────────────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world == null) return;

            CrashGuard.tick();
            XrayHUD.onTick();

            if (client.currentScreen != null) { wasMouseDown = false; wasGuiKey = false; return; }

            long win = client.getWindow().getHandle();

            boolean kd = GLFW.glfwGetKey(win, GUI_KEY) == GLFW.GLFW_PRESS;
            if (kd && !wasGuiKey) client.execute(() -> client.setScreen(new ClickGui()));
            wasGuiKey = kd;

            // ── Modül keybind kontrol ──────────────────────────────────
            for (Module m : ModuleManager.modules) {
                int key = m.getKeybind();
                if (key < 0) continue;
                boolean pressed = GLFW.glfwGetKey(win, key) == GLFW.GLFW_PRESS;
                // Sadece ilk basışta toggle (tuşun bırakılıp tekrar basılmasını bekle)
                Boolean prev = keybindState.getOrDefault(key, false);
                if (pressed && !prev) m.toggle();
                keybindState.put(key, pressed);
            }

            // ── WorldCopy ──────────────────────────────────────────────
            if (ModuleManager.isEnabled("WorldCopy") && worldCopyCooldown <= 0) {
                tickWorldCopy(client);
                worldCopyCooldown = 20;
            }
            // ── LobbyCopy ───────────────────────────────────────────────
            if (ModuleManager.isEnabled("LobbyCopy") && worldCopyCooldown <= 0) {
                tickLobbyCopy(client);
                worldCopyCooldown = 20;
            }
            // ── Mix ──────────────────────────────────────────────────────
            if (ModuleManager.isEnabled("Mix") && worldCopyCooldown <= 0) {
                tickMix(client);
                worldCopyCooldown = 20;
            }
            // ── Paste ─────────────────────────────────────────────────────
            if (ModuleManager.isEnabled("Paste") && pasteCooldown <= 0) {
                tickPaste(client);
                pasteCooldown = 10;
            }
            if (pasteCooldown > 0) pasteCooldown--;
            if (worldCopyCooldown > 0) worldCopyCooldown--;

            boolean md = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
            if (md && !wasMouseDown) {
                double[] mxArr = new double[1], myArr = new double[1];
                GLFW.glfwGetCursorPos(win, mxArr, myArr);
                double scale = client.getWindow().getScaleFactor();
                double sx = mxArr[0] / scale, sy = myArr[0] / scale;
                if (sx >= BTN_X && sx <= BTN_X + BTN_W && sy >= BTN_Y && sy <= BTN_Y + BTN_H) {
                    client.execute(() -> client.setScreen(new ClickGui()));
                }
            }
            wasMouseDown = md;
        });

        System.out.println("[FlexClient " + VERSION + "] Yuklendi! " + ModuleManager.modules.size() + " modul hazir.");
    }

    // ── Watermark ──────────────────────────────────────────────────────
    private void renderWatermark(DrawContext ctx, MinecraftClient client) {
        ctx.fill(BTN_X - 2, BTN_Y - 2, BTN_X + BTN_W + 2, BTN_Y + BTN_H + 2, 0xBB000015);
        ctx.fill(BTN_X - 2, BTN_Y - 2, BTN_X, BTN_Y + BTN_H + 2, 0xFF00FFCC);
        ctx.drawTextWithShadow(client.textRenderer,
            "\u00a7bFlex\u00a7fClient \u00a774.0",
            BTN_X + 3, BTN_Y + 3, 0xFFFFFFFF);
    }

    // ── Module List (ActiveModulesHUD) ─────────────────────────────────
    private void renderModuleList(DrawContext ctx, MinecraftClient client) {
        int sw = client.getWindow().getScaledWidth();
        int y = 2;
        List<Module> enabled = ModuleManager.modules.stream()
            .filter(Module::isEnabled)
            .sorted((a, b) -> b.getName().length() - a.getName().length())
            .toList();
        for (Module m : enabled) {
            String name = m.getName();
            int w = client.textRenderer.getWidth(name);
            int x = sw - w - 4;
            ctx.fill(x - 2, y - 1, sw - 2, y + 9, 0x88000000);
            ctx.fill(sw - 3, y - 1, sw - 2, y + 9, getCatColor(m.getCategory()));
            ctx.drawTextWithShadow(client.textRenderer, name, x, y, 0xFFFFFFFF);
            y += 11;
        }
    }

    // ── NameTags (2D HUD overlay) ──────────────────────────────────────
    private void renderNameTags(DrawContext ctx, MinecraftClient client, float tickDelta) {
        if (!ModuleManager.isEnabled("NameTags") || client.player == null || client.world == null) return;
        Module nt = ModuleManager.get("NameTags");

        for (Entity entity : client.world.getEntities()) {
            if (entity == client.player) continue;
            if (!(entity instanceof LivingEntity le) || le.isDead()) continue;
            if (!(entity instanceof PlayerEntity)) continue;

            Vec3d entityPos = entity.getPos().add(0, entity.getHeight() + 0.3, 0);
            double[] screen = worldToScreen(entityPos, client, tickDelta);
            if (screen == null) continue;

            int sx = (int) screen[0], sy = (int) screen[1];
            String name = entity.getName().getString();
            String healthStr = nt.getSetting("health")
                ? String.format(" \u00a7c%.0f\u00a7f HP", ((LivingEntity) entity).getHealth()) : "";
            String distStr = nt.getSetting("distance")
                ? String.format(" \u00a77%.0fm", client.player.distanceTo(entity)) : "";
            String label = "\u00a7f" + name + healthStr + distStr;

            int tw = client.textRenderer.getWidth(label);
            ctx.fill(sx - tw / 2 - 2, sy - 2, sx + tw / 2 + 2, sy + 10, 0xAA000000);
            ctx.drawTextWithShadow(client.textRenderer, label, sx - tw / 2, sy, 0xFFFFFFFF);
        }
    }

    private double[] worldToScreen(Vec3d worldPos, MinecraftClient mc, float tickDelta) {
        try {
            net.minecraft.client.render.Camera camera = mc.gameRenderer.getCamera();
            Vec3d camPos = camera.getPos();
            Vec3d rel = worldPos.subtract(camPos);

            float yawRad = (float) Math.toRadians(camera.getYaw());
            float pitchRad = (float) Math.toRadians(camera.getPitch());

            double rx = rel.x * Math.cos(yawRad) + rel.z * Math.sin(yawRad);
            double ry = -rel.y * Math.cos(pitchRad) - (rel.x * Math.sin(yawRad) * Math.sin(pitchRad)) + (rel.z * Math.cos(yawRad) * Math.sin(pitchRad)) * (-1);
            double rz = -rel.x * Math.sin(yawRad) * Math.cos(pitchRad) + rel.y * Math.sin(pitchRad) + rel.z * Math.cos(yawRad) * Math.cos(pitchRad);

            if (rz >= 0) return null;

            double fov = mc.options.getFov().getValue();
            double aspect = (double) mc.getWindow().getScaledWidth() / mc.getWindow().getScaledHeight();
            double f = 1.0 / Math.tan(Math.toRadians(fov / 2.0));

            double screenX = (rx / (-rz)) * f / aspect * mc.getWindow().getScaledWidth() / 2.0 + mc.getWindow().getScaledWidth() / 2.0;
            double screenY = (ry / (-rz)) * f * mc.getWindow().getScaledHeight() / 2.0 + mc.getWindow().getScaledHeight() / 2.0;

            return new double[]{screenX, screenY};
        } catch (Exception e) {
            return null;
        }
    }

    // ── WorldCopy: Çevredeki blokları/entities'i NBT olarak kaydeder ──
    private void tickWorldCopy(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        Module mod = ModuleManager.get("WorldCopy");
        if (mod == null) return;

        int range = mod.getIntSetting("range", 64);
        int maxBlocks = mod.getIntSetting("maxBlocks", 50000);
        boolean saveBlocks = mod.getSetting("saveBlocks");
        boolean saveEntities = mod.getSetting("saveEntities");
        boolean saveTile = mod.getSetting("saveTileEntities");
        String mode = mod.getMode();

        copiedBlocks.clear();
        copiedEntities.clear();

        BlockPos origin = client.player.getBlockPos();
        int count = 0;

        for (int dx = -range; dx <= range && count < maxBlocks; dx++) {
            for (int dy = -range; dy <= range && count < maxBlocks; dy++) {
                for (int dz = -range; dz <= range && count < maxBlocks; dz++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    BlockState state = client.world.getBlockState(pos);
                    if (state.isAir()) continue;

                    if (mode.equals("EntitiesOnly") && saveBlocks) saveBlocks = false;

                    if (saveBlocks) {
                        NbtCompound blockNbt = new NbtCompound();
                        blockNbt.putInt("x", pos.getX());
                        blockNbt.putInt("y", pos.getY());
                        blockNbt.putInt("z", pos.getZ());
                        blockNbt.putString("block", state.getBlock().toString());
                        if (saveTile) {
                            BlockEntity be = client.world.getBlockEntity(pos);
                            if (be != null) {
                                NbtCompound beNbt = new NbtCompound();
                                writeBlockEntityNbt(be, beNbt);
                                blockNbt.put("tileEntity", beNbt);
                            }
                        }
                        copiedBlocks.add(blockNbt);
                        count++;
                    }
                }
            }
        }

        if (saveEntities && !mode.equals("BlocksOnly")) {
            for (Entity ent : client.world.getEntities()) {
                if (ent == client.player) continue;
                if (client.player.distanceTo(ent) > range) continue;
                NbtCompound entNbt = new NbtCompound();
                entNbt.putString("type", ent.getType().toString());
                entNbt.putDouble("x", ent.getX());
                entNbt.putDouble("y", ent.getY());
                entNbt.putDouble("z", ent.getZ());
                entNbt.putFloat("yaw", ent.getYaw());
                entNbt.putFloat("pitch", ent.getPitch());
                copiedEntities.add(entNbt);
            }
        }

        if (mod.getSetting("autoSave")) {
            saveCopyToFile("worldcopy");
        }

        client.player.sendMessage(net.minecraft.text.Text.literal(
            "\u00a7b[WorldCopy] \u00a7f" + copiedBlocks.size() + " blok, " +
            copiedEntities.size() + " entite kopyalandi"), false);
    }

    // ── LobbyCopy: Lobi yapısını kopyalar ──────────────────────────
    private void tickLobbyCopy(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        Module mod = ModuleManager.get("LobbyCopy");
        if (mod == null) return;

        int range = mod.getIntSetting("range", 32);
        int maxBlocks = mod.getIntSetting("maxBlocks", 20000);
        boolean saveBlocks = mod.getSetting("saveBlocks");
        boolean saveArmorStands = mod.getSetting("saveArmorStands");
        boolean saveBanners = mod.getSetting("saveBanners");
        String mode = mod.getMode();

        copiedBlocks.clear();
        copiedEntities.clear();

        BlockPos origin = client.player.getBlockPos();
        int count = 0;

        for (int dx = -range; dx <= range && count < maxBlocks; dx++) {
            for (int dz = -range; dz <= range && count < maxBlocks; dz++) {
                int yStart = mode.equals("Area") ? origin.getY() - 5 : -range;
                int yEnd = mode.equals("Area") ? origin.getY() + 10 : range;
                for (int dy = yStart; dy <= yEnd && count < maxBlocks; dy++) {
                    BlockPos pos = origin.add(dx, dy, dz);
                    BlockState state = client.world.getBlockState(pos);
                    if (state.isAir()) continue;

                    NbtCompound blockNbt = new NbtCompound();
                    blockNbt.putInt("x", pos.getX());
                    blockNbt.putInt("y", pos.getY());
                    blockNbt.putInt("z", pos.getZ());
                    blockNbt.putString("block", state.getBlock().toString());

                    BlockEntity be = client.world.getBlockEntity(pos);
                    if (be != null) {
                        NbtCompound beNbt = new NbtCompound();
                        writeBlockEntityNbt(be, beNbt);
                        blockNbt.put("tileEntity", beNbt);
                    }
                    copiedBlocks.add(blockNbt);
                    count++;
                }
            }
        }

        for (Entity ent : client.world.getEntities()) {
            if (ent == client.player) continue;
            if (client.player.distanceTo(ent) > range) continue;
            String type = ent.getType().toString();
            boolean isArmorStand = type.contains("armor_stand");
            if (saveArmorStands && isArmorStand) {
                NbtCompound entNbt = new NbtCompound();
                entNbt.putString("type", type);
                entNbt.putDouble("x", ent.getX());
                entNbt.putDouble("y", ent.getY());
                entNbt.putDouble("z", ent.getZ());
                entNbt.putFloat("yaw", ent.getYaw());
                copiedEntities.add(entNbt);
            }
        }

        saveCopyToFile("lobbycopy");

        client.player.sendMessage(net.minecraft.text.Text.literal(
            "\u00a7b[LobbyCopy] \u00a7f" + copiedBlocks.size() + " blok, " +
            copiedEntities.size() + " entite kopyalandi"), false);
    }

    // ── Mix: Dünya + Lobi kopyalama birleşik ───────────────────────
    private void tickMix(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        Module mod = ModuleManager.get("Mix");
        if (mod == null) return;

        String mode = mod.getMode();
        boolean doWorld = mode.equals("Both") || mode.equals("WorldOnly");
        boolean doLobby = mode.equals("Both") || mode.equals("LobbyOnly");

        copiedBlocks.clear();
        copiedEntities.clear();

        if (doWorld) {
            int worldRange = mod.getIntSetting("worldRange", 64);
            int maxBlocks = mod.getIntSetting("maxBlocks", 50000);
            BlockPos origin = client.player.getBlockPos();
            int count = 0;

            for (int dx = -worldRange; dx <= worldRange && count < maxBlocks; dx++) {
                for (int dy = -worldRange; dy <= worldRange && count < maxBlocks; dy++) {
                    for (int dz = -worldRange; dz <= worldRange && count < maxBlocks; dz++) {
                        BlockPos pos = origin.add(dx, dy, dz);
                        BlockState state = client.world.getBlockState(pos);
                        if (state.isAir()) continue;
                        NbtCompound blockNbt = new NbtCompound();
                        blockNbt.putInt("x", pos.getX());
                        blockNbt.putInt("y", pos.getY());
                        blockNbt.putInt("z", pos.getZ());
                        blockNbt.putString("block", state.getBlock().toString());
                        BlockEntity be = client.world.getBlockEntity(pos);
                        if (be != null && mod.getSetting("saveTileEntities")) {
                            NbtCompound beNbt = new NbtCompound();
                            writeBlockEntityNbt(be, beNbt);
                            blockNbt.put("tileEntity", beNbt);
                        }
                        copiedBlocks.add(blockNbt);
                        count++;
                    }
                }
            }

            if (mod.getSetting("saveEntities")) {
                for (Entity ent : client.world.getEntities()) {
                    if (ent == client.player) continue;
                    if (client.player.distanceTo(ent) > worldRange) continue;
                    NbtCompound entNbt = new NbtCompound();
                    entNbt.putString("type", ent.getType().toString());
                    entNbt.putDouble("x", ent.getX());
                    entNbt.putDouble("y", ent.getY());
                    entNbt.putDouble("z", ent.getZ());
                    copiedEntities.add(entNbt);
                }
            }
        }

        if (doLobby) {
            int lobbyRange = mod.getIntSetting("lobbyRange", 32);
            BlockPos origin = client.player.getBlockPos();

            for (int dx = -lobbyRange; dx <= lobbyRange; dx++) {
                for (int dz = -lobbyRange; dz <= lobbyRange; dz++) {
                    for (int dy = origin.getY() - 5; dy <= origin.getY() + 10; dy++) {
                        BlockPos pos = origin.add(dx, dy, dz);
                        BlockState state = client.world.getBlockState(pos);
                        if (state.isAir()) continue;
                        boolean already = false;
                        for (NbtCompound existing : copiedBlocks) {
                            if (existing.getInt("x") == pos.getX() &&
                                existing.getInt("y") == pos.getY() &&
                                existing.getInt("z") == pos.getZ()) {
                                already = true; break;
                            }
                        }
                        if (!already) {
                            NbtCompound blockNbt = new NbtCompound();
                            blockNbt.putInt("x", pos.getX());
                            blockNbt.putInt("y", pos.getY());
                            blockNbt.putInt("z", pos.getZ());
                            blockNbt.putString("block", state.getBlock().toString());
                            BlockEntity be = client.world.getBlockEntity(pos);
                            if (be != null) {
                                NbtCompound beNbt = new NbtCompound();
                                writeBlockEntityNbt(be, beNbt);
                                blockNbt.put("tileEntity", beNbt);
                            }
                            copiedBlocks.add(blockNbt);
                        }
                    }
                }
            }
        }

        saveCopyToFile("mix");

        client.player.sendMessage(net.minecraft.text.Text.literal(
            "\u00a7b[Mix] \u00a7f" + copiedBlocks.size() + " blok, " +
            copiedEntities.size() + " entite kopyalandi (" + mode + ")"), false);
    }

    // ── Kopyalanan veriyi dosyaya kaydet ─────────────────────────────
    private static File lastSavedFile = null;

    private void writeBlockEntityNbt(BlockEntity be, NbtCompound nbt) {
        try {
            Method m = BlockEntity.class.getDeclaredMethod("writeNbt", NbtCompound.class);
            m.setAccessible(true);
            m.invoke(be, nbt);
        } catch (Exception ignored) {}
    }

    private void saveCopyToFile(String prefix) {
        try {
            File mcDir = MinecraftClient.getInstance().runDirectory;
            File dir = new File(mcDir, "flexclient_copies");
            if (!dir.exists()) dir.mkdirs();
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            File file = new File(dir, prefix + "_" + timestamp + ".json");

            BlockPos origin = MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.getBlockPos() : BlockPos.ORIGIN;

            StringBuilder sb = new StringBuilder();
            sb.append("{\"origin\":{\"x\":").append(origin.getX());
            sb.append(",\"y\":").append(origin.getY());
            sb.append(",\"z\":").append(origin.getZ()).append("}");
            sb.append(",\"blocks\":[");
            for (int i = 0; i < copiedBlocks.size(); i++) {
                if (i > 0) sb.append(",");
                NbtCompound b = copiedBlocks.get(i);
                sb.append("{\"x\":").append(b.getInt("x"));
                sb.append(",\"y\":").append(b.getInt("y"));
                sb.append(",\"z\":").append(b.getInt("z"));
                sb.append(",\"block\":\"").append(b.getString("block")).append("\"}");
            }
            sb.append("],\"entities\":[");
            for (int i = 0; i < copiedEntities.size(); i++) {
                if (i > 0) sb.append(",");
                NbtCompound e = copiedEntities.get(i);
                sb.append("{\"type\":\"").append(e.getString("type")).append("\"");
                sb.append(",\"x\":").append(e.getDouble("x"));
                sb.append(",\"y\":").append(e.getDouble("y"));
                sb.append(",\"z\":").append(e.getDouble("z"));
                sb.append("}");
            }
            sb.append("]}");
            try (FileWriter fw = new FileWriter(file)) { fw.write(sb.toString()); }
            lastSavedFile = file;
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(
                    net.minecraft.text.Text.literal(
                        "\u00a7a[FlexClient] \u00a7fKaydedildi: \u00a7b" + file.getAbsolutePath()),
                    false);
            }
        } catch (IOException e) {
            if (MinecraftClient.getInstance().player != null) {
                MinecraftClient.getInstance().player.sendMessage(
                    net.minecraft.text.Text.literal(
                        "\u00a7c[FlexClient] Kaydetme hatasi: " + e.getMessage()),
                    false);
            }
        }
    }

    // ── Paste: JSON'dan blokları singleplayer'da geri yükle ──────────
    private static final List<int[]> pasteBlocks = new ArrayList<>();
    private static final List<String> pasteBlockNames = new ArrayList<>();
    private static final List<double[]> pasteEntityPos = new ArrayList<>();
    private static final List<String> pasteEntityTypes = new ArrayList<>();

    private void tickPaste(MinecraftClient client) {
        if (client.player == null || client.world == null) return;
        Module mod = ModuleManager.get("Paste");
        if (mod == null) return;

        // İlk açılışta JSON dosyasını yükle
        if (!pasteLoaded) {
            String fileSetting = mod.getStringSetting("file", "latest");
            File file = findPasteFile(fileSetting);
            if (file == null) {
                client.player.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a7c[Paste] \u00a7fDosya bulunamadi! flexclient_copies klasörünü kontrol et."), false);
                mod.setEnabled(false);
                return;
            }
            loadPasteJson(file);
            if (pasteBlocks.isEmpty()) {
                client.player.sendMessage(net.minecraft.text.Text.literal(
                    "\u00a7c[Paste] \u00a7fJSON dosyasinda blok yok veya format hatali."), false);
                mod.setEnabled(false);
                return;
            }

            // Origin ayarla
            String mode = mod.getMode();
            if (mode.equals("Relative")) {
                pasteOriginX = client.player.getBlockPos().getX();
                pasteOriginY = client.player.getBlockPos().getY();
                pasteOriginZ = client.player.getBlockPos().getZ();
            } else {
                pasteOriginX = 0;
                pasteOriginY = 0;
                pasteOriginZ = 0;
            }

            pasteIndex = 0;
            pasteFileName = file.getName();
            client.player.sendMessage(net.minecraft.text.Text.literal(
                "\u00a7b[Paste] \u00a7fYükleniyor: \u00a7e" + pasteFileName +
                "\u00a7f — " + pasteBlocks.size() + " blok, " + pasteEntityPos.size() + " entite"), false);
        }

        int blocksPerTick = mod.getIntSetting("blocksPerTick", 500);
        int offX = mod.getIntSetting("offsetX", 0);
        int offY = mod.getIntSetting("offsetY", 0);
        int offZ = mod.getIntSetting("offsetZ", 0);
        boolean ignoreAir = mod.getSetting("ignoreAir");
        int placed = 0;

        while (pasteIndex < pasteBlocks.size() && placed < blocksPerTick) {
            int[] coords = pasteBlocks.get(pasteIndex);
            String blockName = pasteBlockNames.get(pasteIndex);
            pasteIndex++;

            if (ignoreAir && blockName.contains("air")) continue;

            BlockPos targetPos = new BlockPos(
                coords[0] - pasteOriginX + client.player.getBlockPos().getX() + offX,
                coords[1] - pasteOriginY + client.player.getBlockPos().getY() + offY,
                coords[2] - pasteOriginZ + client.player.getBlockPos().getZ() + offZ
            );

            net.minecraft.block.Block block = parseBlock(blockName);
            if (block == null) continue;

            BlockState state = block.getDefaultState();
            try {
                client.world.setBlockState(targetPos, state, net.minecraft.block.Block.NOTIFY_ALL);
                placed++;
            } catch (Exception ignored) {}
        }

        // Entities
        if (pasteIndex >= pasteBlocks.size() && mod.getSetting("placeEntities")) {
            for (int i = 0; i < pasteEntityPos.size(); i++) {
                double[] pos = pasteEntityPos.get(i);
                String type = pasteEntityTypes.get(i);
                try {
                    var entityType = net.minecraft.entity.EntityType.get(type);
                    if (entityType.isPresent()) {
                        Entity ent = entityType.get().create(client.world);
                        if (ent != null) {
                            ent.setPosition(
                                pos[0] - pasteOriginX + client.player.getX() + offX,
                                pos[1] - pasteOriginY + client.player.getY() + offY,
                                pos[2] - pasteOriginZ + client.player.getZ() + offZ
                            );
                            client.world.spawnEntity(ent);
                        }
                    }
                } catch (Exception ignored) {}
            }
            pasteEntityPos.clear();
            pasteEntityTypes.clear();
        }

        // Tamamlandı
        if (pasteIndex >= pasteBlocks.size()) {
            client.player.sendMessage(net.minecraft.text.Text.literal(
                "\u00a7a[Paste] \u00a7fTamamlandi! " + pasteBlocks.size() + " blok yerlestirildi."), false);
            pasteLoaded = false;
            pasteBlocks.clear();
            pasteBlockNames.clear();
            mod.setEnabled(false);
        }
    }

    private File findPasteFile(String fileSetting) {
        File mcDir = MinecraftClient.getInstance().runDirectory;
        File dir = new File(mcDir, "flexclient_copies");
        if (!dir.exists()) return null;

        if (fileSetting.equals("latest")) {
            File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
            if (files == null || files.length == 0) return null;
            File latest = files[0];
            for (File f : files) if (f.lastModified() > latest.lastModified()) latest = f;
            return latest;
        }

        File f = new File(dir, fileSetting);
        if (f.exists()) return f;
        // Kısmi match dene
        File[] files = dir.listFiles((d, name) -> name.contains(fileSetting));
        if (files != null && files.length > 0) return files[0];
        return null;
    }

    private void loadPasteJson(File file) {
        pasteBlocks.clear();
        pasteBlockNames.clear();
        pasteEntityPos.clear();
        pasteEntityTypes.clear();
        pasteLoaded = true;

        try {
            String content = new String(java.nio.file.Files.readAllBytes(file.toPath()));
            // Basit JSON parse — harici kütüphane olmadan
            // blocks array'ini bul
            int blocksStart = content.indexOf("\"blocks\":[");
            if (blocksStart < 0) return;
            blocksStart += "\"blocks\":[".length();

            int depth = 1;
            int i = blocksStart;
            int blockStart = -1;
            while (i < content.length() && depth > 0) {
                char c = content.charAt(i);
                if (c == '{') {
                    if (depth == 1) blockStart = i;
                    depth++;
                } else if (c == '}') {
                    depth--;
                    if (depth == 1 && blockStart >= 0) {
                        String blockJson = content.substring(blockStart, i + 1);
                        parseBlockEntry(blockJson);
                        blockStart = -1;
                    }
                } else if (c == ']') {
                    depth--;
                }
                i++;
            }

            // entities array'ini bul
            int entStart = content.indexOf("\"entities\":[");
            if (entStart >= 0) {
                entStart += "\"entities\":[".length();
                depth = 1;
                i = entStart;
                blockStart = -1;
                while (i < content.length() && depth > 0) {
                    char c = content.charAt(i);
                    if (c == '{') {
                        if (depth == 1) blockStart = i;
                        depth++;
                    } else if (c == '}') {
                        depth--;
                        if (depth == 1 && blockStart >= 0) {
                            String entJson = content.substring(blockStart, i + 1);
                            parseEntityEntry(entJson);
                            blockStart = -1;
                        }
                    } else if (c == ']') {
                        depth--;
                    }
                    i++;
                }
            }
        } catch (Exception e) {
            pasteLoaded = false;
        }
    }

    private void parseBlockEntry(String json) {
        try {
            int x = jsonIntValue(json, "\"x\":");
            int y = jsonIntValue(json, "\"y\":");
            int z = jsonIntValue(json, "\"z\":");
            String block = jsonStringValue(json, "\"block\":\"");
            pasteBlocks.add(new int[]{x, y, z});
            pasteBlockNames.add(block);
        } catch (Exception ignored) {}
    }

    private void parseEntityEntry(String json) {
        try {
            String type = jsonStringValue(json, "\"type\":\"");
            double x = jsonDoubleValue(json, "\"x\":");
            double y = jsonDoubleValue(json, "\"y\":");
            double z = jsonDoubleValue(json, "\"z\":");
            pasteEntityTypes.add(type);
            pasteEntityPos.add(new double[]{x, y, z});
        } catch (Exception ignored) {}
    }

    private int jsonIntValue(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return 0;
        idx += key.length();
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-'))
            end++;
        return Integer.parseInt(json.substring(idx, end).trim());
    }

    private double jsonDoubleValue(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return 0;
        idx += key.length();
        int end = idx;
        while (end < json.length() && (Character.isDigit(json.charAt(end)) ||
               json.charAt(end) == '.' || json.charAt(end) == '-'))
            end++;
        return Double.parseDouble(json.substring(idx, end).trim());
    }

    private String jsonStringValue(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0) return "";
        idx += key.length();
        int end = json.indexOf("\"", idx);
        if (end < 0) return "";
        return json.substring(idx, end);
    }

    private net.minecraft.block.Block parseBlock(String blockName) {
        if (blockName == null || blockName.isEmpty()) return null;
        // Format: "Block{minecraft:stone}" → "minecraft:stone"
        String cleaned = blockName;
        if (cleaned.contains("minecraft:")) {
            int s = cleaned.indexOf("minecraft:");
            int e = cleaned.indexOf("}", s);
            if (e < 0) e = cleaned.length();
            cleaned = cleaned.substring(s, e);
        }
        return net.minecraft.registry.Registries.BLOCK.get(
            net.minecraft.util.Identifier.tryParse(cleaned));
    }

    private int getCatColor(String cat) {
        return switch (cat) {
            case "Combat"   -> 0xFFFF4466;
            case "Movement" -> 0xFF3399FF;
            case "Render"   -> 0xFF33FF99;
            case "Player"   -> 0xFFFF9922;
            case "World"    -> 0xFF00FFCC;
            default         -> 0xFF00FFCC;
        };
    }
}
