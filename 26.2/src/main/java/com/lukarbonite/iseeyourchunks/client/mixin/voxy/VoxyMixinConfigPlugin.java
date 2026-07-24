package com.lukarbonite.iseeyourchunks.client.mixin.voxy;

import java.util.List;
import java.util.Set;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Gates the Voxy-targeting mixins on Voxy actually being installed.
 *
 * <p>Voxy is an optional client dependency: the mod runs fine without it (distant players just float with no
 * ground). Its classes are absent on a vanilla client and on every dedicated server, so a mixin naming a Voxy
 * class must not be applied there. This returns {@code false} from {@link #shouldApplyMixin} whenever Voxy is
 * not loaded, so the Voxy mixins are simply skipped instead of failing to resolve their target.
 */
public class VoxyMixinConfigPlugin implements IMixinConfigPlugin {
	private static final boolean VOXY_PRESENT = FabricLoader.getInstance().isModLoaded("voxy");

	@Override
	public void onLoad(String mixinPackage) {
	}

	@Override
	public String getRefMapperConfig() {
		return null;
	}

	@Override
	public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
		return VOXY_PRESENT;
	}

	@Override
	public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
	}

	@Override
	public List<String> getMixins() {
		return null;
	}

	@Override
	public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}

	@Override
	public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
	}
}
