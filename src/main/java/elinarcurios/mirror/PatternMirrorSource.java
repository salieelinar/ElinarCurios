package elinarcurios.mirror;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import gregtech.api.interfaces.tileentity.IGregTechTileEntity;
import gregtech.api.util.AssemblyLineUtils;
import gregtech.api.util.GTRecipe.RecipeAssemblyLine;
import gregtech.api.util.GTUtility;
import gregtech.common.tileentities.machines.IDualInputHatch;
import gregtech.common.tileentities.machines.IDualInputInventory;
import gregtech.common.tileentities.machines.IHatchWatcher;

/**
 * 「总成镜像」的核心：从一个**现成的样板总成**（原版 CRIB；将来也许 GTNL 的 360 格版）里，
 * 按"第 k 格物品 / 第 m 格流体"取出那一堆，并把**内部堆的引用**交给装配线。
 *
 * <p>
 * <b>为什么能零搬运</b>：{@code IDualInputInventory.getItemInputs()} 内部是
 * {@code itemInventory.toArray(...)} —— 数组对象是新的，但<b>元素引用就是总成材料袋里的那个堆</b>。
 * 装配线消耗时执行 {@code stackSize -= n}，改的就是总成里那一堆，全程没有任何搬运。
 *
 * <p>
 * <b>为什么格位只能从样板读</b>：总成的材料袋是**按种类合并**存的（{@code insertItem} 里遇到同种
 * 直接 {@code stackSize +=}），插进去那一刻格位就丢了。所以格位序列必须取自样板自身的
 * {@code ICraftingPatternDetails.getAEInputs()} —— 那是**编码时**的格位序列，未被 AE 的
 * {@code expandedInputs} 展开过。
 *
 * <p>
 * <b>两种总成的格位入口不同</b>（唯一需要适配的地方）：原版 CRIB 的 {@code PatternSlot} 把 details
 * 放在 {@code protected final ICraftingPatternDetails patternDetails} <b>字段</b>里；GTNL 的那台
 * 则提供 {@code public ICraftingPatternDetails getPatternDetails()} <b>方法</b>。这里先试方法再试字段
 * （都带按类缓存，不会每 tick 反射），拿到 details 后统一走 {@code getAEInputs()}，两边的格位语义
 * 就一致了（也顺带绕开原版 {@code getPatternInputs()} 里"开头混着 getSharedItems() 前缀"的坑）。
 */
public final class PatternMirrorSource {

    private PatternMirrorSource() {}

    /** 反射缓存：槽类 → getPatternDetails() 方法（值为 null 表示该类没有）。 */
    private static final Map<Class<?>, Method> DETAILS_METHODS = new HashMap<>();
    /** 反射缓存：槽类 → patternDetails 字段（值为 null 表示没有）。 */
    private static final Map<Class<?>, Field> DETAILS_FIELDS = new HashMap<>();
    /** 反射缓存：控制器类 → currentRecipe 字段（值为 null 表示该类没有这个字段）。 */
    private static final Map<Class<?>, Field> RECIPE_FIELDS = new HashMap<>();

    /** 一个样板槽的格位序列：物品与流体**分开且各自保序**，正好对上装配线的两套独立编号。 */
    private static final class PatternSlots {

        final ItemStack[] items;
        final FluidStack[] fluids;

        PatternSlots(ItemStack[] items, FluidStack[] fluids) {
            this.items = items == null ? new ItemStack[0] : items;
            this.fluids = fluids == null ? new FluidStack[0] : fluids;
        }

        boolean isEmpty() {
            return items.length == 0 && fluids.length == 0;
        }
    }

    /** 一次解析的结果：既供 GUI 显示"这一格应该是什么、现在有没有"，也把供料要用的引用带出来。 */
    public static final class SlotView {

        /** 该样板槽第 k 格要求的物品（null = 没有这一格）。 */
        public ItemStack requiredItem;
        /** 该样板槽第 m 格要求的流体。 */
        public FluidStack requiredFluid;
        /** 材料袋里现存的同种堆 —— **引用**，直接交给装配线。 */
        public ItemStack heldItem;
        public FluidStack heldFluid;
        /** 命中的样板槽序号（1-based；0 = 没命中）。 */
        public int slotOrdinal;
        /** 该样板槽这一侧的总格数（GUI 显示"第 7 / 13 格"用）。 */
        public int totalSlots;
        /** 简短状态说明（GUI / 扫描仪用）。 */
        public String note = "";

        public boolean hasRequirement() {
            return requiredItem != null || requiredFluid != null;
        }

        public boolean hasSupply() {
            return heldItem != null || heldFluid != null;
        }
    }

