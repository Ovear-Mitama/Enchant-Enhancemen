package enchant.enhancement.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;
import enchant.enhancement.EnchantEnhancement;

public class EnchantNetwork {
    public static final Identifier CONFIG_SYNC_ID = Identifier.of(EnchantEnhancement.MOD_ID, "config_sync");
    public static final CustomPayload.Id<ConfigSyncPayload> CONFIG_SYNC_PACKET = new CustomPayload.Id<>(CONFIG_SYNC_ID);
    public static final PacketCodec<PacketByteBuf, ConfigSyncPayload> CONFIG_SYNC_CODEC = PacketCodec.tuple(
        ConfigSyncPacket.CODEC,
        ConfigSyncPayload::packet,
        ConfigSyncPayload::new
    );

    public static void registerServerPackets() {
        // 服务器不需要注册客户端发送的包
    }

    public static void registerClientPackets() {
        PayloadTypeRegistry.playS2C().register(CONFIG_SYNC_PACKET, CONFIG_SYNC_CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CONFIG_SYNC_PACKET, (payload, context) -> {
            context.client().execute(() -> {
                payload.packet().handle(context.client());
            });
        });
    }

    public record ConfigSyncPayload(ConfigSyncPacket packet) implements CustomPayload {
        @Override
        public Id<? extends CustomPayload> getId() {
            return CONFIG_SYNC_PACKET;
        }
    }
}
