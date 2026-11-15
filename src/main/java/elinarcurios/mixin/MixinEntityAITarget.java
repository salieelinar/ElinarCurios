package elinarcurios.mixin;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAITarget;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import elinarcurios.util.GhostMode;

/**
 * Makes every AI targeting task (vanilla and modded, they all funnel through
 * {@code EntityAITarget}) skip ghost players.
 *
 * <p>
 * Vanilla already refuses creative players here via {@code capabilities.disableDamage}; this is the
 * same check, only driven by the backpack's ghost toggle instead of the gamemode. The
 * {@code ignoreInvulnerable} argument is respected so the few AIs that ask for invulnerable
 * targets on purpose keep their behaviour.
 */
@Mixin(EntityAITarget.class)
public abstract class MixinEntityAITarget {

    @Inject(
        method = "isSuitableTarget(Lnet/minecraft/entity/EntityLivingBase;Z)Z",
        at = @At("HEAD"),
        cancellable = true)
    private void elinarcurios$skipGhostTarget(final EntityLivingBase target, final boolean ignoreInvulnerable,
        final CallbackInfoReturnable<Boolean> cir) {
        if (!ignoreInvulnerable && GhostMode.isGhost(target)) cir.setReturnValue(false);
    }
}
