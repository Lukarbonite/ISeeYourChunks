package com.lukarbonite.iseeyourchunks.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Sent client -> server on join (and whenever the client config changes) so the server knows how this
 * particular client wants distant players streamed to it. The server never assumes a client is present:
 * a vanilla client simply never sends this, and it receives no far-chunk streaming.
 *
 * <p>A plain record so it compiles on every supported MC version; each version's helper carries it over that
 * version's networking API (the payload API on 1.20.5+, raw channels on 1.20.1), always on {@link #CHANNEL} and
 * always in the {@link #write}/{@link #read} wire format, so any two versions of the mod interoperate.
 *
 * @param protocolVersion       guards against mismatched client/server mod versions
 * @param enabled               whether this client opts into far-player streaming at all
 * @param desiredDistanceBlocks how far out the client wants distant players (its effective Voxy render distance)
 * @param chunkRenderCount      how many chunks of terrain to stream around each viewed player, nearest first
 */
public record ClientHelloPayload(int protocolVersion, boolean enabled, int desiredDistanceBlocks, int chunkRenderCount) {

	/** Bumped to 2 when {@code chunkRenderCount} was added; a v1 server ignores the extra field's effect. */
	public static final int PROTOCOL_VERSION = 2;

	/** Channel path under the mod's namespace. */
	public static final String CHANNEL = "client_hello";

	public void write(FriendlyByteBuf buf) {
		buf.writeVarInt(this.protocolVersion);
		buf.writeBoolean(this.enabled);
		buf.writeVarInt(this.desiredDistanceBlocks);
		buf.writeVarInt(this.chunkRenderCount);
	}

	public static ClientHelloPayload read(FriendlyByteBuf buf) {
		int protocolVersion = buf.readVarInt();
		boolean enabled = buf.readBoolean();
		int desiredDistanceBlocks = buf.readVarInt();
		int chunkRenderCount = buf.readVarInt();
		return new ClientHelloPayload(protocolVersion, enabled, desiredDistanceBlocks, chunkRenderCount);
	}
}
