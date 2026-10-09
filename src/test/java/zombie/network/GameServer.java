package zombie.network;
public final class GameServer {
    public static boolean server;
    public static int players;
    public static int getPlayerCount() { return players; }
    public static final java.util.ArrayList<String> ServerMods = new java.util.ArrayList<>(java.util.List.of("Other", "ServerPanelBridge"));
    public static String serverName = "test-server";
}
