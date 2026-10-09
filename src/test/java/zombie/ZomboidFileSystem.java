package zombie;
public final class ZomboidFileSystem {
    public static final ZomboidFileSystem instance = new ZomboidFileSystem();
    public String getCacheDir() { return System.getProperty("serverpanelbridge.testCache"); }
}
