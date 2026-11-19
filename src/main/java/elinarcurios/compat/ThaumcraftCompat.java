package elinarcurios.compat;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.registry.GameRegistry;
import elinarcurios.item.ItemElinarWand;
import elinarcurios.player.WandPoolTickHandler;
import thaumcraft.api.wands.WandCap;
import thaumcraft.api.wands.WandRod;

/**
 * Thaumcraft 软联动:「初心无垠法杖」(rod/cap tag = "elinar")。
 *
 * <p>
 * 2026-11-19 起 vis 改由「玩家共享魔力池」承载(见 {@code elinarcurios.player.WandPool}):
 * - 杖本身不再限制容量,{@code WAND_CAPACITY} 只是杖杆的标称元数据;真正的池上限是
 * {@code WandPoolConstants.CAPACITY}(9.2e18 raw);
 * - 同一个玩家的所有天玄之杖共用一个池,首次使用永久绑定;
 * - 新杖到手池值为 0,吸多少用多少。
 *
 * <p>
 * 参考:Avaritia 的 Lucrum.java(同款组装方式,但去掉了 CheatyWandUpdate,
 * 且法杖注册为 elinarcurios 自己的 Item,创造标签/NEI 可见)。
 */
public final class ThaumcraftCompat {

    /** 杖杆标称容量(外部单位,×100 = rod 的 int 容量)。共享池的真正上限在 WandPoolConstants.CAPACITY。 */
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

        // 天玄之杖共享魔力池：登录时推一次完整池值，之后每 tick 合并脏标记推一次。
        // 只在这里注册（TC 专属），避免无 TC 环境加载 WandPool 的 TC 类型。
        FMLCommonHandler.instance()
            .bus()
            .register(new WandPoolTickHandler());

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
