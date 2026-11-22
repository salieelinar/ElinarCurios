package elinarcurios.mixin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.spongepowered.asm.lib.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import cpw.mods.fml.common.Loader;

/**
 * Gates the optional-mod mixins (Draconic Evolution, Thaumcraft, Forestry, GalaxySpace) so a pack
 * without one of them never tries to apply a mixin to a missing target class.
 *
 * <p>
 * The GregTech hazard mixin is not gated: GT is a hard dependency of this mod.
 */
public final class ElinarMixinPlugin implements IMixinConfigPlugin {

    private static final Set<String> THAUMCRAFT_MIXINS = new HashSet<String>(
        Arrays.asList("MixinWandManager", "MixinRenderEventHandler", "MixinTileNodeRenderer"));
    private static final Set<String> FORESTRY_MIXINS = new HashSet<String>(
        Arrays.asList("MixinGeneticsUtil", "MixinArmorApiaristHelper"));
    private static final Set<String> GALAXYSPACE_MIXINS = new HashSet<String>(
        Arrays.asList("MixinGSEventHandler", "MixinGSClientEventHandler"));
    private static final Set<String> DRACONIC_MIXINS = new HashSet<String>(
        Arrays.asList("MixinDraconicArmorHandler", "MixinDraconicEventHandler"));

    @Override
    public void onLoad(final String mixinPackage) {}

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(final String targetClassName, final String mixinClassName) {
        final String simpleName = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        if (THAUMCRAFT_MIXINS.contains(simpleName)) return isLoaded("Thaumcraft");
        if (FORESTRY_MIXINS.contains(simpleName)) return isLoaded("Forestry");
        if (GALAXYSPACE_MIXINS.contains(simpleName)) return isLoaded("GalaxySpace");
        if (DRACONIC_MIXINS.contains(simpleName)) return isLoaded("DraconicEvolution");
        return true;
    }

    private static boolean isLoaded(final String modId) {
        try {
            return Loader.isModLoaded(modId);
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public void acceptTargets(final Set<String> myTargets, final Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return new ArrayList<String>();
    }

    @Override
    public void preApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName,
        final IMixinInfo mixinInfo) {}

    @Override
    public void postApply(final String targetClassName, final ClassNode targetClass, final String mixinClassName,
        final IMixinInfo mixinInfo) {}
}
