package dev.gimme.mininghardness;

public class Main {

    public static Main INSTANCE;

    public static Main init() {
        INSTANCE = new Main();
        return INSTANCE;
    }

    private final CommonConfig commonConfig;

    private Main() {
        this.commonConfig = new FcapCommonConfig();
    }

    public CommonConfig getCommonConfig() {
        return commonConfig;
    }
}
