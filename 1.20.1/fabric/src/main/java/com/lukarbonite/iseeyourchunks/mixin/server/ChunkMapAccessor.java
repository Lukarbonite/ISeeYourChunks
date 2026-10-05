package com.lukarbonite.iseeyourchunks.mixin.server;

import net.minecraft.server.level.ChunkMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Opens up {@code ChunkMap.viewDistance} (package-private), the radius 1.20.1 sends chunks to every player within.
 * 1.20.1 predates {@code ChunkMap.isChunkTracked}; VersionHelperImpl rebuilds that check from this radius and
 * vanilla's own {@code ChunkMap.isChunkInRange}, so the far-chunk streamer can skip chunks vanilla already sent.
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAccessor {
	@Accessor("viewDistance")
	int iSeeYourChunks$viewDistance();
}
