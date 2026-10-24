package de.lifemod;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LifeState extends PersistentState {
    private final Map<UUID, PlayerLife> players = new HashMap<>();

    public static LifeState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager()
                .getOrCreate(LifeState::fromNbt, LifeState::new, LifeMod.MOD_ID);
    }

    public boolean has(UUID id) {
        return players.containsKey(id);
    }

    public PlayerLife getOrCreate(UUID id) {
        markDirty();
        return players.computeIfAbsent(id, k -> new PlayerLife());
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtCompound list = new NbtCompound();
        for (Map.Entry<UUID, PlayerLife> e : players.entrySet()) {
            list.put(e.getKey().toString(), e.getValue().toNbt());
        }
        nbt.put("players", list);
        return nbt;
    }

    public static LifeState fromNbt(NbtCompound nbt) {
        LifeState s = new LifeState();
        NbtCompound list = nbt.getCompound("players");
        for (String key : list.getKeys()) {
            s.players.put(UUID.fromString(key), PlayerLife.fromNbt(list.getCompound(key)));
        }
        return s;
    }
}
