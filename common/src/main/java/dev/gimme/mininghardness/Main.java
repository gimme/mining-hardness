package dev.gimme.mininghardness;

import java.nio.file.Path;

public class Main {

    public static Main INSTANCE;

    public static Main init(Path configDir) {
        INSTANCE = new Main(configDir);
        return INSTANCE;
    }

    private final CommonConfig commonConfig;

    private Main(Path configDir) {
        CommonConfig.SPEC.init(configDir, Constants.MOD_ID + "-common.toml");
        this.commonConfig = new CommonConfig();
    }

    public CommonConfig getCommonConfig() {
        return commonConfig;
    }
}
