package com.lukarbonite.iseeyourchunks.platform;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.mixin.server.ChunkMapAccessor;
import com.lukarbonite.iseeyourchunks.network.ClientHelloPayload;
import com.lukarbonite.iseeyourchunks.network.ServerAckPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** MC 1.21.1 (Mojang-mapped) implementation of {@link VersionHelper}. */
public final class VersionHelperImpl implements VersionHelper {

	@Override
	public long packChunk(int chunkX, int chunkZ) {
		return ChunkPos.asLong(chunkX, chunkZ);
	}

	@Override
	public long packChunkPos(ChunkPos pos) {
		return pos.toLong();
	}

	@Override
	public ServerLevel serverLevel(ServerPlayer player) {
		return player.serverLevel();
	}

	@Override
	public boolean isChunkTracked(ChunkMap chunkMap, ServerPlayer player, int chunkX, int chunkZ) {
		return ((ChunkMapAccessor) chunkMap).iSeeYourChunks$invokeIsChunkTracked(player, chunkX, chunkZ);
	}

	@Override
	public int minSectionY(LevelChunk chunk) {
		return SectionPos.blockToSectionCoord(chunk.getMinBuildHeight());
	}

	@Override
	public Object voxyRenderSystemNullable() {
		// Direct call (returned as Object): only ever invoked from the Voxy-guarded compat classes, so
		// IGetVoxyRenderSystem is resolved lazily at call time and this impl still class-loads on a
		// dedicated server / Voxy-less client, where the method is never called.
		return me.cortex.voxy.client.core.IGetVoxyRenderSystem.getNullable();
	}

	@Override
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
		return new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(ISeeYourChunks.MOD_ID, path));
	}

	@Override
	public void registerPayloadTypes() {
		PayloadTypeRegistry.playC2S().register(ClientHelloPayload.TYPE, ClientHelloPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ServerAckPayload.TYPE, ServerAckPayload.CODEC);
	}
}
