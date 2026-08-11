package enchant.enhancement;

import enchant.enhancement.platform.EnchantEnhancementPlatform;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Path;

/**
 * Fabric 平台实现：配置目录为 <gameDir>/config。
 */
public class EnchantEnhancementPlatformImpl extends EnchantEnhancementPlatform {
    @Override
    protected Path getConfigDirImpl() {
        return FabricLoader.getInstance().getConfigDir();
    }
}
