package elinarcurios.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import elinarcurios.common.Reference;
import elinarcurios.tile.TileEntityPeaceTotem;

/**
 * The Peace Totem: a standalone anti-mob-spawn totem (no mod dependencies).
 *
 * <p>
 * Prevents natural hostile-mob spawning inside a configurable ellipsoid around
 * the totem (default 64 blocks horizontal / 32 blocks vertical). Spawners,
 * spawn eggs and /summon are unaffected, matching the classic "Magnum Torch"
 * behaviour (Extra Utilities) without referencing that mod.
 *
 * <p>
 * Model follows Extra Utilities' Magnum Torch exactly: a thin full-height
 * column rendered with three textures (sides / top cap / base), a flame head
 * drawn on the side texture and a deep-purple staff (matching the Infinite
 * Backpack palette).
 */
public class BlockPeaceTotem extends Block {

    public static BlockPeaceTotem instance;

    private IIcon iconTop;
    private IIcon iconBase;

    public BlockPeaceTotem() {
        super(Material.wood);
        setBlockName("peaceTotem");
        setBlockTextureName(Reference.MOD_ID + ":peaceTotem");
        setCreativeTab(ElinarCurios.creativeTab);
        setHardness(1.2F);
        setStepSound(soundTypeWood);
        setBlockBounds(0.375F, 0.0F, 0.375F, 0.625F, 1.0F, 0.625F);
        setLightLevel(1.0F);
        setLightOpacity(0);
    }

    @Override
    public boolean isOpaqueCube() {
        return false;
    }

    @Override
    public boolean renderAsNormalBlock() {
        return false;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(final IIconRegister register) {
        blockIcon = register.registerIcon(Reference.MOD_ID + ":peaceTotem");
        iconTop = register.registerIcon(Reference.MOD_ID + ":peaceTotemTop");
        iconBase = register.registerIcon(Reference.MOD_ID + ":peaceTotemBase");
    }

    @SideOnly(Side.CLIENT)
    @Override
    public IIcon getIcon(final int side, final int metadata) {
        switch (side) {
            case 0:
                return iconBase;
            case 1:
                return iconTop;
            default:
                return blockIcon;
        }
    }

    // Forge TileEntity hooks (same pattern as Extra Utilities' BlockMagnumTorch:
    // a plain Block, not BlockContainer, so the torch-style rendering is kept).

    @Override
    public boolean hasTileEntity(final int metadata) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(final World world, final int metadata) {
        return new TileEntityPeaceTotem();
    }

    @Override
    public boolean canPlaceBlockAt(final World world, final int x, final int y, final int z) {
        return world.isSideSolid(x, y - 1, z, ForgeDirection.UP, true);
    }

    @Override
    public void onNeighborBlockChange(final World world, final int x, final int y, final int z, final Block neighbor) {
        if (!canPlaceBlockAt(world, x, y, z) && world.getBlock(x, y, z) == this) {
            dropBlockAsItem(world, x, y, z, world.getBlockMetadata(x, y, z), 0);
            world.setBlockToAir(x, y, z);
        }
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void randomDisplayTick(final World world, final int x, final int y, final int z, final Random random) {
        final float dx = (float) ((random.nextDouble() * 2.0D - 1.0D) * 0.125D);
        final float dy = (float) ((random.nextDouble() * 2.0D - 1.0D) * 0.0625D);
        final float dz = (float) ((random.nextDouble() * 2.0D - 1.0D) * 0.125D);
        world.spawnParticle("smoke", x + 0.5D + dx, y + 1.0D + dy, z + 0.5D + dz, 0.0D, 0.0D, 0.0D);
        world.spawnParticle("flame", x + 0.5D + dx, y + 1.0D + dy, z + 0.5D + dz, 0.0D, 0.0D, 0.0D);
    }

    public static void register() {
        instance = new BlockPeaceTotem();
        GameRegistry.registerBlock(instance, ItemBlockPeaceTotem.class, "peaceTotem");
        GameRegistry.registerTileEntity(TileEntityPeaceTotem.class, Reference.MOD_ID + ".peaceTotem");
    }
}
