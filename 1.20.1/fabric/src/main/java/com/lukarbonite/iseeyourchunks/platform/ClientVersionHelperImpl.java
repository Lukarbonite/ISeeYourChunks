package com.lukarbonite.iseeyourchunks.platform;

import com.lukarbonite.iseeyourchunks.network.ClientHelloPayload;
import com.lukarbonite.iseeyourchunks.network.ServerAckPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;

import java.util.function.Consumer;

/** MC 1.20.1 (Mojang-mapped) implementation of {@link ClientVersionHelper}. Client only. */
public final class ClientVersionHelperImpl implements ClientVersionHelper {

	@Override
	public Vec3 cameraPosition() {
		Minecraft client = Minecraft.getInstance();
		if (client.gameRenderer == null || client.gameRenderer.getMainCamera() == null) {
			return null;
		}
		return client.gameRenderer.getMainCamera().getPosition();
	}

	@Override
	public boolean canSendHello() {
		return ClientPlayNetworking.canSend(VersionHelperImpl.HELLO_CHANNEL);
	}

	@Override
	public void sendHello(ClientHelloPayload hello) {
		FriendlyByteBuf buf = PacketByteBufs.create();
		hello.write(buf);
		ClientPlayNetworking.send(VersionHelperImpl.HELLO_CHANNEL, buf);
	}

	/** The buffer is only valid on the network thread, so it is decoded there and handled on the client thread. */
	@Override
	public void registerAckReceiver(Consumer<ServerAckPayload> handler) {
		ClientPlayNetworking.registerGlobalReceiver(VersionHelperImpl.ACK_CHANNEL, (client, listener, buf, responseSender) -> {
			ServerAckPayload ack = ServerAckPayload.read(buf);
			client.execute(() -> handler.accept(ack));
		});
	}
}
