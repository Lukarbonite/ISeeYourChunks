package com.lukarbonite.iseeyourchunks.mixin.server;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens up {@code ChunkMap.isChunkTracked}, which is package-private on 1.21.1 (it is public on 26.2, where
 * both the streamer and the tracker mixin call it directly). The far-chunk streamer and
 * {@link ChunkMapTrackedEntityMixin} both need to ask whether a player already tracks a chunk through
 * vanilla, to avoid double-sending it.
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
	@Invoker("isChunkTracked")
	boolean iSeeYourChunks$invokeIsChunkTracked(ServerPlayer player, int chunkX, int chunkZ);
}
