package enchant.enhancement;

import enchant.enhancement.config.EnchantmentConfig;
import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnchantEnhancement implements ModInitializer {
	public static final String MOD_ID = "enchant-enhancement";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		EnchantmentConfig.load();
	}
}