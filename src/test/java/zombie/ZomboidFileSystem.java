package zombie;
public final class ZomboidFileSystem {
    public static final ZomboidFileSystem instance = new ZomboidFileSystem();
    public java.util.ArrayList<String> getModIDs() { return new java.util.ArrayList<>(java.util.List.of("Other", "ServerPanelBridge")); }
    public String getCacheDir() { return System.getProperty("serverpanelbridge.testCache"); }
}
