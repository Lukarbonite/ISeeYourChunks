package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.client.ISeeYourChunksFabricClient;
import com.lukarbonite.iseeyourchunks.client.compat.VoxyIngestBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Two jobs on the client chunk cache, both in service of streamed far terrain.
 *
 * <p><b>Storage radius.</b> Widens {@code Storage}'s radius so chunks streamed around distant players are
 * accepted instead of dropped by {@code Storage.inRange} ("Ignoring chunk since it's not in the view
 * range"). Both entry points must be covered: {@code updateViewRadius} only runs when the server
 * <em>changes</em> the view distance mid-session, whereas on join the radius arrives in the login packet
 * and {@code ClientChunkCache} is built straight from the constructor, so hooking only the former does
 * nothing.
 *
 * <p><b>Voxy ingest.</b> A streamed far chunk sits permanently in the widened cache, beyond Sodium's
 * render distance, so Sodium never draws it and it never unloads - which means Voxy, whose ingest fires
 * on unload, would never see it. On arrival we hand such chunks straight to Voxy. Near chunks are left
 * alone: Sodium draws them and Voxy ingests them the normal way when they eventually unload.
 *
 * <p><b>Beyond the storage radius.</b> The cache can only hold chunks within a bounded radius of the
 * viewer, so a player thousands of blocks away has their streamed chunks rejected outright ("Ignoring
 * chunk since it's not in the view range") before the packet is even decoded. Voxy does not actually
 * need those chunks cached - it keeps its own compact LOD once a chunk is ingested. So for a rejected
 * chunk we decode a throwaway {@link LevelChunk} straight from the still-unread packet buffer and hand
 * only that to Voxy, which lets far terrain render at any distance without holding the chunks in memory.
 */
@Mixin(ClientChunkCache.class)
abstract class ClientChunkCacheMixin {
	/** Counts far chunks decoded via the out-of-range bypass, so the path is visible in logs. */
	@org.spongepowered.asm.mixin.Unique
	private static final java.util.concurrent.atomic.AtomicLong iSeeYourChunks$bypassCount =
		new java.util.concurrent.atomic.AtomicLong();

	@Shadow
	@Final
	ClientLevel level;

	@ModifyVariable(method = "<init>(Lnet/minecraft/client/multiplayer/ClientLevel;I)V", at = @At("HEAD"), argsOnly = true)
	private static int iSeeYourChunks$expandInitialStorageRadius(int radius) {
		return iSeeYourChunks$expand(radius, "constructor");
	}

	@ModifyVariable(method = "updateViewRadius(I)V", at = @At("HEAD"), argsOnly = true)
	private int iSeeYourChunks$expandStorageRadius(int radius) {
		return iSeeYourChunks$expand(radius, "updateViewRadius");
	}

	private static int iSeeYourChunks$expand(int radius, String source) {
		int expanded = Math.max(radius, ISeeYourChunksFabricClient.farChunkRadius());
		if (expanded != radius) {
			ISeeYourChunks.LOGGER.info(
				"Widened client chunk storage radius {} -> {} chunks (via {}) to accept streamed far chunks.",
				radius, expanded, source);
		}
		return expanded;
	}

	@Inject(method = "replaceWithPacketData", at = @At("RETURN"))
	private void iSeeYourChunks$ingestFarChunkIntoVoxy(
		int chunkX, int chunkZ, FriendlyByteBuf buffer,
		Map<Heightmap.Types, long[]> heightmaps,
		Consumer<ClientboundLevelChunkPacketData.BlockEntityTagOutput> blockEntities,
		CallbackInfoReturnable<LevelChunk> cir
	) {
		if (!VoxyIngestBridge.isAvailable()) {
			return;
		}

		LevelChunk chunk = cir.getReturnValue();
		if (chunk != null) {
			// Accepted into the cache. Ingest only the far ones; near chunks are Sodium's to draw.
			if (iSeeYourChunks$isFarChunk(chunkX, chunkZ)) {
				VoxyIngestBridge.ingest(chunk);
			}
			return;
		}

		// Rejected as out-of-range: vanilla logged and returned without touching the buffer, so the chunk
		// data is intact. Decode it into a throwaway chunk purely to feed Voxy's LOD - never cached, so
		// this works at any distance with no unbounded memory. A no-op block-entity sink keeps the decode
		// from registering block entities into a level that has no home chunk for them.
		if (buffer.readableBytes() <= 0) {
			return;
		}
		try {
			LevelChunk far = new LevelChunk(this.level, new ChunkPos(chunkX, chunkZ));
			far.replaceWithPacketData(buffer, heightmaps, tag -> { });
			VoxyIngestBridge.ingest(far);

			long count = iSeeYourChunks$bypassCount.incrementAndGet();
			if (count == 1 || count % 64 == 0) {
				ISeeYourChunks.LOGGER.info(
					"Bypassed storage for {} out-of-range far chunk(s) (last {},{}), decoded straight to Voxy.",
					count, chunkX, chunkZ);
			}
		} catch (RuntimeException exception) {
			// A malformed or partially-read buffer must never take down chunk handling - but surface it,
			// since a systematic decode failure here would mean no far terrain at all.
			ISeeYourChunks.LOGGER.warn("Failed to decode out-of-range far chunk {},{} for Voxy.", chunkX, chunkZ, exception);
		}
	}

	/**
	 * A chunk is "far" - our streaming's job, not Sodium's - when it lies beyond the server's normal
	 * view distance. That is exactly the range within which vanilla already tracks chunks, and which the
	 * streamer deliberately skips, so anything past it can only be a streamed chunk. Falls back to the
	 * client's own render distance before the server ack arrives.
	 */
	@org.spongepowered.asm.mixin.Unique
	private static boolean iSeeYourChunks$isFarChunk(int chunkX, int chunkZ) {
		Minecraft client = Minecraft.getInstance();
		if (client.player == null) {
			return false;
		}

		int known = ISeeYourChunksFabricClient.knownServerRenderDistanceChunks();
		int threshold = known > 0 ? known : client.options.getEffectiveRenderDistance();
		SectionPos player = SectionPos.of(client.player.blockPosition());
		int chebyshev = Math.max(Math.abs(chunkX - player.x()), Math.abs(chunkZ - player.z()));
		return chebyshev > threshold;
	}
}
