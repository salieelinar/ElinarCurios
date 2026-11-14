package elinarcurios.item;

import java.util.List;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import elinarcurios.ElinarCurios;
import elinarcurios.compat.ThaumcraftCompat;
import elinarcurios.util.FabulousText;
import thaumcraft.common.items.wands.ItemWandCasting;

/**
 * 天玄之杖 —— 注册为 elinarcurios 自己的物品(而非复用 TC 的 itemWandCasting),
 * 因此创造标签 / NEI 物品列表 / 配方输出全部可见(TC 的 getSubItems 是硬编码枚举,
 * 自定义 rod/cap 组合不会自动出现在 TC 物品列表里)。
 *
 * 设计(无作弊、新手友好):
 * - 容量 1000 万(内部 ×100 = 10 亿 vis,int 安全),任何充能/消耗都摸不到上限;
 * - 不挂 IWandRodOnUpdate,无自动回满;
 * - 新杖 NBT 无 vis 键,到手 vis = 0,吸多少用多少;
 * - 显示名「天玄之杖」彩虹渐变动画(FabulousText,无尽背包同款);
 * - tooltip 烫金「寰宇之力 握于掌心」。
 *
 * 先例:ThaumicHorizons 的 ItemWandCastingDisposable 同款继承方案;
 * 纹理照搬 Avaritia 的水晶缠绕杖(wand_rod_neutronium / wand_cap_crystal,见 LICENSES/LICENSE-MIT-Avaritia.txt)。
 */
public class ItemElinarWand extends ItemWandCasting {

    public static ItemElinarWand instance;

    /** 组装好的默认法杖(空 vis)。 */
    public ItemStack wand;

    public ItemElinarWand() {
        this.maxStackSize = 1;
        this.setMaxDamage(0);
        this.setHasSubtypes(true);
        this.setCreativeTab(ElinarCurios.creativeTab);
        this.wand = new ItemStack(this);
        this.setCap(this.wand, ThaumcraftCompat.wandCapElinar);
        this.setRod(this.wand, ThaumcraftCompat.wandRodElinar);
    }

    /** 「天玄之杖」彩虹渐变动画名(服务端返回静态文本,安全)。 */
    @Override
    public String getItemStackDisplayName(final ItemStack stack) {
        return FabulousText.makeFabulous(StatCollector.translateToLocal("item.elinarcurios.wand.name"));
    }

    @SideOnly(Side.CLIENT)
    @Override
    public void getSubItems(final Item item, final CreativeTabs tab, final List list) {
        list.add(this.wand);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void addInformation(final ItemStack stack, final EntityPlayer player, final List tooltip,
        final boolean advanced) {
        tooltip.add(StatCollector.translateToLocal("item.elinarcurios.wand.tooltip"));
        super.addInformation(stack, player, tooltip, advanced);
    }
}
