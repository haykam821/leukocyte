package xyz.nucleoid.leukocyte;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DataResult;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMap;
import it.unimi.dsi.fastutil.objects.Reference2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerWorldEvents;
import net.fabricmc.fabric.api.util.NbtType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import xyz.nucleoid.leukocyte.region.ProtectionRegion;
import xyz.nucleoid.leukocyte.region.RegionMap;
import xyz.nucleoid.leukocyte.region.RuleReader;
import xyz.nucleoid.leukocyte.rule.ProtectionRule;
import xyz.nucleoid.leukocyte.rule.RuleResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class ProtectionManager extends PersistentState implements RuleReader {
    private static final String KEY = Leukocyte.ID + ":regions";

    private final RegionMap regions = new RegionMap();
    private final Reference2ObjectMap<RegistryKey<World>, RegionMap> regionsByDimension = new Reference2ObjectOpenHashMap<>();

    static {
        ServerWorldEvents.LOAD.register((server, world) -> ProtectionManager.get(server).onWorldLoad(world));
        ServerWorldEvents.UNLOAD.register((server, world) -> ProtectionManager.get(server).onWorldUnload(world));
    }

    private ProtectionManager() {
        super(KEY);
    }

    public static ProtectionManager get(MinecraftServer server) {
        PersistentStateManager state = server.getOverworld().getPersistentStateManager();
        return state.getOrCreate(ProtectionManager::new, KEY);
    }

    private void onWorldLoad(ServerWorld world) {
        RegistryKey<World> dimension = world.getRegistryKey();

        RegionMap map = new RegionMap();
        for (ProtectionRegion region : this.regions) {
            if (region.scope.contains(dimension)) {
                map.add(region);
            }
        }

        this.regionsByDimension.put(dimension, map);
    }

    private void onWorldUnload(ServerWorld world) {
        this.regionsByDimension.remove(world.getRegistryKey());
    }

    public boolean add(ProtectionRegion region) {
        if (this.regions.add(region)) {
            for (Reference2ObjectMap.Entry<RegistryKey<World>, RegionMap> entry : Reference2ObjectMaps.fastIterable(this.regionsByDimension)) {
                RegistryKey<World> dimension = entry.getKey();
                if (region.scope.contains(dimension)) {
                    entry.getValue().add(region);
                }
            }
            return true;
        }
        return false;
    }

    public boolean remove(String key) {
        if (this.regions.remove(key) != null) {
            for (RegionMap regions : this.regionsByDimension.values()) {
                regions.remove(key);
            }
            return true;
        }
        return false;
    }

    public void replace(ProtectionRegion from, ProtectionRegion to) {
        if (this.regions.replace(from, to)) {
            for (RegionMap regions : this.regionsByDimension.values()) {
                regions.replace(from, to);
            }
        }
    }

    @Nullable
    public ProtectionRegion byKey(String key) {
        return this.regions.byKey(key);
    }

    private RegionMap regionsByDimension(RegistryKey<World> dimension) {
        RegionMap regionsByDimension = this.regionsByDimension.get(dimension);
        if (regionsByDimension == null) {
            this.regionsByDimension.put(dimension, regionsByDimension = new RegionMap());
        }
        return regionsByDimension;
    }

    @Override
    public Iterable<ProtectionRegion> sample(RegistryKey<World> dimension) {
        return this.regionsByDimension(dimension);
    }

    @Override
    public Iterable<ProtectionRegion> sample(RegistryKey<World> dimension, BlockPos pos) {
        List<ProtectionRegion> regions = new ArrayList<>();
        for (ProtectionRegion region : this.regionsByDimension(dimension)) {
            if (region.scope.contains(dimension, pos)) {
                regions.add(region);
            }
        }

        return regions;
    }

    @Override
    public RuleResult test(RegistryKey<World> dimension, ProtectionRule rule, @Nullable ServerPlayerEntity source) {
        if (this.doesSourceBypass(source)) {
            return RuleResult.PASS;
        }

        for (ProtectionRegion region : this.regionsByDimension(dimension)) {
            RuleResult result = region.rules.test(rule, source);
            if (result != RuleResult.PASS) {
                return result;
            }
        }

        return RuleResult.PASS;
    }

    @Override
    public RuleResult test(RegistryKey<World> dimension, BlockPos pos, ProtectionRule rule, @Nullable ServerPlayerEntity source) {
        if (this.doesSourceBypass(source)) {
            return RuleResult.PASS;
        }

        for (ProtectionRegion region : this.regionsByDimension(dimension)) {
            if (region.scope.contains(dimension, pos)) {
                RuleResult result = region.rules.test(rule, source);
                if (result != RuleResult.PASS) {
                    return result;
                }
            }
        }

        return RuleResult.PASS;
    }

    private boolean doesSourceBypass(ServerPlayerEntity source) {
        return source != null && source.hasPermissionLevel(4);
    }

    @Override
    public CompoundTag toTag(CompoundTag root) {
        ListTag regionList = new ListTag();

        for (ProtectionRegion region : this.regions) {
            DataResult<Tag> result = ProtectionRegion.CODEC.encodeStart(NbtOps.INSTANCE, region);
            result.result().ifPresent(regionList::add);
        }

        root.put("regions", regionList);

        return root;
    }

    @Override
    public void fromTag(CompoundTag root) {
        this.regions.clear();
        this.regionsByDimension.clear();

        ListTag regionsList = root.getList("regions", NbtType.COMPOUND);
        for (Tag regionTag : regionsList) {
            ProtectionRegion.CODEC.decode(NbtOps.INSTANCE, regionTag)
                    .map(Pair::getFirst)
                    .result()
                    .ifPresent(this::add);
        }
    }

    @Override
    public boolean isDirty() {
        return true;
    }

    public Set<String> getRegionKeys() {
        return this.regions.getKeys();
    }
}
