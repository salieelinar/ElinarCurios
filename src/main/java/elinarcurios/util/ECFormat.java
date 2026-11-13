package elinarcurios.util;

/**
 * ElinarCurios 自用的数字格式化。
 *
 * <p>
 * <b>为什么不用 GTNHLib 的 {@code NumberFormatUtil}</b>（2026-09-12 实测崩溃）：
 * 那个类在 GTNH 2.8.4 自带的 GTNHLib <b>0.7.10</b> 里<b>不存在</b>（它是 GT5U 5.09.54
 * 依赖的新版才加的），而整合包运行时提供的是 0.7.10 —— 引用的结果是进入世界前
 * {@code NoClassDefFoundError} 直接崩客户端。为了千分位好看去要求玩家升级一个基础库
 * 不划算，所以这里用 JDK 自带的方法实现，零依赖。
 *
 * <p>
 * 输出形如 {@code 8,589,934,592,000}；负数与 0 都正确。
 */
public final class ECFormat {

    private ECFormat() {}

    /** 十进制 + 千分位。 */
    public static String number(long value) {
        return String.format("%,d", value);
    }

    /**
     * 大数值的紧凑写法：绝对值 ≥ 10^15 时用科学计数（如 {@code 8.590e18}），
     * 否则退化为千分位十进制。用于 tooltip / GUI 里那种「一眼看量级」的场合。
     */
    public static String compact(long value) {
        return compactAbove(value, 1_000_000_000_000_000L);
    }

    /**
     * 自定义切换阈值的紧凑写法：绝对值 ≥ {@code scientificThreshold} 时用科学计数（如 {@code 1.182e+12}），
     * 否则千分位十进制。给 GUI 读数用 —— 屏幕宽度固定，数字位数不能无限长（2026-10-18 芷若定的 10^12 阈值）。
     */
    public static String compactAbove(long value, long scientificThreshold) {
        final long abs = value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        if (abs >= scientificThreshold) {
            return String.format("%.3e", (double) value);
        }
        return number(value);
    }
}
