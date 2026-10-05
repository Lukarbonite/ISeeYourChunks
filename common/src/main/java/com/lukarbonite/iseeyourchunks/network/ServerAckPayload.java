package com.lukarbonite.iseeyourchunks.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Sent server -> client in reply to a {@link ClientHelloPayload}, telling the client what the server can
 * actually deliver. The client uses it to keep its terrain slider honest (it cannot ask for more chunks
 * than the server's render distance holds) and to size its chunk storage to reality rather than to a
 * fixed guess.
 *
 * <p>Purely advisory: the server clamps every request regardless, so a client that never receives this
 * (older server, or the packet lost to a race) simply falls back to its conservative defaults.
 *
 * <p>A plain record so it compiles on every supported MC version; see {@link ClientHelloPayload} for how each
 * version carries it.
 *
 * @param protocolVersion       echoes the protocol so a mismatched client can ignore it
 * @param streamingEnabled      whether the server has far-chunk streaming switched on at all
 * @param renderDistanceChunks  the server's view distance in chunks - the hard ceiling on streamed terrain
 */
public record ServerAckPayload(int protocolVersion, boolean streamingEnabled, int renderDistanceChunks) {

	/** Channel path under the mod's namespace. */
	public static final String CHANNEL = "server_ack";

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(this.protocolVersion);
		buf.writeBoolean(this.streamingEnabled);
		buf.writeVarInt(this.renderDistanceChunks);
	}

	public static ServerAckPayload read(FriendlyByteBuf buf) {
		int protocolVersion = buf.readVarInt();
		boolean streamingEnabled = buf.readBoolean();
		int renderDistanceChunks = buf.readVarInt();
		return new ServerAckPayload(protocolVersion, streamingEnabled, renderDistanceChunks);
	}
}
