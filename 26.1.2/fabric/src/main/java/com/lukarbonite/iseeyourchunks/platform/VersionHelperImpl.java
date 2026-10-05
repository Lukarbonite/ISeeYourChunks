package com.lukarbonite.iseeyourchunks.platform;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import com.lukarbonite.iseeyourchunks.network.ClientHelloPayload;
import com.lukarbonite.iseeyourchunks.network.ServerAckPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundForgetLevelChunkPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.function.BiConsumer;

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
	public void registerPayloadTypes() {
		PayloadTypeRegistry.serverboundPlay().register(HelloPacket.TYPE, HelloPacket.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(AckPacket.TYPE, AckPacket.CODEC);
	}

	@Override
	public void registerHelloReceiver(BiConsumer<ServerPlayer, ClientHelloPayload> handler) {
		ServerPlayNetworking.registerGlobalReceiver(HelloPacket.TYPE,
			(packet, context) -> handler.accept(context.player(), packet.payload()));
	}

	@Override
	public void sendAck(ServerPlayer player, ServerAckPayload ack) {
		ServerPlayNetworking.send(player, new AckPacket(ack));
	}

	@Override
	public Packet<?> forgetChunkPacket(int chunkX, int chunkZ) {
		return new ClientboundForgetLevelChunkPacket(new ChunkPos(chunkX, chunkZ));
	}

	/** The shared {@link ClientHelloPayload} on the payload API, in its own wire format on the mod's channel. */
	public record HelloPacket(ClientHelloPayload payload) implements CustomPacketPayload {
		public static final Type<HelloPacket> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(ISeeYourChunks.MOD_ID, ClientHelloPayload.CHANNEL));
		public static final StreamCodec<FriendlyByteBuf, HelloPacket> CODEC = CustomPacketPayload.<FriendlyByteBuf, HelloPacket>codec(
			(packet, buf) -> packet.payload().write(buf), buf -> new HelloPacket(ClientHelloPayload.read(buf)));

		@Override
		public Type<HelloPacket> type() {
			return TYPE;
		}
	}

	/** The shared {@link ServerAckPayload} on the payload API, in its own wire format on the mod's channel. */
	public record AckPacket(ServerAckPayload payload) implements CustomPacketPayload {
		public static final Type<AckPacket> TYPE =
			new Type<>(Identifier.fromNamespaceAndPath(ISeeYourChunks.MOD_ID, ServerAckPayload.CHANNEL));
		public static final StreamCodec<FriendlyByteBuf, AckPacket> CODEC = CustomPacketPayload.<FriendlyByteBuf, AckPacket>codec(
			(packet, buf) -> packet.payload().write(buf), buf -> new AckPacket(ServerAckPayload.read(buf)));

		@Override
		public Type<AckPacket> type() {
			return TYPE;
		}
	}
}