    /**
     * 解析"第 slotIndex 格"（1-based；物品与流体各自独立编号）。
     *
     * @param fluidSide true = 流体镜像（取第 slotIndex 个流体）；false = 物品镜像
     */
    public static SlotView resolve(World world, int mx, int my, int mz, boolean fluidSide, int slotIndex) {
        return resolve(world, mx, my, mz, fluidSide, slotIndex, false, null);
    }

    /**
     * 解析"第 slotIndex 格"，并明确"要不要只认已经有料的样板槽"。
     *
     * <p>
     * ★ <b>两轮遍历</b>的必要性（2026-10-13 实机反馈）：一个样板总成里可以放多条样板（CRIB 有 36 个槽），
     * 而镜像按"第 k 格"取值时**必须先决定读哪个槽**。若只按"这个槽有没有第 k 格"命中，就会**永远读到
     * 第 1 个有样板的槽** —— 于是 AE 推进第 2 条样板的料时镜像仍在读第 1 条，机器拿不到料
     * （实机症状：第 1 个样板槽能用、第 2 个不行；且界面一直显示第 1 个槽的物品）。
     * ⇒ 先扫**有料的槽**（`IDualInputInventory.isEmpty()` 为 false）：AE 一次只推进一条配方的料，
     * 正常情况下这一轮就唯一命中；只有"供料路径"才允许它落空（返回 null ⇒ 机器自然认为没料）。
     * 第二轮才退回"有样板但还没收到料"的槽，仅用于界面显示"这一格应该是什么"。
     *
     * @param requireSupply true = 只认有料的槽（供料路径 `getFirstStack()` / `getFillableStack()` 用）
     */
    public static SlotView resolve(World world, int mx, int my, int mz, boolean fluidSide, int slotIndex,
        boolean requireSupply) {
        return resolve(world, mx, my, mz, fluidSide, slotIndex, requireSupply, null);
    }

    /**
     * 解析"第 slotIndex 格"（带"当前配方"上下文）。
     *
     * <p>
     * ★ 2026-10-17 针对进阶装配线（AAL）偶发永久堵塞的修复：AAL 的每一片都会重新读一次总线，
     * 而"大规模合成"时总成里经常有多个样板槽同时有料。旧逻辑每次都取"第一个该格有料的槽"，
     * 一旦槽序在前的另一个配方有料，某片就会读到别的配方的同格物品 → AAL 的 {@code Slice.start()}
     * 失败 → 该片 progress=0 重试，而 AAL 的 mProgresstime 被锚死、永不重查配方 → 永久堵。
     * 所以这里在"知道当前配方"时（AAL 的 {@code currentRecipe} 可读）只认"该槽第 k 格与配方第 k 格
     * 匹配"的样板槽；读不到配方（普通装配线/未成型）时保持原来的"有料优先"逻辑。
     *
     * @param recipeObject 所属机器当前正在跑的装配线配方；null = 不知道（走旧逻辑）
     */
    public static SlotView resolve(World world, int mx, int my, int mz, boolean fluidSide, int slotIndex,
        boolean requireSupply, Object recipeObject) {
        SlotView view = new SlotView();
        if (world == null || slotIndex < 1) return view;

        RecipeAssemblyLine recipe = recipeObject instanceof RecipeAssemblyLine ? (RecipeAssemblyLine) recipeObject
            : null;

        Object target = unwrapMaster(world, mx, my, mz);
        if (!(target instanceof IDualInputHatch)) {
            view.note = "绑定目标不是样板总成";
            return view;
        }

        ItemStack expectedItem = null;
        ItemStack[] expectedAlts = null;
        FluidStack expectedFluid = null;
        if (recipe != null) {
            int index = slotIndex - 1;
            if (fluidSide) {
                if (recipe.mFluidInputs != null && index < recipe.mFluidInputs.length) {
                    expectedFluid = recipe.mFluidInputs[index];
                }
            } else if (recipe.mInputs != null && index < recipe.mInputs.length) {
                expectedItem = recipe.mInputs[index];
                if (recipe.mOreDictAlt != null && index < recipe.mOreDictAlt.length) {
                    expectedAlts = recipe.mOreDictAlt[index];
                }
            }
        }

        // 知道当前配方：只认与该配方第 k 格匹配的槽（绝不把别的配方的料交出去）。
        if (expectedItem != null || expectedFluid != null) {
            SlotView matched = scanSlots(
                (IDualInputHatch) target,
                fluidSide,
                slotIndex,
                true,
                true,
                expectedItem,
                expectedAlts,
                expectedFluid);
            if (matched != null) return matched;
            view.requiredItem = expectedItem;
            view.requiredFluid = expectedFluid;
            view.note = "当前配方第 " + slotIndex + " 格还没有匹配的样板槽在供料";
            return view;
        }

        // 不知道配方（普通装配线 / 未成型 / 显示路径）：保持原来的两轮遍历。
        for (int pass = 0; pass < 2; pass++) {
            boolean supplyOnly = pass == 0;
            // 供料路径只跑第一轮（有料优先）；显示路径允许第二轮退回"有样板无料"。
            if (!supplyOnly && requireSupply) break;

            SlotView candidate = scanSlots(
                (IDualInputHatch) target,
                fluidSide,
                slotIndex,
                supplyOnly,
                false,
                null,
                null,
                null);
            if (candidate != null) return candidate;
        }

        view.note = "没有样板槽带有第 " + slotIndex + " 格" + (fluidSide ? "流体" : "物品");
        return view;
    }

