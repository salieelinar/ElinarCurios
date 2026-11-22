# Elinar Curios 开发与构建指南(交接文档)

> 本文档记录本项目的完整上下文,供下次继续开发/细化时直接参考。

---

## 当前版本

**v1.0.1**（2026-11-20）。版本号由 git tag 决定：先提交、打 tag、再构建，产物是 `build/libs/ElinarCurios-<版本>.jar`；没打 tag 就构建会带 `-dirty` 后缀。本版 = dev 线整批合并：天玄之杖（原版低模 + 火把光 + 共享魔力池 + 坩埚分解）、物品 tooltip 末行模组标识、束流系列配方换黑洞种子。

**tag 不能带空格**：正式 tag 用 `v1.0.1`；显示名/jar 名需要别的写法时，构建前设 `$env:VERSION = "v1.0.1"`（gtnhgradle 读 `System.getenv("VERSION")` 覆盖 git 版本，`mcmod.info` 与 jar 名都会跟着变）。

**dev 分支（2026-11-16 起）**：日常改动提交到 `dev` 分支，dev 包版本 = `v1.0.2-dev-<git short sha，12 位>`（例 `ElinarCurios-v1.0.2-dev-<sha>.jar`）。dev 包**不发正式 Release**，统一发到**固定 dev 页**（tag `dev-build`、页面标 Pre-release，附件固定名 `ElinarCurios-dev-build.jar` 每次覆盖；https://github.com/salieelinar/ElinarCurios/releases/tag/dev-build ），这样别人在 Releases 页无需登录就能下载，也不会顶掉 main 的 Latest 正式版。dev 上可以**连续攒多个功能**，每个功能单独出 dev 包测；等她确认「这一批可以合」时再一次性把 dev 合并到 `main`、打正式 tag 并发正式 Release（不逐个功能合并）。下一正式版从 `1.0.2` 开始。dev 构建前必须 commit（工作区干净），版本里的 sha 才是当前代码。

变更记录不再手写在本文档里：一个 tag 对应一组提交，`git log v<旧版本>..v<新版本> --oneline` 就是这一版的更新日志。

**一条历史教训（勿重蹈）**：曾把背包容量往 long（`Long.MAX_VALUE`）升级，实测游戏卡死 —— 1.7.10 的容器同步协议对单包大小有硬限制。容量与网络协议**保持 int**，21.4 亿对临时存储足够。

---

## 0. 项目一句话概况

一个 **Minecraft 1.7.10(GTNH 工具链)独立 mod**(**Elinar Curios**,原名 Infinithpackbag)**:无尽背包 = Avaritiaddons 无尽箱子的存储(243 格 × Integer.MAX_VALUE)+ AdventureBackpack 的穿戴体验(背上穿戴、Dragon 外观与 buff、快捷键打开)。**核心内容零 mod 依赖**(不引用任何 AdventureBackpack2 类,已用字节码扫描验证);GT5U(机器/电池)与 SpiceOfLife(午餐盒进食协议)为**功能级依赖**,均有 `isModLoaded` 注册守卫,缺失时只少对应物品、其余照常。

---

## 1. 关键路径速查

| 路径 | 说明 |
|---|---|
| `D:\Agent\GTNH\ElinarCurios\` | **本项目(工作区)** |
| `D:\Agent\GTNH\FullPack\` | **GTNH 全部源码快照**(326 个仓库,参考素材都在这) |
| `F:\AI\toolchain\` | 本机构建工具链(JDK 25、Gradle 9.4.0、dl.js 下载器;JDK 在 `java\` 子目录,GTNH 依赖在 `minecraft\gtnh\`) |
| `dist\ElinarCurios-*.jar` | 最终可玩产物(复制进整合包 mods 即可) |
| `build\libs\` | gradle 产物(dev/sources/reobf 三种) |

### GTNH 参考素材位置(FullPack 内)

| 素材 | 路径 | 我们移植了什么 |
|---|---|---|
| Avaritiaddons(无尽箱子) | `FullPack\src\Avaritiaddons-master\` | 存储核心、GUI、网络 4 包、K/M/B 渲染 |
| AdventureBackpack2(穿戴) | `FullPack\src\AdventureBackpack2-master\` | 穿戴属性模型、Dragon buff、背部渲染思路、Dragon 纹理 |
| Minecraft-Backpack-Mod(Eydamos) | `FullPack\src\Minecraft-Backpack-Mod-master\` | 页签机制参考(已弃用,见 §5.8) |
| GTNH 整合包主仓库 | `FullPack\src\GT-New-Horizons-Modpack-master\` | config(如 Backpack.cfg、adventurebackpack.cfg)参考 |
| GTNH 打包系统 | `FullPack\src\DreamAssemblerXXL-master\` | manifest(各 mod 版本清单)参考 |
| GTNH 核心脚本 | `FullPack\src\NewHorizonsCoreMod-master\` | dreamcraft 极值配方写法参考 |
| NH-Utilities(Kami神环) | `D:\Agent\GTNH\NH-Utilities-1.7.0\`(GPL-3.0) | Kami 无敌/防死亡/永饱的**原理**参考(事件重实现,未复制代码) |

---

## 2. 工程结构(改哪里找哪里)

```
src/main/java/elinarcurios/
├── ElinarCurios.java          # @Mod 主类:GUI ID、创造页签、网络通道
├── CommonProxy.java              # 物品注册、GUI 注册、全部消息注册(共 14 种)
├── Config.java                   # 配置:dragonBackpackRegen/Damage(药水等级)
├── common/Reference.java         # modid/版本/代理类名
├── item/ItemInfiniteBackpack.java# 物品本体 + NBT 根标签 + buff 开关存储(全静态助手)
├── block/
│   ├── BlockPeaceTotem.java      # ★和平图腾方块(细柱火炬模型照抄卓越火炬,发光、自带 TileEntity)
│   ├── ItemBlockPeaceTotem.java  # 图腾物品(和平~宁静~ + 范围 tooltip)
│   ├── BlockEternalChest.java    # 永恒箱子方块(紫色板材+紫红镶边,便携三件套)
│   ├── ItemBlockEternalChest.java
│   ├── BlockVoidChest.java       # ★虚界百宝箱方块(继承永恒箱子,金色镶边,固化 GUI)
│   └── ItemBlockVoidChest.java   # 虚界箱子物品(固化 tooltip)
├── tile/
│   ├── TileEntityPeaceTotem.java # ★图腾 TileEntity:进/出区块时注册/注销坐标
│   ├── TileEntityEternalChest.java # 永恒箱子 TE:全量存储 + 跨页合并 + NBT(getTagRoot 可覆写)
│   └── TileEntityVoidChest.java  # ★虚界箱子 TE:继承永恒箱子,solidify() 固化 + VoidChestData 根键
├── inventory/
│   ├── InventoryInfiniteBackpack.java  # ★存储核心:243格、int上限、合并逻辑、NBT读写
│   ├── ContainerInfiniteBackpack.java  # ★容器:slotClick/mergeItemStack 大堆叠处理
│   ├── ContainerMultiPage.java         # 多页大容量容器公共父类(背包/箱子共用)
│   └── ContainerEternalChest.java      # ★永恒/虚界箱子共用容器(refreshAndResyncAll 全量刷新)
├── gui/
│   ├── GuiInfiniteBackpack.java  # ★GUI(500×276)+穿上/脱下按钮+buff开关按钮
│   ├── GuiEternalChest.java      # ★箱子 GUI 基类(12 页签,标题 key 可覆写)
│   ├── GuiVoidChest.java         # ★虚界箱子 GUI(继承,右上角「固化」按钮)
│   └── GuiMiniButton.java        # 扁平小按钮控件
├── client/
│   ├── ClientProxy.java          # 快捷键注册、背部渲染注册
│   ├── KeyBindings.java          # 快捷键定义(默认 B)
│   ├── KeyInputHandler.java      # 快捷键 → OpenBagPacket
│   ├── BackRenderHandler.java    # ★背部渲染(RenderPlayerEvent,自绘)
│   ├── model/ModelBackpack.java  # 背上 3D 模型(128×64 UV,随身体旋转)
│   └── RenderItemInfinity.java   # K/M/B 大数量渲染(移植自 Avaritiaddons)
├── player/
│   ├── BagProperty.java          # 背上背包的存储(玩家扩展属性,存档持久化)
│   └── PlayerEventHandler.java   # 属性注册、tick buff、死亡掉落、登录同步
├── network/                      # 全部网络消息(服务端权威)
│   ├── InfiniteBagSlotSync / Click / Confirmation / SyncAllSlots  # 大堆叠同步四件套(移植)
│   ├── OpenBagPacket             # 打开背包(手持/穿戴)
│   ├── EquipUnequipBagPacket     # 穿上/脱下
│   ├── ToggleBuffPacket          # 客户端→服务端 buff 开关
│   ├── BuffStatePacket           # 服务端→客户端 buff 状态回传
│   ├── SyncBagPacket             # 背上背包同步到客户端(渲染用)
│   ├── SwitchPagePacket          # 切页(仅点页签时发;打开 GUI 时不再发它!)
│   ├── RequestCurrentPagePacket  # 打开 GUI 时查询当前页(纯查询,绝不切页)
│   ├── CurrentPagePacket         # 服务端→客户端当前页回传(高亮刷新)
│   └── SolidifyVoidChestPacket   # ★虚界箱子固化(C→S 空包,服务端执行 + 全量刷新)
└── util/
    ├── BagUtils.java             # 穿戴/脱下/查询/同步 统一入口
    ├── DragonBuffs.java          # ★Dragon 四 buff 应用/移除(按开关)
    └── PeaceTotemHandler.java    # ★图腾防刷怪逻辑:静态坐标注册表 + CheckSpawn 拦截 + 末影人传送拦截
```

资源:

```
src/main/resources/
├── mcmod.info                    # 版本/描述(构建时注入 modId 等 token)
├── assets/elinarcurios/
│   ├── lang/en_US.lang, zh_CN.lang   # 全部文案(物品名/按钮/buff名/提示)
│   ├── textures/gui/infiniteBackpack.png   # GUI 背景(复制自 Avaritiaddons,MPL)
│   ├── textures/items/infiniteBackpack.png # 物品图标(16×16 程序生成)
│   ├── textures/blocks/peaceTotem.png      # 图腾贴图(16×16 程序生成,十字渲染用)
│   └── textures/models/dragonBackpack.png  # 背部 Dragon 皮肤(复制自 AB2,LGPL)
```

根目录:`build.gradle`(GTNHGradle 约定插件)、`gradle.properties`(modId/modGroup 等)、`dependencies.gradle`(空)、`repositories.gradle`(空)、`settings.gradle`(gtnhsettingsconvention 2.0.29)、`LICENSES/`(LGPL-3.0 / MPL-2.0 / MIT 许可证全文与 NOTICE)。

---

## 3. 构建方法(本机实测可用)

### 3.1 工具链(本机已备好,2026-08 升级到官方现代配置)

| 组件 | 位置 |
|---|---|
| JDK 25(Microsoft OpenJDK 25.0.4 LTS) | `F:\AI\toolchain\java\jdk25\jdk-25.0.4+7` |
| JDK 17(Temurin 17.0.20,仅兼容遗留) | `F:\AI\toolchain\java\jdk17\jdk-17.0.20+8` |
| JDK 8(Temurin 8u442,**不再需要**,历史参考) | `F:\AI\toolchain\java\jdk8\jdk8u442-b06` |
| Gradle | wrapper 9.4.0(见下"网络特殊性"的预置方法) |
| 构建插件 | `gtnhsettingsconvention 2.0.29` + `gtnhconvention` + RetroFuturaGradle 2.0.2(构建时从 GTNH maven 拉取) |

> 说明(重要,纠正旧认知):
> - **Gradle 守护进程跑在 JDK 25 上**,由 `gradle/gradle-daemon-jvm.properties` 的 `toolchainVersion=25` 指定(官方 2026 标准,GTNH 全组织 217 个仓库全部如此);
> - **mod 字节码仍为 Java 8**(class major 52,已验证)——这是编译目标,由约定插件处理,**不需要安装 JDK 8**;
> - 约定插件 2.0.29 本身要求运行 JVM ≥ 25,用旧 JDK 跑会直接报 "requires at least JVM runtime version 25";
> - `enableModernJavaSyntax` 保持 `false`(纯 Java 8 语法);如想用新语法可选 `jabel`(语法级,字节码仍 8)。

### 3.2 标准构建命令(在项目根目录执行)

```powershell
$env:JAVA_HOME = "F:\AI\toolchain\java\jdk25\jdk-25.0.4+7"
.\gradlew.bat spotlessApply build --no-daemon --console=plain
```

- 不需要再设 `GRADLE_OPTS`/`org.gradle.java.installations.paths`(daemon 直接跑在 JDK 25 上,JDK 8 工具链已不需要);
- `spotlessApply` 自动格式化代码(必跑,否则 `spotlessJavaCheck` 会挂);
- 产物:`build\libs\ElinarCurios-v<版本>.jar`(reobf,可玩版);
- 复制到 `dist\` 即为交付物;
- 显示名带空格时（如 `v1.0.0 c7`）：构建前加 `$env:VERSION = "v1.0.0 c7"`，tag 仍用无空格的 `v1.0.0c7`。
- dev 分支构建：commit 后 `$env:VERSION = "v1.0.1-dev-$((git rev-parse --short=12 HEAD))"`，产物形如 `ElinarCurios-v1.0.1-dev-fd3ecfb944ed.jar`；
- 统一发到固定 dev 页（tag `dev-build`，Pre-release，不抢正式版 Latest）：
  `Copy-Item "dist\ElinarCurios-v1.0.1-dev-<sha12>.jar" "dist\ElinarCurios-dev-build.jar" -Force`
  `gh release upload dev-build "dist\ElinarCurios-dev-build.jar" --clobber`
  `gh release edit dev-build --prerelease --title "Dev Build" --notes "开发版，仅供测试；版本 v1.0.1-dev-<sha12>，commit <sha>，内容 …"`
  （固定页首次已建：https://github.com/salieelinar/ElinarCurios/releases/tag/dev-build ；tag 停在首次创建 commit，页面说明里写当前版本/commit。）

#### 3.2.1 受限环境(DSH sandbox,workspace-write)下构建(2026-09-22 实测可用)

sandbox 不允许写工作区之外的 `C:\Users\78296\.gradle`,而 `gradlew` 必须写它的 home(锁文件/缓存)→ 已把整个 Gradle 首页复制到 **`D:\Agent\.gradle-home`**(945 MB,已进 `.gitignore`),改用它构建:

```powershell
$env:JAVA_HOME = "F:\AI\toolchain\java\jdk25\jdk-25.0.4+7"
$env:GRADLE_USER_HOME = "D:\Agent\.gradle-home"
.\gradlew.bat spotlessApply build --no-daemon --console=plain
```

- 该 home 下的 `gradle.properties` 已写死两条:①`org.gradle.java.installations.paths` 指向本机 `java\jdk8|jdk17|jdk25`;②`org.gradle.vfs.watch=false`(sandbox 拒绝文件监视线程,报 `Couldn't open current thread, error = 5`);
- **JDK 8 工具链是 `compileApiJava` 必需的**(RFG 用 Java 8 出 1.7.10 字节码)。不指路的话 Gradle 会尝试从 foojay/GitHub provision 一个 JDK 8 并**超时失败**(`Cannot find a Java installation ... languageVersion=8`);
- **不要加 `--offline`**:`gtnhconvention` 插件要用 Blowdryer 拉共享配置(spotless 等),离线会以 `SocketTimeoutException: timeout` 挂在「applying plugin」阶段;
- 命令行里写 `-Dorg.gradle.vfs.watch=false` 会被 gradlew 当成 task 名(PowerShell 拆参报 `Task '.gradle.vfs.watch=false' not found`),只能写进 `gradle.properties`;
- 首次编译约 30 秒~1 分钟,之后增量 10 秒左右。

### 3.3 版本号机制(重要)

- 版本号由 **git describe** 生成(如 `v1.0.0-master.7+9f3a469bf9`);
- 工作区有未提交改动时版本带 `-dirty` 后缀;
- **改代码后记得 `git add -A && git commit`,再构建**才能得到干净版本;
- 大版本升级:打新 tag(如 `git tag v1.1.0`)。
- 项目当前 tag:`v1.0.0c`(附注 tag;`v1.0.0` 是轻量 tag,`v1.0.0b`/`v1.0.0c` 为附注)。

### 3.4 本机网络特殊性(构建/下载必读)

- **Java TLS 直连可用**(GitHub、GTNH maven、cleanroom、maven central、plugins.gradle.org 都通),所以 Gradle 构建本身不需要代理;
- **services.gradle.org 本机超时**(wrapper 下载 10 秒超时,实测 Read timed out)→ Gradle 发行包需**预置**:从腾讯镜像 `https://mirrors.cloud.tencent.com/gradle/gradle-9.4.0-bin.zip`(快)下载,放到 `C:\Users\78296\.gradle\wrapper\dists\gradle-9.4.0-bin\<hash>\` 下并 `touch gradle-9.4.0-bin.zip.ok`;
- **api.adoptium.net 下载极慢/卡死** → JDK 25 用微软 CDN:`https://aka.ms/download-jdk/microsoft-jdk-25.0.4-windows-x64.zip`(实测 12 秒下完 220MB);
- **PowerShell 的 Invoke-WebRequest / .NET 网络不可用**(TLS 层被掐);
- **node 的系统 DNS 解析不可用**,但直连 IP + SNI 路由可用 → 用 `dl.js`:
  ```powershell
  node F:\AI\toolchain\scripts\dl.js <https-url> <输出文件>
  ```
  (内部按 hostname 查 IP 表 + 跟随重定向;需要新 host 时把 IP 加进文件顶部的 `FALLBACK_IPS`,IP 用 `Resolve-DnsName -Name <host> -Type A` 查);
- 用户的 HTTP 代理在 `127.0.0.1:5210`(HTTP/HTTPS)与 `5220`(SOCKS),需要时可用,但不稳定(用户自己开关)。

### 3.5 语法与格式约束

