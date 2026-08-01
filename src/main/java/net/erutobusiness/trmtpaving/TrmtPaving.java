package net.erutobusiness.trmtpaving;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

/**
 * TRMT Paving — 踏み固めた道を舗装ブロックへ進める。
 *
 * <p>The Roads More Travelled の侵食は {@code trmt:eroded_coarse_dirt} で終わる。
 * そこをさらに歩き続けたら {@code minecraft:dirt_path}（設定可）へ変える。
 * これで「けもの道 → 踏み固め → 街道」が一直線に繋がり、
 * {@code dirt_path} は Via Romana の {@code path_block_ids} に既に入っているので
 * そのまま経路として登録できる。
 *
 * <p><b>なぜ datapack ではなく MOD なのか。</b> 速度差ではない。
 * TRMT はブロック変換のたびに {@code ErosionMapManager.removeEntry(pos)} を呼んで
 * 侵食の記録を掃除している（{@code ShovelItemMixin} がその実例）。
 * datapack の {@code setblock} はこれを通らず、しかも変換後の {@code dirt_path} は
 * TRMT の記録対象でも {@code ErodedDirtBlock} でもないので {@code randomTick} も走らない。
 * ＝<b>その座標の記録が二度と消えない</b>。当部は RoadArchitect で
 * 「ブロック単位の永続マップが肥大して OOM」を経験しているため、これを許容しない。
 *
 * <p><b>fork ではない。</b> TRMT の公開クラスを呼ぶだけで、jar の同梱も再配布もしない。
 * 同等の機能は上流へ提案済み（milkucha/trmt#59）。取り込まれたら本MODは役目を終える。
 */
@Mod(TrmtPaving.MOD_ID)
public class TrmtPaving {

    public static final String MOD_ID = "trmt_paving";

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.ConfigValue<String> RESULT_BLOCK;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("踏み固めた道を舗装ブロックへ進める設定").push("paving");
        ENABLED = b
                .comment("有効にすると trmt:eroded_coarse_dirt を踏み続けたときに舗装ブロックへ変わる")
                .define("enabled", true);
        RESULT_BLOCK = b
                .comment("舗装後のブロックID。Via Romana の道判定に入っているものを選ぶこと")
                .define("resultBlock", "minecraft:dirt_path");
        b.pop();
        SPEC = b.build();
    }

    public TrmtPaving() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, SPEC);
    }
}
