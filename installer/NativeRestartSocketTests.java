import java.nio.file.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
public final class NativeRestartSocketTests {
    public static void main(String[] args) throws Exception {
        boolean expectBlocked=args.length>3 && args[3].equals("expect-blocked");
        Path root=Files.createTempDirectory("bridge-native-restart-");
        try {
            int first,second;
            try(DatagramSocket a=new DatagramSocket(0);DatagramSocket b=new DatagramSocket(0)){first=a.getLocalPort();second=b.getLocalPort();}
            Process old=new ProcessBuilder(args[0],"--enable-native-access=ALL-UNNAMED","-Dbridge.test.value=native-socket-test","-cp",args[1],"zombie.network.GameServer",root.toString(),"raknet",Integer.toString(first),Integer.toString(second),args[2])
                .redirectErrorStream(true).redirectOutput(root.resolve("original.log").toFile()).start();
            if(!old.waitFor(15,TimeUnit.SECONDS) || old.exitValue()!=0) throw new AssertionError("Original native fixture failed: "+Files.readString(root.resolve("original.log")));
            long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
            while(System.nanoTime()<deadline) {
                String log=Files.readString(root.resolve("restart-helper.log"));
                if(expectBlocked?log.contains("Restart cancelled: UDP ports unavailable"):(Files.exists(root.resolve("replacement")) && log.contains("exitCode=0"))) break;
                Thread.sleep(50);
            }
            if(expectBlocked) {
                if(Files.exists(root.resolve("replacement")) || !Files.readString(root.resolve("restart-helper.log")).contains("Restart cancelled: UDP ports unavailable")) throw new AssertionError("Baseline did not reproduce inherited socket failure");
                System.out.println("PASS baseline reproduces native socket retained after parent shutdown and exit");Thread.sleep(250);return;
            }
            if(!Files.exists(root.resolve("replacement"))) throw new AssertionError("Native replacement failed: "+Files.readString(root.resolve("restart-helper.log"))+"; "+Files.readString(root.resolve("restart-console.log")));
            if(!Files.readString(root.resolve("restart-helper.log")).contains("exitCode=0")) throw new AssertionError("Native replacement did not exit normally");
            System.out.println("PASS real installed RakNet closes parent sockets and rebinds both ports in replacement");
            Thread.sleep(250);
        } finally {try(var paths=Files.walk(root)){for(Path p:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) Files.deleteIfExists(p);}}
    }
}
