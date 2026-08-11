package enchant.enhancement.mixin;

import enchant.enhancement.config.EnchantmentConfig;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TridentItem.class)
public class TridentAttackMixin {

    /**
     * 修改三叉戟攻击逻辑，允许激流附魔的三叉戟在左键攻击时触发引雷效果
     * 注入到hurtEnemy方法，这是攻击命中后的回调
     */
    @Inject(
        method = "hurtEnemy",
        at = @At("TAIL")
    )
    private void onHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        // 检查是否启用了三叉戟附魔拓展功能
        if (!EnchantmentConfig.isTridentEnchantmentExpansion()) {
            return;
        }

        // 检查攻击者是否为玩家（避免其他生物触发）
        if (!(attacker instanceof Player)) {
            return;
        }

        Player player = (Player) attacker;
        Level level = player.level();

        // 只在服务器端执行
        if (level.isClientSide) {
            return;
        }

        // 确保是世界服务器
        if (!(level instanceof ServerLevel)) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;

        // 检查三叉戟是否有引雷附魔
        Holder<Enchantment> channelingEntry =
            serverLevel.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(Enchantments.CHANNELING).orElse(null);
        boolean hasChanneling = channelingEntry != null &&
            EnchantmentHelper.getItemEnchantmentLevel(channelingEntry, stack) > 0;

        if (!hasChanneling) {
            return;
        }

        // 即使有激流附魔，也允许触发引雷效果（修改原版限制）
        // 原版中激流和引雷是互斥的，这里修改为允许同时生效

        // 检查是否为雷暴天气（引雷的原始条件）
        if (serverLevel.isThundering()) {
            // 召唤闪电，但不伤害玩家
            summonLightningWithoutPlayerDamage(serverLevel, target, player);
        }
    }

    /**
     * 召唤闪电但不伤害玩家
     */
    private void summonLightningWithoutPlayerDamage(ServerLevel level, LivingEntity target, Player player) {
        // 创建闪电实体（仅视觉效果）
        LightningBolt lightning = EntityType.LIGHTNING_BOLT.create(level);
        if (lightning == null) {
            return;
        }
        lightning.moveTo(target.getX(), target.getY(), target.getZ());

        // 设置为装饰性闪电（不造成伤害）
        lightning.setVisualOnly(true);

        // 生成闪电（仅视觉效果，不造成伤害）
        level.addFreshEntity(lightning);

        // 对目标实体直接造成闪电伤害，确保攻击效果
        // 使用自定义伤害源，避免误伤玩家
        if (target != player && target.isAlive()) {
            // 对目标造成适中的闪电伤害
            // 原版引雷伤害约为5点伤害
            target.hurt(level.damageSources().lightningBolt(), 5.0f);
        }
    }
}
