package com.lukarbonite.iseeyourchunks.platform;

import com.lukarbonite.iseeyourchunks.network.ClientHelloPayload;
import com.lukarbonite.iseeyourchunks.network.ServerAckPayload;
import net.minecraft.world.phys.Vec3;

import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Client-only companion to {@link VersionHelper} for the version-specific API that touches client-only
 * classes ({@code Minecraft}/{@code Camera}, client networking). Kept separate so it is never loaded on a
 * dedicated server: its callers ({@code ClientEntityVisibility}, {@code ISeeYourChunksFabricClient}) are
 * themselves client-only, so {@link #INSTANCE} and the implementation class are resolved lazily and
 * exclusively on the client.
 */
public interface ClientVersionHelper {

	ClientVersionHelper INSTANCE = ServiceLoader.load(ClientVersionHelper.class)
		.findFirst()
		.orElseThrow(() -> new IllegalStateException("No ClientVersionHelper implementation found"));

	/** Position of the main render camera, or {@code null} before the camera exists. */
	Vec3 cameraPosition();

	/** Whether the connected server accepts the hello channel (false until Fabric's channel negotiation completes). */
	boolean canSendHello();

	void sendHello(ClientHelloPayload hello);

	/** Delivers every received {@link ServerAckPayload} to {@code handler} on the client thread. */
	void registerAckReceiver(Consumer<ServerAckPayload> handler);
}
