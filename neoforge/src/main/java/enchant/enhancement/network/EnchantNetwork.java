package enchant.enhancement.network;

import enchant.enhancement.EnchantEnhancement;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class EnchantNetwork {
    public static final ResourceLocation CONFIG_SYNC_ID = ResourceLocation.fromNamespaceAndPath(EnchantEnhancement.MOD_ID, "config_sync");
    public static final CustomPacketPayload.Type<ConfigSyncPayload> CONFIG_SYNC_PACKET = new CustomPacketPayload.Type<>(CONFIG_SYNC_ID);
    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPayload> CONFIG_SYNC_CODEC = StreamCodec.composite(
        ConfigSyncPacket.CODEC,
        ConfigSyncPayload::packet,
        ConfigSyncPayload::new
    );

    // 由 EnchantEnhancement 构造器注册到 MOD 总线
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar(EnchantEnhancement.MOD_ID).versioned("1").optional();
        // 服务器 -> 客户端：配置同步包
        registrar.playToClient(CONFIG_SYNC_PACKET, CONFIG_SYNC_CODEC, (payload, context) -> {
            // 在客户端线程中应用配置
            context.enqueueWork(() -> payload.packet().handle());
        });
    }

    public record ConfigSyncPayload(ConfigSyncPacket packet) implements CustomPacketPayload {
        @Override
        public Type<? extends CustomPacketPayload> type() {
            return CONFIG_SYNC_PACKET;
        }
    }
}
