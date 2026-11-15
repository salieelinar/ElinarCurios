package elinarcurios.mixin;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import elinarcurios.util.GhostMode;

/**
 * Hides ghost players from the "closest vulnerable player" search used by endermen (stare),
 * ghasts, spiders, slimes/magma cubes, silverfish, zombie pigmen and
 * {@code EntityMob#findPlayerToAttack}.
 *
 * <p>
 * Both {@code getClosestVulnerablePlayer(double,double,double,double)} and
 * {@code getClosestVulnerablePlayerToEntity(entity,double)} pass through this method, so one hook
 * covers every direct player search. When the closest vulnerable player happens to be a ghost the
 * search is re-run for the next non-ghost player (multiplayer correctness); in single player that
 * simply yields {@code null}.
 */
@Mixin(World.class)
public abstract class MixinWorldClosestVulnerablePlayer {

    @Inject(
        method = "getClosestVulnerablePlayer(DDDD)Lnet/minecraft/entity/player/EntityPlayer;",
        at = @At("RETURN"),
        cancellable = true)
    private void elinarcurios$skipGhostPlayer(final double x, final double y, final double z, final double distance,
        final CallbackInfoReturnable<EntityPlayer> cir) {
        final EntityPlayer found = cir.getReturnValue();
        if (found == null || !GhostMode.isGhost(found)) return;
        cir.setReturnValue(GhostMode.findClosestOtherPlayer((World) (Object) this, x, y, z, distance));
    }
}