    /** 遍历样板槽，按 {@code supplyOnly} / {@code requireMatch} 找一个可用的候选。 */
    private static SlotView scanSlots(IDualInputHatch target, boolean fluidSide, int slotIndex, boolean supplyOnly,
        boolean requireMatch, ItemStack expectedItem, ItemStack[] expectedAlts, FluidStack expectedFluid) {
        int ordinal = 0;
        Iterator<? extends IDualInputInventory> it = target.inventories();
        while (it != null && it.hasNext()) {
            IDualInputInventory slot = it.next();
            int current = ++ordinal;
            if (slot == null) continue;
            if (supplyOnly && slot.isEmpty()) continue;

            SlotView candidate = matchSlot(slot, current, fluidSide, slotIndex);
            if (candidate == null) continue;
            if (supplyOnly && !candidate.hasSupply()) continue;
            if (requireMatch && !matchesRecipe(candidate, expectedItem, expectedAlts, expectedFluid)) continue;
            return candidate;
        }
        return null;
    }

    /** 样板第 k 格是否就是当前配方第 k 格（物品用装配线同一套 oreDict 判定）。 */
    private static boolean matchesRecipe(SlotView candidate, ItemStack expectedItem, ItemStack[] expectedAlts,
        FluidStack expectedFluid) {
        if (expectedItem != null) {
            return candidate.requiredItem != null
                && RecipeAssemblyLine.getMatchedIngredientAmount(candidate.requiredItem, expectedItem, expectedAlts)
                    >= 0;
        }
        if (expectedFluid != null) {
            return candidate.requiredFluid != null && GTUtility.areFluidsEqual(candidate.requiredFluid, expectedFluid);
        }
        return true;
    }

