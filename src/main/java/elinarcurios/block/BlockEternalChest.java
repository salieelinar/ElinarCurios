package elinarcurios.block;

import java.util.ArrayList;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import elinarcurios.common.Reference;
import elinarcurios.tile.TileEntityEternalChest;

/**
 * 永恒箱子:12 页 × 243 格 × int 上限的巨型静态存储方块。
 * 外观:紫色板材 + 紫红镶边(单张静态贴图,无动画)。
 */
public class BlockEternalChest extends BlockContainer {

    public static BlockEternalChest instance;

    public BlockEternalChest() {
        super(Material.iron);
        setBlockName("eternalChest");
        setStepSound(Block.soundTypeMetal);
        setHardness(50.0F);
        setResistance(2000.0F);
        setLightLevel(0.5F);
        setHarvestLevel("pickaxe", 3);
        setCreativeTab(ElinarCurios.creativeTab);
    }

    public static void register() {
        instance = new BlockEternalChest();
        GameRegistry.registerBlock(instance, ItemBlockEternalChest.class, "eternalChest");
        GameRegistry.registerTileEntity(TileEntityEternalChest.class, Reference.MOD_ID + ".eternalChest");
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileEntityEternalChest();
    }

    @Override
    public void onBlockPlacedBy(final World world, final int x, final int y, final int z,
        final net.minecraft.entity.EntityLivingBase entity, final ItemStack itemStack) {
        final TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof TileEntityEternalChest && itemStack != null && itemStack.stackTagCompound != null) {
            // Restore the carried storage (portable chest behaviour, like the vanilla
            // Avaritiaddons Infinity Chest): the TE picks up the saved pages from the item.
            ((TileEntityEternalChest) tileEntity).readCustomNBT(itemStack.stackTagCompound);
        }
    }

    @Override
    public boolean removedByPlayer(final World world, final EntityPlayer player, final int x, final int y, final int z,
        final boolean willHarvest) {
        if (willHarvest) {
            for (ItemStack drop : getDrops(world, x, y, z, 0, 0)) {
                dropBlockAsItem(world, x, y, z, drop);
            }
        }
        return super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    @Override
    public ArrayList<ItemStack> getDrops(final World world, final int x, final int y, final int z, final int metadata,
        final int fortune) {
        final ArrayList<ItemStack> list = new ArrayList<ItemStack>();
        final TileEntity tileEntity = world.getTileEntity(x, y, z);
        if (tileEntity instanceof TileEntityEternalChest) {
            // Portable chest: the whole storage travels inside the dropped item.
            final ItemStack droppedStack = new ItemStack(this, 1, 0);
            final NBTTagCompound tag = new NBTTagCompound();
            ((TileEntityEternalChest) tileEntity).writeCustomNBT(tag);
            droppedStack.setTagCompound(tag);
            list.add(droppedStack);
        }
        return list;
    }

    @Override
    public boolean onBlockActivated(final World world, final int x, final int y, final int z,
        final EntityPlayer entityPlayer, final int side, final float hitX, final float hitY, final float hitZ) {
        if (!world.isRemote) {
            FMLNetworkHandler
                .openGui(entityPlayer, ElinarCurios.instance, ElinarCurios.GUI_ID_ETERNAL_CHEST, world, x, y, z);
        }
        return true;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(final IIconRegister iconRegister) {
        this.blockIcon = iconRegister.registerIcon("elinarcurios:eternal_chest");
    }
}
