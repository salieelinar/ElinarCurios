package elinarcurios.recipe;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.world.World;

import elinarcurios.item.ItemWirelessBattery;

/**
 * 十字形虚空电池配方:中心 center,上下左右 side(四角留空)。
 *
 * <pre>
 *  S
 * SCS
 *  S
 * </pre>
 *
 * <p>
 * 为什么要自定义配方类:原版 {@link ShapedRecipes} 的匹配
 * (ShapedRecipes.checkMatch)只比对物品与 damage,<b>完全不看 NBT</b> ——
 * 空的电池和满电的电池在合成台上无法区分,于是"4 个空电池 → 1 个满电池"
 * 就能白刷 EU。本类在原生匹配通过后,再检查四角的上一级电池是否已充满
 * (ULV~HV 档没有电量门槛,构造时传 requireFull = false)。
 *
 * <p>
 * 产物由 {@link ItemWirelessBattery#newEmptyBattery(int)} 造出,自带
 * EC.Charge = 0 的 NBT,因此 NEI 显示的产物与实际合成结果一致(都显示 0%)。
 */
public class CrossBatteryRecipe extends ShapedRecipes {

    /** true = 四角(以及网格内任意)虚空电池必须满电才匹配。 */
    private final boolean requireFull;

    public CrossBatteryRecipe(ItemStack output, ItemStack side, ItemStack center, boolean requireFull) {
        super(3, 3, buildPattern(side, center), output);
        this.requireFull = requireFull;
    }

    /** 把 " S "/"SCS"/" S " 展开成 3×3 的 9 格。 */
    private static ItemStack[] buildPattern(ItemStack side, ItemStack center) {
        return new ItemStack[] { null, side, null, side, center, side, null, side, null };
    }

    @Override
    public boolean matches(InventoryCrafting grid, World world) {
        if (!super.matches(grid, world)) return false;
        if (!requireFull) return true;
        // 图案是满 3×3,super.matches 通过时网格内不会有额外物品,
        // 所以这里扫全网格等价于只扫四角的上一级电池(中心是末影之眼块,会被跳过)。
        for (int i = 0; i < grid.getSizeInventory(); i++) {
            ItemStack stack = grid.getStackInSlot(i);
            if (stack != null && stack.getItem() instanceof ItemWirelessBattery
                && !ItemWirelessBattery.isFullyCharged(stack)) {
                return false;
            }
        }
        return true;
    }
}
