package elinarcurios.block;

import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.client.resources.I18n;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;

/** ItemBlock for the Void Chest: its own display name plus the solidify tooltip. */
public class ItemBlockVoidChest extends ItemBlockEternalChest {

    public ItemBlockVoidChest(final Block block) {
        super(block);
    }

    @Override
    public String getUnlocalizedName(final ItemStack stack) {
        return "tile.elinarVoidChest";
    }

    @Override
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List list,
        final boolean advanced) {
        list.add(I18n.format("tile.elinarVoidChest.tooltip.solidify"));
        list.add(I18n.format("tile.elinarVoidChest.tooltip.dissociate"));
    }
}
