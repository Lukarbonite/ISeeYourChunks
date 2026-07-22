package com.lukarbonite.iseeyourchunks.client.mixin;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.client.ISeeYourChunksFabricClient;
import com.lukarbonite.iseeyourchunks.client.compat.VoxyIngestBridge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ClientboundLevelChunkPacketData;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
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
 */
@Mixin(ClientChunkCache.class)
abstract class ClientChunkCacheMixin {
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
		if (chunk != null && iSeeYourChunks$isFarChunk(chunkX, chunkZ)) {
			VoxyIngestBridge.ingest(chunk);
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
