package com.flex.client.xray;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import java.util.*;

/**
 * FlexClient Ultra Xray - Ore Configuration System
 * Minecraft 1.20.1 tum cevherleri, renkleri ve dogal dagilim verileri
 */
public class XrayConfig {

    public static class OreEntry {
        public final Block block;
        public final String name;
        public final int color;          // ARGB
        public final int glowColor;      // Parlama rengi
        public final int minY;
        public final int maxY;
        public final int bestY;          // Optimal Y seviyesi
        public final int maxVeinSize;
        public final int maxPerChunk;    // Anti-xray icin chunk basi maksimum dogal adet
        public final int rarity;         // 1=cok nadir, 10=cok yaygin
        public boolean enabled;
        public int alpha;                // 0-255
        public boolean showTracer;
        public boolean showESP;
        public boolean showCount;

        public OreEntry(Block block, String name, int color, int glowColor,
                        int minY, int maxY, int bestY,
                        int maxVeinSize, int maxPerChunk, int rarity) {
            this.block       = block;
            this.name        = name;
            this.color       = color;
            this.glowColor   = glowColor;
            this.minY        = minY;
            this.maxY        = maxY;
            this.bestY       = bestY;
            this.maxVeinSize = maxVeinSize;
            this.maxPerChunk = maxPerChunk;
            this.rarity      = rarity;
            this.enabled     = true;
            this.alpha       = 220;
            this.showTracer  = false;
            this.showESP     = true;
            this.showCount   = true;
        }
    }

    // Tum ore entryleri (siralama: nadir -> yaygin)
    private static final List<OreEntry> ENTRIES = new ArrayList<>();
    // Hizli lookup icin set
    private static final Set<Block> TARGET_BLOCKS = new HashSet<>();
    // Block -> OreEntry map
    private static final Map<Block, OreEntry> BLOCK_MAP = new HashMap<>();

    static {
        register(new OreEntry(
            Blocks.DIAMOND_ORE,        "Diamond",       0xFFADD8E6, 0xFF00FFFF,
            -64, 16, -58,   8,  16, 1));
        register(new OreEntry(
            Blocks.DEEPSLATE_DIAMOND_ORE, "D.Diamond",  0xFF87CEEB, 0xFF00FFFF,
            -64,  0, -58,   8,  24, 1));
        register(new OreEntry(
            Blocks.ANCIENT_DEBRIS,      "AncientDebris",0xFFCC5500, 0xFFFF6600,
            8,  22,  15,   3,   6, 1));
        register(new OreEntry(
            Blocks.EMERALD_ORE,         "Emerald",      0xFF00AA44, 0xFF00FF66,
            -16,320, 236,   1,   8, 2));
        register(new OreEntry(
            Blocks.DEEPSLATE_EMERALD_ORE,"D.Emerald",   0xFF009933, 0xFF00FF66,
            -16,  0, -16,   1,   6, 2));
        register(new OreEntry(
            Blocks.GOLD_ORE,            "Gold",         0xFFFFD700, 0xFFFFFF00,
            -64, 32, -16,   9,  28, 3));
        register(new OreEntry(
            Blocks.DEEPSLATE_GOLD_ORE,  "D.Gold",       0xFFDAA520, 0xFFFFFF00,
            -64,  0, -16,   9,  32, 3));
        register(new OreEntry(
            Blocks.NETHER_GOLD_ORE,     "NetherGold",   0xFFFFAA00, 0xFFFFDD00,
            10, 117,  20,  10,  64, 4));
        register(new OreEntry(
            Blocks.LAPIS_ORE,           "Lapis",        0xFF1E5F9E, 0xFF4488FF,
            -64, 64,   0,   7,  24, 3));
        register(new OreEntry(
            Blocks.DEEPSLATE_LAPIS_ORE, "D.Lapis",      0xFF1A5080, 0xFF4488FF,
            -64,  0,   0,   7,  28, 3));
        register(new OreEntry(
            Blocks.REDSTONE_ORE,        "Redstone",     0xFFFF1111, 0xFFFF4444,
            -64, 16, -58,   8,  40, 4));
        register(new OreEntry(
            Blocks.DEEPSLATE_REDSTONE_ORE,"D.Redstone", 0xFFDD1111, 0xFFFF4444,
            -64,  0, -58,   8,  48, 4));
        register(new OreEntry(
            Blocks.COPPER_ORE,          "Copper",       0xFFCC7722, 0xFFFF9933,
            -16,112,  48,  16,  64, 5));
        register(new OreEntry(
            Blocks.DEEPSLATE_COPPER_ORE,"D.Copper",     0xFFAA6611, 0xFFFF9933,
            -16,  0,  48,  16,  64, 5));
        register(new OreEntry(
            Blocks.IRON_ORE,            "Iron",         0xFFBBBBBB, 0xFFDDDDDD,
            -64,320,  15,  13,  80, 6));
        register(new OreEntry(
            Blocks.DEEPSLATE_IRON_ORE,  "D.Iron",       0xFF999999, 0xFFBBBBBB,
            -64,  0,  15,  13,  80, 6));
        register(new OreEntry(
            Blocks.COAL_ORE,            "Coal",         0xFF444444, 0xFF777777,
            0, 320,  96,  17, 128, 8));
        register(new OreEntry(
            Blocks.DEEPSLATE_COAL_ORE,  "D.Coal",       0xFF333333, 0xFF666666,
            0,   0,  96,  17, 128, 8));
        // Ozel bloklar
        register(new OreEntry(
            Blocks.CHEST,               "Chest",        0xFF8B4513, 0xFFAA6622,
            -64,320,   0,   1, 999, 7));
        register(new OreEntry(
            Blocks.TRAPPED_CHEST,       "TrChest",      0xFF8B3513, 0xFFDD4422,
            -64,320,   0,   1, 999, 7));
        register(new OreEntry(
            Blocks.ENDER_CHEST,         "EnderChest",   0xFF1A1A5A, 0xFF3333AA,
            -64,320,   0,   1, 999, 5));
        register(new OreEntry(
            Blocks.SPAWNER,             "Spawner",      0xFF444466, 0xFF6666FF,
            -64,320,   0,   1, 999, 4));
        register(new OreEntry(
            Blocks.NETHER_QUARTZ_ORE,   "Quartz",       0xFFEEEEDD, 0xFFFFFFFF,
            10,117,  25,  14, 128, 7));
        register(new OreEntry(
            Blocks.OBSIDIAN,            "Obsidian",     0xFF1A0033, 0xFF7700CC,
            -64, 20, -10,   1, 999, 6));
        register(new OreEntry(
            Blocks.CRYING_OBSIDIAN,     "CryObsidian",  0xFF4400AA, 0xFFAA00FF,
            -64,320,   0,   1, 999, 5));
        // Yapi bloklari (isteğe bağlı)
        register(new OreEntry(
            Blocks.AMETHYST_CLUSTER,    "Amethyst",     0xFFCC88FF, 0xFFFF99FF,
            -64,320,   0,   4,  16, 5));
    }

