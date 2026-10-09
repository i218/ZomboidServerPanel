package io.serverpanelbridge;
import java.util.List;
/** Build 42 handshake uses GameServer.ServerMods, separate from loaded runtime mods.
 * Apply after loading on the first server tick. Live client join remains a deployment check. */
public final class OptionalClientAdapter {
    private OptionalClientAdapter() {}
    public static void apply() throws ReflectiveOperationException {
        if (!PzRuntime.isDedicatedServer()) throw new IllegalStateException("Not a dedicated server");
        Class<?> fs = Class.forName("zombie.ZomboidFileSystem");
        Object instance = fs.getField("instance").get(null);
        exclude(Class.forName("zombie.network.GameServer").getField("ServerMods").get(null),
                fs.getMethod("getModIDs").invoke(instance));
    }
    static void exclude(Object advertised, Object loaded) {
        if (!(advertised instanceof List<?>) || !(loaded instanceof List<?>) || advertised == loaded)
            throw new IllegalStateException("Unsupported mod lists; runtime list will not be changed");
        if (((List<?>)loaded).stream().noneMatch(OptionalClientAdapter::own))
            throw new IllegalStateException("ServerPanelBridge is not loaded yet");
        ((List<?>)advertised).removeIf(OptionalClientAdapter::own);
    }
    private static boolean own(Object id) {
        return "ServerPanelBridge".equals(id) || "\\ServerPanelBridge".equals(id);
    }
}
