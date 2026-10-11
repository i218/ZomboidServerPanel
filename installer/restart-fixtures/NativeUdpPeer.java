package io.serverpanelbridge.fixture;
import java.nio.file.*;
/** Real installed RakNet, temporary ports only; no game world or Steam login. */
public final class NativeUdpPeer {
    public static Object open(String game,int first,int second) throws Exception {
        System.load(Paths.get(game,"RakNet64.dll").toAbsolutePath().toString());
        System.load(Paths.get(game,"ZNetNoSteam64.dll").toAbsolutePath().toString());
        Class<?> type=Class.forName("zombie.core.raknet.RakNetPeerInterface");Object peer=type.getConstructor().newInstance();
        type.getMethod("Init",boolean.class).invoke(peer,false);
        type.getMethod("SetServerIP",String.class).invoke(peer,"127.0.0.1");
        type.getMethod("SetServerPort",int.class,int.class).invoke(peer,first,second);
        var startup=type.getDeclaredMethod("Startup",int.class,String.class,boolean.class);startup.setAccessible(true);
        int code=(int)startup.invoke(peer,2,"42.21.0",true);
        if(code!=0) throw new IllegalStateException("Native RakNet startup code="+code);
        return peer;
    }
    public static void close(Object peer) throws Exception {peer.getClass().getMethod("Shutdown").invoke(peer);}
}
