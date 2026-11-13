package elinarcurios.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.entity.monster.EntityEnderman;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;
import net.minecraftforge.event.entity.living.LivingSpawnEvent;

import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import elinarcurios.Config;
import elinarcurios.tile.TileEntityPeaceTotem;

/**
 * Anti-spawn logic for the Peace Totem.
 *
 * <p>
 * A static registry of totem positions (dim, x, y, z) feeds an ellipsoid
 * range check. Natural hostile spawning is denied inside that range, while
 * spawners / spawn eggs / /summon keep working because Forge fires their
 * {@code LivingSpawnEvent.CheckSpawn} with an {@link Event.Result#ALLOW}
 * result, which this handler leaves untouched (same behaviour as Extra
 * Utilities' Magnum Torch, re-implemented here with zero dependencies).
 */
public class PeaceTotemHandler {

    private static final List<int[]> TOTEMS = new ArrayList<int[]>();

    public static void addTotem(final TileEntity tile) {
        final int[] coord = { tile.getWorldObj().provider.dimensionId, tile.xCoord, tile.yCoord, tile.zCoord };
        for (final int[] existing : TOTEMS) {
            if (existing[0] == coord[0] && existing[1] == coord[1]
                && existing[2] == coord[2]
                && existing[3] == coord[3]) {
                return;
            }
        }
        TOTEMS.add(coord);
    }

    public static void removeTotem(final TileEntity tile) {
        final int dim = tile.getWorldObj().provider.dimensionId;
        for (int i = TOTEMS.size() - 1; i >= 0; i--) {
            final int[] coord = TOTEMS.get(i);
            if (coord[0] == dim && coord[1] == tile.xCoord && coord[2] == tile.yCoord && coord[3] == tile.zCoord) {
                TOTEMS.remove(i);
            }
        }
    }

    /**
     * Ellipsoid check: {@code (dx^2+dz^2)/h^2 + dy^2/v^2 <= 1} around the totem centre.
     */
    public static boolean isInRange(final Entity entity) {
        for (final int[] coord : TOTEMS) {
            if (coord[0] != entity.worldObj.provider.dimensionId) {
                continue;
            }
            if (!entity.worldObj.blockExists(coord[1], coord[2], coord[3])) {
                continue;
            }
            if (!(entity.worldObj.getTileEntity(coord[1], coord[2], coord[3]) instanceof TileEntityPeaceTotem)) {
                continue;
            }
            final double dx = coord[1] + 0.5D - entity.posX;
            final double dy = coord[2] + 0.5D - entity.posY;
            final double dz = coord[3] + 0.5D - entity.posZ;
            final double h = Config.peaceTotemRangeHorizontal;
            final double v = Config.peaceTotemRangeVertical;
            if (dx * dx + dz * dz <= h * h * (1.0D - dy * dy / (v * v))) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void denySpawn(final LivingSpawnEvent.CheckSpawn event) {
        if (event.getResult() == Event.Result.ALLOW) {
            return;
        }
        if (event.entityLiving.isCreatureType(EnumCreatureType.monster, false) && isInRange(event.entityLiving)) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public void denyEndermanTeleport(final EnderTeleportEvent event) {
        if (event.entityLiving instanceof EntityEnderman && !((EntityEnderman) event.entityLiving).isScreaming()) {
            for (final int[] coord : TOTEMS) {
                if (coord[0] != event.entityLiving.worldObj.provider.dimensionId) {
                    continue;
                }
                if (!event.entityLiving.worldObj.blockExists(coord[1], coord[2], coord[3])) {
                    continue;
                }
                if (!(event.entityLiving.worldObj
                    .getTileEntity(coord[1], coord[2], coord[3]) instanceof TileEntityPeaceTotem)) {
                    continue;
                }
                final double h = Config.peaceTotemRangeHorizontal;
                final double v = Config.peaceTotemRangeVertical;
                final double tx = coord[1] + 0.5D - event.targetX;
                final double ty = coord[2] + 0.5D - event.targetY;
                final double tz = coord[3] + 0.5D - event.targetZ;
                final boolean targetInRange = tx * tx + tz * tz <= h * h * (1.0D - ty * ty / (v * v));
                if (!targetInRange) {
                    continue;
                }
                final double cx = coord[1] + 0.5D - event.entityLiving.posX;
                final double cy = coord[2] + 0.5D - event.entityLiving.posY;
                final double cz = coord[3] + 0.5D - event.entityLiving.posZ;
                final boolean movesCloser = tx * tx + ty * ty + tz * tz < cx * cx + cy * cy + cz * cz;
                if (movesCloser) {
                    event.setCanceled(true);
                    return;
                }
            }
        }
    }
}
