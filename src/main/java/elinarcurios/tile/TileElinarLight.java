package elinarcurios.tile;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;

import elinarcurios.player.ElinarWandLightHandler;

/**
 * Safety net for {@link elinarcurios.block.BlockElinarLight}: if the holder logs out, dies,
 * changes dimension or the server crashes, this removes the light block once nobody within
 * three blocks is carrying the Celestial Wand. "Carrying" includes the wand sitting in a
 * non-player slot of the container the player currently has open (arcane workbench, backpack,
 * chest), so opening a GUI does not drop the light.
 */
public final class TileElinarLight extends TileEntity {

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (this.worldObj == null || this.worldObj.isRemote) return;
        if (this.worldObj.getTotalWorldTime() % 8L != 0L) return;
        if (this.hasWandHolderNearby()) return;
        this.worldObj.setBlockToAir(this.xCoord, this.yCoord, this.zCoord);
    }

    private boolean hasWandHolderNearby() {
        for (final Object object : this.worldObj.playerEntities) {
            if (!(object instanceof EntityPlayer)) continue;
            final EntityPlayer player = (EntityPlayer) object;
            if (player.getDistanceSq(this.xCoord + 0.5D, this.yCoord + 0.5D, this.zCoord + 0.5D) > 16.0D) {
                continue;
            }
            if (ElinarWandLightHandler.hasWand(player)) return true;
        }
        return false;
    }
}
