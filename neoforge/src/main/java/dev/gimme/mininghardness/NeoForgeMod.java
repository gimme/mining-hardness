package dev.gimme.mininghardness;

import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;

@Mod(Constants.MOD_ID)
public class NeoForgeMod {

    public NeoForgeMod() {
        Main.init(FMLPaths.CONFIGDIR.get());
    }
}
