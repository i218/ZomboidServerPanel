package zombie.network;
import io.serverpanelbridge.*;
import java.nio.file.*;
/** Isolated process fixture, never the installed game server. */
public final class GameServer {
    public static boolean server=true;
    public static void main(String[] args) throws Exception {
        Path root=Paths.get(args[0]);String mode=args[1];
        Path first=root.resolve("first");
        if(Files.exists(first)) {
            if(!Files.readString(first).equals(System.getProperty("bridge.test.value"))) throw new AssertionError("JVM arguments changed during restart");
            Files.writeString(root.resolve("replacement"),"launched",StandardOpenOption.CREATE_NEW);return;
        }
        Files.writeString(first,System.getProperty("bridge.test.value"),StandardOpenOption.CREATE_NEW);
        try(RestartAdapter.Prepared prepared=new JvmRestartAdapter(root,1).prepare()) {
            if(mode.equals("armed") || mode.equals("timeout")) prepared.arm();
            if(mode.equals("timeout")) Thread.sleep(2000); else Thread.sleep(250);
        } catch(IllegalStateException refusal) {if(!mode.equals("quotes-refused")) throw refusal;}
    }
}
