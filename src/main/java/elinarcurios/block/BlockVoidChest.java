package elinarcurios.block;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.internal.FMLNetworkHandler;
import cpw.mods.fml.common.registry.GameRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import elinarcurios.common.Reference;
import elinarcurios.tile.TileEntityVoidChest;

/**
 * 虚界百宝箱:与永恒箱子相同的 12 页 × 243 格 × int 上限巨型静态存储,
 * 额外带「固化」特性(箱内非空槽数量一键刷新为 10 亿)。
 * 外观:紫色板材 + 金色镶边(单张静态贴图,无动画)。
 *
 * <p>
 * 存储/便携/掉落逻辑全部继承自 {@link BlockEternalChest}(其 instanceof 检查
 * 对子类 TileEntity 同样成立),此处只替换贴图、GUI 入口与方块实体。
 */
public class BlockVoidChest extends BlockEternalChest {

    public static BlockVoidChest instance;

    public BlockVoidChest() {
        super();
        // NB: NOT "voidChest" — Hardcore Ender Expansion already owns that block name
        // (and the tile.voidChest.name / container.voidChest lang keys, shown as
        // 「虚空保存箱」in GTNH's zh_CN txloader translations). Elinar prefix avoids
        // the global lang-key collision.
        setBlockName("elinarVoidChest");
    }

    public static void register() {
        instance = new BlockVoidChest();
        GameRegistry.registerBlock(instance, ItemBlockVoidChest.class, "elinarVoidChest");
        GameRegistry.registerTileEntity(TileEntityVoidChest.class, Reference.MOD_ID + ".elinarVoidChest");
    }

    @Override
    public TileEntity createNewTileEntity(final World world, final int metadata) {
        return new TileEntityVoidChest();
    }

    @Override
    public boolean onBlockActivated(final World world, final int x, final int y, final int z,
        final EntityPlayer entityPlayer, final int side, final float hitX, final float hitY, final float hitZ) {
        if (!world.isRemote) {
            FMLNetworkHandler
                .openGui(entityPlayer, ElinarCurios.instance, ElinarCurios.GUI_ID_VOID_CHEST, world, x, y, z);
        }
        return true;
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void registerBlockIcons(final IIconRegister iconRegister) {
        this.blockIcon = iconRegister.registerIcon("elinarcurios:void_chest");
    }
}
