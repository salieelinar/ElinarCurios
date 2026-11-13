package elinarcurios.block;

import net.minecraft.block.Block;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;

/** ItemBlock for the Eternal Chest (default name from the block's unlocalized name). */
public class ItemBlockEternalChest extends ItemBlock {

    public ItemBlockEternalChest(final Block block) {
        super(block);
        setMaxStackSize(64);
    }

    @Override
    public String getUnlocalizedName(final ItemStack stack) {
        return "tile.eternalChest";
    }
}