    /**
     * 取"所属机器当前正在跑的装配线配方"。
     *
     * <p>
     * 只有进阶装配线 {@code MTEAdvAssLine} 持有 {@code currentRecipe} 字段；普通装配线把配方放在
     * {@code checkProcessing} 的局部变量里，读不到（返回 null ⇒ 走旧的"有料优先"逻辑）。
     * 反射失败一律按"没有配方"处理，绝不抛异常影响机器 tick。
     */
    public static RecipeAssemblyLine currentRecipeOf(IHatchWatcher watcher) {
        if (watcher == null) return null;
        Class<?> clazz = watcher.getClass();
        Field field;
        synchronized (RECIPE_FIELDS) {
            if (RECIPE_FIELDS.containsKey(clazz)) {
                field = RECIPE_FIELDS.get(clazz);
            } else {
                Field found = null;
                for (Class<?> c = clazz; c != null && found == null; c = c.getSuperclass()) {
                    try {
                        Field candidate = c.getDeclaredField("currentRecipe");
                        candidate.setAccessible(true);
                        found = candidate;
                    } catch (Throwable ignored) {
                        // 继续往父类找
                    }
                }
                RECIPE_FIELDS.put(clazz, found);
                field = found;
            }
        }
        if (field == null) return null;
        try {
            Object value = field.get(watcher);
            return value instanceof RecipeAssemblyLine ? (RecipeAssemblyLine) value : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    /** {@link #currentRecipeOf(IHatchWatcher)} 的列表重载：一台机器只有一个控制器 watcher。 */
    public static RecipeAssemblyLine currentRecipeOf(List<? extends IHatchWatcher> watchers) {
        return watchers == null || watchers.isEmpty() ? null : currentRecipeOf(watchers.get(0));
    }

    /** 在单个样板槽里找"第 slotIndex 个物品 / 流体"，命中就返回结果（含材料袋里那一堆的引用）。 */
    private static SlotView matchSlot(IDualInputInventory slot, int ordinal, boolean fluidSide, int slotIndex) {
        PatternSlots slots = readPatternSlots(slot);
        if (slots == null || slots.isEmpty()) return null;

        SlotView view = new SlotView();
        if (fluidSide) {
            if (slots.fluids.length < slotIndex) return null;
            FluidStack need = slots.fluids[slotIndex - 1];
            if (need == null) return null;
            view.requiredFluid = need;
            view.heldFluid = findHeldFluid(slot, need);
            view.totalSlots = slots.fluids.length;
        } else {
            if (slots.items.length < slotIndex) return null;
            ItemStack need = slots.items[slotIndex - 1];
            if (need == null) return null;
            view.requiredItem = need;
            view.heldItem = findHeldItem(slot, need);
            view.totalSlots = slots.items.length;
        }
        view.slotOrdinal = ordinal;
        view.note = "读第 " + ordinal + " 号样板槽";
        return view;
    }

    /** 取该样板槽的格位序列（先反射方法、再反射字段）。 */
    private static PatternSlots readPatternSlots(IDualInputInventory slot) {
        Object details = readDetailsObject(slot);
        if (!(details instanceof ICraftingPatternDetails)) return null;
        IAEStack<?>[] inputs = ((ICraftingPatternDetails) details).getAEInputs();
        if (inputs == null || inputs.length == 0) return null;
        return split(inputs);
    }

    /** 把混排的一维 IAEStack 序列拆成"物品数组 + 流体数组"（各自保序）。 */
    private static PatternSlots split(IAEStack<?>[] inputs) {
        int itemCount = 0;
        int fluidCount = 0;
        for (IAEStack<?> entry : inputs) {
            if (entry instanceof IAEItemStack) itemCount++;
            else if (entry instanceof IAEFluidStack) fluidCount++;
        }
        ItemStack[] items = new ItemStack[itemCount];
        FluidStack[] fluids = new FluidStack[fluidCount];
        int i = 0;
        int f = 0;
        for (IAEStack<?> entry : inputs) {
            if (entry instanceof IAEItemStack) {
                items[i++] = ((IAEItemStack) entry).getItemStack();
            } else if (entry instanceof IAEFluidStack) {
                fluids[f++] = ((IAEFluidStack) entry).getFluidStack();
            }
        }
        return new PatternSlots(items, fluids);
    }

    /**
     * 脱离装配线时的"格位映射自检"：拿样板的产物反查装配线配方，确认"样板第 k 个物品"
     * 是否就是"配方第 k 格物品"。
     *
     * <p>
     * 为什么需要：装配线读的是 {@code recipe.mInputs[i]} 的**格位顺序**，而样板里的物品顺序取决于
     * 编码时怎么填（NEI 直编通常一致，但没人能保证）。搭装配线之前先看一眼，就能判断格位映射是否可信，
     * 免得跑不起来时怀疑到别处。
     *
     * @return 只在"产物确实对得上装配线配方、但格位顺序不符"时返回警告文案；其余情况返回 ""（界面不必显示）
     */
    public static String checkRecipeMapping(World world, int mx, int my, int mz, int slotIndex) {
        if (world == null || slotIndex < 1) return "";
        try {
            Object target = unwrapMaster(world, mx, my, mz);
            if (!(target instanceof IDualInputHatch)) return "";
            Iterator<? extends IDualInputInventory> it = ((IDualInputHatch) target).inventories();

            int ordinal = 0;
            while (it != null && it.hasNext()) {
                IDualInputInventory slot = it.next();
                int current = ++ordinal;
                if (slot == null) continue;
                Object raw = readDetailsObject(slot);
                if (!(raw instanceof ICraftingPatternDetails)) continue;
                ICraftingPatternDetails details = (ICraftingPatternDetails) raw;

                PatternSlots slots = readPatternSlots(slot);
                if (slots == null || slots.items.length < slotIndex) continue;

                ItemStack product = null;
                IAEStack<?>[] condensed = details.getCondensedAEOutputs();
                if (condensed != null) {
                    for (IAEStack<?> entry : condensed) {
                        if (entry instanceof IAEItemStack) {
                            ItemStack candidate = ((IAEItemStack) entry).getItemStack();
                            if (candidate != null) {
                                product = candidate;
                                break;
                            }
                        }
                    }
                }
                if (product == null) return "";

                Collection<RecipeAssemblyLine> recipes = AssemblyLineUtils.findALRecipeByOutput(product);
                if (recipes == null || recipes.isEmpty()) return "";

                ItemStack fromPattern = slots.items[slotIndex - 1];
                for (RecipeAssemblyLine recipe : recipes) {
                    if (recipe.mInputs == null || recipe.mInputs.length < slotIndex) continue;
                    if (GTUtility.areStacksEqual(recipe.mInputs[slotIndex - 1], fromPattern)) return "";
                }
                StringBuilder sb = new StringBuilder();
                sb.append("样板第 ")
                    .append(slotIndex)
                    .append(" 个是 ")
                    .append(fromPattern.getDisplayName());
                for (RecipeAssemblyLine recipe : recipes) {
                    if (recipe.mInputs == null || recipe.mInputs.length < slotIndex) continue;
                    ItemStack expect = recipe.mInputs[slotIndex - 1];
                    if (expect != null) {
                        sb.append("，配方第 ")
                            .append(slotIndex)
                            .append(" 格是 ")
                            .append(expect.getDisplayName());
                        break;
                    }
                }
                sb.append("（格位可能对不上）");
                return sb.toString();
            }
        } catch (Throwable ignored) {
            return "";
        }
        return "";
    }

    /**
     * 按坐标取出"真正的总成 MTE"。
     *
     * <p>
     * ★ 必须先解一层 {@code IGregTechTileEntity}：GT 机器在世界里放的是**基座 tile**，真正的 MTE 在
     * {@code getMetaTileEntity()} 里 —— 直接对 {@code TileEntity} 做 {@code instanceof IDualInputHatch}
     * 永远为 false。这正是实机里界面显示「绑定目标不是样板总成」、镜像完全不出料的原因。
     *
     * <p>
     * 2026-11-07 起改为 public：样板总成侦测器（{@code MTEPatternRedstoneDetector}）也按坐标读总成，
     * 复用同一套解包逻辑，避免两处各写一份。
     */
    public static Object unwrapMaster(World world, int x, int y, int z) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (te instanceof IGregTechTileEntity) {
            return ((IGregTechTileEntity) te).getMetaTileEntity();
        }
        return te;
    }

    /** 反射拿 ICraftingPatternDetails：先找 getPatternDetails() 方法（GTNL），再找 patternDetails 字段（原版 CRIB）。 */
    private static Object readDetailsObject(IDualInputInventory slot) {
        Class<?> clazz = slot.getClass();

        Method method;
        synchronized (DETAILS_METHODS) {
            if (DETAILS_METHODS.containsKey(clazz)) {
                method = DETAILS_METHODS.get(clazz);
            } else {
                Method found = null;
                try {
                    Method candidate = clazz.getMethod("getPatternDetails");
                    candidate.setAccessible(true);
                    found = candidate;
                } catch (Throwable ignored) {
                    // 没有这个方法：原版 CRIB 走字段
                }
                DETAILS_METHODS.put(clazz, found);
                method = found;
            }
        }
        if (method != null) {
            try {
                Object result = method.invoke(slot);
                if (result != null) return result;
            } catch (Throwable ignored) {
                // 落到字段
            }
        }

        Field field;
        synchronized (DETAILS_FIELDS) {
            if (DETAILS_FIELDS.containsKey(clazz)) {
                field = DETAILS_FIELDS.get(clazz);
            } else {
                Field found = null;
                for (Class<?> c = clazz; c != null && found == null; c = c.getSuperclass()) {
                    try {
                        Field candidate = c.getDeclaredField("patternDetails");
                        candidate.setAccessible(true);
                        found = candidate;
                    } catch (Throwable ignored) {
                        // 继续往父类找
                    }
                }
                DETAILS_FIELDS.put(clazz, found);
                field = found;
            }
        }
        if (field != null) {
            try {
                return field.get(slot);
            } catch (Throwable ignored) {
                // 什么都没拿到
            }
        }
        return null;
    }

    /** 从材料袋里找与 need 同种的那一堆（返回**引用**，不是拷贝）。 */
    private static ItemStack findHeldItem(IDualInputInventory slot, ItemStack need) {
        try {
            ItemStack[] held = slot.getItemInputs();
            if (held == null) return null;
            for (ItemStack stack : held) {
                if (stack != null && GTUtility.areStacksEqual(need, stack)) return stack;
            }
        } catch (Throwable ignored) {
            // 视为没有
        }
        return null;
    }

    private static FluidStack findHeldFluid(IDualInputInventory slot, FluidStack need) {
        try {
            FluidStack[] held = slot.getFluidInputs();
            if (held == null) return null;
            for (FluidStack stack : held) {
                if (stack != null && GTUtility.areFluidsEqual(need, stack)) return stack;
            }
        } catch (Throwable ignored) {
            // 视为没有
        }
        return null;
    }
}
