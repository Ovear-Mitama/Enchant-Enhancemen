package enchant.enhancement.mixin;

import enchant.enhancement.config.EnchantmentConfig;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.TridentItem;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(TridentItem.class)
public class TridentAttackMixin {
    
    /**
     * 修改三叉戟攻击逻辑，允许激流附魔的三叉戟在左键攻击时触发引雷效果
     * 注入到postHit方法，这是攻击命中后的回调
     */
    @Inject(
        method = "postHit", 
        at = @At("TAIL")
    )
    private void onPostHit(ItemStack stack, LivingEntity target, LivingEntity attacker, CallbackInfoReturnable<Boolean> cir) {
        // 检查是否启用了三叉戟附魔拓展功能
        if (!EnchantmentConfig.isTridentEnchantmentExpansion()) {
            return;
        }
        
        // 检查攻击者是否为玩家（避免其他生物触发）
        if (!(attacker instanceof PlayerEntity)) {
            return;
        }
        
        PlayerEntity player = (PlayerEntity) attacker;
        World world = player.getWorld();
        
        // 只在服务器端执行
        if (world.isClient) {
            return;
        }
        
        // 确保是世界服务器
        if (!(world instanceof ServerWorld)) {
            return;
        }
        
        ServerWorld serverWorld = (ServerWorld) world;
        
        // 检查三叉戟是否有引雷附魔
        RegistryEntry<net.minecraft.enchantment.Enchantment> channelingEntry = 
            serverWorld.getRegistryManager().get(RegistryKeys.ENCHANTMENT)
                .getEntry(Enchantments.CHANNELING).orElse(null);
        boolean hasChanneling = channelingEntry != null && 
            EnchantmentHelper.getLevel(channelingEntry, stack) > 0;
        
        if (!hasChanneling) {
            return;
        }
        
        // 即使有激流附魔，也允许触发引雷效果（修改原版限制）
        // 原版中激流和引雷是互斥的，这里修改为允许同时生效
        
        // 检查是否为雷暴天气（引雷的原始条件）
        if (serverWorld.isThundering()) {
            // 召唤闪电，但不伤害玩家
            summonLightningWithoutPlayerDamage(serverWorld, target, player);
        }
    }
    
    /**
     * 召唤闪电但不伤害玩家
     */
    private void summonLightningWithoutPlayerDamage(ServerWorld world, LivingEntity target, PlayerEntity player) {
        // 创建闪电实体（仅视觉效果）
        LightningEntity lightning = new LightningEntity(EntityType.LIGHTNING_BOLT, world);
        lightning.setPosition(target.getX(), target.getY(), target.getZ());
        
        // 尝试设置为装饰性闪电（不造成伤害）
        try {
            lightning.setCosmetic(true);
        } catch (NoSuchMethodError e) {
            // 如果方法不存在，忽略
        }
        
        // 生成闪电（仅视觉效果，不造成伤害）
        world.spawnEntity(lightning);
        
        // 对目标实体直接造成闪电伤害，确保攻击效果
        // 使用自定义伤害源，避免误伤玩家
        if (target != player && target.isAlive()) {
            // 对目标造成适中的闪电伤害
            // 原版引雷伤害约为5点伤害
            target.damage(world.getDamageSources().lightningBolt(), 5.0f);
        }
    }
}