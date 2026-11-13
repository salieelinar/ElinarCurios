package elinarcurios.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import squeek.applecore.api.food.FoodValues;
import squeek.spiceoflife.foodtracker.FoodHistory;
import squeek.spiceoflife.foodtracker.FoodModifier;
import squeek.spiceoflife.foodtracker.foodgroups.FoodGroupRegistry;
import squeek.spiceoflife.helpers.FoodHelper;
import squeek.spiceoflife.helpers.MealPrioritizationHelper;

/**
 * 午餐盒的选食器：语义与 {@link MealPrioritizationHelper#findBestFoodForPlayerToEat} 完全一致，
 * 但把「每个候选槽跑一次 EvalEx 表达式求值」降成「每个 count 跑一次」。
 *
 * <p>
 * 为什么需要它：SpiceOfLife 自己的食物盒是 6~54 格，逐槽求值毫无压力；午餐盒是 2916 格，
 * 而 GTNH 2.8.4 的递减公式是一段两层 IF 的 EvalEx 表达式（实测 3.00 µs/次），逐槽求值在满盒时
 * 单次选食就要 ~10 ms，而一次进食 SpiceOfLife 会在三个地方各调一次选食。
 *
 * <p>
 * 关键观察：在**没有任何 food group**（GTNH 默认配置就是空的）时，modifier 只由
 * {@code count}（该食物在 recentHistory 里的出现次数）与全局量决定 —— 而 count 的取值只有
 * 0..{@code food.history.length}（GTNH 配的是 20）共 21 种。于是按 count 分组、每组只求值一次即可。
 *
 * <p>
 * 一旦整合包配了 food group（不同组、同 count 会得到不同 modifier），本类自动回退到
 * SpiceOfLife 的原实现，绝不自行猜测语义。
 */
public final class LunchBoxSelector {

    private LunchBoxSelector() {}

    /** 返回最优食物的扁平槽位；-1 表示没有可吃的。 */
    public static int findBestFoodSlot(final EntityPlayer player, final IInventory inventory) {
        if (player == null || inventory == null) return -1;
        if (FoodGroupRegistry.numFoodGroups() > 0) {
            // 有 food group 时 count 不再是唯一变量，直接交给 SpiceOfLife 保证语义一致。
            return MealPrioritizationHelper.findBestFoodForPlayerToEat(player, inventory);
        }
        return findBestFoodSlotFast(player, inventory);
    }

    private static int findBestFoodSlotFast(final EntityPlayer player, final IInventory inventory) {
        final FoodHistory history = FoodHistory.get(player);
        // 与 MealPrioritizationHelper.findBestFoodForPlayerToEat 一致：按「还差多少饱食度」择优。
        final int hungerMissing = 20 - player.getFoodStats()
            .getFoodLevel();
        final Map<Integer, Float> modifierByCount = new HashMap<>();

        int bestSlot = -1;
        float bestModifier = 0f;
        FoodValues bestModified = null;

        for (int slot = 0; slot < inventory.getSizeInventory(); slot++) {
            final ItemStack food = inventory.getStackInSlot(slot);
            if (food == null || food.stackSize <= 0) continue;
            if (!FoodHelper.isFood(food)) continue;

            final FoodValues base = FoodValues.get(food);
            if (base == null) continue;

            final float modifier;
            final FoodValues modified;
            if (FoodHelper.canFoodDiminish(food)) {
                final int count = history.getFoodCountIgnoringFoodGroups(food);
                Float cached = modifierByCount.get(Integer.valueOf(count));
                if (cached == null) {
                    cached = Float.valueOf(FoodModifier.getFoodModifier(player, food));
                    modifierByCount.put(Integer.valueOf(count), cached);
                }
                modifier = cached.floatValue();
                modified = FoodModifier.getModifiedFoodValues(base, modifier);
            } else {
                // 照抄 SpiceOfLife：不可递减的食物用 NaN（Float.compare 里 NaN 视为最大）且不改食物值。
                modifier = Float.NaN;
                modified = base;
            }

            if (bestModified == null || ranksBefore(modifier, modified, bestModifier, bestModified, hungerMissing)) {
                bestSlot = slot;
                bestModifier = modifier;
                bestModified = modified;
            }
        }
        return bestSlot;
    }

    /**
     * 候选是否应当取代当前最优 —— 逐字对应 SpiceOfLife 的
     * {@code MealPrioritizationHelper.FoodInfoComparator.compare(best, candidate) > 0}：
     * 先比递减系数（大者优），再比「补满后剩下的余量」（绝对值小者优，相等时取补得更多的那个），
     * 最后比 hunger × saturationModifier。
     */
    private static boolean ranksBefore(final float candidateModifier, final FoodValues candidateModified,
        final float bestModifier, final FoodValues bestModified, final int hungerMissing) {
        int result = Float.compare(candidateModifier, bestModifier);
        if (result == 0) {
            final int bestRemainder = hungerMissing - bestModified.hunger;
            final int candidateRemainder = hungerMissing - candidateModified.hunger;
            result = Integer.compare(Math.abs(bestRemainder), Math.abs(candidateRemainder));
            if (result == 0 && bestRemainder != candidateRemainder) {
                result = candidateRemainder > bestRemainder ? 1 : -1;
            }
        }
        if (result == 0) {
            result = Float.compare(
                candidateModified.saturationModifier * candidateModified.hunger,
                bestModified.saturationModifier * bestModified.hunger);
        }
        return result > 0;
    }
}
