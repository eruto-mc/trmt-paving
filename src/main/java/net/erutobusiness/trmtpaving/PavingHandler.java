package net.erutobusiness.trmtpaving;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import milkucha.trmt.TRMTBlocks;
import milkucha.trmt.TRMTConfig;
import milkucha.trmt.erosion.ChunkErosionMap;
import milkucha.trmt.erosion.ErosionEntry;
import milkucha.trmt.erosion.ErosionMapManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 侵食の終点を踏み続けたら舗装ブロックへ進める。
 *
 * <p>累積と閾値は<b>TRMT の仕組みをそのまま使う</b>（元MOD踏襲）:
 * <ul>
 *   <li>加算量は {@code TRMTConfig.erosionMultipliers}（徒歩 = player、騎乗 = player × mounted）</li>
 *   <li>保存先は TRMT の {@code ErosionMapManager}。<b>独自の永続データを一切増やさない</b></li>
 *   <li>閾値は {@code BlockThresholds.randomThreshold} が返す
 *       {@code erosionThresholds.coarseDirt}（既定 12〜20）の乱数値</li>
 * </ul>
 *
 * <p>副次的に、TRMT が終点ブロックに対して {@code onStep} を呼ばない
 * （＝{@code lastTouchedGameTime} が更新されず、使い続けている道でも
 * 逆侵食のタイマーが進む）点も、こちらが呼ぶことで解消される。
 * この挙動は上流へ質問済み（milkucha/trmt#60）。
 */
@Mod.EventBusSubscriber(modid = TrmtPaving.MOD_ID)
public final class PavingHandler {

    private PavingHandler() {
    }

    /** 同じマスに立ち続けている間は数えないための直前位置（TRMT の trmt$lastGroundPos と同じ考え方）。 */
    private static final Map<UUID, BlockPos> LAST_GROUND = new HashMap<>();

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_GROUND.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (!Boolean.TRUE.equals(TrmtPaving.ENABLED.get())) {
            return;
        }

        Level level = player.level();
        if (level.isClientSide) {
            return;
        }
        if (!player.onGround()) {
            // ⚠ 接地していない tick は数えない。/tp 直後は接地が戻るまで数tick かかるので、
            //    検証台本が「踏んだのに増えない」ときは真っ先にここを疑う
            debug(player, "not-on-ground");
            return;
        }

        BlockPos ground = player.blockPosition().below();
        // 立ち止まっている間に溜まらないよう、マスが変わったときだけ1歩と数える
        BlockPos last = LAST_GROUND.get(player.getUUID());
        if (ground.equals(last)) {
            return;
        }
        LAST_GROUND.put(player.getUUID(), ground.immutable());

        Block finalStage = TRMTBlocks.ERODED_COARSE_DIRT.get();
        if (!level.getBlockState(ground).is(finalStage)) {
            return;
        }

        BlockState paved = resolveResultBlock();
        if (paved == null) {
            return;
        }

        // 加算量は TRMT の設定に合わせる（mounted は player 倍率に掛ける、が上流の仕様）
        TRMTConfig.Multipliers multipliers = TRMTConfig.get().erosionMultipliers;
        float amount = multipliers.player;
        if (player.isPassenger()) {
            amount *= multipliers.mounted;
        }

        ErosionMapManager manager = ErosionMapManager.getInstance();
        if (manager == null) {
            return;
        }
        manager.onStep(ground, finalStage, amount, level.getGameTime());

        ChunkErosionMap chunkMap = manager.getChunkMap(new ChunkPos(ground));
        if (chunkMap == null) {
            return;
        }
        ErosionEntry entry = chunkMap.getEntry(ground);
        if (entry == null) {
            debug(player, "entry-null after onStep " + ground);
            return;
        }
        debug(player, String.format("step %s block=%s count=%.2f/%.2f",
                ground, entry.getTrackedBlock(), entry.getWalkedOnCount(), entry.getThreshold()));
        if (entry.getWalkedOnCount() < entry.getThreshold()) {
            return;
        }

        level.setBlockAndUpdate(ground, paved);
        // ⚠ ここを省くと侵食の記録が永久に残る。ShovelItemMixin と同じ後始末。
        manager.removeEntry(ground);
        debug(player, "PAVED " + ground);
    }

    /** 検証用のログ。同じ理由を連続で出さないよう、直前と違うときだけ書く。 */
    private static String lastDebug = "";

    private static void debug(ServerPlayer player, String msg) {
        if (!Boolean.TRUE.equals(TrmtPaving.DEBUG.get())) {
            return;
        }
        if (msg.equals(lastDebug)) {
            return;
        }
        lastDebug = msg;
        org.slf4j.LoggerFactory.getLogger(TrmtPaving.MOD_ID).info("[trmt_paving] {}", msg);
    }

    private static BlockState resolveResultBlock() {
        // ⚠ ResourceLocation.tryParse(String) は 1.20.2 以降のメソッド。1.20.1 では使えない
        //    （RoadArchitect 1.6.6 がこれで丸ごと動かなくなっている。Shadscure/RoadArchitect#54）
        ResourceLocation id;
        try {
            id = new ResourceLocation(TrmtPaving.RESULT_BLOCK.get());
        } catch (Exception e) {
            return Blocks.DIRT_PATH.defaultBlockState();
        }
        Block block = BuiltInRegistries.BLOCK.get(id);
        // 未知IDは AIR が返るので、そのときは既定へ落とす（道が消える事故を防ぐ）
        if (block == Blocks.AIR) {
            return Blocks.DIRT_PATH.defaultBlockState();
        }
        return block.defaultBlockState();
    }
}
