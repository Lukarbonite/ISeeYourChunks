package com.lukarbonite.iseeyourchunks.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ServiceLoader;

/**
 * SPI abstraction for the handful of Minecraft APIs that differ between the supported MC versions
 * (26.2 unobfuscated vs 1.21.1 Mojang-mapped). Every method here corresponds to one verified
 * 26.2-vs-1.21.1 delta; the shared code in {@code common} calls {@link #INSTANCE} instead of the
 * version-specific API, and each version module ships exactly one {@code VersionHelperImpl}
 * registered via {@code META-INF/services/com.lukarbonite.iseeyourchunks.platform.VersionHelper}.
 *
 * <p>This implementation is {@code ServiceLoad}ed eagerly (it backs payload registration, which runs on
 * the dedicated server too), so no client-only or Voxy type may appear in a method <em>signature</em>.
 * Method bodies may reference them: the JVM resolves those lazily at call time, and every such method is
 * only called behind the existing environment / Voxy-loaded guards. Client-only camera access lives in
 * {@link ClientVersionHelper}.
 */
public interface VersionHelper {

	VersionHelper INSTANCE = ServiceLoader.load(VersionHelper.class)
		.findFirst()
		.orElseThrow(() -> new IllegalStateException("No VersionHelper implementation found"));

	/** Packs a chunk coordinate to a long ({@code ChunkPos.pack}/{@code asLong}). */
	long packChunk(int chunkX, int chunkZ);

	/** Packs a {@link ChunkPos} to a long ({@code ChunkPos#pack}/{@code #toLong}). */
	long packChunkPos(ChunkPos pos);

	/** The player's server level ({@code ServerPlayer#level}/{@code #serverLevel}). */
	ServerLevel serverLevel(ServerPlayer player);

	/** Whether the player already tracks the chunk through vanilla ({@code ChunkMap#isChunkTracked}). */
	boolean isChunkTracked(ChunkMap chunkMap, ServerPlayer player, int chunkX, int chunkZ);

	/** The chunk's lowest section Y index ({@code getMinSectionY}/{@code blockToSectionCoord(getMinBuildHeight)}). */
	int minSectionY(LevelChunk chunk);

	/**
	 * Voxy's current render system, or {@code null} ({@code IVoxyRenderSystemHolder}/{@code IGetVoxyRenderSystem}
	 * depending on the Voxy fork). Returned as {@link Object} so no Voxy type is in the signature; the caller
	 * (confined to Voxy-present code) casts to {@code me.cortex.voxy.client.core.VoxyRenderSystem}.
	 */
	Object voxyRenderSystemNullable();

	/** Builds a namespaced payload type ({@code Identifier}/{@code ResourceLocation}). */
	<T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path);

	/** Registers the mod's serverbound + clientbound payload types with the loader's registry. */
	void registerPayloadTypes();
}
