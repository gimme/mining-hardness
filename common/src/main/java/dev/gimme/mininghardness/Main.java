package dev.gimme.mininghardness;

public class Main {

    public static Main INSTANCE;

    public static Main init() {
        INSTANCE = new Main();
        return INSTANCE;
    }

    private final ServerConfig serverConfig;

    private Main() {
        this.serverConfig = new FcapServerConfig();
    }

    public ServerConfig getServerConfig() {
        return serverConfig;
    }
}
