package damage.engine.network;

import damage.engine.DamageEngine;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record DamagePayload(int entityId, float amount, boolean isCrit, int attackerId, String debugInfo, double posX, double posY, double posZ, boolean isProjectile, boolean killed) implements CustomPayload {
    public static final CustomPayload.Id<DamagePayload> ID = new CustomPayload.Id<>(Identifier.of(DamageEngine.MOD_ID, "damage_packet"));
    
    public static final PacketCodec<RegistryByteBuf, DamagePayload> CODEC = new PacketCodec<>() {
        @Override
        public DamagePayload decode(RegistryByteBuf buf) {
            return new DamagePayload(
                buf.readVarInt(),
                buf.readFloat(),
                buf.readBoolean(),
                buf.readVarInt(),
                PacketCodecs.STRING.decode(buf),
                buf.readDouble(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readBoolean(),
                buf.readBoolean()
            );
        }
        
        @Override
        public void encode(RegistryByteBuf buf, DamagePayload value) {
            buf.writeVarInt(value.entityId);
            buf.writeFloat(value.amount);
            buf.writeBoolean(value.isCrit);
            buf.writeVarInt(value.attackerId);
            PacketCodecs.STRING.encode(buf, value.debugInfo);
            buf.writeDouble(value.posX);
            buf.writeDouble(value.posY);
            buf.writeDouble(value.posZ);
            buf.writeBoolean(value.isProjectile);
            buf.writeBoolean(value.killed);
        }
    };

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
