package com.thebeyond.isleweaver.compat;

import com.thebeyond.isleweaver.Isleweaver;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.LevelResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

// pool order of the discovered End biomes, kept per world: the Beyond picks a cell's biome by its position in the pool
final class InjectedBiomeOrder {
    private static final String FILE = "isleweaver_biome_order.dat";
    private static final String SORTED = "sorted";
    private static final String ORDER = "order";

    private InjectedBiomeOrder() {}

    static String apply(MinecraftServer server, Map<ResourceKey<Biome>, Holder<Biome>> candidates) {
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve(FILE);
        List<String> arrival = candidates.keySet().stream().map(k -> k.location().toString()).toList();
        List<String> order;
        String how;
        try {
            boolean first = !Files.exists(file);
            CompoundTag tag = first ? new CompoundTag() : NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            if (first) {
                // older worlds were generated in arrival order, which only drifts between starts with Better End in the pack
                tag.putBoolean(SORTED, !server.getWorldData().overworldData().isInitialized());
            }
            boolean sorted = tag.getBoolean(SORTED);
            boolean dirty = first;
            if (sorted) {
                order = arrival.stream().sorted().toList();
            } else {
                List<String> kept = new ArrayList<>();
                ListTag saved = tag.getList(ORDER, Tag.TAG_STRING);
                for (int i = 0; i < saved.size(); i++) kept.add(saved.getString(i));
                Set<String> known = new HashSet<>(kept);
                List<String> added = new ArrayList<>(arrival.stream().filter(id -> !known.contains(id)).toList());
                if (!first) added.sort(null);
                if (!added.isEmpty()) {
                    kept.addAll(added);
                    ListTag list = new ListTag();
                    kept.forEach(id -> list.add(StringTag.valueOf(id)));
                    tag.put(ORDER, list);
                    dirty = true;
                }
                order = kept;
            }
            if (dirty) {
                Files.createDirectories(file.getParent());
                NbtIo.writeCompressed(tag, file);
            }
            how = sorted ? "by id" : "as the world was generated";
            if (first) how += sorted ? ", new world" : ", recorded now";
        } catch (Exception e) {
            Isleweaver.LOGGER.error("[Isleweaver] Could not read or write {}, End biomes go into the pool by id", file, e);
            order = arrival.stream().sorted().toList();
            how = "by id, fallback";
        }

        Map<ResourceKey<Biome>, Holder<Biome>> copy = new LinkedHashMap<>(candidates);
        Map<String, ResourceKey<Biome>> keyOf = new HashMap<>();
        copy.keySet().forEach(k -> keyOf.put(k.location().toString(), k));
        candidates.clear();
        for (String id : order) {
            ResourceKey<Biome> key = keyOf.get(id);
            if (key != null) candidates.put(key, copy.get(key));
        }
        return how;
    }
}
