package com.lukarbonite.iseeyourchunks.network;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent client -> server on join (and whenever the client config changes) so the server knows how this
 * particular client wants distant players streamed to it. The server never assumes a client is present:
 * a vanilla client simply never sends this, and it receives no far-chunk streaming.
 *
 * @param protocolVersion       guards against mismatched client/server mod versions
 * @param enabled               whether this client opts into far-player streaming at all
 * @param desiredDistanceBlocks how far out the client wants distant players (its effective Voxy render distance)
 * @param chunkRenderCount      how many chunks of terrain to stream around each viewed player, nearest first
 */
public record ClientHelloPayload(int protocolVersion, boolean enabled, int desiredDistanceBlocks, int chunkRenderCount)
	implements CustomPacketPayload {

	/** Bumped to 2 when {@code chunkRenderCount} was added; a v1 server ignores the extra field's effect. */
	public static final int PROTOCOL_VERSION = 2;

	public static final Type<ClientHelloPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(ISeeYourChunks.MOD_ID, "client_hello"));

	public static final StreamCodec<io.netty.buffer.ByteBuf, ClientHelloPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ClientHelloPayload::protocolVersion,
		ByteBufCodecs.BOOL, ClientHelloPayload::enabled,
		ByteBufCodecs.VAR_INT, ClientHelloPayload::desiredDistanceBlocks,
		ByteBufCodecs.VAR_INT, ClientHelloPayload::chunkRenderCount,
		ClientHelloPayload::new
	);

	@Override
	public Type<ClientHelloPayload> type() {
		return TYPE;
	}
}
