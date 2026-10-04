package com.lukarbonite.iseeyourchunks.platform;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.network.ClientHelloPayload;
import com.lukarbonite.iseeyourchunks.network.ServerAckPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** MC 26.1.2 (unobfuscated) implementation of {@link VersionHelper}. */
public final class VersionHelperImpl implements VersionHelper {

	@Override
	public long packChunk(int chunkX, int chunkZ) {
		return ChunkPos.pack(chunkX, chunkZ);
	}

	@Override
	public long packChunkPos(ChunkPos pos) {
		return pos.pack();
	}

	@Override
	public ServerLevel serverLevel(ServerPlayer player) {
		return player.level();
	}

	@Override
	public boolean isChunkTracked(ChunkMap chunkMap, ServerPlayer player, int chunkX, int chunkZ) {
		return chunkMap.isChunkTracked(player, chunkX, chunkZ);
	}

	@Override
	public int minSectionY(LevelChunk chunk) {
		return chunk.getMinSectionY();
	}

	@Override
	public Object voxyRenderSystemNullable() {
		// Direct call (returned as Object): only ever invoked from the Voxy-guarded compat classes, so
		// IVoxyRenderSystemHolder is resolved lazily at call time and this impl still class-loads on a
		// dedicated server / Voxy-less client, where the method is never called.
		return me.cortex.voxy.client.core.IVoxyRenderSystemHolder.getNullable();
	}

	@Override
	public <T extends CustomPacketPayload> CustomPacketPayload.Type<T> payloadType(String path) {
		return new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(ISeeYourChunks.MOD_ID, path));
	}

	@Override
	public void registerPayloadTypes() {
		PayloadTypeRegistry.serverboundPlay().register(ClientHelloPayload.TYPE, ClientHelloPayload.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(ServerAckPayload.TYPE, ServerAckPayload.CODEC);
	}
}
