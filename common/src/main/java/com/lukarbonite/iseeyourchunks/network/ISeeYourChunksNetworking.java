package com.lukarbonite.iseeyourchunks.network;

import com.lukarbonite.iseeyourchunks.platform.VersionHelper;
import com.lukarbonite.iseeyourchunks.server.FarChunkStreamer;

/**
 * Registers the mod's custom payloads and the server-side receiver. Kept separate from client entry
 * points so the dedicated server never touches client-only classes.
 */
public final class ISeeYourChunksNetworking {
	private static boolean payloadsRegistered;

	private ISeeYourChunksNetworking() {
	}

	/** Registers payload types on both logical sides; safe to call from client and server init. */
	public static void registerPayloads() {
		if (payloadsRegistered) {
			return;
		}
		payloadsRegistered = true;
		VersionHelper.INSTANCE.registerPayloadTypes();
	}

	/** Server-side wiring: payload types plus the hello receiver. */
	public static void registerCommon() {
		registerPayloads();
		VersionHelper.INSTANCE.registerHelloReceiver(FarChunkStreamer::handleHello);
	}
}
