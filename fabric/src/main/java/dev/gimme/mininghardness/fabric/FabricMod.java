package dev.gimme.mininghardness.fabric;

import dev.gimme.mininghardness.CommonConfig;
import dev.gimme.mininghardness.Constants;
import dev.gimme.mininghardness.Main;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.neoforged.fml.config.ModConfig;

public class FabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigRegistry.INSTANCE.register(Constants.MOD_ID, ModConfig.Type.COMMON, CommonConfig.SPEC, CommonConfig.FILE_NAME);

        Main.init();
    }
}
