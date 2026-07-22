package com.lukarbonite.iseeyourchunks.network;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Sent server -> client in reply to a {@link ClientHelloPayload}, telling the client what the server can
 * actually deliver. The client uses it to keep its terrain slider honest (it cannot ask for more chunks
 * than the server's render distance holds) and to size its chunk storage to reality rather than to a
 * fixed guess.
 *
 * <p>Purely advisory: the server clamps every request regardless, so a client that never receives this
 * (older server, or the packet lost to a race) simply falls back to its conservative defaults.
 *
 * @param protocolVersion       echoes the protocol so a mismatched client can ignore it
 * @param streamingEnabled      whether the server has far-chunk streaming switched on at all
 * @param renderDistanceChunks  the server's view distance in chunks - the hard ceiling on streamed terrain
 */
public record ServerAckPayload(int protocolVersion, boolean streamingEnabled, int renderDistanceChunks)
	implements CustomPacketPayload {

	public static final Type<ServerAckPayload> TYPE =
		new Type<>(Identifier.fromNamespaceAndPath(ISeeYourChunks.MOD_ID, "server_ack"));

	public static final StreamCodec<io.netty.buffer.ByteBuf, ServerAckPayload> CODEC = StreamCodec.composite(
		ByteBufCodecs.VAR_INT, ServerAckPayload::protocolVersion,
		ByteBufCodecs.BOOL, ServerAckPayload::streamingEnabled,
		ByteBufCodecs.VAR_INT, ServerAckPayload::renderDistanceChunks,
		ServerAckPayload::new
	);

	@Override
	public Type<ServerAckPayload> type() {
		return TYPE;
	}
}
