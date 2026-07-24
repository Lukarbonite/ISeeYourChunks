package com.lukarbonite.iseeyourchunks.client.compat;

import net.minecraft.network.protocol.game.ClientboundLightUpdatePacketData;

/**
 * Carries the real light payload of a chunk packet from the packet handler down into our Voxy ingest.
 *
 * <p>A far chunk's true block and sky light (torches, lava, actual sky exposure) ride along in the
 * {@link ClientboundLightUpdatePacketData} of its {@code ClientboundLevelChunkWithLightPacket}, but vanilla
 * only applies that light for chunks it accepts - our out-of-range far chunks never get it, and our ingest
 * hook on {@code ClientChunkCache.replaceWithPacketData} only sees the block data, not the light. So a mixin
 * stashes the packet's light here at the start of {@code handleLevelChunkWithLight} and clears it at the end;
 * because that handler calls {@code replaceWithPacketData} (and thus our ingest) synchronously on the same
 * thread, the ingest can read the matching light straight back out.
 */
public final class FarChunkLightCapture {
	private static final ThreadLocal<Captured> CURRENT = new ThreadLocal<>();

	private FarChunkLightCapture() {
	}

	private record Captured(int chunkX, int chunkZ, ClientboundLightUpdatePacketData light) {
	}

	/** Stash the light for the chunk currently being handled. Paired with {@link #end()}. */
	public static void begin(int chunkX, int chunkZ, ClientboundLightUpdatePacketData light) {
		CURRENT.set(new Captured(chunkX, chunkZ, light));
	}

	/** Clear the stash once the handler returns, so it can never be read for the wrong chunk. */
	public static void end() {
		CURRENT.remove();
	}

	/** The stashed light for {@code (chunkX, chunkZ)}, or {@code null} if none is currently captured for it. */
	public static ClientboundLightUpdatePacketData lightFor(int chunkX, int chunkZ) {
		Captured captured = CURRENT.get();
		return captured != null && captured.chunkX() == chunkX && captured.chunkZ() == chunkZ
			? captured.light()
			: null;
	}
}
