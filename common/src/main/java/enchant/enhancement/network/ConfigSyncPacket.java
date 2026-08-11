package enchant.enhancement.network;

import enchant.enhancement.config.EnchantmentConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.HashMap;
import java.util.Map;

public class ConfigSyncPacket {
    public static final StreamCodec<FriendlyByteBuf, ConfigSyncPacket> CODEC = new StreamCodec<>() {
        @Override
        public ConfigSyncPacket decode(FriendlyByteBuf buffer) {
            return read(buffer);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, ConfigSyncPacket value) {
            value.write(buffer);
        }
    };

    private final Map<String, Object> generalConfig;
    private final Map<String, Integer> enchantmentsConfig;

    public ConfigSyncPacket(Map<String, Object> generalConfig, Map<String, Integer> enchantmentsConfig) {
        this.generalConfig = generalConfig;
        this.enchantmentsConfig = enchantmentsConfig;
    }

    public void write(FriendlyByteBuf buf) {
        // 写入常规配置
        buf.writeInt(generalConfig.size());
        for (Map.Entry<String, Object> entry : generalConfig.entrySet()) {
            buf.writeUtf(entry.getKey());
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
            buf.writeUtf(entry.getKey());
            buf.writeInt(entry.getValue());
        }
    }

    public static ConfigSyncPacket read(FriendlyByteBuf buf) {
        // 读取常规配置
        int generalSize = buf.readInt();
        Map<String, Object> generalConfig = new HashMap<>();
        for (int i = 0; i < generalSize; i++) {
            String key = buf.readUtf();
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
            String key = buf.readUtf();
            enchantmentsConfig.put(key, buf.readInt());
        }

        return new ConfigSyncPacket(generalConfig, enchantmentsConfig);
    }

    /**
     * 在客户端线程中执行：同步配置到客户端。
     * Fabric 端在 context.client().execute 中调用，NeoForge 端在 context.enqueueWork 中调用。
     */
    public void handle() {
        EnchantmentConfig.syncConfig(generalConfig, enchantmentsConfig);
    }
}
