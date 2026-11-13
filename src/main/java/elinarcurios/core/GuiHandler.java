package elinarcurios.core;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import cpw.mods.fml.common.network.IGuiHandler;
import elinarcurios.ElinarCurios;
import elinarcurios.gui.GuiEternalChest;
import elinarcurios.gui.GuiInfiniteBackpack;
import elinarcurios.gui.GuiLunchBox;
import elinarcurios.gui.GuiVoidChest;
import elinarcurios.inventory.ContainerEternalChest;
import elinarcurios.inventory.ContainerInfiniteBackpack;
import elinarcurios.inventory.ContainerLunchBox;
import elinarcurios.item.ItemInfiniteBackpack;
import elinarcurios.item.ItemLunchBox;
import elinarcurios.tile.TileEntityEternalChest;
import elinarcurios.tile.TileEntityVoidChest;
import elinarcurios.util.BagUtils;

public final class GuiHandler implements IGuiHandler {

    public static final GuiHandler instance = new GuiHandler();

    private GuiHandler() {}

    @Override
    public Object getServerGuiElement(final int ID, final EntityPlayer player, final World world, final int x,
        final int y, final int z) {
        switch (ID) {
            case ElinarCurios.GUI_ID_BAG_HOLDING: {
                final ItemStack held = player.getHeldItem();
                if (!ItemInfiniteBackpack.isInfiniteBackpack(held)) return null;
                return new ContainerInfiniteBackpack(held, player.inventory);
            }
            case ElinarCurios.GUI_ID_BAG_WEARING: {
                final ItemStack worn = BagUtils.getWornInfiniteBackpack(player);
                if (worn == null) return null;
                return new ContainerInfiniteBackpack(worn, player.inventory);
            }
            case ElinarCurios.GUI_ID_ETERNAL_CHEST: {
                final TileEntity tileEntity = world.getTileEntity(x, y, z);
                if (!(tileEntity instanceof TileEntityEternalChest)) return null;
                return new ContainerEternalChest((TileEntityEternalChest) tileEntity, player.inventory);
            }
            case ElinarCurios.GUI_ID_VOID_CHEST: {
                final TileEntity tileEntity = world.getTileEntity(x, y, z);
                if (!(tileEntity instanceof TileEntityVoidChest)) return null;
                return new ContainerEternalChest((TileEntityEternalChest) tileEntity, player.inventory);
            }
            case ElinarCurios.GUI_ID_LUNCH_BOX: {
                final ItemStack held = player.getHeldItem();
                if (!ItemLunchBox.isLunchBox(held)) return null;
                return new ContainerLunchBox(held, player.inventory);
            }
            default:
                return null;
        }
    }

    @Override
    public Object getClientGuiElement(final int ID, final EntityPlayer player, final World world, final int x,
        final int y, final int z) {
        switch (ID) {
            case ElinarCurios.GUI_ID_BAG_HOLDING: {
                final ItemStack held = player.getHeldItem();
                if (!ItemInfiniteBackpack.isInfiniteBackpack(held)) return null;
                return new GuiInfiniteBackpack(held, false, player.inventory);
            }
            case ElinarCurios.GUI_ID_BAG_WEARING: {
                final ItemStack worn = BagUtils.getWornInfiniteBackpack(player);
                if (worn == null) return null;
                return new GuiInfiniteBackpack(worn, true, player.inventory);
            }
            case ElinarCurios.GUI_ID_ETERNAL_CHEST: {
                final TileEntity tileEntity = world.getTileEntity(x, y, z);
                if (!(tileEntity instanceof TileEntityEternalChest)) return null;
                return new GuiEternalChest((TileEntityEternalChest) tileEntity, player.inventory);
            }
            case ElinarCurios.GUI_ID_VOID_CHEST: {
                final TileEntity tileEntity = world.getTileEntity(x, y, z);
                if (!(tileEntity instanceof TileEntityVoidChest)) return null;
                return new GuiVoidChest((TileEntityVoidChest) tileEntity, player.inventory);
            }
            case ElinarCurios.GUI_ID_LUNCH_BOX: {
                final ItemStack held = player.getHeldItem();
                if (!ItemLunchBox.isLunchBox(held)) return null;
                return new GuiLunchBox(held, player.inventory);
            }
            default:
                return null;
        }
    }
}
