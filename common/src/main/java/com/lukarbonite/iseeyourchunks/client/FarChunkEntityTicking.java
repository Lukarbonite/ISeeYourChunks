package com.lukarbonite.iseeyourchunks.client;

import com.lukarbonite.iseeyourchunks.client.mixin.ClientLevelAccessor;
import com.lukarbonite.iseeyourchunks.platform.VersionHelper;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.ChunkPos;

/**
 * Keeps entities ticking in streamed chunks that lie past the client's chunk-storage radius.
 *
 * <p>The client only ticks entities in chunks it has marked ticking, and it marks a chunk when
 * {@code ClientChunkCache.replaceWithPacketData} stores it. A streamed chunk beyond the storage radius is rejected
 * there (and decoded straight into Voxy instead), so it is never marked, and the mobs the server reveals in it
 * never tick. Movement and rotation packets only set an interpolation target that the entity's own tick moves it
 * towards, so those mobs froze in place or lagged behind. Players are exempt (they are always ticked), which is why
 * the viewed player moved while the mobs around them didn't; a mount they ride was frozen too, carrying them along.
 *
 * <p>So a rejected streamed chunk is marked ticking exactly as a stored one would be, and unmarked when the server
 * forgets it ({@code ClientChunkCache.drop}, which only unmarks chunks it stored). Chunks the cache does store are
 * left to vanilla.
 */
public final class FarChunkEntityTicking {
	private static final LongSet TICKING = new LongOpenHashSet();
	private static ClientLevel trackedLevel;

	private FarChunkEntityTicking() {
	}

	/** A streamed chunk at {@code chunkX, chunkZ} arrived; {@code cached} is whether the cache stored it. */
	public static void onChunkArrived(ClientLevel level, int chunkX, int chunkZ, boolean cached) {
		resetIfLevelChanged(level);
		long packed = VersionHelper.INSTANCE.packChunk(chunkX, chunkZ);
		if (cached) {
			// Vanilla owns it now, and unmarks it itself when it drops the chunk.
			TICKING.remove(packed);
		} else if (TICKING.add(packed)) {
			((ClientLevelAccessor) level).iSeeYourChunks$entityStorage().startTicking(new ChunkPos(chunkX, chunkZ));
		}
	}

	/** The server forgot {@code pos}; stop ticking it if we were the ones who started. */
	public static void onChunkDropped(ClientLevel level, ChunkPos pos) {
		resetIfLevelChanged(level);
		if (TICKING.remove(VersionHelper.INSTANCE.packChunkPos(pos))) {
			((ClientLevelAccessor) level).iSeeYourChunks$entityStorage().stopTicking(pos);
		}
	}

	private static void resetIfLevelChanged(ClientLevel level) {
		if (level != trackedLevel) {
			trackedLevel = level;
			TICKING.clear();
		}
	}
}
