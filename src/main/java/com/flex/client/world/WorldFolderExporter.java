package com.flex.client.world;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minecraft 1.20.1 uyumlu gerçek dünya klasörü export eden yardımcı.
 * Çıktı: .minecraft/flexclient_worlds/<isim>_<timestamp>/
 *   - level.dat
 *   - region/*.mca (standart Anvil formatında)
 * Bu klasör doğrudan .minecraft/saves/ altına kopyalanarak singleplayer'da açılabilir.
 */
public class WorldFolderExporter {

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

    /**
     * Client tarafında yüklü chunk'lardan standart Anvil .mca region dosyaları oluştur.
     */
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

    /**
     * Tek bir region (.mca) dosyası yaz — Anvil formatı.
     */
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
            raf.write(new byte[8192]); // header placeholder

            int sectorOffset = 2;
            int[] locations = new int[1024];

            for (int i = 0; i < 1024; i++) {
                if (chunkDataArray[i] == null) {
                    locations[i] = 0;
                    continue;
                }

                byte[] data = chunkDataArray[i];
                int dataLen = data.length;
                int sectors = (dataLen + 5 + 4095) / 4096;

                locations[i] = (sectorOffset << 8) | sectors;

                raf.seek(sectorOffset * 4096L);

                // 4-byte length + 1-byte compression (2=zlib) + data
                raf.writeByte((dataLen >> 24) & 0xFF);
                raf.writeByte((dataLen >> 16) & 0xFF);
                raf.writeByte((dataLen >> 8) & 0xFF);
                raf.writeByte(dataLen & 0xFF);
                raf.writeByte(2);
                raf.write(data);

                int written = 5 + dataLen;
                int padLen = (sectors * 4096) - written;
                if (padLen > 0) raf.write(new byte[padLen]);

                sectorOffset += sectors;
            }

            // Location table
            raf.seek(0);
            for (int i = 0; i < 1024; i++) {
                raf.writeByte((locations[i] >> 24) & 0xFF);
                raf.writeByte((locations[i] >> 16) & 0xFF);
                raf.writeByte((locations[i] >> 8) & 0xFF);
                raf.writeByte(locations[i] & 0xFF);
            }

