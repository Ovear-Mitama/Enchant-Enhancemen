package enchant.enhancement.network;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.util.Identifier;
import enchant.enhancement.EnchantEnhancement;

public class EnchantNetwork {
    public static final Identifier CONFIG_SYNC_ID = new Identifier(EnchantEnhancement.MOD_ID, "config_sync");

    public static void registerServerPackets() {
        // 服务器不需要注册客户端发送的包
    }

    public static void registerClientPackets() {
        ClientPlayNetworking.registerGlobalReceiver(CONFIG_SYNC_ID, (client, handler, buf, responseSender) -> {
            ConfigSyncPacket packet = ConfigSyncPacket.read(buf);
            client.execute(() -> {
                packet.handle(client);
            });
        });
    }
}
