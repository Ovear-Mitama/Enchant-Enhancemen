package damage.engine.mixin;

import damage.engine.network.DamagePayload;
import damage.engine.util.AttackerAccessor;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.Ownable;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends Entity {
    private static final Logger LOGGER = LoggerFactory.getLogger("damage-engine-mixin");

    @Unique
    private float damageEngine$previousHealth;
    @Unique
    private float damageEngine$previousAbsorption;
    @Unique
    private boolean damageEngine$wasCrit;
    @Unique
    private int damageEngine$attackerId;
    @Unique
    private double damageEngine$sourceX;
    @Unique
    private double damageEngine$sourceY;
    @Unique
    private double damageEngine$sourceZ;
    @Unique
    private boolean damageEngine$isProjectile;

    public LivingEntityMixin(net.minecraft.entity.EntityType<?> type, net.minecraft.world.World world) {
        super(type, world);
    }

    @Inject(method = "applyDamage", at = @At("HEAD"))
    private void onApplyDamageHead(DamageSource source, float amount, CallbackInfo ci) {
        if (this.getWorld().isClient()) return;
        if ((Object)this instanceof PlayerEntity) return;
        
        LivingEntity self = (LivingEntity) (Object) this;
        this.damageEngine$previousHealth = self.getHealth();
        this.damageEngine$previousAbsorption = self.getAbsorptionAmount();
        
        this.damageEngine$wasCrit = false;
        if (source.getAttacker() instanceof PlayerEntity player) {
             if (player.fallDistance > 0.0F && !player.isOnGround() && !player.isClimbing() && !player.isTouchingWater()) {
                 this.damageEngine$wasCrit = true;
             }
        }
        
        this.damageEngine$attackerId = -1;
        this.damageEngine$isProjectile = false;
        Entity attacker = source.getAttacker();
        Entity directSource = source.getSource();
        
        if (attacker == null) {
            if (directSource instanceof Ownable ownable) {
                attacker = ownable.getOwner();
            } else if (directSource instanceof net.minecraft.entity.decoration.EndCrystalEntity crystal) {
                if (crystal instanceof AttackerAccessor accessor) {
                    attacker = accessor.damageEngine$getAttacker();
                }
            }
        }
        
        if (attacker == null && (source.isOf(DamageTypes.ON_FIRE) || source.isOf(DamageTypes.IN_FIRE))) {
            attacker = ((LivingEntity)(Object)this).getAttacker();
        }

        if (attacker != null) {
            this.damageEngine$attackerId = attacker.getId();
        }
        
        // Capture hit position: projectile position for projectile attacks, target position otherwise
        if (directSource instanceof net.minecraft.entity.projectile.ProjectileEntity) {
            this.damageEngine$isProjectile = true;
            this.damageEngine$sourceX = directSource.getX();
            this.damageEngine$sourceY = directSource.getY();
            this.damageEngine$sourceZ = directSource.getZ();
        } else {
            this.damageEngine$sourceX = self.getX();
            this.damageEngine$sourceY = self.getY() + self.getStandingEyeHeight() * 0.6;
            this.damageEngine$sourceZ = self.getZ();
        }
    }

    @Inject(method = "applyDamage", at = @At("RETURN"))
    private void onApplyDamageReturn(DamageSource source, float amount, CallbackInfo ci) {
        if (this.getWorld().isClient()) return;
        if ((Object)this instanceof PlayerEntity) return;

        LivingEntity self = (LivingEntity) (Object) this;
        float currentHealth = self.getHealth();
        float currentAbsorption = self.getAbsorptionAmount();

        float healthLost = this.damageEngine$previousHealth - currentHealth;
        float absorptionLost = this.damageEngine$previousAbsorption - currentAbsorption;
        float actualDamage = healthLost + absorptionLost;
        
        String debugInfo = "";
        
        if (damage.engine.DamageEngineConfig.getInstance().debugMode) {
            String attackerName = source.getName();
            if (this.damageEngine$attackerId != 0 && self.getWorld() instanceof ServerWorld serverWorld) {
                Entity attacker = serverWorld.getEntityById(this.damageEngine$attackerId);
                if (attacker != null) {
                    attackerName = attacker.getName().getString();
                }
            }
            debugInfo = String.format("%s 造成伤害%.1f 伤害对象 %s", attackerName, actualDamage, self.getName().getString());
            LOGGER.info(debugInfo);
        }
        

        boolean killed = !self.isAlive() || self.getHealth() <= 0f;
        
        if (actualDamage > 0 || killed) {
            DamagePayload payload = new DamagePayload(this.getId(), actualDamage, this.damageEngine$wasCrit, this.damageEngine$attackerId, debugInfo,
                this.damageEngine$sourceX, this.damageEngine$sourceY, this.damageEngine$sourceZ, this.damageEngine$isProjectile, killed);
            
            for (ServerPlayerEntity player : PlayerLookup.tracking(this)) {
                ServerPlayNetworking.send(player, payload);
            }
            
            if ((Object)this instanceof ServerPlayerEntity selfPlayer) {
                ServerPlayNetworking.send(selfPlayer, payload);
            }
        }
    }
}