            // Timestamp table
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
     * Bir chunk'ı Anvil NBT formatında serialize et.
     * 1.20.1 chunk NBT yapısı (Level altında):
     *   xPos, zPos, Status, sections[], Heightmaps, block_entities
     */
    private static byte[] serializeChunk(World world, int chunkX, int chunkZ) {
        try {
            WorldChunk chunk = world.getChunk(chunkX, chunkZ);
            if (chunk == null) return null;

            NbtCompound root = new NbtCompound();
            NbtCompound level = new NbtCompound();

            level.putInt("xPos", chunkX);
            level.putInt("zPos", chunkZ);
            level.putString("Status", "full");
            level.putLong("InhabitedTime", 0L);
            level.putLong("LastUpdate", world.getTime());

            // --- Section'lar ---
            NbtList sectionsList = new NbtList();
            ChunkSection[] sections = chunk.getSectionArray();
            int minSectionY = world.getBottomSectionCoord() >> 4;

            for (int i = 0; i < sections.length; i++) {
                ChunkSection section = sections[i];
                if (section == null) continue;

                NbtCompound sectionNbt = new NbtCompound();
                sectionNbt.putByte("Y", (byte) (minSectionY + i));

                // Block states — manuel palette + packed data
                NbtCompound blockStatesNbt = serializeSectionBlocks(world, chunkX, chunkZ, minSectionY + i);
                sectionNbt.put("block_states", blockStatesNbt);

                // Biomes — varsayılan
                NbtCompound biomesNbt = new NbtCompound();
                NbtList biomePalette = new NbtList();
                biomePalette.add(net.minecraft.nbt.NbtString.of("minecraft:plains"));
                biomesNbt.put("palette", biomePalette);
                sectionNbt.put("biomes", biomesNbt);

                // Light — tam aydınlık (0xFF = her nibble 15)
                sectionNbt.putByteArray("BlockLight", fullLightArray());
                sectionNbt.putByteArray("SkyLight", fullLightArray());

                sectionsList.add(sectionNbt);
            }
            level.put("sections", sectionsList);

            // --- Heightmaps ---
            NbtCompound heightmaps = new NbtCompound();
            long[] emptyHeightmap = new long[37]; // 16x16 -> 37 longs (9 bits per entry)
            java.util.Arrays.fill(emptyHeightmap, 0L);
            heightmaps.putLongArray("WORLD_SURFACE", emptyHeightmap);
            level.put("Heightmaps", heightmaps);

            // --- Block entities ---
            NbtList blockEntities = new NbtList();
            try {
                var beMap = chunk.getBlockEntities();
                if (beMap != null) {
                    for (var entry : beMap.entrySet()) {
                        BlockPos bePos = entry.getKey();
                        var be = entry.getValue();
                        if (be != null) {
                            NbtCompound beNbt = new NbtCompound();
                            try {
                                java.lang.reflect.Method m = be.getClass().getMethod("writeNbt", NbtCompound.class);
                                m.setAccessible(true);
                                m.invoke(be, beNbt);
                                beNbt.putInt("x", bePos.getX());
                                beNbt.putInt("y", bePos.getY());
                                beNbt.putInt("z", bePos.getZ());
                                blockEntities.add(beNbt);
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
            level.put("block_entities", blockEntities);

            root.put("Level", level);
            root.putInt("DataVersion", 3465);

            // NBT'yi compressed byte[] olarak yaz
            File tmpFile = File.createTempFile("flexchunk", ".nbt");
            NbtIo.writeCompressed(root, tmpFile);
            byte[] data = Files.readAllBytes(tmpFile.toPath());
            tmpFile.delete();
            return data;

        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Bir chunk section'daki tüm blokları okuyup NBT palette + packed data üret.
     * 16x16x16 = 4096 blok pozisyonu.
     */
    private static NbtCompound serializeSectionBlocks(World world, int chunkX, int chunkZ, int sectionY) {
        NbtCompound nbt = new NbtCompound();

        int baseX = chunkX << 4;
        int baseY = sectionY << 4;
        int baseZ = chunkZ << 4;

        // Her blok pozisyonu için BlockState oku
        BlockState[] states = new BlockState[4096];
        for (int bx = 0; bx < 16; bx++) {
            for (int by = 0; by < 16; by++) {
                for (int bz = 0; bz < 16; bz++) {
                    int index = (by << 8) | (bz << 4) | bx; // Anvil index sırası: y >> z >> x
                    BlockPos pos = new BlockPos(baseX + bx, baseY + by, baseZ + bz);
                    states[index] = world.getBlockState(pos);
                }
            }
        }

        // Palette oluştur — her unique block state için bir entry
        Map<String, Integer> paletteMap = new LinkedHashMap<>();
        List<NbtCompound> paletteEntries = new ArrayList<>();

        for (BlockState state : states) {
            String blockId = getBlockId(state);
            if (!paletteMap.containsKey(blockId)) {
                int idx = paletteEntries.size();
                paletteMap.put(blockId, idx);

                NbtCompound entry = new NbtCompound();
                entry.putString("Name", blockId);

                // Properties (yön, su seviyesi vb.)
                NbtCompound props = new NbtCompound();
                try {
                    for (var propEntry : state.getEntries().entrySet()) {
                        Property<?> prop = propEntry.getKey();
                        Comparable<?> val = propEntry.getValue();
                        props.putString(prop.getName(), val.toString());
                    }
                } catch (Exception ignored) {}

                if (!props.getKeys().isEmpty()) {
                    entry.put("Properties", props);
                }
                paletteEntries.add(entry);
            }
        }

        NbtList paletteList = new NbtList();
        for (NbtCompound entry : paletteEntries) paletteList.add(entry);
        nbt.put("palette", paletteList);

        // Packed data — eğer palette > 1 ise
        if (paletteMap.size() > 1) {
            int bits = Math.max(4, Integer.SIZE - Integer.numberOfLeadingZeros(paletteMap.size() - 1));
            int blocksPerLong = 64 / bits;
            int longCount = (4096 + blocksPerLong - 1) / blocksPerLong;
            long[] packed = new long[longCount];

            for (int i = 0; i < 4096; i++) {
                String blockId = getBlockId(states[i]);
                int paletteIdx = paletteMap.getOrDefault(blockId, 0);
                int longIdx = i / blocksPerLong;
                int bitOffset = (i % blocksPerLong) * bits;
                packed[longIdx] |= ((long) paletteIdx & ((1L << bits) - 1)) << bitOffset;
            }

            nbt.putLongArray("data", packed);
        }

        return nbt;
    }

    /**
     * BlockState'ten registry ID'sini al (örn: "minecraft:stone")
     */
    private static String getBlockId(BlockState state) {
        if (state == null || state.isAir()) return "minecraft:air";
        try {
            return net.minecraft.registry.Registry.BLOCK.getId(state.getBlock()).toString();
        } catch (Exception e) {
            return "minecraft:stone";
        }
    }

    /**
     * 2048 byte'lık tam aydınlık light array'i (her nibble = 15)
     */
    private static byte[] fullLightArray() {
        byte[] arr = new byte[2048];
        java.util.Arrays.fill(arr, (byte) 0xFF);
        return arr;
    }

    /**
     * level.dat oluştur — Minecraft'ın dünyayı tanıması için.
     */
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
            data.putLong("RandomSeed", System.currentTimeMillis());
            data.putString("LevelName", "FlexClient_" + worldDir.getName());
            data.putString("generatorName", "default");
            data.putInt("generatorVersion", 0);
            data.putString("LevelGeneratorOptions", "");
            data.putInt("GameType", 1); // Creative
            data.putBoolean("MapFeatures", true);
            data.putBoolean("allowCommands", true);
            data.putBoolean("hardcore", false);
            data.putInt("Difficulty", 0);
            data.putBoolean("DifficultyLocked", false);
            data.putLong("Time", 0);
            data.putLong("DayTime", 6000); // Gündüz
            data.putInt("version", 19133);
            data.putInt("DataVersion", 3465);

            // Player
            NbtCompound player = new NbtCompound();
            player.putDouble("playerGameType", 1); // Creative
            player.put("Inventory", new NbtList());
            player.put("EnderItems", new NbtList());

            NbtList pos = new NbtList();
            pos.add(net.minecraft.nbt.NbtDouble.of(spawnX + 0.5));
            pos.add(net.minecraft.nbt.NbtDouble.of(spawnY + 0.5));
            pos.add(net.minecraft.nbt.NbtDouble.of(spawnZ + 0.5));
            player.put("Pos", pos);

            NbtList rot = new NbtList();
            rot.add(net.minecraft.nbt.NbtFloat.of(0.0f));
            rot.add(net.minecraft.nbt.NbtFloat.of(0.0f));
            player.put("Rotation", rot);

            data.put("Player", player);

            // Version
            NbtCompound version = new NbtCompound();
            version.putInt("Id", 3465);
            version.putString("Name", "1.20.1");
            version.putBoolean("Snapshot", false);
            data.put("Version", version);

            // WorldGenSettings (1.20.1'de zorunlu)
            NbtCompound worldGenSettings = new NbtCompound();
            worldGenSettings.putLong("seed", System.currentTimeMillis());
            worldGenSettings.putBoolean("generate_features", true);
            worldGenSettings.putBoolean("bonus_chest", false);

            NbtCompound dimensions = new NbtCompound();
            NbtCompound overworld = new NbtCompound();
            overworld.putString("type", "minecraft:overworld");

            NbtCompound generator = new NbtCompound();
            generator.putString("type", "minecraft:noise");

            NbtCompound genSettings = new NbtCompound();
            genSettings.putString("type", "minecraft:overworld");
            generator.put("settings", genSettings);

            NbtCompound biomeSource = new NbtCompound();
            biomeSource.putString("type", "minecraft:multi_noise");
            NbtList preset = new NbtList();
            biomeSource.put("preset", preset);
            generator.put("biome_source", biomeSource);

            overworld.put("generator", generator);
            dimensions.put("minecraft:overworld", overworld);
            worldGenSettings.put("dimensions", dimensions);
            data.put("WorldGenSettings", worldGenSettings);

            root.put("Data", data);

            File levelDat = new File(worldDir, "level.dat");
            NbtIo.writeCompressed(root, levelDat);

            File levelDatMcr = new File(worldDir, "level.dat_mcr");
            NbtIo.writeCompressed(root, levelDatMcr);

            // session.lock
            File sessionLock = new File(worldDir, "session.lock");
            try (FileOutputStream fos = new FileOutputStream(sessionLock)) {
                fos.write(0);
            }

        } catch (Exception e) {
            try {
                new File(worldDir, "level.dat").createNewFile();
            } catch (IOException ignored) {}
        }
    }
}
