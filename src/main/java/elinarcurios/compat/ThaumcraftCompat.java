package elinarcurios.compat;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.item.ItemElinarWand;
import thaumcraft.api.wands.WandCap;
import thaumcraft.api.wands.WandRod;

/**
 * Thaumcraft 软联动:「初心无垠法杖」(rod/cap tag = "elinar")。
 *
 * 设计目标(无作弊、新手友好):
 * - 容量 1000 万:TC 内部按 ×100 计算,getMaxVis() = 10 亿,远低于 int 溢出边界
 * (2,147,483,647),任何充能/消耗都摸不到上限,没有魔力焦虑;
 * - 不挂 IWandRodOnUpdate:没有自动回满机制,vis 只能靠充能台/源质充入;
 * - 新杖 NBT 不含 vis 键,getVis() 返回 0 —— 拿到手是空的,吸多少用多少。
 *
 * 参考:Avaritia 的 Lucrum.java(同款组装方式,但去掉了 CheatyWandUpdate,
 * 且法杖注册为 elinarcurios 自己的 Item,创造标签/NEI 可见)。
 */
public final class ThaumcraftCompat {

    /** 法杖 vis 容量(外部单位)。内部实际 = 该值 × 100 = 1,000,000,000。 */
    public static final int WAND_CAPACITY = 10_000_000;

    public static WandRod wandRodElinar;
    public static WandCap wandCapElinar;

    private ThaumcraftCompat() {}

    public static boolean isAvailable() {
        return Loader.isModLoaded("Thaumcraft");
    }

    /**
     * 注册杖杆/杖端。必须在两端(客户端+服务端)都执行:
     * WandRod.rods / WandCap.caps 是静态表,getRod()/getCap() 按 tag 查表,
     * 漏注册一端会得到 null 导致 NPE。
     */
    public static void init() {
        if (!isAvailable()) {
            return;
        }

        wandRodElinar = new WandRod(
            "elinar",
            WAND_CAPACITY,
            new ItemStack(Blocks.diamond_block),
            100,
            new ResourceLocation("elinarcurios", "textures/models/wand_rod_elinar.png"));
        wandRodElinar.setGlowing(true);

        wandCapElinar = new WandCap("elinar", 0.0F, new ItemStack(Blocks.gold_block), 100);
        wandCapElinar.setTexture(new ResourceLocation("elinarcurios", "textures/models/wand_cap_elinar.png"));
    }

    /**
     * 注册法杖物品(必须在 {@link #init()} 之后调用,ItemElinarWand 构造要引用 rod/cap)。
     * 物品注册为 elinarcurios 自己的 Item,创造标签/NEI 可见。
     */
    public static void registerItem() {
        if (!isAvailable()) {
            return;
        }
        ItemElinarWand.instance = new ItemElinarWand();
        GameRegistry.registerItem(ItemElinarWand.instance, "ElinarWand");
    }

    /** 生成法杖物品(新杖 NBT 无 vis 键,到手 vis = 0)。 */
    public static ItemStack createWand() {
        return ItemElinarWand.instance.wand.copy();
    }
}
