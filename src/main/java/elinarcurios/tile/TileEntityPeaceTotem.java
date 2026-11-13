package elinarcurios.tile;

import net.minecraft.tileentity.TileEntity;

import elinarcurios.util.PeaceTotemHandler;

/**
 * Tile entity backing the Peace Totem block.
 *
 * <p>
 * Registers its position into the global anti-spawn registry while it exists
 * in a loaded chunk, and removes it on unload / block break. Re-asserts the
 * registration periodically as a safety net (same pattern as Extra Utilities'
 * anti-mob torch, implemented natively without any dependency).
 */
public class TileEntityPeaceTotem extends TileEntity {

    @Override
    public void validate() {
        super.validate();
        if (worldObj != null && !worldObj.isRemote) {
            PeaceTotemHandler.addTotem(this);
        }
    }

    @Override
    public void invalidate() {
        if (worldObj != null && !worldObj.isRemote) {
            PeaceTotemHandler.removeTotem(this);
        }
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        if (worldObj != null && !worldObj.isRemote) {
            PeaceTotemHandler.removeTotem(this);
        }
        super.onChunkUnload();
    }

    @Override
    public void updateEntity() {
        // Safety net: re-assert registration every second in case the tile
        // was recreated in place without a fresh validate() pass.
        if (!worldObj.isRemote && worldObj.getTotalWorldTime() % 20L == 0L) {
            PeaceTotemHandler.addTotem(this);
        }
    }
}
