package enchant.enhancement;

import enchant.enhancement.platform.EnchantEnhancementPlatform;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Path;

/**
 * NeoForge 平台实现：配置目录为 FMLPaths.CONFIGDIR（<gameDir>/config）。
 */
public class EnchantEnhancementPlatformImpl extends EnchantEnhancementPlatform {
    @Override
    protected Path getConfigDirImpl() {
        return FMLPaths.CONFIGDIR.get();
    }
}
