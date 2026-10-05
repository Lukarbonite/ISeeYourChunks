package com.lukarbonite.iseeyourchunks.platform;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.mixin.server.ChunkMapAccessor;
import com.lukarbonite.iseeyourchunks.network.ClientHelloPayload;
import com.lukarbonite.iseeyourchunks.network.ServerAckPayload;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.SectionPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.function.BiConsumer;

/** MC 1.20.1 (Mojang-mapped) implementation of {@link VersionHelper}. */
public final class VersionHelperImpl implements VersionHelper {
	/** The hello/ack channels: the same ids the 1.20.5+ payload types use, so every version of the mod interoperates. */
	public static final ResourceLocation HELLO_CHANNEL = new ResourceLocation(ISeeYourChunks.MOD_ID, ClientHelloPayload.CHANNEL);
	public static final ResourceLocation ACK_CHANNEL = new ResourceLocation(ISeeYourChunks.MOD_ID, ServerAckPayload.CHANNEL);

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

	/**
	 * 1.20.1 has no per-player chunk-tracking view: it sends every player each chunk within the server's view radius
	 * of the section they were last placed in, ignoring the client's own render distance. This is that exact test,
	 * as vanilla's {@code move}, {@code updatePlayerStatus} and {@code getPlayers} make it.
	 */
	@Override
	public boolean isChunkTracked(ChunkMap chunkMap, ServerPlayer player, int chunkX, int chunkZ) {
		SectionPos section = player.getLastSectionPos();
		return ChunkMap.isChunkInRange(chunkX, chunkZ, section.x(), section.z(),
			((ChunkMapAccessor) chunkMap).iSeeYourChunks$viewDistance());
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

	/** Raw channels need no type registration. */
	@Override
	public void registerPayloadTypes() {
	}

	/** The buffer is only valid on the network thread, so it is decoded there and handled on the server thread. */
	@Override
	public void registerHelloReceiver(BiConsumer<ServerPlayer, ClientHelloPayload> handler) {
		ServerPlayNetworking.registerGlobalReceiver(HELLO_CHANNEL, (server, player, listener, buf, responseSender) -> {
			ClientHelloPayload hello = ClientHelloPayload.read(buf);
			server.execute(() -> handler.accept(player, hello));
		});
	}

	@Override
	public void sendAck(ServerPlayer player, ServerAckPayload ack) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		ack.write(buf);
		ServerPlayNetworking.send(player, ACK_CHANNEL, buf);
	}

	@Override
	public Packet<?> forgetChunkPacket(int chunkX, int chunkZ) {
		return new ClientboundForgetLevelChunkPacket(chunkX, chunkZ);
	}
}
