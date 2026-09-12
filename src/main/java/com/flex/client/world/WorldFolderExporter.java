package com.flex.client.world;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Minecraft 1.20.1 uyumlu gerçek dünya klasörü export eden yardımcı.
 * Çıktı: .minecraft/flexclient_worlds/<isim>_<timestamp>/
 *   - level.dat
 *   - region/*.mca (standart Anvil formatında)
 * Bu klasör doğrudan .minecraft/saves/ altına kopyalanarak singleplayer'da açılabilir.
 */
public class WorldFolderExporter {

    private static final int DATA_VERSION = 3465;

    public static File exportWorld(String worldName, int range) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return null;

        World world = client.world;
        File mcDir = client.runDirectory;
        File outputDir = new File(mcDir, "flexclient_worlds");
        if (!outputDir.exists()) outputDir.mkdirs();

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String folderName = worldName + "_" + timestamp;
        File worldDir = new File(outputDir, folderName);
        if (!worldDir.exists()) worldDir.mkdirs();

        File regionDir = new File(worldDir, "region");
        if (!regionDir.exists()) regionDir.mkdirs();

        int[] result = generateRegionFilesFromLoadedChunks(world, regionDir, range);
        int copiedRegions = result[0];
        int copiedChunks = result[1];

        generateLevelDat(worldDir, world);

        if (client.player != null) {
            client.player.sendMessage(net.minecraft.text.Text.literal(
                "\u00a7a[KlasorOlarak] \u00a7fDünya kaydedildi: \u00a7b" + worldDir.getAbsolutePath() +
                "\u00a7f — " + copiedRegions + " region, " + copiedChunks + " chunk"), false);
            client.player.sendMessage(net.minecraft.text.Text.literal(
                "\u00a7fBu klasörü \u00a7e.minecraft/saves/ \u00a7faltına kopyala ve singleplayer'da aç."), false);
        }

