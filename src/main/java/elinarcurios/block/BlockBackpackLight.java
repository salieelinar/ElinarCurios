package elinarcurios.block;

import java.util.Random;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.util.BagSettings;
import elinarcurios.util.BagUtils;

/**
 * Invisible, non-colliding light source placed at the backpack wearer's feet by
 * {@link elinarcurios.player.BackpackBuffHandler}.
 *
 * <p>
 * 1.7.10's light engine calls the no-argument {@link Block#getLightValue()}, so a metadata-driven
 * light value would be ignored. Instead there is one block instance per light level (1-15) and the
 * handler places the instance matching the slider; the miner-light radius therefore changes
 * immediately. Vanilla light propagates one level per block and caps at 15, so a level-15 source
 * lights roughly a 31x31 area around itself (about 2x2 chunks). The block removes itself when no
 * nearby player still has the miner light enabled, which also cleans up after a crash.
 */
public final class BlockBackpackLight extends Block {

    private static final BlockBackpackLight[] INSTANCES = new BlockBackpackLight[16];

    private final int level;

    private BlockBackpackLight(final int level) {
        super(Material.air);
        this.level = level;
        setBlockName("elinarcurios.backpackLight" + level);
        setBlockUnbreakable();
        setResistance(6000000F);
        setLightOpacity(0);
        setLightLevel(level / 15F);
        setTickRandomly(true);
        setCreativeTab(null);
        setBlockBounds(0F, 0F, 0F, 0F, 0F, 0F);
    }

    public static void register() {
        for (int level = 1; level <= 15; level++) {
            INSTANCES[level] = new BlockBackpackLight(level);
            GameRegistry.registerBlock(INSTANCES[level], null, "BackpackLight" + level);
        }
    }

    /** The block instance for a light level (1-15), or null for 0 / out of range. */
    public static Block forLevel(final int level) {
        return level >= 1 && level <= 15 ? INSTANCES[level] : null;
    }

    public static boolean isLightBlock(final Block block) {
        return block instanceof BlockBackpackLight;
    }

    public int getLightLevel() {
        return level;
    }

    @Override
    public int getLightValue() {
        return level;
    }

    @Override
    public int getLightValue(final IBlockAccess world, final int x, final int y, final int z) {
        return level;
    }

    @Override
    public int getRenderType() {
        return -1;
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
    public boolean isReplaceable(final IBlockAccess world, final int x, final int y, final int z) {
        return true;
    }

    @Override
    public AxisAlignedBB getCollisionBoundingBoxFromPool(final World world, final int x, final int y, final int z) {
        return null;
    }

    @Override
    public int quantityDropped(final Random random) {
        return 0;
    }

    @Override
    public void updateTick(final World world, final int x, final int y, final int z, final Random random) {
        if (world.isRemote) return;
        for (Object o : world.playerEntities) {
            final EntityPlayer player = (EntityPlayer) o;
            if (player == null || player.isDead) continue;
            final ItemStack bag = BagUtils.getWornInfiniteBackpack(player);
            if (bag == null) continue;
            if (BagSettings.getFloat(bag, BagSettings.MINE_LIGHT) <= 0F) continue;
            if (player.getDistanceSq(x + 0.5D, y + 0.5D, z + 0.5D) <= 64.0D) return;
        }
        world.setBlockToAir(x, y, z);
    }
}
