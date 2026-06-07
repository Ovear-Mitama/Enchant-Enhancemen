package enchant.enhancement.network;

import net.minecraft.client.MinecraftClient;
import net.minecraft.network.PacketByteBuf;
import enchant.enhancement.config.EnchantmentConfig;

import java.util.HashMap;
import java.util.Map;

public class ConfigSyncPacket {
    
    private final Map<String, Object> generalConfig;
    private final Map<String, Integer> enchantmentsConfig;

    public ConfigSyncPacket(Map<String, Object> generalConfig, Map<String, Integer> enchantmentsConfig) {
        this.generalConfig = generalConfig;
        this.enchantmentsConfig = enchantmentsConfig;
    }

    public void write(PacketByteBuf buf) {
        // 写入常规配置
        buf.writeInt(generalConfig.size());
        for (Map.Entry<String, Object> entry : generalConfig.entrySet()) {
            buf.writeString(entry.getKey());
            if (entry.getValue() instanceof Boolean) {
                buf.writeBoolean(true);
                buf.writeBoolean((Boolean) entry.getValue());
            } else if (entry.getValue() instanceof Number) {
                buf.writeBoolean(false);
                buf.writeDouble(((Number) entry.getValue()).doubleValue());
            }
        }

        // 写入附魔配置
        buf.writeInt(enchantmentsConfig.size());
        for (Map.Entry<String, Integer> entry : enchantmentsConfig.entrySet()) {
            buf.writeString(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    public static ConfigSyncPacket read(PacketByteBuf buf) {
        // 读取常规配置
        int generalSize = buf.readInt();
        Map<String, Object> generalConfig = new HashMap<>();
        for (int i = 0; i < generalSize; i++) {
            String key = buf.readString();
            boolean isBoolean = buf.readBoolean();
            if (isBoolean) {
                generalConfig.put(key, buf.readBoolean());
            } else {
                generalConfig.put(key, buf.readDouble());
            }
        }

        // 读取附魔配置
        int enchantmentsSize = buf.readInt();
        Map<String, Integer> enchantmentsConfig = new HashMap<>();
        for (int i = 0; i < enchantmentsSize; i++) {
            String key = buf.readString();
            enchantmentsConfig.put(key, buf.readInt());
        }

        return new ConfigSyncPacket(generalConfig, enchantmentsConfig);
    }

    public void handle(MinecraftClient client) {
        // 同步配置到客户端
        EnchantmentConfig.syncConfig(generalConfig, enchantmentsConfig);
    }
}
