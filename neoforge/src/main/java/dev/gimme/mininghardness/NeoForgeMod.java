package dev.gimme.mininghardness;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(Constants.MOD_ID)
public class NeoForgeMod {

    public NeoForgeMod(ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC, CommonConfig.FILE_NAME);

        Main.init();
    }
}
