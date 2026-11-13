package elinarcurios.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

import elinarcurios.Config;

/**
 * Item form of the Peace Totem; adds the flavour line and the range tooltip.
 */
public class ItemBlockPeaceTotem extends ItemBlock {

    public ItemBlockPeaceTotem(final Block block) {
        super(block);
    }

    @Override
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List list,
        final boolean advanced) {
        list.add(I18n.format("tile.peaceTotem.tooltip.flavor"));
        list.add(
            I18n.format(
                "tile.peaceTotem.tooltip.range",
                Config.peaceTotemRangeHorizontal,
                Config.peaceTotemRangeVertical));
    }
}
