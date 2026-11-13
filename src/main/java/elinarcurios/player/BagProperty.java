package elinarcurios.player;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.IExtendedEntityProperties;

/**
 * Player property holding the worn Infinite Backpack.
 * Modeled after AdventureBackpack2's BackpackProperty (LGPL-3.0).
 */
public class BagProperty implements IExtendedEntityProperties {

    private static final String PROPERTY_NAME = "elinarcurios.property";

    private EntityPlayer player;
    private ItemStack wearable = null;

    public BagProperty(final EntityPlayer player) {
        this.player = player;
    }

    public static void register(final EntityPlayer player) {
        player.registerExtendedProperties(PROPERTY_NAME, new BagProperty(player));
    }

    public static BagProperty get(final EntityPlayer player) {
        return (BagProperty) player.getExtendedProperties(PROPERTY_NAME);
    }

    @Override
    public void saveNBTData(final NBTTagCompound compound) {
        if (wearable != null) compound.setTag("wearable", wearable.writeToNBT(new NBTTagCompound()));
    }

    @Override
    public void loadNBTData(final NBTTagCompound compound) {
        if (compound == null) return;
        setWearable(
            compound.hasKey("wearable") ? ItemStack.loadItemStackFromNBT(compound.getCompoundTag("wearable")) : null);
    }

    @Override
    public void init(final Entity entity, final World world) {
        this.player = (EntityPlayer) entity;
    }

    public void setWearable(final ItemStack wearable) {
        this.wearable = wearable;
    }

    public ItemStack getWearable() {
        return wearable;
    }

    public EntityPlayer getPlayer() {
        return player;
    }
}
