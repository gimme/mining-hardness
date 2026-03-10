package dev.gimme.mininghardness.fabric;

import dev.gimme.mininghardness.Main;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;

public class FabricMod implements ModInitializer {

    @Override
    public void onInitialize() {
        Main.init(FabricLoader.getInstance().getConfigDir());
    }
}
