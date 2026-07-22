package com.lukarbonite.iseeyourchunks.server;

import com.lukarbonite.iseeyourchunks.ISeeYourChunks;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.level.ServerPlayer;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Optional integration with vanish mods, resolved at class-init time through a {@link MethodHandle}.
 *
 * <p>A handle rather than a mixin because there is exactly one call site and the target
 * ({@code VanishAPI.canSeePlayer}) is a public, plugin-facing entry point: binding to it costs no
 * compile-time dependency and survives internal refactors on the vanish side. A handle rather than
 * plain reflection because this runs once per revealable player per streaming pass, and an invokeExact
 * on a constant handle folds down to roughly a direct call.
 *
 * <p>Everything here fails <em>open</em>: if the mod is absent, the API has moved, or the call throws,
 * players stay visible. A vanish integration that failed closed would silently hide everyone the moment
 * it broke, which is far harder to diagnose than a vanished player who is briefly visible.
 */
final class VanishSupport {
	private static final String VANISH_MOD_ID = "melius-vanish";
	private static final String VANISH_API_CLASS = "me.drex.vanish.api.VanishAPI";
	private static final String VANISH_API_METHOD = "canSeePlayer";

	/** Null whenever no vanish integration is active, which is the overwhelmingly common case. */
	private static final MethodHandle CAN_SEE_PLAYER = resolveHandle();

	private VanishSupport() {
	}

	/** Whether {@code viewer} is permitted to see {@code target}, per the installed vanish mod. */
	static boolean isVisibleTo(ServerPlayer target, ServerPlayer viewer) {
		if (CAN_SEE_PLAYER == null) {
			return true;
		}

		try {
			return (boolean) CAN_SEE_PLAYER.invokeExact(target, viewer);
		} catch (Throwable throwable) {
			// invokeExact is declared to throw Throwable; a broken integration must not take the
			// streaming pass down with it, so swallow and fall back to visible.
			return true;
		}
	}

	private static MethodHandle resolveHandle() {
		if (!FabricLoader.getInstance().isModLoaded(VANISH_MOD_ID)) {
			return null;
		}

		try {
			MethodType signature = MethodType.methodType(boolean.class, ServerPlayer.class, ServerPlayer.class);
			MethodHandle handle = MethodHandles.publicLookup()
				.findStatic(Class.forName(VANISH_API_CLASS), VANISH_API_METHOD, signature);
			ISeeYourChunks.LOGGER.info("Vanish integration active: distant players hidden by {} stay hidden.", VANISH_MOD_ID);
			return handle;
		} catch (ReflectiveOperationException | RuntimeException exception) {
			ISeeYourChunks.LOGGER.warn(
				"{} is installed but {}.{} could not be bound; distant players will not be vanish-filtered.",
				VANISH_MOD_ID, VANISH_API_CLASS, VANISH_API_METHOD, exception);
			return null;
		}
	}
}
