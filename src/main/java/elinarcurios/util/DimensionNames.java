package elinarcurios.util;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.world.World;

/**
 * 维度 ID → 中文名（{@code /Elinar infiore} 这类服务端聊天输出用）。
 *
 * <p>
 * 服务端 {@code StatCollector} 的语言固定是 en_US，拿不到客户端汉化，所以这里直接写死表。
 * 号段取自 GTNH 2.9.0-RC-2 实例的真实配置文件（不是猜的）：
 * <ul>
 * <li>{@code config/Galacticraft/core.conf}：Moon 28 / OverworldOrbit 27 / OverworldOrbitStatic 26 / Overworld 0</li>
 * <li>{@code config/Galacticraft/planets.conf}：Mars 29 / Asteroids 30</li>
 * <li>{@code config/GalaxySpace/dimensions.conf}：其余行星与轨道（25~88）</li>
 * <li>{@code config/TwilightForest.cfg}：7；{@code config/ExtraUtilities.cfg}：Deep Dark 100</li>
 * </ul>
 * 中文名取整合包自带汉化（{@code config/txloader/load/.../zh_CN.lang} 里的
 * {@code dimension.*.name} 与 GalaxySpace 成就名，如「鸟神星」「鲸鱼座T星E」「织女一B」）。
 * 表里没有的维度退化成 {@code WorldProvider.getDimensionName()} 的原始名字。
 */
public final class DimensionNames {

    private static final Map<Integer, String> NAMES = new HashMap<>();

    static {
        // 原版 / 常驻
        NAMES.put(-1, "下界");
        NAMES.put(0, "主世界");
        NAMES.put(1, "末地");
        NAMES.put(7, "暮色森林");
        NAMES.put(100, "深层空间");

        // Galacticraft
        NAMES.put(26, "主世界轨道（静态）");
        NAMES.put(27, "主世界轨道");
        NAMES.put(28, "月球");
        NAMES.put(29, "火星");
        NAMES.put(30, "小行星带");

        // GalaxySpace 行星
        NAMES.put(25, "鸟神星");
        NAMES.put(31, "半人马座α星Bb");
        NAMES.put(32, "巴纳德C");
        NAMES.put(33, "柯伊伯带");
        NAMES.put(35, "木卫二");
        NAMES.put(36, "木卫一");
        NAMES.put(37, "水星");
        NAMES.put(38, "火卫一");
        NAMES.put(39, "金星");
        NAMES.put(40, "火卫二");
        NAMES.put(41, "土卫二");
        NAMES.put(42, "谷神星");
        NAMES.put(43, "木卫三");
        NAMES.put(44, "土卫六");
        NAMES.put(45, "木卫四");
        NAMES.put(46, "天卫四");
        NAMES.put(47, "海卫八");
        NAMES.put(48, "海卫一");
        NAMES.put(49, "冥王星");
        NAMES.put(51, "天王星");
        NAMES.put(71, "木星");
        NAMES.put(74, "海王星");
        NAMES.put(77, "土星");
        NAMES.put(81, "巴纳德E");
        NAMES.put(82, "巴纳德F");
        NAMES.put(83, "妊神星");
        NAMES.put(84, "织女一B");
        NAMES.put(85, "鲸鱼座T星E");
        NAMES.put(86, "天卫五");

        // GalaxySpace 轨道 / 空间站维度
        NAMES.put(34, "火星轨道");
        NAMES.put(52, "天王星轨道");
        NAMES.put(53, "天王星轨道（静态）");
        NAMES.put(72, "木星轨道");
        NAMES.put(73, "木星轨道（静态）");
        NAMES.put(75, "海王星轨道");
        NAMES.put(76, "海王星轨道（静态）");
        NAMES.put(78, "土星轨道");
        NAMES.put(79, "土星轨道（静态）");
        NAMES.put(80, "火星轨道（静态）");
        NAMES.put(87, "金星轨道");
        NAMES.put(88, "金星轨道（静态）");
    }

    private DimensionNames() {}

    /** 维度 ID 的中文名；未知 ID 返回 null。 */
    public static String ofId(int dimensionId) {
        return NAMES.get(dimensionId);
    }

    /** 世界所属维度的中文名；未知维度退化成原版维度的英文名。 */
    public static String of(World world) {
        if (world == null || world.provider == null) return "未知维度";
        String name = NAMES.get(world.provider.dimensionId);
        if (name != null) return name;
        try {
            String fallback = world.provider.getDimensionName();
            if (fallback != null && !fallback.trim()
                .isEmpty()) return fallback;
        } catch (Throwable ignored) {
            // WorldProvider 有第三方实现，取名字失败不该让命令整个报错
        }
        return "未知维度";
    }
}