        return worldDir;
    }

    private static int[] generateRegionFilesFromLoadedChunks(World world, File regionDir, int range) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return new int[]{0, 0};

        BlockPos origin = client.player.getBlockPos();
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;

        int chunkRange = range > 0 ? Math.max(1, range / 16) : 64;

        int minRegionX = (originChunkX - chunkRange) >> 5;
        int maxRegionX = (originChunkX + chunkRange) >> 5;
        int minRegionZ = (originChunkZ - chunkRange) >> 5;
        int maxRegionZ = (originChunkZ + chunkRange) >> 5;

        int regionsGenerated = 0;
        int totalChunks = 0;

        for (int rx = minRegionX; rx <= maxRegionX; rx++) {
            for (int rz = minRegionZ; rz <= maxRegionZ; rz++) {
                int chunksInRegion = writeRegionFile(world, regionDir, rx, rz, originChunkX, originChunkZ, chunkRange);
                if (chunksInRegion > 0) {
                    regionsGenerated++;
                    totalChunks += chunksInRegion;
                }
            }
        }

        if (client.player != null) {
            client.player.sendMessage(net.minecraft.text.Text.literal(
                "\u00a77[KlasorOlarak] \u00a7f" + regionsGenerated + " region, " +
                totalChunks + " chunk yazıldı (Anvil .mca)."), false);
        }

        return new int[]{regionsGenerated, totalChunks};
    }

    private static int writeRegionFile(World world, File regionDir, int rx, int rz,
                                        int originChunkX, int originChunkZ, int chunkRange) {
        File regionFile = new File(regionDir, "r." + rx + "." + rz + ".mca");
        int chunksWritten = 0;

        byte[][] chunkDataArray = new byte[1024][];
        int[] chunkTimestamps = new int[1024];

        for (int cx = 0; cx < 32; cx++) {
            for (int cz = 0; cz < 32; cz++) {
                int worldChunkX = (rx << 5) + cx;
                int worldChunkZ = (rz << 5) + cz;
                int distX = Math.abs(worldChunkX - originChunkX);
                int distZ = Math.abs(worldChunkZ - originChunkZ);
                if (distX > chunkRange || distZ > chunkRange) continue;

                // Sadece gerçekten yüklü chunk'ları yaz — boş/yarım chunk üretme
                if (!world.getChunkManager().isChunkLoaded(worldChunkX, worldChunkZ)) continue;

                int chunkIndex = cz * 32 + cx;
                byte[] nbtData = serializeChunk(world, worldChunkX, worldChunkZ);
                if (nbtData != null) {
                    chunkDataArray[chunkIndex] = nbtData;
                    chunkTimestamps[chunkIndex] = (int) (System.currentTimeMillis() / 1000L);
                    chunksWritten++;
                }
            }
        }

        if (chunksWritten == 0) return 0;

        try (RandomAccessFile raf = new RandomAccessFile(regionFile, "rw")) {
            raf.setLength(0);
            raf.write(new byte[8192]); // location + timestamp tables

            int sectorOffset = 2;
            int[] locations = new int[1024];

            for (int i = 0; i < 1024; i++) {
                if (chunkDataArray[i] == null) {
                    locations[i] = 0;
                    continue;
                }

                byte[] data = chunkDataArray[i];
                // Anvil: length = compression_type(1) + compressed payload
                int payloadLen = data.length + 1;
                int sectors = (payloadLen + 4 + 4095) / 4096;

                locations[i] = (sectorOffset << 8) | (sectors & 0xFF);

                raf.seek(sectorOffset * 4096L);

                raf.writeByte((payloadLen >> 24) & 0xFF);
                raf.writeByte((payloadLen >> 16) & 0xFF);
                raf.writeByte((payloadLen >> 8) & 0xFF);
                raf.writeByte(payloadLen & 0xFF);
                // NbtIo / GZIPOutputStream → compression type 1 (gzip), not 2 (zlib)
                raf.writeByte(1);
                raf.write(data);

                int written = 4 + payloadLen;
                int padLen = (sectors * 4096) - written;
                if (padLen > 0) raf.write(new byte[padLen]);

                sectorOffset += sectors;
            }

            raf.seek(0);
            for (int i = 0; i < 1024; i++) {
                raf.writeByte((locations[i] >> 24) & 0xFF);
                raf.writeByte((locations[i] >> 16) & 0xFF);
                raf.writeByte((locations[i] >> 8) & 0xFF);
                raf.writeByte(locations[i] & 0xFF);
            }

            for (int i = 0; i < 1024; i++) {
                int ts = chunkTimestamps[i];
                raf.writeByte((ts >> 24) & 0xFF);
                raf.writeByte((ts >> 16) & 0xFF);
                raf.writeByte((ts >> 8) & 0xFF);
                raf.writeByte(ts & 0xFF);
            }

        } catch (IOException e) {
            return 0;
        }

        return chunksWritten;
    }

    /**
     * 1.18+ Anvil chunk NBT: alanlar kökte (Level sarmalayıcısı YOK).
     */
    private static byte[] serializeChunk(World world, int chunkX, int chunkZ) {
        try {
            WorldChunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk == null) return null;

            int bottomSection = world.getBottomSectionCoord();
            int bottomY = world.getBottomY();

            NbtCompound root = new NbtCompound();
            root.putInt("DataVersion", DATA_VERSION);
            root.putInt("xPos", chunkX);
            root.putInt("yPos", bottomSection);
            root.putInt("zPos", chunkZ);
            root.putString("Status", "minecraft:full");
            root.putLong("InhabitedTime", 0L);
            root.putLong("LastUpdate", world.getTime());
            root.putBoolean("isLightOn", true);
            root.put("block_ticks", new NbtList());
            root.put("fluid_ticks", new NbtList());
            root.put("PostProcessing", new NbtList());
            root.put("structures", new NbtCompound());

            NbtList sectionsList = new NbtList();
            ChunkSection[] sections = chunk.getSectionArray();

            int[] surfaceHeights = new int[256];
            int[] motionHeights = new int[256];
            Arrays.fill(surfaceHeights, 0);
            Arrays.fill(motionHeights, 0);

            for (int i = 0; i < sections.length; i++) {
                ChunkSection section = sections[i];
                int sectionY = bottomSection + i;
                if (section == null || section.isEmpty()) continue;

                NbtCompound sectionNbt = new NbtCompound();
                sectionNbt.putByte("Y", (byte) sectionY);

                BlockState[] states = readSectionStates(section);
                sectionNbt.put("block_states", buildPaletteNbt(states));

                NbtCompound biomesNbt = new NbtCompound();
                NbtList biomePalette = new NbtList();
                biomePalette.add(NbtString.of("minecraft:plains"));
                biomesNbt.put("palette", biomePalette);
                sectionNbt.put("biomes", biomesNbt);

                // Tam aydınlık — isLightOn=true ile birlikte karanlık dünyayı engeller
                sectionNbt.putByteArray("BlockLight", fullLightArray());
                sectionNbt.putByteArray("SkyLight", fullLightArray());

                sectionsList.add(sectionNbt);

                // Heightmap: her section'daki en yüksek dolu blok (tüm section'lar boyunca max tutulur)
                int baseY = sectionY << 4;
                for (int bx = 0; bx < 16; bx++) {
                    for (int bz = 0; bz < 16; bz++) {
                        int col = (bz << 4) | bx;
                        for (int by = 15; by >= 0; by--) {
                            BlockState st = states[(by << 8) | (bz << 4) | bx];
                            if (st == null || st.isAir()) continue;
                            int packed = (baseY + by) - bottomY + 1;
                            if (packed > surfaceHeights[col]) surfaceHeights[col] = packed;
                            if (packed > motionHeights[col]) motionHeights[col] = packed;
                            break;
                        }
                    }
                }
            }
            root.put("sections", sectionsList);

            // Heightmap'ler boş bırakılırsa lighting/occlusion kırılır → karanlık + görünmez blok
            NbtCompound heightmaps = new NbtCompound();
            heightmaps.putLongArray("WORLD_SURFACE", packHeightmap(surfaceHeights));
            heightmaps.putLongArray("WORLD_SURFACE_WG", packHeightmap(surfaceHeights));
            heightmaps.putLongArray("MOTION_BLOCKING", packHeightmap(motionHeights));
            heightmaps.putLongArray("MOTION_BLOCKING_NO_LEAVES", packHeightmap(motionHeights));
            heightmaps.putLongArray("OCEAN_FLOOR", packHeightmap(motionHeights));
            heightmaps.putLongArray("OCEAN_FLOOR_WG", packHeightmap(motionHeights));
            root.put("Heightmaps", heightmaps);

            NbtList blockEntities = new NbtList();
            try {
                var beMap = chunk.getBlockEntities();
                if (beMap != null) {
                    for (var entry : beMap.entrySet()) {
                        BlockPos bePos = entry.getKey();
                        var be = entry.getValue();
                        if (be == null) continue;
                        NbtCompound beNbt = new NbtCompound();
                        try {
                            java.lang.reflect.Method m = be.getClass().getMethod("writeNbt", NbtCompound.class);
                            m.setAccessible(true);
                            m.invoke(be, beNbt);
                            beNbt.putInt("x", bePos.getX());
                            beNbt.putInt("y", bePos.getY());
                            beNbt.putInt("z", bePos.getZ());
                            if (!beNbt.contains("id")) {
                                try {
                                    var id = net.minecraft.registry.Registries.BLOCK_ENTITY_TYPE.getId(be.getType());
                                    if (id != null) beNbt.putString("id", id.toString());
                                } catch (Exception ignored) {}
                            }
                            blockEntities.add(beNbt);
                        } catch (Exception ignored) {}
                    }
                }
            } catch (Exception ignored) {}
            root.put("block_entities", blockEntities);

            return compressGzipNbt(root);

        } catch (Exception e) {
            return null;
        }
    }

    private static BlockState[] readSectionStates(ChunkSection section) {
        BlockState[] states = new BlockState[4096];
        for (int by = 0; by < 16; by++) {
            for (int bz = 0; bz < 16; bz++) {
                for (int bx = 0; bx < 16; bx++) {
                    int index = (by << 8) | (bz << 4) | bx;
                    try {
                        states[index] = section.getBlockState(bx, by, bz);
                    } catch (Exception e) {
                        states[index] = net.minecraft.block.Blocks.AIR.getDefaultState();
                    }
                }
            }
        }
        return states;
    }

    private static NbtCompound buildPaletteNbt(BlockState[] states) {
        NbtCompound nbt = new NbtCompound();

        Map<String, Integer> paletteMap = new LinkedHashMap<>();
        List<NbtCompound> paletteEntries = new ArrayList<>();
        int[] indices = new int[4096];

        for (int i = 0; i < 4096; i++) {
            BlockState state = states[i];
            String key = stateKey(state);
            Integer existing = paletteMap.get(key);
            if (existing == null) {
                int idx = paletteEntries.size();
                paletteMap.put(key, idx);
                paletteEntries.add(stateToPaletteEntry(state));
                indices[i] = idx;
            } else {
                indices[i] = existing;
            }
        }

        NbtList paletteList = new NbtList();
        for (NbtCompound entry : paletteEntries) paletteList.add(entry);
        nbt.put("palette", paletteList);

        if (paletteMap.size() > 1) {
            int bits = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(paletteMap.size() - 1));
            int blocksPerLong = 64 / bits;
            int longCount = (4096 + blocksPerLong - 1) / blocksPerLong;
            long[] packed = new long[longCount];

            for (int i = 0; i < 4096; i++) {
                int paletteIdx = indices[i];
                int longIdx = i / blocksPerLong;
                int bitOffset = (i % blocksPerLong) * bits;
                packed[longIdx] |= ((long) paletteIdx & ((1L << bits) - 1)) << bitOffset;
            }
            nbt.putLongArray("data", packed);
        }

        return nbt;
    }

    private static String stateKey(BlockState state) {
        if (state == null || state.isAir()) return "minecraft:air";
        StringBuilder sb = new StringBuilder(getBlockId(state));
        try {
            for (var propEntry : state.getEntries().entrySet()) {
                Property<?> prop = propEntry.getKey();
                sb.append('|').append(prop.getName()).append('=').append(propEntry.getValue());
            }
        } catch (Exception ignored) {}
        return sb.toString();
    }

    private static NbtCompound stateToPaletteEntry(BlockState state) {
        NbtCompound entry = new NbtCompound();
        entry.putString("Name", getBlockId(state));
        try {
            NbtCompound props = new NbtCompound();
            for (var propEntry : state.getEntries().entrySet()) {
                Property<?> prop = propEntry.getKey();
                props.putString(prop.getName(), propEntry.getValue().toString());
            }
            if (!props.getKeys().isEmpty()) entry.put("Properties", props);
        } catch (Exception ignored) {}
        return entry;
    }

    private static String getBlockId(BlockState state) {
        if (state == null || state.isAir()) return "minecraft:air";
        try {
            return net.minecraft.registry.Registries.BLOCK.getId(state.getBlock()).toString();
        } catch (Exception e) {
            return "minecraft:stone";
        }
    }

    private static long[] packHeightmap(int[] values) {
        // 256 entry, 9 bit — 1.18+ world height
        int bits = 9;
        int perLong = 64 / bits;
        long[] data = new long[(256 + perLong - 1) / perLong];
        for (int i = 0; i < 256; i++) {
            int v = Math.max(0, Math.min((1 << bits) - 1, values[i]));
            int longIdx = i / perLong;
            int bitOffset = (i % perLong) * bits;
            data[longIdx] |= ((long) v) << bitOffset;
        }
        return data;
    }

    private static byte[] fullLightArray() {
        byte[] arr = new byte[2048];
        Arrays.fill(arr, (byte) 0xFF);
        return arr;
    }

    private static byte[] compressGzipNbt(NbtCompound root) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(4096);
        try (GZIPOutputStream gzip = new GZIPOutputStream(baos)) {
            // NbtIo.write(output, compound) — uncompressed NBT into gzip stream
            NbtIo.write(root, new java.io.DataOutputStream(gzip));
        }
        return baos.toByteArray();
    }

    private static void generateLevelDat(File worldDir, World world) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();
            int spawnX = client.player != null ? client.player.getBlockX() : 0;
            int spawnY = client.player != null ? client.player.getBlockY() : 64;
            int spawnZ = client.player != null ? client.player.getBlockZ() : 0;

            NbtCompound root = new NbtCompound();
            NbtCompound data = new NbtCompound();

            data.putInt("SpawnX", spawnX);
            data.putInt("SpawnY", spawnY);
            data.putInt("SpawnZ", spawnZ);
            data.putLong("RandomSeed", 0L);
            data.putString("LevelName", "FlexClient_" + worldDir.getName());
            data.putInt("GameType", 1);
            data.putBoolean("MapFeatures", false);
            data.putBoolean("allowCommands", true);
            data.putBoolean("hardcore", false);
            data.putInt("Difficulty", 0);
            data.putBoolean("DifficultyLocked", false);
            data.putLong("Time", 1000L);
            data.putLong("DayTime", 6000L);
            data.putInt("version", 19133);
            data.putInt("DataVersion", DATA_VERSION);
            data.putBoolean("initialized", true);
            data.putBoolean("raining", false);
            data.putBoolean("thundering", false);
            data.putInt("clearWeatherTime", 999999);
            data.putInt("rainTime", 0);
            data.putInt("thunderTime", 0);

            NbtCompound player = new NbtCompound();
            player.putInt("playerGameType", 1);
            player.put("Inventory", new NbtList());
            player.put("EnderItems", new NbtList());

            NbtList pos = new NbtList();
            pos.add(NbtDouble.of(spawnX + 0.5));
            pos.add(NbtDouble.of(spawnY + 0.5));
            pos.add(NbtDouble.of(spawnZ + 0.5));
            player.put("Pos", pos);

            NbtList rot = new NbtList();
            rot.add(NbtFloat.of(0.0f));
            rot.add(NbtFloat.of(0.0f));
            player.put("Rotation", rot);
            data.put("Player", player);

            NbtCompound version = new NbtCompound();
            version.putInt("Id", DATA_VERSION);
            version.putString("Name", "1.20.1");
            version.putBoolean("Snapshot", false);
            data.put("Version", version);

            // Flat void-benzeri generator: kopyalanmayan alanlar rastgele terrain üretmesin
            NbtCompound worldGenSettings = new NbtCompound();
            worldGenSettings.putLong("seed", 0L);
            worldGenSettings.putBoolean("generate_features", false);
            worldGenSettings.putBoolean("bonus_chest", false);

            NbtCompound dimensions = new NbtCompound();
            NbtCompound overworld = new NbtCompound();
            overworld.putString("type", "minecraft:overworld");

            NbtCompound generator = new NbtCompound();
            generator.putString("type", "minecraft:flat");

            NbtCompound settings = new NbtCompound();
            settings.putString("biome", "minecraft:plains");
            settings.put("layers", new NbtList()); // boş = void
            settings.put("structures", new NbtCompound());
            generator.put("settings", settings);

            overworld.put("generator", generator);
            dimensions.put("minecraft:overworld", overworld);
            worldGenSettings.put("dimensions", dimensions);
            data.put("WorldGenSettings", worldGenSettings);

            root.put("Data", data);

            File levelDat = new File(worldDir, "level.dat");
            NbtIo.writeCompressed(root, levelDat);

            File sessionLock = new File(worldDir, "session.lock");
            try (FileOutputStream fos = new FileOutputStream(sessionLock)) {
                fos.write(new byte[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00});
            }

        } catch (Exception e) {
            try {
                new File(worldDir, "level.dat").createNewFile();
            } catch (IOException ignored) {}
        }
    }
}
