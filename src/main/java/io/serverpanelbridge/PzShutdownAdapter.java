package io.serverpanelbridge;
/** Exact public B42 APIs, probed on the game thread. No process kill or shell fallback. */
public final class PzShutdownAdapter implements ShutdownAdapter {
    private final boolean allowPlayers;
    public PzShutdownAdapter(boolean allowPlayers) { this.allowPlayers=allowPlayers; }
    @Override public void requestStop() throws Exception {
        if(!PzRuntime.isDedicatedServer()) throw new IllegalStateException("Not a dedicated server");
        Class<?> server=Class.forName("zombie.network.GameServer");
        int players=((Number)server.getMethod("getPlayerCount").invoke(null)).intValue();
        if(players<0 || (players>0 && !allowPlayers)) throw new IllegalStateException("Server has players; stopWhenPlayersOnline is disabled");
        Class<?> map=Class.forName("zombie.network.ServerMap");
        Object instance=map.getField("instance").get(null);
        if(instance==null) throw new IllegalStateException("ServerMap not ready");
        var method=map.getMethod("QueueQuit");
        if(method.getReturnType()!=void.class) throw new NoSuchMethodException("Expected void QueueQuit()");
        method.invoke(instance);
    }
}