    private static void register(OreEntry e) {
        ENTRIES.add(e);
        TARGET_BLOCKS.add(e.block);
        BLOCK_MAP.put(e.block, e);
    }

    // ── Public API ────────────────────────────────────────────

    public static boolean isTargetBlock(Block b) {
        OreEntry e = BLOCK_MAP.get(b);
        if (e == null) return false;
        return e.enabled;
    }

    public static OreEntry getEntry(Block b) {
        return BLOCK_MAP.get(b);
    }

    public static List<OreEntry> getAllEntries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static Set<Block> getTargetBlocks() {
        return Collections.unmodifiableSet(TARGET_BLOCKS);
    }

    public static int getColorFor(Block b) {
        OreEntry e = BLOCK_MAP.get(b);
        return e != null ? e.color : 0xFFFFFFFF;
    }

    public static int getGlowColorFor(Block b) {
        OreEntry e = BLOCK_MAP.get(b);
        return e != null ? e.glowColor : 0xFFFFFFFF;
    }

    /** Y seviyesinin bu cevher icin gecerli olup olmadigini kontrol eder */
    public static boolean isValidYLevel(Block b, int y) {
        OreEntry e = BLOCK_MAP.get(b);
        if (e == null) return false;
        return y >= e.minY && y <= e.maxY;
    }

    /** AntiXray icin: chunk basi bu cevherden kac tane olabilecegini dondurur */
    public static int getMaxPerChunk(Block b) {
        OreEntry e = BLOCK_MAP.get(b);
        return e != null ? e.maxPerChunk : 0;
    }

    /** Bu blok cevher mi (dogal dagilim analizi icin) */
    public static boolean isNaturalOre(Block b) {
        OreEntry e = BLOCK_MAP.get(b);
        if (e == null) return false;
        return e.rarity <= 6; // Chest/Spawner hariç
    }

    public static void setEnabled(String name, boolean en) {
        for (OreEntry e : ENTRIES) {
            if (e.name.equalsIgnoreCase(name)) { e.enabled = en; return; }
        }
    }

    public static void setAlpha(String name, int alpha) {
        for (OreEntry e : ENTRIES) {
            if (e.name.equalsIgnoreCase(name)) { e.alpha = Math.max(0, Math.min(255, alpha)); return; }
        }
    }

    // Renk pul sayisi (parlaklik icin) - uzakliga gore alpha hesapla
    public static int getColorWithDistance(Block b, double distSq, double maxDist) {
        OreEntry e = BLOCK_MAP.get(b);
        if (e == null) return 0xFFFFFFFF;
        double ratio = 1.0 - Math.min(1.0, distSq / (maxDist * maxDist));
        int a = (int)(ratio * e.alpha);
        return (a << 24) | (e.color & 0x00FFFFFF);
    }

    // Tum hedef blok isimlerinin listesi (GUI icin)
    public static String[] getEnabledNames() {
        return ENTRIES.stream()
            .filter(e -> e.enabled)
            .map(e -> e.name)
            .toArray(String[]::new);
    }
}
