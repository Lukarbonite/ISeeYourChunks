package com.lukarbonite.iseeyourchunks.platform;

import com.lukarbonite.iseeyourchunks.network.ClientHelloPayload;
import com.lukarbonite.iseeyourchunks.network.ServerAckPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** MC 26.2 (unobfuscated) implementation of {@link ClientVersionHelper}. Client only. */
public final class ClientVersionHelperImpl implements ClientVersionHelper {

	@Override
	public Vec3 cameraPosition() {
		Minecraft client = Minecraft.getInstance();
		if (client.gameRenderer == null || client.gameRenderer.mainCamera() == null) {
			return null;
		}
		return client.gameRenderer.mainCamera().position();
	}

	@Override
	public boolean canSendHello() {
		return ClientPlayNetworking.canSend(VersionHelperImpl.HelloPacket.TYPE);
	}

	@Override
	public void sendHello(ClientHelloPayload hello) {
		ClientPlayNetworking.send(new VersionHelperImpl.HelloPacket(hello));
	}

	@Override
	public void registerAckReceiver(Consumer<ServerAckPayload> handler) {
		ClientPlayNetworking.registerGlobalReceiver(VersionHelperImpl.AckPacket.TYPE,
			(packet, context) -> handler.accept(packet.payload()));
	}
}
