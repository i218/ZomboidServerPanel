import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
public final class RestartProcessTests {
    public static void main(String[] args) throws Exception {
        Path root=Files.createTempDirectory("bridge-restart-process-");
        try {
            for(String mode:List.of("armed","cancelled","timeout","quotes-refused","early-exit","port-busy","port-release")) {
                Path fixture=Files.createDirectory(root.resolve(mode+" path Ж"));
                try(java.net.DatagramSocket portOwner=new java.net.DatagramSocket(null)) {
                portOwner.setReuseAddress(false);portOwner.bind(new java.net.InetSocketAddress(0));
                int port=portOwner.getLocalPort();if(!mode.equals("port-busy") && !mode.equals("port-release")) portOwner.close();
                Process original=new ProcessBuilder(args[0],"-Dbridge.test.value="+(mode.equals("quotes-refused")?"a b \\\"quoted\\\"":"a b tail\\\\"), "-cp",args[1],"zombie.network.GameServer",fixture.toString(),mode,Integer.toString(port))
                    .redirectErrorStream(true).redirectOutput(fixture.resolve("original.log").toFile()).start();
                if(!original.waitFor(15,TimeUnit.SECONDS) || original.exitValue()!=0)
                    throw new AssertionError("Fixture failed: "+Files.readString(fixture.resolve("original.log")));
                if(mode.equals("port-release")) {Thread.sleep(500);portOwner.close();}
                boolean expectReplacement=mode.equals("armed") || mode.equals("port-release");
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
                while(expectReplacement && !Files.exists(fixture.resolve("replacement")) && System.nanoTime()<deadline) Thread.sleep(50);
                Thread.sleep(250);
                if(mode.equals("early-exit")) {
                    long exitDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                    while(!Files.readString(fixture.resolve("restart-helper.log")).contains("exitCode=1") && System.nanoTime()<exitDeadline) Thread.sleep(50);
                    if(!Files.readString(fixture.resolve("restart-helper.log")).contains("exitCode=1")) throw new AssertionError("Early startup failure was not observed");
                }
                if(mode.equals("port-busy")) {
                    long busyDeadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(12);
                    while(!Files.readString(fixture.resolve("restart-helper.log")).contains("Restart cancelled: UDP ports unavailable") && System.nanoTime()<busyDeadline) Thread.sleep(50);
                    String log=Files.readString(fixture.resolve("restart-helper.log"));
                    if(!log.contains("Restart cancelled: UDP ports unavailable") || !log.contains("ownerPid="+ProcessHandle.current().pid())) throw new AssertionError("Occupied port not diagnosed: "+log);
                    Thread.sleep(250);
                }
                if(Files.exists(fixture.resolve("replacement"))!=expectReplacement)
                    throw new AssertionError("Unexpected replacement: "+mode+"; "+(Files.exists(fixture.resolve("restart-console.log"))?Files.readString(fixture.resolve("restart-console.log")):"no console")+"; "+Files.readString(fixture.resolve("restart-helper.log")));
                System.out.println("PASS real separate-process restart: "+mode);
                }
            }
        } finally {
            try(var paths=Files.walk(root)) {for(Path path:paths.sorted(Comparator.reverseOrder()).toArray(Path[]::new)) Files.deleteIfExists(path);}
        }
    }
}
