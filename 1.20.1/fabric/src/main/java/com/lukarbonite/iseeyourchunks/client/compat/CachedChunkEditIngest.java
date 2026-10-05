package com.lukarbonite.iseeyourchunks.client.compat;

import com.lukarbonite.iseeyourchunks.platform.ClientVersionHelper;
import com.lukarbonite.iseeyourchunks.platform.VersionHelper;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps Voxy's LOD in step with edits to cached chunks that Sodium is not drawing (1.20.1 only).
 *
 * <p><b>The gap.</b> A 1.20.1 server ignores the client's render distance: it sends every chunk within its own view
 * distance. With a client render distance below the server's, the chunks in between sit in the client cache, Sodium
 * does not draw them, and Voxy shows them instead. Their edits arrive as ordinary vanilla block-update packets, but
 * Voxy only re-ingests a cached chunk when it unloads (its own block-change hook covers just a block turning to air on
 * a section border), and the far-chunk streamer leaves these chunks to vanilla. So an edit there stayed invisible until
 * the chunk unloaded. (1.20.2+ servers stop at the client's render distance, so later versions have no such ring.)
 *
 * <p><b>The fix.</b> A block change in a cached chunk marks it, and at the end of that client tick it goes through
 * Voxy's own {@code VoxelIngestService.tryAutoIngestChunk}: exactly what Voxy would ingest when the chunk unloads, lit
 * by the client light engine, which has real light for these vanilla-sent chunks. Edits show within the tick they
 * arrive in, and several in one tick cost one ingest. The edit's light may not have settled by then (the client
 * relights on render, the server's light update follows), so the chunk is ingested once more {@link #FOLLOW_UP_TICKS}
 * after its last edit to pick up the final light. Chunks Sodium draws are skipped; Voxy hides its LOD there and
 * ingests the chunk itself when Sodium lets it go.
 *
 * <p>Only block edits trigger this, never light updates on their own: every chunk arrives with one, so reacting to
 * them re-ingested the whole cached ring on joining, piled on top of Voxy's own ingest of the same chunks, and tripped
 * Voxy's section saving ("Section freed while marked as dirty").
 */
public final class CachedChunkEditIngest {
	/** Ticks after a chunk's last edit before it is ingested again with settled light. */
	private static final long FOLLOW_UP_TICKS = 10L;

	/** Chunks (packed) edited since the last tick. */
	private static final LongOpenHashSet EDITED = new LongOpenHashSet();
	/** Chunk (packed) -> tick its settled-light follow-up ingest is due. */
	private static final Long2LongOpenHashMap FOLLOW_UP = new Long2LongOpenHashMap();

	private static ClientLevel trackedLevel;
	private static long tick;

	private CachedChunkEditIngest() {
	}

	/** A block in {@code level} changed state; called from {@code ClientLevel.setBlocksDirty}. */
	public static void noteBlockChanged(ClientLevel level, BlockPos pos) {
		if (!VoxyIngestBridge.isAvailable()) {
			return;
		}
		int chunkX = pos.getX() >> 4;
		int chunkZ = pos.getZ() >> 4;
		markEdited(level, chunkX, chunkZ);
		// A block on a chunk face changes the faces (and light) the neighbouring chunk's LOD shows there.
		int localX = pos.getX() & 15;
		int localZ = pos.getZ() & 15;
		if (localX == 0) markEdited(level, chunkX - 1, chunkZ);
		if (localX == 15) markEdited(level, chunkX + 1, chunkZ);
		if (localZ == 0) markEdited(level, chunkX, chunkZ - 1);
		if (localZ == 15) markEdited(level, chunkX, chunkZ + 1);
	}

	/** Ingests this tick's edits and any follow-ups that are due; called at the end of each client tick. */
	public static void tick() {
		tick++;
		if (EDITED.isEmpty() && FOLLOW_UP.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		ClientLevel level = client.level;
		if (level == null || level != trackedLevel) {
			reset();
			return;
		}
		Vec3 camera = ClientVersionHelper.INSTANCE.cameraPosition();
		if (camera == null) {
			return;
		}
		double sodiumReachBlocks = Math.max(0, client.options.getEffectiveRenderDistance() - 1) * 16.0D;
		double sodiumReachSq = sodiumReachBlocks * sodiumReachBlocks;

		LongIterator edited = EDITED.iterator();
		while (edited.hasNext()) {
			long packed = edited.nextLong();
			if (ingest(level, camera, sodiumReachSq, packed)) {
				FOLLOW_UP.put(packed, tick + FOLLOW_UP_TICKS);
			}
		}
		EDITED.clear();

		ObjectIterator<Long2LongMap.Entry> followUps = FOLLOW_UP.long2LongEntrySet().fastIterator();
		while (followUps.hasNext()) {
			Long2LongMap.Entry entry = followUps.next();
			if (entry.getLongValue() <= tick) {
				ingest(level, camera, sodiumReachSq, entry.getLongKey());
				followUps.remove();
			}
		}
	}

	/** Drops all pending work (disconnect, or a different level). */
	public static void reset() {
		EDITED.clear();
		FOLLOW_UP.clear();
		trackedLevel = null;
	}

	/** Ingests one chunk if it is cached and outside Sodium's reach; whether it was ingested. */
	private static boolean ingest(ClientLevel level, Vec3 camera, double sodiumReachSq, long packed) {
		int chunkX = ChunkPos.getX(packed);
		int chunkZ = ChunkPos.getZ(packed);
		if (distanceToChunkSq(camera, chunkX, chunkZ) < sodiumReachSq) {
			return false;
		}
		// A chunk the client dropped was ingested by Voxy as it unloaded, with its final state.
		LevelChunk chunk = (LevelChunk) level.getChunkSource().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
		if (chunk == null) {
			return false;
		}
		Backend.ingest(chunk);
		return true;
	}

	private static void markEdited(ClientLevel level, int chunkX, int chunkZ) {
		if (level != trackedLevel) {
			reset();
			trackedLevel = level;
		}
		EDITED.add(VersionHelper.INSTANCE.packChunk(chunkX, chunkZ));
	}

	/** Squared horizontal distance from the camera to the nearest point of a chunk's footprint. */
	private static double distanceToChunkSq(Vec3 camera, int chunkX, int chunkZ) {
		double minX = chunkX << 4;
		double minZ = chunkZ << 4;
		double dx = Math.max(Math.max(minX - camera.x, camera.x - (minX + 16.0D)), 0.0D);
		double dz = Math.max(Math.max(minZ - camera.z, camera.z - (minZ + 16.0D)), 0.0D);
		return dx * dx + dz * dz;
	}

	/** Every Voxy reference lives here, only touched once Voxy is confirmed present. */
	private static final class Backend {
		private Backend() {
		}

		static void ingest(LevelChunk chunk) {
			try {
				me.cortex.voxy.common.world.service.VoxelIngestService.tryAutoIngestChunk(chunk);
			} catch (Throwable throwable) {
				// Voxy not initialised yet, or its internals moved - never let a live edit break the client tick.
			}
		}
	}
}
