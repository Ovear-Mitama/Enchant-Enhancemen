package enchant.enhancement.platform;

import java.nio.file.Path;

/**
 * 平台抽象层：common 代码不直接依赖 Fabric/NeoForge 的加载器 API，
 * 各平台在初始化时通过 {@link #setInstance(EnchantEnhancementPlatform)} 注入自己的实现。
 */
public abstract class EnchantEnhancementPlatform {
    private static EnchantEnhancementPlatform INSTANCE;

    public static void setInstance(EnchantEnhancementPlatform instance) {
        INSTANCE = instance;
    }

    /**
     * 获取平台的配置目录（通常为 <gameDir>/config）。
     * 若平台尚未初始化，回退到当前工作目录下的 config，避免空指针崩溃。
     */
    public static Path getConfigDir() {
        EnchantEnhancementPlatform instance = INSTANCE;
        if (instance == null) {
            return Path.of("config");
        }
        return instance.getConfigDirImpl();
    }

    protected abstract Path getConfigDirImpl();
}