- 目标 **Java 8**(`enableModernJavaSyntax = false`)——**禁止** switch 表达式、`instanceof X x` 模式匹配、var 等新语法(踩过坑);
- 代码风格由 spotless 强制(4 空格、import 分组等),写完跑 `spotlessApply` 即可;
- 所有对其它 mod 的可选引用都已移除,新增功能时**默认不要引入对 GTNH 其它 mod 的硬依赖**(如需引用,用类名字符串/GameRegistry 查找等软方式)。
  - **例外条款(2026-09-12 芷若拍板)**:做 GT 机器这类**本质依赖 GT5U** 的功能时允许硬依赖。用户原话:「我感觉没必要零依赖,毕竟已经是给 gtnh 开发的了」。此时可直接 `extends` GT5U 基类、在 MTE 构造器里注册机器,**不必**再套类加载隔离/代理层。使用硬依赖时需要同步三处:`dependencies.gradle` 的 `compileOnly` 声明、`mcmod.info` 的 `dependencies`(`after:gregtech`)、以及本节说明。
  - **`transitive = false` 的连带坑**:声明 GT5U 时若写 `transitive = false`,则 GT5U 的 api 依赖**全都不在编译类路径上**,用到哪个就得自己补 `compileOnly`。实测最少需要三个:`GTNHLib`(数字格式化 `NumberFormatUtil`)、`Applied-Energistics-2-Unofficial`(GT5U 的 `MetaTileEntity implements appeng.api.crafting.ICraftingIconProvider`,缺了会报「无法访问 appeng...」)、`ModularUI2`(MUI2 GUI)。版本照抄 GT5U 自己的 `dependencies.gradle`。
  - **★ 引用第三方 mod 的 `compileOnly` 一律要 `rfg.deobf(files(...))` 包起来(2026-09-22 午餐盒踩坑)**:玩家实例 `mods\` 里的 jar 是**运行时(srg 名)**版,直接 `compileOnly files(...)` 交给 javac 的话,对方覆写的原版方法显示为 `func_xxx` —— 于是继承它时既**不能 `@Override`** 也**不能 `super.xxx(...)`**(报「方法不覆写超类方法」/「找不到符号」);虽然 reobf 阶段仍会把名字转对,但源码里无法标注也无法调 super,极易写出静默失效的代码(方法名没转对 = 原版永远不调用它)。写法见 `dependencies.gradle` 里 SpiceOfLife / AppleCore 两行,细节见 §5.20。

---

## 4. 常见修改点速查(直接改哪里)

| 想改什么 | 改哪个文件 | 备注 |
|---|---|---|
| 格子数量(243) | `ItemInfiniteBackpack.STORAGE_SLOTS` | 同时注意 GUI/容器布局代码里写死的 243/279 |
| 页数(10) | `ItemInfiniteBackpack.PAGE_COUNT` | 分页存储,`pages` NBT,每页独立;按钮在 `GuiInfiniteBackpack.initGui`(id 100+i) |
| 每格上限(int) | `InventoryInfiniteBackpack.getInventoryStackLimit()` | 已是 MAX_VALUE |
| 快捷键(默认 B) | `client/KeyBindings.java` | 改键位常量 |
| buff 效果 | `util/DragonBuffs.java` | 当前仅夜视;如加回再生/力量参考 AB 的 itemDragon |
| 无敌/防死亡 | `player/PlayerEventHandler.java` | Kami 神环移植(LivingAttack/Hurt/Death 事件,HIGHEST 优先级,常驻不可关;永饱已于 0ea053c 移除) |
| buff 开关按钮文案 | `lang/*.lang` 的 `button.elinarcurios.buff.nv` | 按钮布局在 `GuiInfiniteBackpack.initGui`(BUFF_KEYS 决定按钮数量) |
| 穿上/脱下按钮位置 | `GuiInfiniteBackpack.initGui`(0 号按钮坐标) | 当前右上角 (xSize-56, 3) 52×13 |
| 物品图标 | `textures/items/infiniteBackpack.png`(16×16) | 程序生成的,可替换 |
| 背部外观 | `textures/models/dragonBackpack.png` + `model/ModelBackpack.java` | UV 布局与 AB 模型一致 |
| 背包中文名/提示 | `lang/zh_CN.lang` | 键见 §5.1 |
| 死亡掉落行为 | `player/PlayerEventHandler.onPlayerDeath` | 当前:掉落+清除属性 |
| 禁入维度 | 目前没做(AdventureBackpack 禁了维度 55;如需可加) | — |
| 背包合成配方 | 当前没有(创造页签获取) | 见 §5.9 思路 |
| 和平图腾防刷怪范围 | `Config.java` 的 `peaceTotemRangeHorizontal/Vertical` | 默认水平 64/垂直 32,椭球判定 |
| 和平图腾合成配方 | **已删除(2026-08)**,物品经任务书/奖励发放 | 原:8 钻石块围 1 火把 |
| 和平图腾贴图 | `textures/blocks/peaceTotem{,.Top,.Base}.png` | 程序生成(PIL),三面贴图照抄卓越火炬布局,桩身深紫 |
| `/Elinar` 命令 | `command/CommandElinar.java`（子命令/输出/边界检测） | infiore/setore/help,见 §5.27;维度中文名在 `util/DimensionNames.java` |
| 采掘工厂并行立即生效 | `machine/MTEComprehensiveMiningPlant.applyConfiguredParallel()` | GUI 改并行仍是“下次启动生效”,命令才是立即生效 |
| 采掘工厂 AE 倾倒频率 | `Config.infMinerFlushSeconds` + `MTEComprehensiveMiningPlant.shouldFlushToAe()` | 秒;0=实时,默认 120,最大 86400,改完重启 |

---

## 5. 关键设计备忘(下次细化的起点)

### 5.1 语言键清单
`item.infiniteBackpack.name` / `container.infiniteBackpack` / `itemGroup.elinarcurios` / `keys.elinarcurios.category` / `keys.elinarcurios.open` / `button.elinarcurios.equip|unequip` / `button.elinarcurios.buff.nv|fr|reg|str` / `elinarcurios:messages.already.equipped` / `item.elinarcurios.tooltip.*`

### 5.2 NBT 结构(背包物品的 tag)
```
elinarcurios (compound)
├── pages: {
│     "0".."9": { inventory: [ { Slot:short, id, Damage, Count, intCount:int, tag? }, ... ] }  # 每页 243 格
│   }
├── currentPage: int   # 记忆当前页(0-9,关闭重开保持)
└── buffs: { nightVision:bool }
```
- `intCount` 是关键:原版 NBT 的 Count 只有 short,大堆叠靠 intCount 存真实数量。
- 旧档迁移:无 `pages` 的旧格式 `inventory` 自动迁入第 0 页(`InventoryInfiniteBackpack.migrateLegacyFormat`)。
- 换页 = 保存当前页 → 清空 contents 数组 → 加载新页(**同一数组**,容器槽引用不变);服务端全量 `InfiniteBagSyncAllSlots` 重同步 + `CurrentPagePacket` 回传确认。
- 打开 GUI 时客户端发 **`RequestCurrentPagePacket`(纯查询,服务端只回 `CurrentPagePacket`,绝不切页)**;`SwitchPagePacket` 只用于玩家实际点击页签。
- **教训(commit 95b1ef6):** 曾用「打开 GUI 时发 `SwitchPagePacket(客户端当前页)` 请求确认高亮」,但穿戴背包的客户端副本只在 equip/unequip/登录时同步,副本页滞后 → 服务端收到旧页码会真的执行 switchPage,**把记忆页码覆写回 0**(表现为穿上后每次重开都回到第一页)。手持正常是因为手持栈随窗口同步、副本不旧,同页 no-op 掩盖了问题。

### 5.3 大堆叠网络协议(为何有 4 个包)
原版容器同步(0x32 窗口物品)数量上限 32767 → 自定义协议:
- `SlotSync`(服务端→客户端,逐格,VarInt 传 int 数量)
- `Click`(客户端→服务端,自定义点击,防抖/事务)
- `Confirmation`(双向事务确认,失败回滚 + `SyncAllSlots` 全量重发)
- 全部消息 ID 见 `CommonProxy.preInit`,加新消息时按顺序追加 ID。

### 5.4 穿戴数据流
`BagProperty`(玩家扩展属性)→ 服务端权威 → `SyncBagPacket` 同步给客户端(equip/unequip/登录时)→ 客户端用于背部渲染 + 快捷键判断。GUI 从穿戴打开时,`GuiHandler` 的 `GUI_ID_BAG_WEARING` 读取属性里的背包栈。

- **教训(下界丢背包,commit `1fee75e`):** 1.7.10 客户端**换维度时重建 `EntityClientPlayerMP`**(`Minecraft.setDimensionAndSpawnPlayer` 里 `new EntityClientPlayerMP`,已用反编译源码确认)——扩展属性挂在实体上,新实体只有空的 `BagProperty`;服务器端实体不重建、属性完好。表现:进下界/末地后背上模型消失、按 B 无反应,重进游戏(`PlayerLoggedIn` 重新同步)又恢复。
- **修复:** `KeyInputHandler.onPlayerTick` 检测 `Minecraft.thePlayer` 实体实例变化 → 发 `RequestWornBagPacket`(C→S,空包)→ 服务端 `syncWornBagToClient` 回传 `SyncBagPacket` 恢复。`SyncBagPacket` 客户端处理器优先按 entityId 查实体,失败回退到 `mc.thePlayer`(新实体可能尚未进世界)。
- 客户端实体重建的触发点:换维度、死亡重生(被 Kami 拦住的死亡不会重建)。

### 5.5 buff 数据流
`PlayerEventHandler.onPlayerTick`(**注意:FML 事件,必须注册在 FMLCommonHandler bus 上**,曾因只注册 Forge bus 导致 buff 不生效)→ `DragonBuffs.apply(player, wornBag)` 按 NBT 开关应用 → 关闭时 `ToggleBuffPacket` 触发服务端移除并回传 `BuffStatePacket` 刷新按钮。当前 buff:夜视(默认开,`DragonBuffs` 加药水效果)、幽灵(默认关,不产生药水效果,走 `util/GhostMode` 的 AI 拦截)。新增 buff 时三处同步加:`ItemInfiniteBackpack.BUFF_KEYS` + `BUFF_DEFAULTS`(缺键时的默认值,**新开关一律默认 false**,否则老存档升级后行为会变)+ `BUFF_LANG_KEYS`(GUI 按钮文案);药水类效果再在 `DragonBuffs.apply/removeBuff` 加逻辑。

### 5.10 Kami 无敌(常驻,穿戴即生效)
移植自 NH-Utilities Kami Divine Ring(GPL-3.0,事件重实现,不用 mixin):
- `LivingAttackEvent`/\`LivingHurtEvent`(HIGHEST):穿着背包 → 取消(免疫一切 attackEntityFrom 伤害,与神环字段级无敌覆盖一致)
- `LivingDeathEvent`(HIGHEST):取消死亡 + 保底 1 血(替代神环 isDead=false)
- ~~永饱(每 20 tick 补满饥饿)~~:**已按需求移除**(GTNH 部分成就依赖吃东西,不能锁饱食度;若需恢复,参考 git 历史 6ae808a 的 onLivingUpdate)
- **重要坑**:1.7.10 Forge EventBus 取消事件后仍会调用后续监听器 → 死亡掉落处理(LOW)必须自己判断 `isWearingInfiniteBackpack` 后 return,否则玩家"死亡"(被取消)瞬间背包会被掉落/清属性
- 所有处理都在服务端(`!worldObj.isRemote`)

### 5.6 与 GTNH 其它 mod 的兼容性现状
- AdventureBackpack2:**零依赖**(已完全去耦合);
- **GT5U:自 2026-09-12 起为项目级硬依赖**(天玄虚空能量节点是 GT 机器,必须 extends GT5U 基类)。依据见 §3.5 例外条款;具体依赖清单见 `dependencies.gradle` 顶部注释;
- Eydamos 背包:共存,互不影响(它的物品栏页签是它自己的功能);
- TConstruct:无引用(早期页签排序逻辑已随页签功能删除);
- 可穿在背上的同时,其它 mod 的"背上"槽位互不冲突(我们用独立属性)。

### 5.7 已知遗留问题 / 未验证
- 背部渲染的潜行/隐身分支未在多人实测;模型偏移(offsetY/offsetZ)可能需要微调;
- 超大 NBT(243 格全满)的存档体积与网络包大小未实测极限;
- `ItemInfiniteBackpack.getUsedSlots` tooltip 在非玩家视角(如 NEI)可能 NPE?——已用 hasTagCompound 防御,未实测。

### 5.8 页签(已删除,勿直接加回)
曾实现物品栏页签,因与 Eydamos 页签视觉重复且无法根除而删除。如将来要加:**不要**再走 InitGuiEvent 添加普通按钮(会和 Eydamos/TConstruct 页签打架),要么用 mixin 接管,要么与 Eydamos 页签按钮合并逻辑。

### 5.16 教训:原版 0x32 WindowItems 全量包会截断大堆叠,必须覆写 addCraftingToCrafters(2026-08 实测)
- **症状**:无尽背包 GUI 打开后,玩家自身 36 格背包错乱——R 键整理失效、物品位置每次打开都不同、部分物品"消失"(打开其他箱子后恢复)。
- **根因**:1.7.10 原版 `Container.addCraftingToCrafters` 会调 `sendContainerAndContentsToPlayer` → 发 **0x32 S30PacketWindowItems 全量包**,其堆叠数量编码为 **byte**(methods.csv 确认:"size (byte)")。存储槽里 >127 的物品数量全部被截断成负数/错误值,客户端显示被污染;整理功能基于污染后的显示操作 → 全乱。**永恒箱子覆写了 `addCraftingToCrafters` 跳过 0x32(见 §5.14),但无尽背包的 `ContainerMultiPage` 漏了**(继承自 Avaritiaddons 原版,原版也漏)。
- **修法**:`ContainerMultiPage.addCraftingToCrafters` override——跳过原版 0x32,直接 `resyncAllToPlayers()`(自定义 int 安全全量)+ `CurrentPagePacket`。**任何自定义 int 大堆叠容器都必须覆写此方法**,否则打开瞬间 0x32 就把客户端槽位污染。
- **检查**:写新容器时 grep 确认覆写了 `addCraftingToCrafters`;跑游戏实测 >127 堆叠 + R 键整理。

### 5.9 可扩展方向(下次细化的候选)
1. **背包合成配方**:普通工作台配方(无尽箱子+材料)或 GTNH 极值工作台配方(参考 `NewHorizonsCoreMod` 的 `ScriptAvaritiaAddons.java` 写法);(和平图腾已示范了 addShapedRecipe 写法)
2. **背包分级**:小/中/大容量版本(参考 Eydamos Backpack.cfg 的 36/63/90);
3. **禁维度配置**:加 `forbiddenDimensions` 配置项(参考 AB 的做法);
4. **更多外观**:把 AB 的其它皮肤纹理打包进来做多 meta 版本;
5. **背包内工作台/末影**:参考 Eydamos 的 workbench/ender 背包;
6. **NEI 集成**:给 GUI 加 NEI 物品栏叠层(参考 Avaritiaddons 的 NEIConfig)。

### 5.11 和平图腾(防刷怪,2026-08 新增)
机制参考 Extra Utilities 卓越火炬(反编译 1.2.13a 学习,零依赖重实现):
- `TileEntityPeaceTotem` 在 `validate()` 注册 `[dim,x,y,z]` 进 `PeaceTotemHandler` 静态表,`invalidate()`/`onChunkUnload()` 注销;`updateEntity` 每 20 tick 兜底重注册;
- `PeaceTotemHandler.denySpawn(LivingSpawnEvent.CheckSpawn)`:结果已是 `ALLOW` 直接放行(刷怪笼/刷怪蛋/命令)→ 只拦自然刷怪;`EnumCreatureType.monster` 才拦(动物不受影响);
- 范围判定:椭球 `(dx²+dz²)/h² + dy²/v² ≤ 1`,h/v 来自 `Config`(默认 64/32),**水平 64 垂直 32,不是球**;
- 附赠:`denyEndermanTeleport(EnderTeleportEvent)` 禁止末影人瞬移进范围;
- **模型(照抄卓越火炬)**:`BlockPeaceTotem extends Block`(不是 BlockContainer!),用 Forge 的 `hasTileEntity/createTileEntity` 挂 TE;不覆写 getRenderType(默认 0),靠 `setBlockBounds(0.375,0,0.375,0.625,1,0.625)` 细柱 + 三张贴图(侧面 `peaceTotem.png` 画火焰头+桩身、顶 `peaceTotemTop.png`、底 `peaceTotemBase.png`,`getIcon` 按面分发);发光 15 级、`setLightOpacity(0)`、`canPlaceBlockAt` 要求下方实心、`onNeighborBlockChange` 断支撑即掉落、`randomDisplayTick` 冒烟+火焰粒子(全部照抄 XU 写法);
- **桩身配色**:深紫(取自背包图标 RGB 32,16,64 系),金环装饰;火焰头保持橙白;
- 配方:**已删除(2026-08)**,与背包/法杖一致改走任务书/奖励发放;原配方:8 钻石块围 1 火把;
- 创造页签:与背包同在 `ElinarCurios.creativeTab`(zh 页签名已改为「埃利纳之珍奇」)。

### 5.12 教训:服务端起不来的元凶(FML SideTransformer)
- **症状**:`runServer` 在 preInit 崩,`Attempted to load class net/minecraft/client/multiplayer/WorldClient for invalid side SERVER`,栈顶在 `CommonProxy.preInit` 的消息注册处;
- **根因**:`SimpleNetworkWrapper.registerMessage` 会 `handler.newInstance()`——**双端都会实例化 handler**;任何 handler 类只要方法体里引用了 `@SideOnly(CLIENT)` 的类(`Minecraft`/`WorldClient`/`GuiScreen` 等),服务端加载该类时 JVM 解析链就会把客户端类拉进服务端,FML `SideTransformer` 直接抛异常;
- **为什么之前没发现**:一直只跑 `runClient`(客户端加载这些类没问题),从未跑过专用服务端;`GuiHandler` 引用了 GUI 类却没事,是因为它走静态实例、不经反射实例化(不要依赖这个行为);
- **正确姿势(已改)**:handler 里不写客户端类,客户端逻辑下沉到 `ClientProxy`(@SideOnly(CLIENT),只被客户端加载),handler 只调 `ElinarCurios.proxy.xxx()`(公共类型 CommonProxy);本次把 `SyncBagPacket`/`BuffStatePacket`/`CurrentPagePacket` 三个 handler 的客户端逻辑移进了 `ClientProxy.handleSyncBagMessage/refreshBuffButtonsIfOpen/refreshPageButtonsIfOpen`;
- **写新消息时检查**:handler(含嵌套 Handler 类)的 import 里出现 `net.minecraft.client.*` / `GuiScreen` / `Minecraft.getMinecraft` 就是雷,一律走 proxy 下沉;
- 例外:`I18n`(net.minecraft.client.resources.I18n)不是 @SideOnly,在 addInformation 里用没问题(ItemInfiniteBackpack 先例)。

### 5.13 TC 软联动:天玄之杖(2026-08 新增,已提交 fdebc62)
- **定位**:新手友好的大容量 TC 法杖,无作弊。rod/cap tag 均 `"elinar"`;**显示名自定义**为「天玄之杖」(override `getItemStackDisplayName`,不走 TC 的 cap+rod+法杖拼接),带 **FabulousText 彩虹渐变动画**(无尽背包同款,服务端返回静态文本);tooltip 烫金「寰宇之力 握于掌心」(`§6§o`)。
- **设计**:capacity = 1000 万(内部 ×100 = 10 亿 vis,int 不溢出);**不挂 IWandRodOnUpdate**(无自动回满);新杖 NBT 无 vis 键 → 到手 vis=0,充能台吸多少用多少;**无合成配方(2026-08 删除,与图腾/背包一致走任务书/奖励发放)**。
- **实现**:`elinarcurios/compat/ThaumcraftCompat.java` + `elinarcurios/item/ItemElinarWand.java`(extends `ItemWandCasting`,注册为 elinarcurios 自己的物品,创造标签/NEI 可见 —— 因为 **TC 的 getSubItems 是硬编码枚举,自定义 rod/cap 组合不会出现在 TC 物品列表**,复用 TC 物品做变体的话 NEI 物品搜索找不到,只有配方输出能索引;先例:ThaumicHorizons ItemWandCastingDisposable);**纹理照搬 Avaritia 水晶缠绕杖**(wand_rod_neutronium / wand_cap_crystal → wand_rod/cap_elinar.png,许可证 LICENSES/LICENSE-MIT-Avaritia.txt)。
- **依赖**:`dependencies.gradle` 加 `implementation('thaumcraft:Thaumcraft:1.7.10-4.2.3.5:dev') { transitive = false }` + `runtimeOnly('com.github.GTNewHorizons:Baubles-Expanded:2.2.21-GTNH:dev') { transitive = false }`(官方 TC 联动 mod 标准配法:implementation 只进 dev classpath,发布 jar 不含 TC;dev 环境跑 runServer 必须带 Baubles,否则 TC 自带 DepLoader 会去失效的 dropbox 链接下载它);运行时 `Loader.isModLoaded("Thaumcraft")` 防御,无 TC 时跳过注册、mod 不崩(GTNH 整合包必有 TC,实际等效强依赖)。
- **坑(从 javap 反汇编验证)**:
  0. **软联动防御必须在调用处守卫,不能只在 compat 类内部判断**(2026-08 虚界百宝箱冒烟测试暴露):`ThaumcraftCompat` 的字段/方法引用 TC 类型(`WandRod`/`ItemElinarWand`),JVM 加载该类本身就要解析它们——`CommonProxy.preInit` 无条件调 `ThaumcraftCompat.init()` 时,无 TC 环境在**类加载阶段**就 `NoClassDefFoundError: thaumcraft.common.items.wands.ItemWandCasting`(isAvailable() 方法体根本没机会执行)。修法:`if (Loader.isModLoaded("Thaumcraft")) { ThaumcraftCompat.init(); ... }` 把守卫放在调用点,TC 不存在时整个类都不加载。
  1. **注册必须两端**:`WandRod.rods`/`WandCap.caps` 是静态表,`getRod()` 按 tag 查表,漏一端注册 → null → NPE。放 `CommonProxy.init()`(ClientProxy 调 super.init())。
  2. **tag 必须唯一**:同 tag 后注册静默覆盖先注册(Avaritia 的 `"infinity"` 不能复用,否则全服 Avaritia 杖变容);旧存档 NBT 也按 tag 解析,会跟着变。
  3. **addVis 单次注入 ≥ 21,474,837(外部单位)会溢出成负 vis 废杖**(amount×100 int 溢出)。1000 万容量下任何理智充能来源都够不到,天然安全;若以后加容量勿超 21,474,836。
  4. **lang 键**:`item.Wand.<tag>.rod` / `item.Wand.<tag>.cap`(自定义 rod/cap 格式,非 TC 原版数字索引格式)。
  5. **override TC 方法必须用裸类型**:`ItemWandCasting.addInformation`/`getSubItems` 的 `List` 是裸类型,子类 override 写 `List<String>` 会报"名称冲突…均不覆盖对方"(泛型签名不一致);用裸 `List` + `@SuppressWarnings("rawtypes")`。
  6. **3D 渲染器按物品实例注册**:TC 的 `ClientProxy` 只 `MinecraftForgeClient.registerItemRenderer(ConfigItems.itemWandCasting, new ItemWandRenderer())`(Forge 按 Item 实例存 HashMap)——**自定义子类物品不注册就没有法杖 3D 模型/纹理**(物品栏/NEI 显示空白或扁平图标)。必须在自己的 ClientProxy(@SideOnly(CLIENT))里给 `ItemElinarWand.instance` 注册同一个 `thaumcraft.client.renderers.item.ItemWandRenderer`;ItemWandRenderer 内部用 instanceof 检查,子类兼容。
  7. **复制 Avaritiaddons GUI 必须带全交互方法**:只有 mouseClicked 不够——`handleMouseClick`(发 InfiniteBagClick 大堆叠协议包)、`mouseClickMove`、`mouseMovedOrUp`(拖拽状态机)一个都不能少,否则"鼠标点不动物品、shift 却正常"(shift 走独立路径);`handleDragAndDrop` 是 1.8+ 方法,1.7.10 不存在,原版用 `flag2 → b0` 模式。
  8. **便携方块存储必须三件套**:TE 加 `readCustomNBT/writeCustomNBT`;Block 加 `getDrops`(掉物品塞 TE NBT)+ `removedByPlayer`(willHarvest 手动 drop)+ `onBlockPlacedBy`(放置恢复)。漏任何一个 → 拆掉重放内容丢失(原版无尽箱子是内容跟物品走的便携行为)。
  9. **Java 构造顺序陷阱(崩溃源)**:父类构造函数里调用了被子类 override 的方法(如 `createStorageSlot`),此时子类字段(this.chest)还是 null——槽里存了 null 引用,运行时其他 mod(MouseTweaks 渲染 tick 调 `Slot.getStack()`)直接 NPE 崩溃。修复:override 方法里用**构造参数**(super() 传进去的 inventory),不要用子类字段;子类字段赋值必须放在 super() 之后的所有逻辑能接受的范围。
  10. **永恒箱子是"整体存储"**:12 页 × 243 格在 TE 里是单个 `pageCache[12][243]` 数组(全局槽号 = page*243+slot),页签只是 GUI 视图;`findSlotFor` 遍历全部 2916 格实现跨页合并,漏斗走 IInventory 全局槽号自动全局合并;Container 用 `EternalChestSlot`(固定 slotInPage + 当前页映射)访问。
  11. **客户端显示模型(核心教训)**:MC 1.7.10 `Slot.getStack()` **直读 inventory(无本地缓存)**,而客户端 TE 的存储从不从服务端同步 → GUI 槽直读客户端 TE = 空/旧/错位(物品"消失"+"每次位置不一样")。修复:自定义 Slot 持 `localStack` 本地缓存,服务端在 `addCraftingToCrafters`(打开 GUI 时,跳过原版 WindowItems short 截断包)和 `switchPage` 时用自定义 int 安全协议全量推送;客户端 TE 只当"写靶子",显示完全服务端驱动。
  12. **合并逻辑会污染槽缓存(复制 bug 根源)**:同步推送(`Slot.putStack`)绝对不能走跨页合并逻辑——合并会把传入 stack 数量清零/改走,槽缓存与服务端脱节 → 物品消失 + 可重复拿取(复制)。必须分离:推送用 `rawSetStack`(直接落槽镜像),合并只在"放入"路径(`insertStack`/`setInventorySlotContents`,漏斗/玩家点击用 `putStackWithMerge` 钩子)。
  13. **NBT 格式要跟用户确认清楚**:多页容器用户可能默认"后台一个整体、前台分页"(NBT 单列表 + 全局槽号),而不是 pages 分页——先问清楚再定格式,免得返工。
- **待办**:获取途径(任务书/奖励)未定;如需专属杖杆/杖端纹理已就位(wand_rod/cap_elinar.png)。

### 5.14 永恒箱子(Eternal Chest,2026-08 新增,待 commit)
- **定位**:12 页 × 243 格/页 = **2916 格整体箱子**(非 12 个独立箱子),每格 Integer.MAX_VALUE,与原版无尽箱子(Avaritiaddons 244 格单页)同逻辑、只是格子数扩展;用户命名「永恒箱子」。
- **后台存储(按用户要求单页化)**:TE 内存 `pageCache[12][243]` 单数组,全局槽号 = page*243+slot;**NBT 为单一列表** `EternalChestData.Items[{Slot(0-2915), intCount, tag}]`(无 pages 分页),页签纯粹是 GUI 前台渲染。旧分页格式不兼容(功能未交付,直接换格式)。
- **核心文件**:
  - `tile/TileEntityEternalChest.java`:全量存储 + `setInventorySlotContents`(跨页合并)/`rawSetStack`(同步镜像)/`read-writeCustomNBT`(便携)
  - `inventory/ContainerMultiPage.java`(新抽公共父类)+ `ContainerEternalChest.java`(EternalChestSlot:localStack 缓存 + insertStack 合并 + globalSlot 页映射)+ `GuiEternalChest.java`(12 页签,精简版)
  - `block/BlockEternalChest.java` + `ItemBlockEternalChest.java`(硬方块 50/2000/发光,便携三件套)
- **行为**:跨页合并(第 10 页丢木炭 → 堆到第 1 页已有堆);漏斗/自动化全局存取(getSizeInventory=2916);拆掉重放内容跟物品走;无配方(任务书/奖励发放)。
- **GUI 复用**:与背包共用 `infiniteBackpack.png` 底图(500×276 布局一致);页签按钮 id 100+i;大堆叠渲染/RenderItemInfinity 同背包。
- **网络**:复用背包的 InfiniteBagClick/SlotSync/SyncAllSlots/SwitchPage/CurrentPagePacket(instanceof 泛化到 ContainerMultiPage);`addCraftingToCrafters` override 跳过原版 WindowItems(short 截断),打开 GUI 即推全量 + 当前页。

### 5.15 虚界百宝箱(Void Treasure Chest,2026-08 新增,待 commit)
- **定位**:永恒箱子的「固化/解离」变体——存储/容量/页签/便携行为与永恒箱子**完全一致**(12 页 × 243 格 × int 上限整体存储),GUI 右上角两个按钮:
  - **「固化」**:箱内**所有非空槽位**的物品数量统一刷新为 **10 亿**(`1_000_000_000`,少于补齐、多于压回);
  - **「解离」**:固化镜像操作,统一刷新为 **1**(`DISSOCIATE_COUNT`);
  - 空槽保持为空;物品类型/NBT 不变。10 亿 ≤ int 上限,网络协议安全,无 long 风险。
- **外观**:永恒箱子紫色板材 + **金色镶边**(程序生成 `void_chest.png`:边框三层 A/B/C 换金色系 122,90,18 / 216,172,42 / 178,136,32,板材 D/E 保留紫色)。
- **实现(继承复用,零复制)**:虚界箱子不新写存储逻辑,而是**继承**永恒箱子:
  - `TileEntityVoidChest extends TileEntityEternalChest`:覆写 `getTagRoot()` → `VoidChestData`(独立 NBT 根键,两箱数据互不串扰)、`getInventoryName()` → `container.elinarVoidChest`;新增 `solidify()`(→10 亿)/`dissociate()`(→1),共用私有 `setAllCounts(count)`(遍历 2916 格非空槽置数 + markDirty)。
  - `BlockVoidChest extends BlockEternalChest`:覆写 `setBlockName("elinarVoidChest")`/`createNewTileEntity`/`onBlockActivated`(GUI_ID_VOID_CHEST=3)/`registerBlockIcons`(金色贴图);父类的 `onBlockPlacedBy`/`getDrops`/`removedByPlayer` 便携三件套靠 `instanceof TileEntityEternalChest` 对子类自动生效,无需重写。
  - `ItemBlockVoidChest extends ItemBlockEternalChest`:显示名 `tile.elinarVoidChest` + 固化/解离 tooltip。
  - **Container 直接复用 `ContainerEternalChest`**(其构造器收父类 TE 类型,虚界 TE 是子类,天然兼容);`GuiVoidChest extends GuiEternalChest`:覆写 `getContainerTitleKey()` + initGui 加「解离」按钮(id=201,右上角,固化左侧)+「固化」按钮(id=200)+ actionPerformed 发包。
  - `SolidifyVoidChestPacket` / `DissociateVoidChestPacket`(C→S 空包):服务端 handler 校验 `openContainer instanceof ContainerEternalChest` 且 `chest instanceof TileEntityVoidChest` → `solidify()`/`dissociate()` → `container.refreshAndResyncAll()`(新加:刷新槽缓存 + 全量推给所有打开者,多人同步)。
- **配套重构**:`TileEntityEternalChest.TAG_ROOT`(private static final)→ `protected getTagRoot()`;`GuiEternalChest` 去 final + 标题 key 抽为 `getContainerTitleKey()`。均为向后兼容,永恒箱子行为不变。
- **lang 键**:`container.elinarVoidChest` / `tile.elinarVoidChest.name` / `tile.elinarVoidChest.tooltip.solidify` / `tile.elinarVoidChest.tooltip.dissociate` / `button.elinarcurios.solidify` / `button.elinarcurios.dissociate`(双语)。
- **撞名教训(2026-08 实测踩坑)**:起名时**不能只查 modId/注册名,还要查 lang 键**——1.7.10 的 lang 表是全局合并的,`tile.voidChest.name`/`container.voidChest` 被 Hardcore Ender Expansion 占用(它的虚空保存箱,GTNH 中文经 txloader 强制加载,必然覆盖我们的翻译)。当初只对照了 FullPack 的注册名没查 lang,导致游戏里显示成「虚空保存箱」。修复:全部改为 `elinarVoidChest` 前缀。**新增物品/方块时用 `grep "tile.X.name\|container.X" FullPack` 查 lang 撞名**;改名后**必须全项目 grep 旧键名**(含 .java 里 `I18n.format("tile.…")` 的引用——改名时曾漏改 `ItemBlockVoidChest.addInformation`,tooltip 直接显示键名原文)。
- **待办**:无配方(与全 mod 一致走任务书/奖励发放);按钮文案/位置如需要可调。

### 5.17 天玄·虚空电池(Wireless Battery,原 EnergyCeph,2026-09 并入)
- **定位**:15 档无线电池,`itemDamage` = tier(0 = ULV 8V … 14 = MAX 2,147,483,640V)。容量 = `GTValues.V[tier] × 15625`(HV 档 = 8,000,000,原型验证值),充放上限 2A(`transferLimit = 2×V`)。该系数保证"从 30% 起以 2A 满放 ≥2000 tick"全档成立(0.3×15625/2 = 2343 tick)。`V[]` 有 16 项(第 15 项是防越界的错误档),`TIER_COUNT=15` 只用到 0..14。
- **绑定与记账**:手持右键写 `EC.OwnerUUID`/`EC.OwnerName` 并调 `WirelessNetworkManager.strongCheckOrAddUser`;GT 侧按**队伍 leader** 记账,所以绑定个人 = 绑定其所在队伍。补电调 `addEUToGlobalEnergyMap(owner, -need)`,返回 false = 余额不足则整笔取消(不会扣成负数)。
- **补电时机(2026-09-11 改造)**:挂在每次**真实**充/放触碰上(`charge`/`discharge` 的 `!simulate` 分支),电量 <30% 即**一次性补满**;不设冷却窗口 —— 放进机器后第一次被触碰就补满,不必等下一个 2000 tick。只有补电**失败**(未绑定 / UUID 非法 / 余额不足)才往 NBT 写 `EC.LastRefill` 退避 2000 tick,避免余额不足时每 tick 反复操作 GT 的 `BigInteger` 表(`GlobalEnergy` 是普通 HashMap,`SpaceProjectManager.spaceTeams` 也是)。
- **坑(2026-09-11 修复)**:
  1. **`use()` 的 simulate/真实放电分歧**:GT 原版 `MetaBaseItem.use()` 是"先 simulate 试一次,不够就 return false,但无论如何都再真实放一次电";在 GT 那里安全,是因为它的 `discharge` 无副作用。我们的 `discharge` 会触发补电 → 空电池出现"返回 false 但电已被补满并抽走"(表现为第一次使用无效、第二次才好)。修法:`use()` 入口先 `tryWirelessRefill`。
  2. **`MinecraftServer.getServer()` 未判空** → 纯客户端(多人)读 `getTickCounter()` 会 NPE。已加 null 守卫并直接 return。
  3. **退避盖章时机**:原实现在校验 UUID / 余额**之前**就写 `LastRefill`,未绑定或没钱也照吃 2000 tick。已改为只在失败后退避(未绑定不盖章,绑定后第一次触碰即可补)。
  4. **lang 里的 `%%`**:`StatCollector.translateToLocal` **不做** `String.format`(`StringTranslate.translateKey` 直返原文),所以 `30%%` 会原样显示成"30%%"。用 `translateToLocal` 就别转义,只有 `translateToLocalFormatted` 才转义。中英各一处已修。
  5. **`getIconFromDamage(int)` 返回 null**:覆写 `registerIcons` 后基类 `itemIcon` 从未赋值,第三方(NEI / 其它渲染器)走 `getIconFromDamage` 会拿到 null。已在 `registerIcons` 末尾 `itemIcon = icons[0][3]` 兜底。注意 1.7.10 主渲染链走的是 `getIcon(stack, pass)`,电量档图标必须挂在那里。
  6. **`IElectricItemManager.getShareTag()` 不存在**:类里那个 `getShareTag` 覆写的是 `net.minecraft.item.Item.getShareTag()`(Forge 默认就返回 true),不是 IC2 接口,属于无害冗余,保留未动。
- **配方备忘**:`Materials.EnderEye.getBlocks(1)` **正常返回** —— `blockEnderEye` 由 `LoaderGTBlockFluid` 的 `BlockMetal`(`gt.blockgem1` / `sBlockGem1`,材料数组含 EnderEye)注册。**这个 GT 版本没有 `MaterialBuilder.addBlockItems()`**,材料方块全靠 `LoaderGTBlockFluid` 里的显式材料数组,别拿"builder 里没调 addBlockItems"推断某材料没有方块。因此 `WirelessBatteryRecipes` 里的 `if (eyeBlock == null) return;` 永不触发,配方齐全(线 1 十五档 + 线 2 十二档 = 27 条;ULV 档有红石/钽电容两条并行)。
- **配方规则(2026-09-12 用户拍板,取代原先"复杂合成机制"的计划)**:
  1. **产物一律是空电池** —— `ItemWirelessBattery.newEmptyBattery(tier)` 显式写 `EC.Charge = 0`。旧版产物无 NBT,而 `getRealCharge` 把无 NBT 当满电,"4 个空电池 → 1 个满电池"就能白刷 EU(ULV 甚至只要 4 红石 + 1 末影珍珠)。
  2. **`EV~MAX` 要求四角 4 个上一级电池全部满电**;`ULV~HV` 不设门槛(GT 锂电池合成出来本来就是空的,不需要先充)。
  3. **能量账**:容量 = `V×15625`,每档 ×4,而 C 组正好吃 4 个上一级电池 → 每次升级消化掉一块上一级满电电池(≈ 新档容量的 25%),不会无中生电。MAX 档 `V[14] = Integer.MAX_VALUE-7` 不是精确 4 倍(4×UXV 比 MAX 容量多 125,000 EU),但产物已改空,不存在溢出损耗。
  4. **线 2 · 同级直通(2026-09-13 用户拍板)**:ULV = 中心末影珍珠 + 四围 4 个钽电容(`ItemList.Battery_RE_ULV_Tantalum`);EV~MAX = 中心末影之眼块 + 四围 4 个同级 GT 电池(EV/IV/LuV = `BatteryHull_EV/IV/LuV_Full`,ZPM/UV = `_ZPM/_UV_Full`,UHV/UEV/UIV = `_UHV/_UEV/_UIV_Full`,UMV/UXV = `_UMV/_UxV_Full`,MAX = `ZPM5`)。**空/满皆可、不设门槛**,产物同样为空电池。与线 1 的四周材料不重叠(线 1 吃虚空电池、线 2 吃 GT 电池),两条线不会互相抢匹配。
- **坑(2026-09-12)**:**原版 `ShapedRecipes.checkMatch` 只比对 item + damage,完全不看 NBT** → "必须满电"这类约束原生配方根本做不到。修法:新增 `recipe/CrossBatteryRecipe extends ShapedRecipes`,覆写 `matches()` —— 先 `super.matches()` 再用 `ItemWirelessBattery.isFullyCharged(stack)` 扫网格判定;用 `GameRegistry.addRecipe(IRecipe)` 注册。`ShapedRecipes.recipeItems` 是 public final,所以 NEI 仍能正常画配方网格。另:`CraftingManager.findMatchingRecipe` 返回 `getCraftingResult()` 副本(`CraftingManager.java:330`),产物不会共享同一个 ItemStack 实例。
- **已知设计取舍(用户拍板)**:①补电一次性抽 `max-real`,不受 2A 限流约束,也不校验持有者(电池转手后仍抽原绑定者的电)——用户确认后期不缺电,不担心;②EV~MAX 用"满电门槛"而非 GT 式电量继承,是因为后者要整套配方改走 `GTModHandler.addCraftingRecipe` 且受 int 上限(≈21.4 亿 EU)约束,用户认为太复杂。
- **待办**:电池本体已完成;线 2(2026-09-13)已落地,待用户进游戏实测 —— 重点看 NEI 里两条线是否都画得出来、27 条配方有无互相抢匹配。

### 5.18 天枢·恒温核心(Thermostatic Core,2026-09-12 新增;数值 2026-09-13 改版)
- **定位**:一个物品同时充当 IC2 传统核电的**反应堆隔板 + 冷却单元 + 热交换器**三种元件。用户拍板的参数(2026-09-13 改版,原话「ElinarCurios 我希望改成如下的数值」+「隔板加成 改为 20,000,000 Hu」):
  - 自身散热 `selfVent` = **1,000,000 Hu/s**
  - 主动吸热(相邻元件)`switchSide` = **1,000,000 Hu/s**
  - 核心吸热(堆芯 hull)`switchReactor` = **1,000,000 Hu/s**
  - 热容 `HEAT_STORAGE` = **1,000,000,000 Hu**(对标 1G 中子热容,未动)
  - 隔板加成 `PLATING_HEAT_PER_HEAT_PASS` = **20,000,000**,热趟施加 → 每秒 **20,000,000 Hu/s**(她先提 10 亿,自查后改口 2000 万)
- **★ 秒口径修定(2026-09-13,用运行版 jar 的字节码实证)**:`TileEntityNuclearReactorElectric.getTickRate()` 硬编码返回 **20**,`updateEntityServer` 里由 `updateTicker++ % 20 == 0` 门控 → **堆芯每秒只结算一次**;每次结算 `processChambers()` 跑 pass 0/1 两趟,而 **`heatrun = (pass == 0)`**(原版隔板与本元件都只在 heatrun 趟生效)。所以**元件常量 n 的口径就是 n Hu/s,不存在"1e6/趟 × 2 趟 = 2e6/s"这回事**——旧版文档与 tooltip 的 ×2 是错的,已改回 1:1(`PLATING_HEAT_PER_SECOND = PLATING_HEAT_PER_HEAT_PASS`)。
- **核心文件**:`item/ItemElinarReactorCore.java`(实现 `ic2.api.reactor.IReactorComponent` 全部 7 方法)+ `recipe/ReactorCoreRecipes.java`(9 青金石块)+ `CommonProxy.preInit/init` 的 `Loader.isModLoaded("IC2")` 守卫注册 + `tools/make_icons.py` 生成的两张 16×16 贴图。
- **数值上限铁律(改数值前必读)**:
  1. IC2 侧热值全是 **int32**(`IReactor.getHeat/getMaxHeat`、TE 的 `int heat/maxHeat`、NBT 整数),隔板加 **绝不能让堆芯 maxHeat 越过 2,147,483,647**。2000 万/颗要叠 **107 颗**才越界,而 IC2 反应堆满配只有 **54 格** → 实际不可能溢出。(旧文写"溢出变负数 → `power=heat/maxHeat ≥ 1` → 当场爆炸"**不准确**:`power` 是 float 除法,maxHeat 为负则 power 为负,**熔毁/辐射/燃烧判定会全部失效**——不炸,但保护丢失,同样不能要。)
  2. 更硬的限制是 **GUI**:`gaugeHeatScaled(i) { return i * heat / (maxHeat / 100 * 85); }` 与百分比 `100 * heat / maxHeat` 都是整数运算 → **堆芯热量 heat 超过 21,474,836(百分比)或 13,421,772(温度条,i=160)时分子溢出** → 温度条/百分比错乱(堆芯 GUI 与 Nuclear Control 传感器都吃这个)。单颗 2000 万时堆芯上限 20,010,000 < 21,474,836 → **只放一颗时永远碰不到溢出线**;多颗叠加会抬高上限,要堆芯真烧到千万级才可能失真——而本元件每秒抽热/排热各 100 万,正常堆的热量被压在低位。
  3. 堆芯每 tick 先 `maxHeat = 10000; hem = 1.0` 再遍历元件重算 → 隔板效果**无需注册/反注册**,拔掉即失效;本元件只在 **pass 0(heatrun=true)** 施加,避免两趟双算。
- **实现要点**:
  - 热存 `ItemStackNBT` 的 `heat` 键(不用 damage,damage 只做 0..100 进度条,**每格 = `HEAT_STORAGE / 100` = 1000 万 Hu**);无 NBT 一律按 0,新合成件天然空热。
  - `alterHeat` 遵循 IC2 协议:返回**拒收量**;超容截顶不销毁元件、不减耐久(刻意与 IC2 `ItemReactorHeatStorage` 的"过热销毁"不同)。
  - 换热/散热在 `processChamber` 热趟内按「抽堆芯 → 换四邻 → 自身散热」顺序执行;`selfVent` 排掉的量走 `reactor.addEmitHeat()`(流体堆里计入 HU 产出)。
  - **不做流体堆限制**(`canStoreHeat` 不看 `isFluidCooled()`),与 GT5U 冷却单元那套"进流体堆前必须冷却"的行为不同,刻意为之。
  - `setMaxStackSize(1)` + `setNoRepair()` + `showDurabilityBar` 常显;`getIcon(ItemStack,int)` 按热比例切冷/热贴图。
- **配方**:3×3 全是青金石块(oreDict `blockLapis`,GT `LoaderGTOreDictionary` 已把原版青金石块挂进该词条)。产物**无 NBT**;用户说"先这样定,后期可能改"。
- **★ 事故与修复(v1.2.1 → v1.2.2,2026-09-12 用户实测)**:`GameRegistry.addRecipe` **不接受 oreDict 字符串当材料**——传字符串会让 `CraftingManager` 在 `HashMap<Item,ItemStack>` 里查到 null,紧接着 `func_92103_a` 对 null 调 `func_77946_l()`(copy)→ 初始化期 NPE,**mod 加载失败、游戏卡死在加载界面**(crash-report 指向 `ReactorCoreRecipes.register` → `CommonProxy.init`)。修法:先 `OreDictionary.getOres("blockLapis")` 取条目 `.copy()` 成 ItemStack 再注册(meta 为 `WILDCARD_VALUE` 要归一成 0,否则配方永不命中),词条为空时打 `FMLLog.warning` 跳过而不是崩。**教训:编译通过 ≠ 可运行,凡涉及注册表/配方的改动必须实际进游戏验证一次**;`GameRegistry` 里只有 `addShapedRecipe(ItemStack,...)` 那个重载接受字符串,与 `addRecipe` 混淆必踩。
- **待办**:配方可能改;是否需要"过热销毁"语义用户未表态;**热学行为(温度条、HU 产出、NEI 配方显示)仍未进游戏实测**——只验证了配方注册不再崩。**2026-09-13 数值改版后需重测**:重点看 100 万/秒的抽排是否把堆芯热压在 0 附近、20,000,000 隔板下单颗堆芯上限是否显示 20,010,000、tooltip 各行数字是否与 DEV 表一致。

### 5.19 天玄虚空能量节点(Celestial Void Energy Node,2026-09-12 新增)—— ElinarCurios 首台 GT 机器
- **定位**:单方块 GT 机器。**六面进电** → 把导线送来的电攒进内部缓存 → 一次性倾泻进 GT 全局无线电网。两条倾倒机制:**①攒满即冲(主)**、②每 100 秒兜底(芷若明确:「攒满即冲优先级高于每 100 秒清空,每 100 秒清空只是为了在低压阶段也能用」)。
- **参数(用户拍板)**:电压上限 `Integer.MAX_VALUE`(2,147,483,647,超过**会炸**)、电流上限 **200 万安**、单档 **tier 14(MAX)**、容量 **8.59e18 EU**(= 一整周期满值)、注册名 `celestinull`、中文名「天玄虚空能量节点」、英文名 `Celestial Void Energy Node`、机器 ID **19082**。
- **核心文件**:`machine/MTECelestinullVoidNode.java` + `machine/ElinarMachines.java`(注册与 ID 台账) + `gui/CelestinullVoidNodeGui.java` + `CommonProxy.preInit` 的 `Loader.isModLoaded("gregtech")` 块 + `textures/blocks/celestinullVoidNode.png`(`tools/make_celestinull_icon.py` 生成)。
- **电量铁律(务必遵守)**:`maxEUStore() + maxEUInput() × maxAmperesIn() ≤ Long.MAX_VALUE`。GT 的 `BaseMetaTileEntity.injectEnergyUnits` 用 long 做 `aVoltage * aAmperage`,并以 `aIgnoreTooMuchEnergy = true` 直接累加进 `mStoredEnergy` —— 越界**静默翻负、机器报废,不报错不爆炸**。这正是 TecTech/TST 的无线能源仓把「int 上限电压 × int 上限电流」的乘积卡在 Long.MAX 以内的原因。本机 4.295e15 × 2000tick = 8.59e18,达标。
- **水位写法**:水位 = `CAPACITY - maxEUInput()`(留一个整包)。若水位直接取 CAPACITY,水箱差一个包就满时 `injectEnergyUnits` 里 `1 + ((capacity - stored) / voltage)` 会算成 0,线缆里的电被永久卡住。
- **放网顺序(不能反)**:先 `WirelessNetworkManager.addEUToGlobalEnergyMap(owner, stored)`,**成功才** `setEUVar(0)`。该方法语义是「会让余额变负就整体拒绝并返回 false」,所以不存在半扣;失败时电量留本地、下 tick 重试。
- **归属**:`BaseMetaTileEntity.getOwnerUuid()`(= 摆放者,NBT `mOwnerUuid` 自动持久化)→ `strongCheckOrAddUser` → `SpaceProjectManager.getLeader` 归到**队伍** UUID。同队共用一张网,这是 GT 的设计,与 `ItemWirelessBattery` 的 `EC.OwnerUUID` 体系一致。
- **计时**:用 TE 自带、已被 GT 同步的 `aTick` 取模(`aTick % 2000 == 0`),**不要**自己存 tick 计数器 —— 自带持久化,重登/区块卸载不重置。
- **GUI 写法(★2026-09-13 用 2.8.4 版本锁定源码逐行核实后重写,下面这些是唯一正确做法;旧版写的 `extends MTETieredMachineBlockBaseGui` / `createContentSection()` 已作废,那套 API 在 5.09.51 里不存在)**:
  1. **不继承任何 GT5U GUI 基类**。运行版 GT5U 5.09.51.482 里**没有** `MTETieredMachineBlockBaseGui`(5.09.54 才加),照它写会类加载失败且被静默吞掉。
  2. **★★ 必须覆写 `forceUseMui2()` 返回 true —— 这是「GUI 能开却全空」的真正根因**。`CommonMetaTileEntity.openGui` 的判定是 `if ((GTGuis.GLOBAL_SWITCH_MUI2 && useMui2()) || forceUseMui2())` 才走 MUI2,否则落到 MUI1 老界面 `GTUIInfos.openGTTileEntityUI`;而 5.09.51 里 `GTGuis.GLOBAL_SWITCH_MUI2` 初值是 **false**,且**整个 GTNH 2.8.4 源码集(197 仓库)里没有任何地方给它赋 true**(MUI2 迁移未完成,GT 自家机器也全走 MUI1)。所以**只写 `useMui2()` 完全无效** —— `buildUI()` 一次都不会被调用,症状是「GUI 能开、里面全空、连物品栏都没有、鼠标悬停无反应」,而且**日志里一行异常都没有**(这不是异常,是分支走错;曾为此盲改十几轮布局)。
  3. 正确写法:`buildUI()` 里 `GTGuis.mteTemplatePanelBuilder(this, data, sync, uiSettings).setWidth(176).setHeight(200).doesAddGregTechLogo(false).build().child(screen.pos(8, 12))`;黑框 = `new ParentWidget<>().size(160, 92).padding(3, 2, 3, 2).widgetTheme(GTWidgetThemes.BACKGROUND_TERMINAL)`(5.09.51 里它是 `String` 常量;`ParentWidget` 默认**全透明**,必须显式给主题)。**`padding` 的参数顺序是 `(left, right, top, bottom)`**(MUI2 `IPositioned.padding`,不是 CSS 的 left/top/right/bottom),所以这里左右实为 3 和 2、可用内宽 **155px** —— 内容列超过它就会触发 MUI2 的 `[SIZING][Column]` 自检(2026-10-18 就栽在「实时输入」那行超宽上,见下条)。**没有按钮**(用户要求)。
  4. **面板高度要 ≥ 屏幕底部 + 物品栏顶部**:玩家物品栏由 `playerInventory()` 以 `bottom(7)` 锚定、总高 76(主体三行 y=0/18/36,快捷栏 y=58),所以面板 166 时物品栏顶端在 y=83;屏幕若占到 y=104 就会把物品栏第一行整个盖掉(这正是「连背包格子都没了」的另一半原因)。屏幕占 12~104 ⇒ 面板高度取 200。
  5. 内容用 `Flow.column().coverChildren().childPadding(1)` 逐行 `IKey.dynamic(...).asWidget()`。
  6. **★★ 同步值的取回方式(2026-09-13 修正,之前在 DEV_GUIDE 里记错过一次,并因此产出过一个 bug)**:MUI2 **2.2.18** 用 `syncManager.syncValue(名字, handler)` 注册,但**取回必须带后缀** —— map 的真实键是 `makeSyncKey(名字, 0)` = **`"名字:0"`**(`PanelSyncManager.putSyncValue:127` / `makeSyncKey:293`),而 `getSyncHandler(mapKey)` 是**直接查 map**(`:266`),传裸名字会**静默返回 null**。GT5U 自己也是硬编码带后缀取的(`MTEMultiBlockBaseGui:311` `getSyncHandler("recipeInfo:0")`,另有 `"machineMode:0"`/`"recipeLock:0"`/`"coverage:0"`)。**最稳的写法是注册时就把 handler 存进局部变量直接引用,根本不去查表**(本项目现在就是这么做的)。另:`findSyncHandler(String, Class)` 是 2.3.x 才有的,运行时不存在。
  7. **同步频率**:`getTimer()` 这类每 tick 都变的值不要原样同步(那是 20 包/秒);界面显示单位是秒就同步 `getTimer() / 20`,值每秒才变一次,直接省掉 19/20 的包。
  8. **排查提醒**:如果界面「有显示但数字不变」,先看是不是 `null` 兜底把「取数失败」吞成了 0 —— 「值恒为初值/恒为 0」是**没拿到数据**的典型指纹,不是「数据没变化」。
  9. GT logo 固定在 (152, 63)、尺寸 17×17,会被屏幕(x 8..168)压住 —— 用 `.doesAddGregTechLogo(false)` 关掉,别留一个被盖住还占着 hover 的 widget。
  10. **★★ 行长上限(2026-10-18,v1.2.14 修复)**:`Flow.column().coverChildren()` 的宽度 = 最宽那一行;MUI2 在 `DimensionSizer.applyMarginAndPaddingToPos()` 里自检「左 padding + 列宽 + 右 padding > 父宽」,超了就**每 tick 打一条** `[SIZING][Column]: Margin/padding is set on both sides on axis X, but total size exceeds parent size`(实测开界面 24 秒 482 条),而且**不会裁切** —— 文字直接画到黑框外。当时「实时输入(最近均值)： 11,816,060 EU/t」单行约 174px,已超 155px。修法 = 拆两行(标签行 + 数值/单位行,列宽降到约 95px),并给两个会增长的读数(实时输入、缓存电量)统一 `ECFormat.compactAbove(value, GUI_SCIENTIFIC_THRESHOLD = 1e12)`:≥10^12 用 `1.182e+12` 科学计数,避免后期数字变长再撑破(缓存那行约 1e13 就会越界)。**以后给这个黑框加行/加长文案,先按最坏情况估宽度(中文 9px/字、数字 6px/位)**。
- **GT5U 注解处理器的坑(踩过)**:GT5U 本体的机器靠自己的注解处理器生成 `saveNBTData/loadNBTData` 等样板方法,那个处理器**不随发布 jar 提供**,所以 addon 必须手写。且父链上它们是**抽象方法**,`super.saveNBTData()` 会编译报错「无法直接访问抽象方法」——直接写自己的读写即可。同理 `allowPutStack/allowPullStack` 必须实现(无物品栏就一律 return false)。**不要**去覆写 `supportsInventoryRow()` / `doesBindPlayerInventory()` —— 这两个在 5.09.51 里**不存在**,覆写会编译报错(是 5.09.54 才有的基类成员);玩家物品栏交给 `mteTemplatePanelBuilder` 的模板面板管就行。
- **★ 事故与修复(v1.2.3 首版 → 修正版,2026-09-12 芷若实测崩溃)**:第一版为了数字千分位引用了 `com.gtnewhorizon.gtnhlib.util.numberformatting.NumberFormatUtil`,**编译通过、构建成功,但进游戏在 preInit 阶段 `NoClassDefFoundError` 直接崩客户端**。根因不是代码错,而是**版本错配**:该类只存在于 GT5U 5.09.54 依赖的新版 GTNHLib(**0.11.37**),而 GTNH 2.8.4 整合包内置的是 GTNHLib **0.7.10**,类根本不存在(已实测:0.11.37 的 jar 里有 `numberformatting/*`,0.7.10 没有)。**教训:编译期依赖的版本 ≠ 运行时整合包的版本,凡引用非 GT5U 本体的库都要先确认整合包里的实际版本。** 修法:自写 `util/ECFormat`(`String.format("%,d")` + 大数科学计数),运行期零 GTNHLib 引用。注意 **GTNHLib 仍必须留在 `compileOnly`** —— GT5U 的 `MetaTileEntity` 继承链上有 `com.gtnewhorizon.gtnhlib.capability.CapabilityProvider`,删掉依赖会报「无法访问 CapabilityProvider」。验证手法:用 python 解析自己的 class 常量池列出全部第三方引用,再逐个到玩家实例的 mods jar 里查是否存在(`D:\Agent\GTNH\ref\check_refs.py`、`gtnhlib_probe.py`)——比只跑构建可靠得多。
  - 参考:整合包实际版本可在 `E:\GTNH\1、GTNH-PrismLauncher\instances\GT_New_Horizons_2.8.4_Java_17-25\.minecraft\mods\` 直接看 jar 名(如 `gtnhlib-0.7.10.jar`、`gregtech-5.09.51.482.jar`、`modularui2-2.2.18-1.7.10.jar`)。
- **贴图**:`getTextureSet()` 只需返回非空数组;真正的渲染走覆写的 `getTexture(...)`,用 `MACHINE_CASINGS[mTier][colorIndex + 1]`(**colorIndex +1 才是外壳数组索引**)叠 `OVERLAYS_ENERGY_ON_WIRELESS[mTier + 1]`。
- **待办**:①配方未做(用户此前语义:无配方,走任务书/奖励;要配方需另定)。②**GUI 复测(进行中)**:2026-09-13 分两轮修掉了两个根因 —— 第一轮 `forceUseMui2()` 缺失(界面能开后全空的根因,她已确认「确实正常显示了」),第二轮同步值取回键漏 `":0"` 导致数值不刷新(缓存恒 0、倒数恒 100 秒)。当前 jar MD5 **`66002ECE95A52EA4F7E4DC2EE06CD629`**;判据 = 黑框 + 八行信息(实时输入分两行) + 玩家物品栏格子齐全,且**缓存与倒数随机器状态变化**;2026-10-18 又修了「实时输入」行超宽(GUI 第 10 条),v1.2.14 另加一条判据 = 打开界面后日志里**不再**出现 `[SIZING][Column]`。③**线缆电压策略按用户指示暂缓不动**(她原话「你只关注第一个就行」,并自认第二个问题「是我输入的电流太大了」。要动时三个备选:tier 自适应取最细线电压 / 固定电压档 / 自适应+电流随档缩放 —— 属设计取舍,**必须让她拍板,别擅自替她选**)。④NEI 显示(内部电量 + 归零时间)延后到 GUI 确认之后再做。⑤ID 台账:19082 已占,预留 19082-19101。⑥实装状态:已能进游戏、能摆放、NBT 存电正常(2026-09-12 实测);六面进电与两条倾倒触发路径的**热行为**尚未逐项验证。

---

### 5.20 午餐盒(Eternal Lunchbox,2026-09-22 新增,已随 v1.2.7 发版)—— 多页食物容器 + SpiceOfLife 进食协议

- **定位**:手持右键进食的超级食物容器。**12 页 × 243 格、每格 int 上限**;进食协议整套来自 SpiceOfLife 的 `ItemFoodContainer`(盒盖 `Open` 状态、`EnumAction.eat` + 32 tick 原版进食动画、智能选食、AppleCore 食物值、丢出/关屏复位)。中文名「天厨·永恒食盒」、英文 `Eternal Lunchbox`。无配方(与其余物品一致,走任务书/奖励)。
- **★ 架构**:`ItemLunchBox extends ItemFoodContainer`,但存储层整体换成项目自己的多页 int 引擎 —— `InventoryLunchBox extends FoodContainerInventory`:
  - `getSizeInventory()` 直接返回 **2916**(12 页扁平化),并且**整个数组常驻内存**,于是**换页只是改一个页号**,不需要任何重载/落盘(这点与无尽背包的「单页视图 + 换页落盘」不同)。
  - 由此**白拿跨全页选食**:SpiceOfLife 的 `MealPrioritizationHelper.findBestFoodForPlayerToEat(player, inventory)` 拿到的就是这个 2916 槽的 IInventory,自动跨 12 页挑当前最优食物(含食物多样性递减权重)。**不要再自己写多页选食算法**,重写只会与 SpiceOfLife 的权重体系脱节。
  - 序列化**不用** SpiceOfLife 的 `Inventory/Items` 结构(那里 Count 是 byte,>127 的堆叠存档即损坏),覆写为 `lunchbox.pages."N".inventory[{Slot,id,Count,Damage,intCount}]`,根键 `lunchbox`(与背包的 `elinarcurios` 根互不串扰);页号存 `lunchbox.currentPage`。
- **★ 依赖写法(本次最大的坑,务必照抄)**:SpiceOfLife / AppleCore 必须写成 `compileOnly rfg.deobf(files('.../SpiceOfLife-2.2.3-carrot.jar'))`。
  - 理由见 §3.5 最后一条:不 deobf 的话 javac 看到 srg 名,继承第三方 mod 类时**既不能 `@Override` 覆写原版方法、也不能调 `super`**。
  - 版本对齐运行时 2.8.4:`SpiceOfLife-2.2.3-carrot.jar` / `AppleCore-3.3.7.jar`(AppleCore 是 SpiceOfLife 的前置,其类型出现在 SpiceOfLife 的方法签名里,必须同进类路径)。
  - **注册点必须 `Loader.isModLoaded("SpiceOfLife")` 包住** —— 继承类在**类加载阶段**就需要父类,守卫写进方法体已经太晚(同 §5.13 ThaumcraftCompat 的教训)。`CommonProxy.preInit` 里已这样写。
  - **reobf 产物已复核**(`javap -classpath build/libs/*.jar elinarcurios.item.ItemLunchBox`):`onEaten`→`func_77654_b`、`onItemRightClick`→`func_77659_a`、`addInformation`→`func_77624_a`,即覆写真的落在了原版方法上;第三方引用面用 `python D:\Agent\GTNH\ref\check_refs_lunchbox.py <jar>` 查,5 个 `squeek.*` 引用全部存在于运行时 jar,且只出现在 `ItemLunchBox` / `InventoryLunchBox` 两个类上(都在守卫之后才会加载)。
- **★ 进食同步(直接撞「不整包传 NBT」那条红线)**:1.7.10 服务端每 tick 跑 `player.inventoryContainer.detectAndSendChanges()`,把每个槽与上一 tick 的快照(`Container.inventoryItemStacks`)比对,**一旦不同就整包重发该物品(含全部 NBT)** —— 午餐盒是物品 NBT 存储的 12 页仓库,吃一口就改一次 NBT,于是**每吃一口把整个仓库推给客户端**(满载百 KB 量级)。
  - 两条一起用:①`LunchBoxEatSync`(S→C)只推「变化的那一格」(扁平索引 + 新堆叠;计数单独走 VarInt,因为 `ByteBufUtils` 里的 Count 是 byte,>127 会截断 —— 与 §5.3 同款处理);②`util/VanillaSyncGuard` 用反射把 `Container.inventoryItemStacks` 里午餐盒槽位的快照对齐成新值,于是下一个 tick 原版认为「无变化」、不再发整包。反射先按 `inventoryItemStacks` / `field_75153_a` 两个名字找,失败再按「元素不是 Slot 的 List 字段」兜底,**全失败就静默跳过**(退化成允许整包,功能不受影响,不崩)。
  - 客户端收到增量包后直接改手持物品的 NBT(`LunchBoxNbt.writeSlot`),界面开着时再刷新对应槽。
- **性能保护(两处都必要,别删)**:①`isEmpty()` 覆写成读 NBT 里的 `usedSlots` 计数缓存(O(1))—— `getIconIndex` 会**每帧**问一次「盒子空不空」,逐页扫 NBT 列表太浪费;②`getFoodValues()` 加 250ms 缓存窗口 —— AppleCore/SpiceOfLife 的 tooltip/HUD 在悬停期间反复查询,而父类实现每次都新建 Inventory + 全页选食,等于**每帧解析整个多页 NBT**。
- **交互**(`onItemRightClick`,**严格对齐原版 SpiceOfLife 语义,别再"优化"**):合盖右键 = 打开仓库并掀盖;开盖 + 能吃(非空 + 饥饿未满;创造模式也算,见下) = 进食;开盖 + 吃不了(空盒 / 不饿) = **不弹界面**,只在聊天栏给一句提示;潜行右键 = 开/合盒盖。**关界面不自动合盖**(SpiceOfLife 的 `ContainerFoodContainer` 会合,我们用自己的 `ContainerLunchBox` 所以不会)。
  - **★ 教训(2026-09-22 被芷若判为 bug)**:第一版我嫌"开盖却吃不了时右键没反应"体验差,改成一律弹仓库界面 —— 结果她在**不饿**的状态下测试,看到「右键总是开界面、无法进食」,直接报成 bug。`canEat(false)` 在饱食度满时本就是 false,**不饿不能吃不是缺陷**。原版语义本身就是规格,要偏离必须先说出来。
  - `canPlayerEatFrom` 覆写后比原版宽松一档:**创造模式也允许进食**(原版 `canEat` 的 `!capabilities.disableDamage` 让创造模式永远吃不了,SpiceOfLife 自己的午餐盒也有这毛病),方便测试与创造模式日常使用。
- **GUI**:`GuiLunchBox`(从 `GuiInfiniteBackpack` 复制改造,720→674 行),删掉装备/增益按钮,保留 12 页签 + `RenderItemInfinity` 大堆叠渲染 + 全部鼠标拖拽与 `InfiniteBagClick` int 点击协议。唯一必要替换:`drawSlot` 里 `instanceof InventoryInfiniteBackpack` → `InventoryLunchBox`(否则 243 个存储槽走原版渲染、>64 堆叠显示损坏)。界面贴图 `textures/gui/lunchBox.png` 直接复用背包版式;物品三态贴图 `lunchBox.png` / `lunchBox_open_empty.png` / `lunchBox_open_full.png` 程序生成。
- **顺带泛化的一处**:`RequestCurrentPagePacket` 原本只认 `ContainerInfiniteBackpack`,午餐盒(同为 `ContainerMultiPage` 子类、但无继承关系)那次页号查询会被服务端直接丢弃 → 判断改为 `instanceof ContainerMultiPage`。背包/箱子语义不变。
- **★ 实测修复记录(2026-09-22,她报「右键总是开界面、无法进食」)**:根因是为性能做的同步抑制**把客户端唯一的权威 NBT 通道也一并掐了** —— 客户端手里的盒子状态长期滞后 → 它以为自己吃不了 → 发 `OpenLunchBoxPacket` 请求开界面 → 旧 handler 无条件照办(服务端明明认为能吃,却被客户端带偏)。四处修复:①**开/合盒盖不再抑制整包同步**(低频动作,这条通道必须留着);②`OpenLunchBoxPacket` 只拒绝「正在进食时弹界面」,不再替客户端决定进食;③客户端容器视图重定位被服务端替换的物品实例(`InventoryLunchBox.retarget` + `ContainerLunchBox.retargetClientStack`,由 `GuiLunchBox.drawScreen` 每帧调用);④交互语义对齐原版(见上一条)。
- **★ 选食性能(必修,别把 LunchBoxSelector 换回原生)**:SpiceOfLife 一次进食会从**三处**各要一次"最优食物"(进食速度 `PlayerUseItemEvent.Start` / 食物值结算 `FoodEvent.GetPlayerFoodValues` / `onEaten`),每处都是全盒扫描;GTNH 的递减公式是 EvalEx 表达式,**实测 3.00 µs/次求值**,满 2916 格单次扫描约 13 ms。`util/LunchBoxSelector` 按 history count 分组(最多 21 次求值)+「内容版本 + 历史指纹」缓存 → 约 1~3 ms/口。**边界:一旦 `config\SpiceOfLife\*.json` 里有 `enabled=true` 的 food group,它会自动回退到 SpiceOfLife 原生实现**(那时 count 不再是 modifier 的唯一变量)。
- **待办**:①配方未做(与全 mod 一致,走任务书/奖励);②中文名「天厨·永恒食盒」仍是工作名,改 lang 两行即可;③若嫌物品贴图朴素,直接换 `textures/items/lunchBox*.png` 三张图。

---

### 5.21 已作废:样板输入总成PRO(v1.2.9 ~ v1.3.5,2026-09-25 全线作废)

**芷若 2026-09-25 拍板**:「你把版本回到1.2.8,1.2.9之前就作废了,以后也是从1.2.8开始开发」。⇒ 分支已退回 tag `v1.2.8`(`44daea8`),PRO 的三个文件(`machine/MTEHatchCraftingInputBusPro.java`、`recipe/CraftingInputBusProRecipes.java`、`textures/gui/crafting_input_pro_bg.png`)已从工作区删除,老版本里编译出的 class 也已清掉。

**作废原因(她实测)**:
- 「AE接口终端那边出问题了」。360 格样板总成在 AE 侧撑不住:接口终端按 `rows() × rowSize()` 索引并要同步/渲染全部格子,样板注册、NBT 与同步成本都随格数线性上涨。
- **她的方向性结论(重做时的路线)**:「我看别人ae开发团队的实现方式**应该是一个总线去模仿多个总线**」⇒ 正确的扩容不是"把一个总成撑到 360 格",而是**让一个总线对外表现为多个虚拟总线**(每个仍只挂 36 格)。**以后重做优先走这条**。
- 另:整合包里**已有一台同类机器在开发中**,她打算先看那台怎么实现再动手。

**作废但依然有效的技术结论(重做一定用得上,别当废纸)**:
- `provideCrafting` 用的是运行时 `arraylength`,**绝不要覆写它**(在它里面加日志会抛 ICCE 把 AE 打崩)。
- 滚动网格 = `Scrollable` + **逐个 `SlotWidget.setPos`**(GT++ `MTEHatchSuperBusInput` 是官方先例);**`SlotGroup` 是整块自动布局,不能拼**。
- 四颗按钮由 `CommonBaseMetaTileEntity.createWindow()` 写死在 `(170,10)/(170,28)/(170,46)/(194,10)`,**属于全体 GT 机器共用的模板**,别跟它对抗,只在自己的格子区做手术。
- MUI1 点击是 hover 驱动的:**矩形相交(哪怕只压 1px)就会被后加入的控件抢走点击**;竖直滚动条真实宽度是 **2px**(别照抄范例的 4px)。
- `internalInventory` 是 `final PatternSlot[36]`,扩容要用**字段真实组件类型**建数组 + 按参数类型挑构造器;handler 必须与 `mInventory` **共享同一份数据**(`new ArrayList<>(Arrays.asList(arr))` 会静默分叉)。

**考古位置(全都在,不要丢)**:
- 代码:`git tag v1.3.5` = commit `971630a`;作废线共 28 个 tag / 40 个提交,起点 `v1.2.9` = `db1c279`(首次加入 PRO)。**tag 全部保留未删**。
- 产物:`dist\ElinarCurios-v1.2.9.jar` ~ `dist\ElinarCurios-v1.3.5.jar` 全部留在 `dist\`。
- 记忆库:一条《样板输入总成PRO 扩容全史》topic + 三条教训 topic(八个被推翻的推断 / GT GUI 共用模板 / MUI1 点坑)。

**⚠️ 换回 v1.2.8 后,存档里已放置的 PRO 会怎样(已核实 2.8.4 源码,不会崩档)**:
MetaID 19083 不再注册 ⇒ 那些方块会打一行 `MetaID 19083 not loadable => locking TileEntity!` 并**锁死**(MTE 为 null:不工作、打不开)。`BlockMachines.breakBlock` 与 `getDrops` 都有 null 守卫(`canAccessData()` = `!isDead && hasValidMetaTileEntity()`),所以**拆掉是安全的**。最干净的顺序:先用 v1.3.5 那版进游戏把 PRO 拆掉、换回原版样板输入总成,再换回 v1.2.8 的 jar。

---

### 5.22 【已作废】水世界(Water World,2026-09-29 新增,2026-10-13 整条移除)—— 永不耗水的炼药锅

> **⚠️ 2026-10-13 芷若判定"这个水世界是个废案",四个文件 + 两处注册 + 配置项 + 双语 lang 键 + mixins 条目已全部删除。本节仅作考古记录,勿据此开发。**（下面第 560 行起那段 "dev 环境状态" 仍然有效,已并入 §5.23。）

**芷若需求原话(2026-09-29)**:「我想在ElinarCurios内增加一个永远装满水的炼药锅,无论如何消耗都是满的,合成出来的初始版本也是满的。而合成表就是原版炼药锅+8个铁水桶围一圈,你看是否可行,用哪种方案」;命名拍板:「直接叫"水世界"」;玻璃瓶取舍拍板:「不管,玻璃瓶照常无限装」。

**实现(commit `e12ee8d`,产物 `ElinarCurios-v1.2.8-master.3+e12ee8d28d.jar`)**:`block/BlockWaterWorld extends net.minecraft.block.BlockCauldron` + `ItemBlockWaterWorld` + `recipe/WaterWorldRecipes`。

- **锁水位只有一处**:原版把水位存在方块 metadata(0~3),**所有**变更都走唯一收口 `func_150024_a(World,int,int,int,int)`。覆写它 ⇒ 无论谁来写(灭火 `-1` / 装瓶 `-1` / 洗染色 `-1` / 降雨 `+1` / 水桶写 `3`)落地都是 `metadata 3`。附带覆写 `onBlockPlaced`(放置即满)与 `damageDropped`(掉落也是满的)。**用 `getWaterLevel/setWaterLevel` 会编译不过**:1.7.10 的真实成员名是 `func_150024_a` / `func_150027_b`(2026-09-29 javap 核 `build/rfg/recompiled_minecraft-1.7.10.jar` 确认,当时照着解压源码的注释猜名字,白吃一次编译错误)。
- **渲染零成本**:`RenderBlocks` 对 `getRenderType() == 24` 的方块<b>强转成 `BlockCauldron`</b> 并调用 `renderBlockCauldron`,子类天然命中;四张 `cauldron_side/top/inner/bottom` 静态贴图沿用原版,无需新增美术资源。物品侧 `getMetadata` 恒 3 + `getSubItems` 只列一份 ⇒ 创造栏/NEI 不会出现"空水世界"。
- **配方**:`8 × etfuturum:iron_barrel` 围 `1 × 原版炼药锅` → 水世界 ×1。EFR 是 GTNH 必装但本仓库**不加编译依赖**,运行时 `GameRegistry.findBlock("etfuturum","iron_barrel")` 查(查不到只警告);守卫写在调用点(`CommonProxy.init` 的 `Loader.isModLoaded("etfuturum")`)。注册名来源已核实:EFR jar 里 `tile.etfuturum.iron_barrel.name=Iron Barrel`。
- **已知代价(她拍板接受)**:空玻璃瓶可无限右键装水成水瓶(原版靠"装瓶掉一级水位"限制);水桶右键永远满、装不干。
- **诊断日志**:`WaterWorldRecipes.logRegistration()` 与 `ReactorCoreRecipes` 同款,加载时会打 `[ElinarCurios] water world recipe registered: barrel=… cauldron=… output=…; recipes with this output = N`,用来一眼分清"配方没注册进去"与"注册进去了但 NEI 不显示"。**（代码已随废案删除,此段仅存其思路。）**

**⚠️ dev 环境状态(2026-09-29 实测,动 runClient 前先读)**:本项目 `runClient` **至今起不到主菜单**,与 mod 代码无关,是两个 dev 类路径缺口:
1. `usesMixins=true` 让 GradleStart 加 `--tweakClass org.spongepowered.asm.launch.MixinTweaker`,而 unimixins 只声明了 `compileOnly` ⇒ `ClassNotFoundException`。已在 `dependencies.gradle` 补一条 `runtimeOnly` 指向同一份本地 jar(**只影响 dev,不进成品 jar**)。
2. 补完之后的下一个报错是 `LoaderException: NoClassDefFoundError: elinarcurios/item/ItemWirelessBattery` —— GT5U/IC2 **没有进 dev 运行期类路径**(同样是 `compileOnly`),而 `GTNewHorizonsCoreMod` 会去加载那个类。要真修得把 GT5U/IC2 及其整条依赖链(AE2/GTNHLib/MUI…)加进 `runtimeOnly`,属于独立工程。
⇒ **本 mod 的实机验证只能在她实例里做**（历史与现状皆然）；新增元件/新功能改用离线行为探针验证逻辑，游戏内集成（注册/贴图/配方/tooltip）由她换 jar 确认。完整口径见 §5.23 末尾。

---

### 5.23 菲涅尔透镜(Fresnel Lens,2026-10-13 新增,已随 v1.2.9 发版)—— 全堆均匀中子辐照元件

**芷若需求原话**:「有个这东西的生产过程,就是放在核反应堆内,靠着普通核燃料棒,它就会接受辐射,接受的辐射到达一定程度,就会从荧石变成阳光化合物棒。我现在就像写一个元件,直接放进反应堆里,就会给每一格均匀的辐射」;命名拍板:「我希望这个元件叫 菲涅尔透镜」。

**机制前提(全部按字节码/反编译核实)**:GT5U 增殖棒 `ItemBreederCell`(荧石棒→阳光化合物棒 / 锂棒→氚棒)每收一次脉冲 `进度 += 1 + 堆芯热量/3000`、满 10000 换产物;它**只在热趟(heatrun=true)累加**,而热趟的脉冲**不产生任何 EU**(燃料棒的 `addOutput` 写在 `!heatrun` 分支)。⇒ **要催熟走热趟,要刷产能走能量趟,两条路不可混。**

- **实现**(`item/ItemElinarFresnelLens.java`,`implements IReactorComponent`,零状态):热趟遍历 `MAX_COLUMNS=9 × ROWS=6`,`IReactor` 没有 `getReactorSize()` 而 `getItemAt` 自带越界检查,所以全扫 + 跳过 null 即可覆盖任意仓数。
- **合格判据是结构性的**:`canStoreHeat()==false && getMaxHeat()==0`。增殖棒成立;燃料棒(被打到会返回 true → 进相邻棒的 `checkPulseable` → `triangularNumber` 平方级产热,可能爆堆)、IC2 反射板(每次被脉冲扣耐久)、GT5U 反射板(会把脉冲转发回燃料棒)、冷却单元/散热片/隔板全部排除。**绝不按类名判断。**
- **元件自身**:不储热、`alterHeat` 原样返回、`acceptUraniumPulse` 恒返回 false(否则会被算作中子反射板);整段目标判定包 `catch Throwable`(堆芯 tick 里抛异常会打崩游戏)。
- **红石门禁:刻意不加** —— 透镜在热趟只发脉冲、不产 EU 不产热,关堆时热趟照常发生、堆芯也不产热,于是关堆与开堆的催熟速度完全一致;加了反而会出现"关堆长进度、开堆不长"的错位。(对比:天玄·恒温核心在**能量趟**做功产 EU,那道门禁必须加。)
- **数值**:`Config.fresnelLensPulsesPerTick` 默认 **4**(芷若拍板)。每格每秒进度 `= 4 × (1 + 堆芯热量/3000)` ⇒ 堆芯 0 热时约 2500 秒全堆同清、9900 热时约 590 秒。**透镜刻意保持热中性、不改数值平衡**,速度完全由玩家堆的堆芯热量决定;放 N 枚 = 每格每 tick `4N` 次脉冲,线性叠加。
- **配方**:8 原版玻璃板围 1 块 IC2 中子反射板(`ic2.core.Ic2Items.reactorReflector`);`recipe/FresnelLensRecipes.java`。
- **注册**:`CommonProxy.preInit` 的 `Loader.isModLoaded("IC2")` 守卫内(调用点守卫,无 IC2 时该类不会被加载)。
- **验证**:离线探针 17 项断言全过(真实编译产物 + 真实 IC2/Minecraft jar 直驱 `processChamber`,不需要启动游戏)→ 热趟每格 4 次 / 能量趟零次 / 不产 EU / 不动 heat·maxHeat·hem / 储热类元件被跳过 / 空堆不崩 / 多枚线性叠加;源码存档 `D:\Agent\Work\lensprobe\LensProbe.java`。**她已实机测试确认没问题。**

**⚠️ dev 环境状态(2026-09-29 起,动 runClient 前先读)**:本项目 `runClient` **至今起不到主菜单**,与 mod 代码无关,是两个 dev 类路径缺口:
1. `usesMixins=true` 让 GradleStart 加 `--tweakClass org.spongepowered.asm.launch.MixinTweaker`,而 unimixins 只声明了 `compileOnly` ⇒ `ClassNotFoundException`。已在 `dependencies.gradle` 补一条 `runtimeOnly` 指向同一份本地 jar(**只影响 dev,不进成品 jar**)。
2. 补完之后的下一个报错是 `LoaderException: NoClassDefFoundError: elinarcurios/item/ItemWirelessBattery` —— GT5U/IC2 **没有进 dev 运行期类路径**(同样是 `compileOnly`),而 `GTNewHorizonsCoreMod` 会去加载那个类。要真修得把 GT5U/IC2 及其整条依赖链(AE2/GTNHLib/MUI…)加进 `runtimeOnly`,属于独立工程。
⇒ **本 mod 的实机验证目前只能在她实例里做**;新增元件/新功能改用上面的离线探针做行为验证,游戏内集成(注册/贴图/配方/tooltip)由她换 jar 确认。

---

### 5.24 装配线样板镜像总成（物品 / 流体，2026-10-16 并入，v1.2.11~v1.2.12）

两个方块,把**现成的样板总成**（原版 CRIB,或 GTNL 的 360 格总成）按“第 N 格”喂给装配线:

| 方块 | 类 | mName | ID | 放置位置 | 中文名 |
|---|---|---|---:|---|---|
| 物品总成镜像 | `MTEHatchPatternMirrorItem` | `alap_pattern_mirror_item` | 19102 | 装配线输入总线位（每片底部中央 `i`/`I`） | 物品总成镜像 |
| 流体总成镜像 | `MTEHatchPatternMirrorFluid` | `alap_pattern_mirror_fluid` | 19103 | 装配线输入仓位（底部侧边 `b`） | 流体总成镜像 |

- **绑定**:用闪存先**左键**样板总成（记坐标）、再**右键**镜像;两者都按“坐标现查”,总成被拆/移走时自然失效。格号默认按装配线位置自动识别,也可以在镜像 GUI 里改;放置后的镜像只对外提供“总成第 N 格物品 / 第 M 格流体”。
- **零搬运**:镜像自身不存料（库存 0 格）。物品版 `getFirstStack()` / 流体版 `getFillableStack()` 直接返回总成材料袋里**那一堆的引用**;装配线消耗时执行 `stackSize -= n` / `amount -= n`,改的就是总成里的数量,没有搬运事务,也不存在“投递一半失败”。
- **为什么不能实现 `IDualInputHatch`**:`MTEMultiBlockBase.addInputBusToMachineList` / `addInputHatchToMachineList` 会先判这个接口并塞进 `mDualInputHatches`,而装配线只遍历 `mInputBusses` / `mInputHatches`（`GTRecipe.RecipeAssemblyLine#getItemConsumptionAmountArray` 等）⇒ 挂在装配线上会“永远读不到”（现成 CRIB 直接挂装配线就是这个症状）。两个镜像只分别继承 `MTEHatchInputBus` / `MTEHatchInput`。
- **格位来源**:样板自身的 `ICraftingPatternDetails.getAEInputs()`（编码时的格位序列,未被 AE 的 `expandedInputs` 展开）。**不要用 `getPatternInputs()`**——原版 CRIB 的返回数组开头混了 `getSharedItems()` 的电路/手动槽,按“第 k 格”会取到电路。两种总成的 details 入口不同:原版 CRIB 在 `patternDetails` 字段,GTNL 提供 `getPatternDetails()` 方法;`PatternMirrorSource` 先方法后字段并做类级缓存。
- **多样板槽的选槽**:先扫“有料”的槽（AE 一次只推进一条配方,正常唯一命中）;显示路径第二轮才退回“有样板但还没收到料”的槽。**AAL 场景**:进阶装配线每片都会重读总线,大电流超频后配方 <1 秒、批次接连不断;旧逻辑“第一个该格有料的槽”会让某片读到别的配方的同格物品 → `Slice.start()` 失败且进度锚死 → **永久堵**。v1.2.11/12 已改为“知道 `currentRecipe` 时只认与该配方第 k 格匹配的样板槽”,并在批次边界缓存 20 tick。
- **格位自检**:`PatternMirrorSource.checkRecipeMapping` 拿样板产物反查装配线配方,比对“样板第 k 个物品 vs 配方第 k 格”,不符时在 GUI 红字提示（对得上就不显示,符合“留白”偏好）。**不需要装配线在场**——放 CRIB + 样板 + 镜像、让 AE 推一次料就能提前验掉“样板顺序 ≠ 配方格位”的假设。
- **限制**:镜像只读每个输入总线/仓的第一个堆（装配线语义）;多个样板槽同时有料时依赖“当前配方”或“有料优先”选槽;总成里的材料袋按种类合并,格位只能从样板读。

### 5.25 高级装配线（High-Tier Assembly Line，2026-10-18 并入，v1.2.13）

- **来源**:独立验证工程 `D:\Agent\ElinarAssemblyLine`（ElinarAssemblyLine 0.1.1，2026-10-18 芷若实机测试通过）;按原 **ID 32030** / mName **`elinar.machine.high_tier_assemblyline`** 并入,避免她测试存档里已放置的方块失效。类:`elinarcurios.machine.MTEHighTierAssemblyLine`（`MTEExtendedPowerMultiBlockBase` 子类）。
- **结构**:与 GTNH 原版装配线 1:1（3×4×5~16 片,投影仪盖法不变）;只有 `'e'` 能源仓位的白名单变了。
- **`'e'` 三选一**:普通能源仓 / 多安能源仓 / 激光仓（`ExoticEnergy`）/ GTNL 并行控制仓（`ParallelControllerElement`）。结构校验相应从 `checkHasEnergyHatch` 改成 `checkHasAnyEnergy`。
- **并行**:有 GTNL 并行控制仓 → 反射读它的 `getParallel()`（LuV 上限 256 / ZPM 1024 / UV 4096,GUI 可往下调）;没有控制仓 → **GTNL 已安装默认 8、未安装默认 64**。实际并行 = `min(输入总线能支持的份数, 并行上限, 功率允许)`,再把结果交给原版的 1t 压缩 / batch 逻辑;取料仍是原版“每片一个输入总线、按 NEI 顺序”。
- **升压**:保留 `配方 EU/t ≤ 平均电压 × 4` 等级门——两个 LuV 普通 2A 能源仓可直接跑 ZPM 配方（双仓升压）,与 §5.19/§2 的电压口径一致。
- **供电口径**:父类 `MTEExtendedPowerMultiBlockBase` 的 `getAverageInputVoltage()` / `getMaxInputAmps()` / `drainEnergyInput()` 本来就把 `mExoticEnergyHatches` 算进去;所以只需放开白名单 + 修 `amperageOC` 的单仓判断（`!(mEnergyHatches.size()==1 && mExoticEnergyHatches.isEmpty())`）,不需要重写能量逻辑。
- **GTNL 软依赖**:`Loader.isModLoaded("sciencenotleisure")` + `Class.forName("com.science.gtnl.common.machine.hatch.ParallelControllerHatch")`;GTNL 不在时并行控制仓元素永远匹配失败,该位置仍可放普通/激光仓,机器按 64 并行运行,不会崩。
- **配方**:暂用独立验证版的原型配方（钻石 + 末影珍珠 + 钻石块 + 下界之星,见 `HighTierAssemblyLineRecipes`）,正式平衡配方待定;机器本体不依赖配方。
- **编译注意**:独立 addon 编译时踩过两个坑,本工程已处理:①`libs/jvmdg-stub.jar`（compileOnly,GT5U 的 `StructureErrors` record 基类）;②GT5U multi-release jar 的 Java17 变体没有 `canInsertItem` / `canExtractItem` / `getAccessibleSlotsFromSide` / `isItemValidForSlot` / `openInventory` / `closeInventory` 等 IInventory 方法,机器类里显式实现了一遍。
- **安装注意**:v1.2.13 与独立验证版 **不能同时装**——两者都注册 32030;换 v1.2.13 前先删掉 `ElinarAssemblyLine-0.1.1.jar`。

### 5.26 高级放射仓（Advanced Radio Hatch，2026-10-31 并入，v1.2.27）—— 消耗速度 1/10000 的原版放射仓

- **需求原话（芷若 2026-10-31）**：「gtnh原版有个仓室叫放射仓，消耗材料太厉害了……做一个新的“高级放射仓”，保持原来的材料表不变，但是消耗材料速度是原来的万分之一。例如镅杆时间是59.9秒，在“高级放射仓”内就是599000秒。」合成口径：「9个原版放射仓合成一个」。外观：采用原版材质。
- **机制（实机 GT5U 5.09.54.205 反编译核对）**：原版放射仓（`bartworks.common.tileentities.tiered.MTERadioHatch`，直接打包在 GT5U jar 里）的消耗模型 =「每 `decayTime` tick 消耗 1 点 mass」；`decayTime = calcDecayTicks(SV)`，`SV ≤ 100` 时 `ceil((8000·tanh(-SV/20)+8000)×1000)` ticks，否则 `ceil(8000·tanh(-SV/65)+8000)`。镅（95 质子）→ **1198 ticks = 59.9 秒**，与她说的一致。
- **实现**：`machine/MTEAdvancedRadioHatch extends MTERadioHatch`，只覆写 `onPostTick`：super 跑完后检查父类是否重设了 `decayTime`（装入新放射性材料时才会），一发现就 `decayTime × 倍率` 并记录原始值；`saveNBTData/loadNBTData` 额外存 `elinar.radhatch.rawDecayTime`，避免读档后二次放大。材料清单、SV、mass、覆盖度/辐射闸门、GUI 全部继承 —— 材料表不变，只是每根材料熬的时间 ×10000。
- **注册**：ID **19104**、mName **`elinar.machine.radio_hatch_advanced`**、tier **3（HV）**（放射仓的 tier 只决定贴图颜色，不影响多方块结构；选 HV 就让外观与原版基础放射仓完全一致）。ID 台账见 `ElinarMachines` 类注释（19082/19084/19102/19103/19104/32030；19083 留给独立版 ElinarInputHatch）。
- **配方**：`recipe/AdvancedRadioHatchRecipes` 遍历 `MetaTileEntityIDs` 里所有 `RadioHatch_` 条目（HV~MAX 共 12 级），各注册一条 3×3「9 个该等级放射仓 → 1 个高级放射仓」；任一等级都能合。
- **兼容**：细菌培养缸（`MTEBioVat`）用 `instanceof MTERadioHatch` 认放射仓，子类天然通过；`RadioHatchElement` 仍只允许每个机器 1 个放射仓。`getRecipeMap()` 继承 `radioHatchFakeRecipes`，NEI 里按 U 查高级放射仓能看到完整放射性材料表。
- **配置**：`config\ElinarCurios.cfg` 的 `advancedRadioHatchMultiplier`（默认 10000，范围 1~1000000）。**改完立即生效**（机器每 tick 读一次），不需要重放方块；读档时也会按新倍率重算。
- **验收现象（进游戏后应看到）**：①NEI 搜「高级放射仓」有物品，U 键能看到 12 条 9 仓合成表；②放到细菌培养缸的放射仓位置能成型（每个机器只允许 1 个放射仓）；③放入镅杆后，仓的 GUI / 三录仪剩余时间从 59.9 秒变成 **599,000 秒（约 166.4 小时 / 6.9 天）**；④螺丝刀右键仍能打开辐射闸门调节；⑤`config\ElinarCurios.cfg` 出现 `advancedRadioHatchMultiplier=10000`。
- **注意**：依赖 GT5U 内部 `bartworks` 类（不是独立 mod），GT5U 升级大版本后若该类变动需重编；当前按 2.9.0 RC-2 的 5.09.54.205 校验过（类/方法签名与 RC-1 的 5.09.54.183 一致）。

### 5.27 `/Elinar` 命令（综合采掘工厂的查询 / 批量改并行 / 帮助，2026-11-02，v1.2.28）

- **需求原话（芷若 2026-11-02）**：「查询所有矿机位置 `/Elinar infiore`」「将所有矿机配置设置为 流体10并行 矿石256并行 `/Elinar setore 10 256`」「模组命令格式提示 `/Elinar help`」；`infiore` 的目标行格式就是她给的那一行：`综合采掘工厂 0（主世界） -48 83 -61（坐标） 流体10X 矿石256X`。
- **文件**：`command/CommandElinar.java`（子命令分发 / 输出 / 边界检测）、`machine/MinerRegistry.java`（机器登记表）、`util/DimensionNames.java`（维度 ID→中文名）、`machine/MTEComprehensiveMiningPlant.applyConfiguredParallel()`（立即生效）。命令仍在 `ElinarCurios` 类里 `registerServerCommand` 注册，没有新增注册点。
- **`/Elinar infiore`**：逐台一行 `综合采掘工厂 <dim>（<中文名>） <x> <y> <z>（坐标） 流体<configuredFluid>X 矿石<configuredOre>X`，同时 `System.out.println` 一份到日志；无机器时提示「无（只统计已加载区块里的机器）」。**并行显示的是「配置值」**（`configuredOre/configuredFluid`，也就是 GUI 并行面板里填的数），不是当前正在跑的运行值。
- **`/Elinar setore <流体并行> <矿石并行>`**：参数顺序**先流体后矿石**（她给的例子 `setore 10 256` = 流体 10 + 矿石 256）。流程：解析整数 → 边界检测 → 只对通过检测的参数动手。范围 = 流体 `0~2000`、矿石 `1~48000`（直接读 `MAX_FLUID_PARALLEL/MAX_ORE_PARALLEL` 常量，以后改上限命令自动跟上）。超限时**只回提示、不改任何机器**（不会半套生效）；额外做了个顺序提示：第一位超过 2000 而第二位在矿石范围内时，回「第一位是流体、第二位是矿石，是不是顺序反了？」。改完输出一行汇总（台数 + 并行 + `(流体+矿石)×32768` 的 EU/t）+ 每台一行的现状，方便当场核对。
- **立即生效（不用开关机）**：`applyConfiguredParallel()` = 把 `oreParallel/fluidParallel` 直接换成配置值 → 重算 `computeDropMap()` / `computeFluids()` → 若机器正在运行（`mMachine && isWorkEnabled()`）按新容量 `refillEnergy()` 补缓冲。细节：①并行**调低**时 `energyBuffer` 超出新容量的部分**退回无线电网**（`addEUToGlobalEnergyMap` 传正数），不静默吞电；②**停机中的机器不补电**（补了也用不上，还会白扣电网）；③补电失败（电网不够）时机器按现有缓冲继续跑，命令里额外提示「N 台无线电网电量不足」。**GUI 并行面板仍是「下次启动生效」语义**（原逻辑只在 `onWorkStart()` 读配置），只有命令是立即生效，别把两者混为一谈。
- **登记表 `MinerRegistry`**：只在服务端 `onFirstTick` 登记；`unregister` 在 `onRemoval` 里按**对象身份**摘除（`onRemoval` 时坐标/世界可能已经取不到了，按 key 删会漏）；`snapshot()` 再用 `MetaTileEntity.isValid()` 兜底清理跨存档残留。**坑**：GT 的 `BaseMetaTileEntity.invalidate()` 里 `super.invalidate()` 之前先写 `tileEntityInvalid = false`，所以 `TileEntity.isInvalid()` 对 GT 机器永远返回 false，不能用它判活；`isValid()` 才是对的（base 非空 + `getMetaTileEntity() == this` + 未 dead）。
- **维度中文名 `util/DimensionNames.java`**：服务端 `StatCollector` 语言固定 en_US，拿不到客户端汉化 ⇒ 直接写死 ID→中文表。号段来源是 **GTNH 2.9.0-RC-2 实例的真实配置**（`Galacticraft/core.conf`：月球 28、主世界轨道 27/26；`planets.conf`：火星 29、小行星带 30；`GalaxySpace/dimensions.conf`：25~88 的其余行星与轨道；`TwilightForest.cfg`：7；`ExtraUtilities.cfg`：深层空间 100），中文名取整合包自带汉化（`txloader/load/.../zh_CN.lang` 的 `dimension.*.name` + GalaxySpace 成就名，如「鸟神星」「鲸鱼座T星E」「织女一B」）。表里没有的维度退化成 `WorldProvider.getDimensionName()` 的原始名；取名字抛异常也不会让命令报错。
- **`/Elinar help`**（别名 `?`）：列出三条命令、范围与例子；不带参数或未知子命令时也会自动打印。
- **验收现象（进游戏后应看到）**：①`/Elinar help` 出现三条命令说明；②`/Elinar infiore` 每台机器一行、维度显示中文名（主世界/小行星带/月球…）、末尾带 `流体NX 矿石MX`；③`/Elinar setore 10 256` 回「已设置 N 台…（已立即生效，无需开关机；耗电 …EU/t）」并列出每台；此时打开机器 GUI 的并行面板应是 10/256，且**耗电立刻按新并行计算、不用软锤开关机**；④`/Elinar setore 5000 256` 回流体上限提示且机器不变；⑤`/Elinar setore 10 99999` 回矿石上限提示且机器不变。
- **未验证**：以上均为编译 + 代码走查结论，**尚未实机跑过**（2026-11-02 交付，等芷若实机）。

### 5.28 综合采掘工厂：AE 批量倾倒（2026-11-02，v1.2.29）

- **需求原话（芷若 2026-11-02）**：「现在有个性能上的问题，就是我在想能不能将综合采掘工厂改成矿石和流体先进机器缓存，然后每120秒倾倒一次进ae网络，并在配置文件内提供设置，最大为86400秒，最小为0秒（实时），默认为120秒」。
- **配置**：`config\ElinarCurios.cfg` 的 `infMinerFlushSeconds`（默认 **120**，范围 **0~86400**；**0 = 实时**，即每秒倒一次的原行为）。改完要重启游戏（`Configuration` 只在启动时读一次）。机器 tooltip 会动态显示当前间隔。
- **实现**：矿石/流体仍然每秒 `generateOres()/generateFluid()` 生成进 `itemCache` / `fluidCache`（本来就是 `HashMap<ItemId,Long>` / `HashMap<Fluid,Long>`，只存数量，攒 120 秒不占额外内存）；`flushToAE()` 改由 `shouldFlushToAe()` 的秒表控制（`flushCounter`，在 `onWorkStart` 归零）。GUI 里「矿石采掘速度」那几行照旧每秒刷新（读的是当秒产量，与倾倒频率无关）。
- **边界处理**：①**停机（软锤/断电）也按间隔补倾倒**，免得关机器后产出卡在缓存里等下次开机；②**拆控制器时 `onBlockDestroyed()` 先 `flushToAE()`**，两分钟的批次不会随方块消失（AE 离线时送不进去就算了）；③缓存本身进 NBT（`items` / `cacheFluids`），停服/保存/读档不丢；④AE 满或离线时缓存继续增长（只涨 long 计数），下次阈值到了再重试。
- **已知取舍**：48000 矿石并行下 120 秒约 **3.07 亿件/批**，单次插入更集中（这正是"用低频换高频"的目的）。要更平滑就把 `infMinerFlushSeconds` 调小。
- **未实机验证**：2026-11-02 交付，只做了编译 + 代码走查。

### 5.29 综合采掘工厂：流体读数单位修正（2026-11-05，v1.2.30）

- **现象（芷若 2026-11-05）**：「综合采掘工厂的流体显示 80000B 每秒，实际是 80B 每秒，或者你把显示的单位改成 mB」。
- **根因**：`MTEComprehensiveMiningPlant#getFluidRateLines()` 里 `rateText` 的**单位字符串写错**。`rate = amount × fluidParallel / 5000`，而 `amount` 来自 `GTUOFluid#getRandomAmount()`（GT 地下流体油藏数据，量级 ≈ `MaxAmount × DIVIDER`），`/5000` 之后得到的就是 **mB/秒**——同一批数值直接进 `fluidCache` 和 `new FluidStack(fluid, amount)`，而 `FluidStack` 的量纲恒为 mB。所以 80000 是 mB/s = 80 B/s，**产量本身没错，只是标签少了 `m`**。
- **改动**：只改单位后缀，数值/阈值逻辑原样（`≥1000` 用整数、否则保留 1 位小数）——`"B/秒"` → `"mB/秒"`（两处）。
- **验收现象**：机器 GUI「流体采掘速度」行显示成 `流体名 80000mB/秒`（80 B/秒），与 AE 里实际增长速率一致。
- **顺带结论（三模式性能分析，详见 `D:\Agent\GTNH\ref\综合采掘工厂-三模式性能分析.md`）**：后期「结算模式」相对现有「汇集模式」性能收益**很小**（每 120 秒只省掉 119 次 O(矿种数) 循环 + 119 次产物显示发布），AE 侧 `poweredInsert` 次数与批插尖峰**完全相同**；相对「实时模式」才是真降 120×。

### 5.30 综合采掘工厂：三档产出模式 mod1/mod2/mod3（2026-11-05，v1.2.31）

- **需求原话（芷若 2026-11-05）**：「我想提供三个模式选择。前期的实时模式，就像以前一样每秒结算矿物后直接塞进AE网络里…中期的汇集模式，就是每秒产出都汇集在机器内部，每120秒再统一灌进AE网络里。后期的结算模式，每120秒计算并产出一次矿物，并灌入AE内部」；并要求「在机器gui设置并行的那里增加三种模式的选择按钮」+「增加一个全局命令 /Elinar setore mod1/mod2/mod3」。她同时明确「其实现阶段也不卡，就是为了避免后期有人去堆这个机器」——所以这是**玩法分档 + 未来防堆机器**，不是当下的救火优化。
- **三个档位**（`MTEComprehensiveMiningPlant.MODE_*`，对外就叫 mod1/mod2/mod3）：
  - `mod1 实时`：每秒产一次 + 每秒 `flushToAE()`，= v1.2.28 及以前的行为；
  - `mod2 汇集`：每秒产进 `itemCache`，每 `infMinerFlushSeconds` 秒批量倒，= v1.2.29 的行为（**默认档**）；
  - `mod3 结算`：每 `infMinerFlushSeconds` 秒才 `generateOres/generateFluid` 一次并倒，中间那些秒只累计 `settleSeconds`。
- **存盘**：每台机器的档位存自身 NBT `miningMode`；新加的 cfg `infMinerMode`（默认 **2**，范围 1~3）只决定**新放置**机器用哪档；老存档没有 `miningMode` 时按 cfg 默认开局（升级前后行为一致）。`settleSeconds` 也进 NBT，读档后继续攒，不白丢最多一个间隔的产出。
- **实现要点**（改这块先看这里）：
  - `onPostTick` 里只有一个秒表：`tickSecond = aTick % 20 == 0`。mod1/mod2 走 `shouldFlushToAe()`（mod1 的 `getIntervalSeconds()` 恒 1），mod3 走 `settleSeconds++` 到点结算；**停机分支不结算**，只按间隔补倒缓存（`settlePending()` 只在切档/拆机时补算）。
  - 结算档只数「机器真正在跑」的秒数（停机不计），否则关掉机器再开就能白拿产出。
  - `generateOres(int seconds)` 的 amount 用 **long**：48000 并行 × 86400 秒（cfg 上限）会溢出 int。
  - 产物显示刷新频率跟着模式走：mod1/mod2 每秒刷 `mOutputItems/mOutputFluids`，mod3 只在结算那一下刷。
  - `settlePending()` 在 `setMode()` 和 `onBlockDestroyed()` 里调用，避免切档/拆机吞掉未结算的产出。
- **GUI**：并行副面板（右上螺丝刀按钮）里新增「产出模式」一行三个按钮 `mod1 实时 / mod2 汇集 / mod3 结算`，**选中档绿字加粗**，点哪档当场生效（`InteractionSyncHandler` 只在服务端改，改完 `mode.notifyUpdate()` 同步回客户端）；下方一行动态说明当前档。副面板尺寸 180×138；标题改成「并行 / 产出模式」，提示行「并行：下次启动生效；模式：立即生效」。
- **命令**：`/Elinar setore mod1|mod2|mod3` —— 批量把所有（已加载的）采掘工厂切到该档（立即生效，无需开关机），**并把该档写回 `config\ElinarCurios.cfg` 的 `infMinerMode`**（`Config.setInfMinerMode()`），这样之后新放的机器也跟着。数字形式的 `/Elinar setore <流体并行> <矿石并行>` 行为完全不变（`mod` 开头的参数会先被判成模式词）。
- **其它显示**：WAILA 与 GT 的「机器信息」面板都多了一行 `模式: 汇集（每 120 秒倾倒一次）`；tooltip 里列了三个档位。**`/Elinar infiore` 那行没动**（按她 2026-11-02 给的样例逐字对齐，没加模式字段）。
- **未实机验证（2026-11-05 交付）**，验收现象：
  1. 打开机器 GUI → 右上螺丝刀 → 副面板里有「产出模式」三个按钮，当前档是绿字加粗（老机器应为 `mod2 汇集`）；
  2. 点 `mod3 结算` → 绿字跳到 mod3，下面说明变成「结算：每 120 秒结算一次」；WAILA/信息面板的「模式」行跟着变；
  3. 敲 `/Elinar setore mod3` → 聊天栏回「已把 N 台综合采掘工厂切到 mod3（结算：每 120 秒结算一次并倾倒），立即生效…」，并逐台列出坐标行；重进世界后档位还在（NBT）；
  4. 结算档下 AE 里每 120 秒跳一批货（中途不动）；切回 mod1 后恢复每秒都有货。
- **性能口径**：本次实现**不做**额外优化；`mOutputItems/mOutputFluids` 封顶 Top 12 仍是待做项（三档共用收益）。

### 5.31 样板总成侦测器（Pattern Assembly Detector，2026-11-07 新增，v1.2.32）

- **需求原话（芷若 2026-11-07）**：「我想给ElinarCurios增加一个工具方块…如果总成内有物品（有合成的任务下发到这个总成），它就会发出红石信号，并且可以设置这个红石信号在总成那边清空后还会持续多少秒后熄灭。你先调查下实现原理和方式」。拍板时的用途说明：「这个方块的作用其实是为了省材料，例如某台机器开机时候会一直消耗耗材，那这个方块检测到总成发送物品时候在发出红石信号让机器开机工作，如果方块不发出信号机器就不开机，所以其实不需要考虑合成过程中的状态，这个红石持续时间让玩家自行设置就行，范围从1秒到2147483647秒。」；另拍板「红石信号直接6个面都发送。」「合成表是9个红石火把。」「名称叫“样板总成侦测器”」。
- **状态语义**：只看「绑定的样板总成材料袋里有没有料」这一个布尔量（`IDualInputHatch#getFirstNonEmptyInventory().isPresent()`，任意一条样板槽非空即算），**不看合成进度**（她的要求）。有料 → 六面 `setOutputRedstoneSignal(15)`；料空 → 继续亮 `holdSeconds` 秒后熄灭；期间再来料重新计时。
- **为什么不需要「合成中」状态**：GT 的 `GTRecipe.consumeInput` 直接对总成里那些 ItemStack **引用**做 `stackSize -=`（`GTRecipe.java:653`），配方一开始料就被扣走 ⇒ 总成只在「AE 推料到机器开工前」有料。这正是她要的「总成发送物品 = 上料脉冲」，所以延时只需覆盖停机间隙。
- **读总成**（复用镜像）：`world.getTileEntity` → `PatternMirrorSource.unwrapMaster`（2026-11-07 起改为 public）→ `instanceof IDualInputHatch`。原版 CRIB（`MTEHatchCraftingInputME`）与 GTNL 的 `SuperCraftingInputHatchME` 都实现该接口（后者用 javap 反编译确认 `getFirstNonEmptyInventory` 语义一致）；不编译期引用 GTNL 类。
- **发红石**：`MTETieredMachineBlock` + `allowGeneralRedstoneOutput() → true` + 逐面 `setOutputRedstoneSignal`（链路 `BlockMachines.isProvidingWeakPower` → `BaseMetaTileEntity.getGeneralRS`）。GT 自家 `MTEHeatSensor` 同款做法。
- **★ 区块加载坑**：`World.getTileEntity` 会经 `getChunkFromChunkCoords` 把区块拉起来 ⇒ 探测前必须 `world.getChunkProvider().chunkExists(masterX>>4, masterZ>>4)`；未加载时**不探测、也不减倒计时**（保持信号、不闪断、不把远处区块拉起来）。每 5 tick 探测一次。
- **★ 事件驱动（v1.2.33 修，解决「料刚到就被机器吞掉」）**：只靠 5 tick 轮询时，AE 推来的料可能只停留 1 个 tick（推料后下一 tick 机器 `checkRecipe` 就按 `GTRecipe.consumeInput` 扣走），采样有概率漏检 ⇒ 芷若 2026-11-07 提出「600 秒快结束时料进来又瞬间被机器吞掉，能不能续上」。修法：侦测器实现 `IHatchWatcher`，在探测时 `((ISmartInputHatch) hatch).addWatcher(this)` —— CRIB 的 `justHadNewItems`（`MTEHatchCraftingInputME.onPostTick` 第 545-552 行）与 GTNL 的 `pushPattern → notifyWatchers()` 都会回调 `scheduleRecipeCheckImmediate()`，**推料那一刻直接把倒计时重置为满值**，与轮询相位无关。注销点：换绑、方块被拆（`onBlockDestroyed`）、区块卸载（`onRemoval`，`BaseMetaTileEntity.invalidate` 会调）、总成被拆（下一轮探测发现不是 `IDualInputHatch`）。轮询保留：兜底手动放料等不走事件的路径 + 判断总成是否还在。
- **GUI**：`PatternRedstoneDetectorGui`（纯设置面板，无槽位，同镜像做法）——绑定状态 / 「信号保持：<输入框> 秒」/ 范围提示 / 当前状态（有料、剩余 N 秒、已熄灭、区块未加载、绑定目标不是样板总成）。输入框 1~2147483647，回车生效。
- **NBT**：`elinar.detector.master`（x/y/z）、`elinar.detector.holdSeconds`、`elinar.detector.countdown`；`save/loadNBTData` **不能调 super**（父链抽象方法，同虚空节点的坑）。
- **ID/外观**：ID 19085（19082 预留区，19083 是独立工程、19084 是采掘工厂）；tier 1（LV，外壳最基础、扳手 WrenchLevel1）；贴图 = `MACHINE_CASINGS[mTier][colorIndex+1]` + `OVERLAY_REDSTONE_TRANSMITTER`，亮起时该覆盖层走 glow。
- **未实机验证（2026-11-07 交付）**，验收现象：
  1. 放方块 → 右键 GUI 显示「未绑定：闪存左键总成 → 右键本方块」；
  2. 闪存左键样板输入总成（或 GTNL 超级总成）→ 右键本方块 → 聊天栏「已绑定到样板总成」，GUI 显示坐标；
  3. 触发一次 AE 合成：总成有料的瞬间，方块任意一面弱充能 15（红石灯亮 / 红石线通电 / GT 机器能读到）；
  4. 料被扣走后，继续亮 GUI 里设置的秒数再熄灭；期间再来料会重新计时；
  5. 改秒数（如 10）→ 回车 → 熄灭延迟变成 10 秒；重进世界后秒数与绑定都还在。
  6. （v1.2.33）把保持秒数设成 600，等到只剩几秒时再触发一次合成：料刚进总成就被机器扣走，信号也会重新续满 600 秒（事件驱动，不靠轮询）。

### 5.32 束流倍流器（Beam Flux Multiplier，2026-11-07 新增，v1.2.34）

- **需求原话（芷若 2026-11-07）**：「我想做一个单方块的倍流器，就是可以把束流的通量翻几倍（可自定义）该单方块机器通过扳手调整机器的正对面，正面通过束流管输入束流，而背面则通过束流管输出束流，右键方块查看束流粒子情况，并可设置通量加倍倍数（1x-36768x）」；随后纠错并拍板：「我口误，32768倍数+流量int峰顶，允许串联（反正上限是int，2台没意义），你可以开始做了，塞进我的ElinarCurios」。
- **★ 为什么继承 `MTEHatchInputBeamline`**：GTNH 2.9.0 的束流传输是「出束仓每 20 tick（`aTick % 20 == MOVE_AT`，MOVE_AT=4）沿正面做一次直线射线 1~129 格」，射线遇到 `MTEBeamlinePipe` 只跳过，终点用 `instanceof MTEHatchInputBeamline` 认仓 —— 普通 `MTETieredMachineBlock` 挡在线上会被直接 `return`，收不到包。所以本方块伪装成束流输入仓（单方块、1×1×1）。
- **为什么管道不能当中继**：管道不转发、不存包（`MTEBeamlinePipe.getNext()` 恒 null，`onPostTick` 只做连接渲染 + 每 32 tick 重算连接位）；所以本方块在 `setContents()` 收到包后，沿**背面**复制同样射线，把新包塞给下游输入仓（`pushDownstream`）。
- **正面门禁**：上游射线不检查输入仓朝向，`setContents` 也没有来源参数 ⇒ 收到包时先沿正面扫「第一个非管道方块」：束流输出仓 / 同类倍流器的背面 ⇒ 接受；否则拒绝并在 GUI 显示「背面来束 / 正面无来束」。两面同时有源时按正面处理（API 固有限制，正常摆线不会遇到）。
- **数值**：只乘 `rate`；`energy/focus/particleId` 原样透传。`rate` 是 int，乘法用 long，输出 clamp 到 `Integer.MAX_VALUE`（32768× 时输入 rate 超过 65536 就会封顶）；串联 = 乘积再封顶，允许但通常没意义。
- **1 秒节拍**：出束仓每 20 tick 一包；本方块在 `setContents` 里同步立刻往下游推（不等下一个 MOVE_AT），下游拿到的包与上游同 tick；GUI 读数因此每秒刷新一次。
- **贴图**：正面 = 机器外壳 + `OVERLAY_BI_ACTIVE/BI_SIDES`（束流输入仓同款），背面 = `OVERLAY_BO_ACTIVE/BO_SIDES`；激活状态由父类 `onPostTick` 在 MOVE_AT 根据 `dataPacket != null` 自动 setActive，贴图直接用 `aActive`。
- **GUI**：`BeamFluxMultiplierGui`（纯设置面板，无槽位，同侦测器做法）——粒子/能量/聚焦/输入通量/输出通量 + 「倍率：<输入框> x」（1~32768，回车生效）+ 短状态行（无束流 / 背面来束 / 正面无来束 / 下游无接收仓 / 通量已封顶 / 正常）。粒子名在客户端用 `Particle.getLocalisedName()` 拼（服务端 locale 不对）。
- **ID/配方**：ID **19086** / mName `elinar.machine.beam_flux_multiplier` / tier 7（ZPM）；合成 = 4 束流管（`LanthItemList.BEAMLINE_PIPE`）+ 4 ZPM 机壳 + 1 ZPM 发射器（形状 `ABA/BCB/ABA`）。不耗电（她未要求）。
- **NBT**：`elinar.beamflux.multiplier`（父类 `eDATA` 仍存 `dataPacket`，`save/loadNBTData` 照常调 super）。
- **未实机验证（2026-11-07 交付）**，验收现象：
  1. 摆线：出束仓 → 若干束流管 → 倍流器（正面朝来束）→ 若干束流管 → 靶室/对撞机/合成器的束流输入仓；整条线必须是一条直线。
  2. 扳手点哪个面，哪个面变正面（输入），背面自动变输出；束流管连接位 1~2 秒内跟着变。
  3. 空手右键打开 GUI，能看到粒子/能量/聚焦/输入通量/输出通量；设 1x 时输出 = 输入（透传）。
  4. 设 10x：输出通量 = 输入×10，靶室蚀刻明显变快（或对撞机并行/合成器缓存加速）。
  5. 设 32768x：输出通量显示封顶（输入够大时），GUI 状态「通量已封顶」。
  6. 反向放置（正面朝下游）：GUI 显示「背面来束」，靶室停机；扳手转回来恢复。
  7. 拆掉上游任意一段管道：1~2 秒内 GUI 变「无束流」；接回恢复。
  8. 退出重进：倍率保持；走远让区块卸载再回来：不崩、不刷屏。
  9. 两台串联（可选）：输出 = 输入×k1×k2，超过 int 时封顶。

### 5.33 教训：FML `@SubscribeEvent` 监听器类必须是 public（2026-11-08，v1.2.35）

- **现象**：掉线/被动登出时服务器线程崩，`crash-2026-11-08_21.11.30-server.txt` 首行
  `java.lang.IllegalAccessError: failed to access class elinarcurios.compat.StructureLibCompat$CacheCleaner from class cpw.mods.fml.common.eventhandler.ASMEventHandler_2926_...`。
- **原因**：FML 的 `EventBus.register()` 会给每个 `@SubscribeEvent` 方法生成一个处理器类，包名固定为
  `cpw.mods.fml.common.eventhandler`、类加载器是 `ASMEventHandler$ASMClassLoader`；JVM 的访问检查按
  **运行时包**（类加载器 + 包名）算，所以生成类访问 `elinarcurios.compat.StructureLibCompat$CacheCleaner`
  时等同于跨包访问 ⇒ 监听器类在 class 文件里必须是 `ACC_PUBLIC`（嵌套类的 `private` 只写在 InnerClasses
  属性里，class 文件标志是包私有）。方法本身 public 不够，**类也必须 public**。
- **为什么长期没炸**：v1.2.24~v1.2.34 期间该监听器只在单机跑；1.7.10 单机「保存并退出」
  走 `MinecraftServer` 停服路径，不经过 `NetHandlerPlayServer.onDisconnect`，`PlayerLoggedOutEvent`
  根本没触发（2026-11-08 当天 08:47 / 17:51 两次干净退出都没有异常可证）。只有连接被真正掐断
  （掉线、被踢、服务端异常）时才触发 —— 一触发就崩。
- **规则**：任何注册进 FML/Forge EventBus 的监听器类（含静态内部类）一律 `public`；只用 `@SubscribeEvent`
  的私有内部类是不可用的。审查时直接看 class 文件 flags 是否为 `ACC_PUBLIC`（`javap -v`）。
- **验证手段**：`javap -v -classpath <jar> 'elinarcurios.compat.StructureLibCompat$CacheCleaner' | findstr flags`
  → 应为 `ACC_PUBLIC, ACC_FINAL, ACC_SUPER`。

### 5.34 ME 输出快刷:玩家下单期间当 tick 刷产(2026-11-13,v1.2.36)

**背景(为什么需要它)**:GT5U 的 `Output Bus (ME)` 与 `Output Hatch (ME)` 共用
`MTEHatchOutputMEBase`,它每 `refreshTime`(默认 40 tick = 2 秒)才把缓存整包 `injectItems` 进 AE。
那个 2 秒是**写死的下限**,不是"可以调小的默认值":

- 构造器 `refreshTime = 40`;`MIN_REFRESH_TIME = 40`;`setRefreshTime(t)` 就是 `Math.max(40, t)`;
- GUI 那格输入的是**秒**(写入 `sec*20`、显示 `getRefreshTime()/20`),闪存复制粘贴也走 `setRefreshTime`
  ⇒ 输入 1 也会被夹回 2;
- 全 jar 扫 `refreshTime` 只有 `MTEHatchOutputMEBase` 与它的 WAILA 助手引用,`config/GregTech/*.cfg`
  里没有任何相关键 ⇒ **没有配置项、没有命令**;
- 值存在每台自己的 NBT `refreshTime`,不是全局。

后果:`持续吞吐上限 ≈ 缓存容量 ÷ 刷新间隔` —— 物品总线 1600 个 / 2 秒 = **800 个/秒(12.5 组/秒)**,
流体仓 128,000 L / 2 秒 = **64,000 L/秒**;缓存满了 `storePartial` 就拒收,且开着**检查模式**时
并行数也是按剩余空间算的 ⇒ 高频产线会被这个 2 秒卡住。

**做法(不改 `refreshTime`,直接替它刷)**:新增 `elinarcurios/meout/MEOutputFlushHandler.java`,
挂在 FML bus 的 `TickEvent.WorldTickEvent`(END)与 Forge bus 的 `WorldEvent.Unload` 上,每 tick:

1. **只在"机器在干活"时介入**:`getTotalRuntimeInTicks() - getLastWorkingTick() <= meOutFastFlushHoldTicks`
   (默认 100 tick = 5 秒,含停手后的尾巴,保证一单的最后几批产物也立刻出去);
2. **只在"这张 AE 网有玩家单"时刷**:遍历 `ICraftingGrid.getCpus()`,命中
   `isBusy() && getSourcePlayer() != null`(或 `getCurrentJobSource() instanceof PlayerSource`);
3. **只刷"有货"的仓**:`getCachedAmount() > 0` 才调 `flushCachedStack()` —— 空缓存调用会把 GT 的
   `lastOutputTick` 往后推,反而拖慢停机后的最后一次兜底刷;
4. 刷的对象是机器 `mOutputBusses` 里的 `MTEHatchOutputBusME` 与 `mOutputHatches` 里的 `MTEHatchOutputME`
   的 `getProvider()`(基类 `MTEHatchOutputMEBase`,`flushCachedStack()` 是 public)。

**为什么"玩家单"这条判据可靠**:AE2 在 `CraftingCPUCluster.submitJob(grid, job, src, requester)` 里
只对 `PlayerSource` 写 `sourcePlayer`;AE2FC 的库存维持器(`TileLevelMaintainer`)构造器里是
`new MachineSource(this)` ⇒ 它下的单永远拿不到名字,不会被快刷。而 **ECO 的 `NativeECOCpu` 与
GTNL 的 `ECraftingCPUCluster` 都 `extends CraftingCPUCluster`**、走同一个 `submitJob`
(ECO 还有 `trackRequester` 显式写 `sourcePlayer`),GTNL 的 `MixinCraftingGridCache` 把量子 CPU 塞进
`getCpus()` ⇒ 终端/ECO/GTNL 量子计算机界面下的玩家单都能命中。

**性能**:多方块列表按世界缓存,每 20 tick 重建一次(`world.loadedTileEntityList` 里筛
`MTEMultiBlockBase`),每 tick 只遍历缓存列表(几百台量级);每张网每 tick 只算一次"有没有玩家单"
(`IdentityHashMap` 缓存,每 tick 清);世界卸载时丢缓存。大基地里不会每 tick 扫整张 TE 表。

**配置**:`config\ElinarCurios.cfg` 的 `meOutFastFlushEnabled`(默认 `true`)、
`meOutFastFlushHoldTicks`(默认 `100`,范围 0~24000;**改完需重启**)。
`0` = 只在机器真正干活的 tick 快刷。**命令**:`/Elinar meflush` 打印开关、hold、上一 tick 命中的
"有玩家单的网 / 在干活的多方块 / 实际刷出仓"三个数,以及累计刷出次数。

**验收(未实机)**:

1. 一台带 `Output Bus (ME)` 的多方块(AE 里能下单做的配方),在终端 / ECO / GTNL 量子计算机界面下单;
2. 下单后立刻 `/Elinar meflush`:应看到「有玩家单的 AE 网 ≥ 1、在干活的多方块 ≥ 1」;
3. 产物进 AE 的延迟从 0~2 秒变成 ≤ 1 tick(观察 ME 里产物出现的节奏;WAILA 的 `next in Xs` 仍是
   GT 自己的 2 秒计时器,两者不同步是正常的,因为我们不改那个字段);
4. 单子跑完约 5 秒后 `/Elinar meflush`:三个数回到 0;
5. 反例验证:用 AE2FC 库存维持器下同样的单 ⇒ 「有玩家单的网」应为 0,产物回到 2 秒节奏;
6. 把 `meOutFastFlushEnabled` 改 `false` 重启 ⇒ 行为与 v1.2.35 完全一致(2 秒)。

**实机结论(2026-11-13 芷若)**:「感觉不需要,我貌似没看到有卡顿情况,估计可能后期开头会卡,那也是因为 ae 自己发单的问题,但是现在这种丝滑的体验确实不错,小东西下单 1 秒就收到产物」。
⇒ 不加降频/批量阈值旋钮,保持"每 tick 刷";她观察到的"后期开头卡"发生在**产物产生之前**(AE 自己的合成计划计算 + 首批发单,GTNL 量子 CPU 还要跑 BatchDispatch 规划),与本功能无关 —— 本功能只在缓存里有货时才动作。

**边界与已知限制**:

- **按"这张 AE 网"判定**:CPU 在主网、机器挂在被石英纤维/无线连接器隔开的**子网**时看不到主网的
  玩家单 ⇒ 那种布局不会快刷(CPU 与机器需同网);
- 机器**常驻加工**(持续供料的产线)会一直快刷 —— 这是"有产出就快"的语义,不是"只有下单才快";
- 玩家单在跑时,同网其它正在干活的机器也会一起快刷(空仓调用零成本,不影响正确性);
- 不影响**其它**输出仓/总线,也不碰别的 mod 的缓存;
- 关掉 cfg 即完全回到原版行为,不需要拆方块/改存档。

### 5.35 净水厂七/八级成功率彩蛋（mixin，默认关闭，2026-11-14，v1.0.0c4）

**背景**：GT5U 净水厂（`MTEPurificationPlant`）+ 8 台净化单元是一整条「上一级水 → 下一级净化水」链路。七级「残留净化剂除气机净化单元」（`MTEPurificationUnitDegasser`）和八级「绝对重子完美净化单元」（`MTEPurificationUnitBaryonicPerfection`）的配方本来就只吃上一级水，但最终成功率由各自覆盖的 `calculateFinalSuccessChance()` 硬判定：脱气要求当周期随机出来的 4 位控制信号所需的液体全部按精确量投放，重子要求输入总线里插对随机两连夸克催化剂（每根另耗熔融无尽），否则一律 0%。按芷若要求只改这两台、默认关闭、开关放配置文件（2026-11-14 拍板）。

**实现**（2 个 mixin，只在开关打开时生效）：

| 文件 | 目标 | 注入 |
|---|---|---|
| `mixin/MixinMTEPurificationUnitDegasser.java` | `MTEPurificationUnitDegasser#calculateFinalSuccessChance()F` | `@Inject(HEAD, cancellable)` → 返回 `100.0f` |
| `mixin/MixinMTEPurificationUnitBaryonicPerfection.java` | `MTEPurificationUnitBaryonicPerfection#calculateFinalSuccessChance()F` | 同上 |

- 配置：`Config.purificationTier78AlwaysSucceed`（`ElinarCurios.cfg`，`CATEGORY_GENERAL`，默认 `false`）。
- 为什么选这两个方法头：全库只有 `MTEPurificationUnitBase.endCycle()`（决定成败）和 `getInfoData()`（扫描器显示）调用它，所以在方法头短路等于一并覆盖「行为 + 显示」，不用碰基类，也不用 ghost 催化剂或改配方。
- `mixins.elinarcurios.json` 里追加两个类名；`remap = false`（GT5U 类不混淆）。
- 编译依赖仍是 `build-libs/2.9.0-RC-1/gregtech-5.09.54.183.jar`：两个类与 `public float calculateFinalSuccessChance()` 在 5.09.54.183 与运行版 5.09.54.265 中都存在（已用 javap 核对），签名未变，不需要换依赖。

**副作用**：

- 脱气：4 位控制信号、惰性气体 / 超导熔液 / 中子素熔液全部变成装饰；`outputMultiplier` 恒 1（没有超导体加成水量）。
- 重子：不再需要夸克催化剂与熔融无尽；配方自带的产出照旧（每周期 500 L 稳定重子物质 + 2 个未对齐催化剂，这是配方输出，不是彩蛋加的）。
- 其余 6 台净化单元与其它 GT 机器完全不受影响；关掉开关即完全恢复原版。

**风险**：GT5U 升级后若类 / 方法名变动，mixin 会在启动阶段直接报错（不会静默半生效），届时改 target 或临时把开关删掉。

**验收**：两台都只灌上一级水、不喂任何催化剂 → 一个周期后七号出七级净化水、八号出八级净化水，扫描器成功率显示 100%；把 cfg 改成 `false` 重启 → 恢复原版行为。

### 5.36 无尽背包·幽灵模式(2026-11-15,v1.0.0c5)

**需求(芷若原话)**:「我想给我的无尽背包增加一个开关,就是幽灵模式,开启后玩家还能被看到,但是怪无法识别玩家」。按钮放在夜视旁边、样式与夜视开关一致(文案「幽灵√/×」)。

**机制取证(1.7.10,怪识别玩家只有两条通道,过滤条件都是 `capabilities.disableDamage`)**:

| 通道 | 入口 | 用它的怪 |
|---|---|---|
| AI 选敌 | `EntityAITarget.isSuitableTarget`(L143 过滤创造玩家) | 僵尸/骷髅/苦力怕/女巫、凋灵、铁傀儡/雪傀儡、几乎所有 mod 怪 |
| 直接找玩家 | `World.getClosestVulnerablePlayer`(L3855 同样过滤) | 末影人(64 格凝视)、恶魂(100 格)、蜘蛛/洞穴蜘蛛、史莱姆/岩浆怪(16 格)、蠹虫、僵尸猪人、`EntityMob.findPlayerToAttack` |
| 已锁定仇恨 | `EntityLiving.attackTarget` / `EntityCreature.entityToAttack` | 前者 AI 派系,后者末影人/蜘蛛/史莱姆/蠹虫(不会每 tick 重判) |
| 被打反击 | `EntityMob.attackEntityFrom` 直接写 `entityToAttack` | 所有 EntityMob 子类 |

**实现**:

- `mixin/MixinEntityAITarget`: `isSuitableTarget` 的 `HEAD`,目标玩家为幽灵且 `ignoreInvulnerable=false` → 返回 `false`(通道 A)。
- `mixin/MixinWorldClosestVulnerablePlayer`: `getClosestVulnerablePlayer` 的 `RETURN`,命中幽灵则改用 `GhostMode.findClosestOtherPlayer` 复刻原版扫描找下一个非幽灵玩家(通道 B;`getClosestVulnerablePlayerToEntity` 也走它)。
- `util/GhostMode` + `player/GhostModeHandler`(FML 总线):每 server tick 从在线玩家表重建 `IdentityHashMap` 幽灵集合(≤1 tick 生效、换维度旧实体不残留);每 5 tick(0.25s)对每个幽灵玩家扫 64 格内 `EntityCreature`,用公开 API 清仇(`setTarget(null)`、`setAttackTarget(null)`)——覆盖「开开关前已被锁定」与「打怪反击」。无幽灵时只剩一个集合判空,零开销。
- **默认不还手(她 2026-11-15 拍板 )**:幽灵模式下主动打怪,怪不会回头追打(清扫会清掉反击目标)。
- 开关复用背包 buff 管线:`BUFF_GHOST = "ghost"`(NBT `buffs.ghost`,**默认 false**,靠新增的 `BUFF_DEFAULTS` 实现)、`BUFF_LANG_KEYS` 驱动按钮文案;`ToggleBuffPacket`/`BuffStatePacket` 无需改动。GUI 按钮数量由 `BUFF_KEYS` 驱动,第二颗落在 x=368、夜视在 406、穿上按钮 444,不冲突。
- **为什么不把 `capabilities.disableDamage` 置 true**(那是最省事的「假无敌」):会让玩家不能进食(`EntityPlayer.canEat`,原版 `ItemFood` 与天厨食盒都卡)、传送门进入时间变 0(秒传)、饥饿不消耗、箭矢/凋灵头骨直接忽略你,其它 mod 也会把你当创造玩家。所以只在 AI 侧拦截。

**构建前提(重要)**:vanilla mixin 必须有 refmap;本工程原有三个 mixin 打的是非混淆 GT5U 类(`remap=false`),所以以前没有注解处理器。现于 `dependencies.gradle` 加 `annotationProcessor files('D:/Agent/GTNH/build-libs/mixin-ap/mixin-0.8.7-processor.jar')`(Sponge maven 下载的自包含 jar,不进仓库),GTNHGradle 会自动传 searge 映射并生成/打包 `mixins.elinarcurios.refmap.json`;`mixins.elinarcurios.json` 里补 `"refmap"` 字段。

**覆盖**:上表全部原版怪;SpecialMobs / InfernalMobs 等继承原版 AI 的 mod 怪自动覆盖;TwilightForest / HEE / Witchery 等自定义怪多数走同一批基类或 `getClosestVulnerablePlayer`,实机抽查即可。

**边界**:①凋灵头骨的随机扫射不走 AI 选敌,仍可能被选中(玩家本来免伤);②其它 mod 若也调 `getClosestVulnerablePlayer*` 找玩家会一并跳过幽灵玩家(唯一外溢面);③末影龙没有仇恨机制,不受影响;④幽灵只在**穿着**背包时生效,脱下有 ≤1 tick 延迟。

**验收**:僵尸/苦力怕贴身不追不炸;盯末影人不尖叫不追;恶魂不喷火球;已追你的怪 1 秒内停下;打怪不还手;关开关立即恢复;第三人称与他人视角模型/名字完全正常。

### 5.37 无尽背包·防御与净化(2026-11-16,v1.0.0 c6 → c7)

**c7 修复（2026-11-16）**：c6 把 `onPlayerTick` 顶部改成 `isRemote` 早退，连带把 `DragonBuffs.apply` 的客户端每 tick 自续砍掉 → 无尽背包夜视「被屏蔽」。修复：`DragonBuffs.apply` 挪到 `isRemote` 早退之前（双端自续），`StatusCleanse` 显式跳过 `Potion.nightVision.id`。教训：给 tick handler 加端侧守卫前，先列出哪些逻辑客户端也必须跑（自续 buff/粒子/渲染）。

**需求**:防御端对齐氪金萝莉(稳定第一);防缴械、防数据清除必须;负面效果全清(含神秘时代扭曲)。

**实现**:

| 项 | 做法 |
|---|---|
| `/clear` | 1.7.10 有该命令。Forge `CommandEvent` 拦截:命令名 `clear`、目标玩家穿着背包 → 取消命令 + 给发送者提示(`PlayerEventHandler#onCommand`)。命令层拦截,不动 `InventoryPlayer` |
| 死亡兜底 | `PlayerEvent.Clone(wasDeath)` 把穿戴包复制给重生后的新玩家(Forge 只在跨维度复制扩展属性);未穿戴死亡时 `PlayerDropsEvent` 把包从掉落列表摘出,存 `PlayerPersisted`(Forge 跨死亡复制),重生/登录归还 |
| 0 血兜底 | 穿戴时每 tick `health <= 0 → setHealth(1)`(防绕过 `onDeath` 的直杀) |
| 负面效果 | 每 20 tick(照龙之研究神龙套):拷贝 `getActivePotionEffects()` → 反射 `Potion.isBadEffect`(`field_76418_K`,按 id 缓存)全部移除 + `extinguish()`。不扣能量、无例外表 |
| 扭曲 | 每 20 tick 读 TC `PlayerKnowledge` 的 `warp/warpTemp/warpSticky/warpCount`,非 0 才写 0。永久扭曲是完成研究时**一次性累积**的值(不是按研究数量重算),清 0 即永久清(官方 `/thaumcraft warp <p> set 0 PERM` 同路径) |
| 防缴械 | 盗贼骷髅:攻击失败就不调 `onTypeAttack`,现有免伤事件天然挡住;Witchery Expelliarmus:`EntityJoinWorld` 记录"正好是手持物的掉落",mixin `EntityPlayer.setCurrentItemOrArmor` 取消清槽并删掉那个掉落物(避免复制) |

**配置**(`config\ElinarCurios.cfg`,默认全 true):`protectInfiniteBackpack` / `blockClearCommand` / `cleanseNegativeEffects` / `clearThaumWarp` / `clampZeroHealth`。

**边界**:①装备/手持的 TC 装备扭曲(`IWarpingGear`)不在清零范围(不常驻 warp ward);②`/clear` 只对"目标穿着者"生效;③`InventoryPlayer.dropAllItems` 的 mixin 因 1.7.10 refmap 不生成 `@Shadow` 字段映射而放弃,批量清包由命令层 + 死亡掉落层覆盖。

**验收**(2026-11-16 实机):清空背包、切换维度、清扭曲、`/kill`、负面 debuff 均正常;启动日志无 mixin/本模组报错,全部 6 个 mixin 均 `Mixing ... into` 成功。

### 5.38 /Elinar gtid：GT 机器 ID 占用表导出（dev，2026-11-16）

**命令**：`/Elinar gtid`（大小写不敏感，服务器/单人命令）。

**输出**：`.minecraft/logs/gregtech_machine_ids.csv`（UTF-8 带 BOM，Excel 直接打开），列：

| 列 | 含义 |
|---|---|
| `id` | 1..32768 |
| `occupied` | 1 = GT 注册表里该 id 有条目，0 = 空闲 |
| `meta_name` | `IMetaTileEntity.getMetaName()` |
| `local_name` | `getLocalName()`（本地化名，取不到时可能是 key） |
| `class_name` | 实现类全名（可看出属于 GT / GT++ / BartWorks 等） |
| `note` | `out_of_gt_range`（id ≥ 32766，超出 `GregTechAPI.METATILEENTITIES` 长度）、`meta_error` / `local_error`（单个条目取值异常） |

**实现**：`command/GtidExport` + `command/GtidExportHandler`（FML `ServerTickEvent`）；数据源 `gregtech.api.GregTechAPI.METATILEENTITIES`（长度 `Short.MAX_VALUE-1 = 32766`）；每秒 tick 扫 512 个 id，32768 个约 **3.2 秒**，不卡顿；扫描期间不打印任何明细，只在完成时聊天栏给文件绝对路径。再次执行期间只会提示“正在导出”。

**边界**：服务器中途关闭会留下部分文件（没有做停机收尾）；单个 MTE 的名字取值异常不会中断扫描，记进 `note`。**2026-11-16 她实测通过**（命令、CSV、位置均正常）。

### 5.39 无尽背包·巫术免祭坛能量（软联动，dev，2026-11-22，v1.0.2-dev）

- **需求原话**：「如果我让我的无尽背包增加一个特性，就是穿上后，能无视巫术所需的能量，原理上是否可行」；拍板「那就先做方案A，挂一个IPowerSource，按你说的」。
- **机制**：巫术的全部祭坛功耗都走 `PowerSources.get(...)` → 最近 `IPowerSource`（大锅/水壶/蒸馏塔/纺车/水晶球/火盆/镜子/传送石/仪式）。给穿戴者注册一个玩家随身 `IPowerSource`：`getCurrentPower()=1e9`、`consumePower()` 恒 true、`getRange()=16`、位置=玩家，于是 16 格内的设备把玩家当最近能量源、不扣祭坛。
- **实现**：`compat/WitcheryPowerCompat`（外层类不引用任何巫术类型，巫术类型全在私有 `Impl` 里，未装巫术时即使被加载也不会解析巫术类）；`PlayerEventHandler#onPlayerTick` 在 `Loader.isModLoaded("witchery")` 守卫内每 tick `update(player, worn && Config.witcheryFreeAltarPower)`；logout / 死亡 clone / 停服移除；配置 `witcheryFreeAltarPower`（默认 true）；物品 tooltip 仅在巫术存在且配置开启时显示。
- **边界**：①未安装巫术 → 分支不进入，功能自动停用；②方案 A 不保证绝对优先：真祭坛比玩家离设备更近时，巫术仍选祭坛并扣它的电，要“永不扣祭坛”需后续 mixin `PowerSources.get` 把随身源排到第一位；③玩家离开 16 格 / 无人值守时失效；④多人服上，站在别人设备旁也会替对方供能；⑤大锅仪式的反噬风险（圈/risk）与能量无关，照旧。
- **验收**：穿着背包站在大锅旁（先不放祭坛）完整走一次幸运液滴仪式（约 10.5 s 出锅）；再看“有祭坛且祭坛比玩家更近”时是否扣电；不装巫术时游戏行为与以前完全一致。

## 6. 集成到整合包

- 把 `dist\ElinarCurios-*.jar` 放进整合包的 `mods\` 文件夹即可;
- **前置依赖**:自 2026-09-12 起需要 **GT5U**(天玄虚空能量节点是 GT 机器)。GTNH 整合包必装,所以实际无感;非 GTNH 环境会跳过机器注册、其余内容照常。`mcmod.info` 标的是 `after:gregtech`(不是 `required-after`,便于 dev 环境无 GT 时也能起);
  - 另需 **SpiceOfLife**(午餐盒的进食协议父类,GTNH 必装)。`mcmod.info` 标了 `after:SpiceOfLife`;无它时只跳过午餐盒注册,其余内容照常;
- 无配置文件必需(首次运行自动生成 `config\ElinarCurios.cfg`);
- 服务端/客户端都要放(消息为双端协议);
- 与 GTNH 2.7+ 兼容(Forge 10.13.4.1614,Java 8 字节码)。

---

## 7. 许可证

- 本项目整体按 **LGPL-3.0** 分发(`LICENSES/LICENSE-LGPL3.txt`);
- 移植自 Avaritiaddons 的部分保留 **MPL-2.0** 文件署名(`LICENSES/LICENSE-MPL2.txt`、源文件头注释);
- Dragon 皮肤纹理与穿戴/渲染思路来自 AdventureBackpack2(LGPL-3.0),已在 `LICENSES/NOTICE` 声明;
- 修改时请保留文件头的版权注释。