package dev.gimme.mininghardness.fabric;

import dev.gimme.mininghardness.FcapCommonConfig;
import dev.gimme.mininghardness.Constants;
import dev.gimme.mininghardness.Main;
import fuzs.forgeconfigapiport.fabric.api.v5.ConfigRegistry;
import net.fabricmc.api.ModInitializer;
import net.neoforged.fml.config.ModConfig;

public class FabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        ConfigRegistry.INSTANCE.register(Constants.MOD_ID, ModConfig.Type.COMMON, FcapCommonConfig.SPEC, FcapCommonConfig.FILE_NAME);

        Main.init();
    }
}
