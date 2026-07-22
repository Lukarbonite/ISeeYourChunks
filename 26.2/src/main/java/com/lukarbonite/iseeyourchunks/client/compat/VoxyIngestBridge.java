package com.lukarbonite.iseeyourchunks.client.compat;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Pushes a streamed far chunk into Voxy's LOD model right when it arrives.
 *
 * <p>Voxy normally ingests a chunk only when it <em>unloads</em> from the vanilla client cache
 * ({@code MixinClientChunkCache} → {@code VoxelIngestService.tryAutoIngestChunk}), on the assumption that
 * anything still resident is being drawn live by Sodium. Our streamed chunks break that assumption: they
 * sit permanently in the widened cache, beyond Sodium's render distance, so they are never drawn live and
 * never unload - Voxy would otherwise never see them. Calling the same public ingest entry point on
 * arrival closes that gap.
 *
 * <p>Voxy is an optional, compile-time-only dependency. Every reference to its classes is confined to
 * {@link Backend}, which is only ever touched once {@link #VOXY_PRESENT} is confirmed, so Voxy's classes
 * never resolve when the mod is absent.
 */
public final class VoxyIngestBridge {
	private static final boolean VOXY_PRESENT = FabricLoader.getInstance().isModLoaded("voxy");

	/** Total far chunks handed to Voxy; the first, and each power-of-two-ish milestone, is logged. */
	private static final AtomicLong INGEST_COUNT = new AtomicLong();

	private VoxyIngestBridge() {
	}

	public static boolean isAvailable() {
		return VOXY_PRESENT;
	}

	/** Hands {@code chunk} to Voxy's auto-ingest, if Voxy is installed. Fails open on any error. */
	public static void ingest(LevelChunk chunk) {
		if (!VOXY_PRESENT || chunk == null) {
			return;
		}
		Backend.ingest(chunk);

		long count = INGEST_COUNT.incrementAndGet();
		if (count == 1 || count % 64 == 0) {
			ISeeYourChunks.LOGGER.info("Handed {} streamed far chunk(s) to Voxy for LOD rendering.", count);
		}
	}

	private static final class Backend {
		private Backend() {
		}

		private static void ingest(LevelChunk chunk) {
			try {
				me.cortex.voxy.common.world.service.VoxelIngestService.tryAutoIngestChunk(chunk);
			} catch (Throwable throwable) {
				// Voxy not initialised yet, or its internals moved - never let ingestion break chunk receipt.
			}
		}
	}
}
