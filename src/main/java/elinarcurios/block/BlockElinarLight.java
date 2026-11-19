package elinarcurios.block;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.common.Reference;
import elinarcurios.tile.TileElinarLight;

/**
 * Invisible light block used by the held torch-like Celestial Wand.
 *
 * <p>
 * The block has no item, no collision, no selection box and no renderer; its only job is to
 * emit light level 14 at the holder's feet (or just above the head when the feet block is
 * occupied). A tiny tile entity removes leftovers when nobody nearby holds the wand, so a
 * server crash cannot leave permanent invisible light blocks behind.
 */
public final class BlockElinarLight extends Block {

    public static final int LIGHT_LEVEL = 14;

    public static BlockElinarLight instance;

    public BlockElinarLight() {
        super(Material.circuits);
        this.setBlockName("elinarLight");
        this.setBlockTextureName("elinarcurios:elinar_light");
        this.setLightLevel(LIGHT_LEVEL / 15.0F);
        this.setLightOpacity(0);
        this.setHardness(0.0F);
        this.setResistance(0.0F);
        this.setStepSound(Block.soundTypeStone);
        this.setCreativeTab(null);
        this.setBlockBounds(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    public static void register() {
        instance = new BlockElinarLight();
        GameRegistry.registerBlock(instance, null, "elinarLight");
        GameRegistry.registerTileEntity(TileElinarLight.class, Reference.MOD_ID + ".elinarLight");
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @Override
    public int getRenderType() {
        return -1;
    }

    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(final World world, final int x, final int y, final int z) {
        return null;
    }

    @Override
    public AxisAlignedBB getSelectedBoundingBoxFromPool(final World world, final int x, final int y, final int z) {
        return null;
    }

    @Override
    public boolean canSilkHarvest() {
        return false;
    }

    @Override
    public ArrayList<ItemStack> getDrops(final World world, final int x, final int y, final int z, final int meta,
        final int fortune) {
        return new ArrayList<ItemStack>();
    }

    @Override
    public boolean hasTileEntity(final int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(final World world, final int metadata) {
        return new TileElinarLight();
    }

    @Override
    public boolean isReplaceable(final IBlockAccess world, final int x, final int y, final int z) {
        return false;
    }
}
