package io.serverpanelbridge;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/** Relays launch arguments through a private pipe, never a persisted script or shell. */
public final class JvmRestartAdapter implements RestartAdapter {
    private final Path logs; private final int timeout;
    public JvmRestartAdapter(Path logs, int timeout) { this.logs=logs;this.timeout=timeout; }
    public Prepared prepare() throws Exception {
        if(!PzRuntime.isDedicatedServer()) throw new IllegalStateException("Not a dedicated server");
        ProcessHandle parent=ProcessHandle.current(); ProcessHandle.Info info=parent.info();
        String executable=info.command().orElseThrow(()->new IllegalStateException("Java executable unavailable"));
        String[] arguments=info.arguments().orElse(null);
        Instant started=info.startInstant().orElseThrow(()->new IllegalStateException("Process identity unavailable"));
        Path binary=Paths.get(executable);
        String name=binary.getFileName().toString().toLowerCase(Locale.ROOT);
        if(!binary.isAbsolute() || !Files.isRegularFile(binary) || !(name.equals("java") || name.equals("java.exe") || name.equals("javaw.exe")))
            throw new IllegalStateException("Unsupported launcher; original Java executable required");
        if(arguments==null && System.getProperty("os.name", "").startsWith("Windows")) arguments=WindowsLaunch.capture(parent.pid(),binary);
        if(arguments==null) throw new IllegalStateException("Original JVM arguments unavailable");
        if(arguments.length==0 || arguments.length>1024 || Arrays.stream(arguments).anyMatch(s->s.startsWith("@") || s.indexOf('"')>=0))
            throw new IllegalStateException("Unsupported or indirect JVM arguments");
        // No guessing from sun.java.command: whitespace cannot reliably recover an argument vector.
        if(Arrays.stream(arguments).noneMatch(s->s.equals("zombie.network.GameServer")))
            throw new IllegalStateException("Original dedicated-server main class unavailable");
        Path directory=Paths.get(System.getProperty("user.dir")).toRealPath();
        Path jar=Paths.get(RestartHelper.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toAbsolutePath();
        if(!Files.isRegularFile(jar)) throw new IllegalStateException("Restart requires the installed mod JAR");
        Files.createDirectories(logs);
        Process helper=new ProcessBuilder(executable,"-cp",jar.toString(),RestartHelper.class.getName())
            .directory(directory.toFile()).redirectError(ProcessBuilder.Redirect.appendTo(logs.resolve("restart-helper.log").toFile())).start();
        DataOutputStream input=new DataOutputStream(helper.getOutputStream());
        ExecutorService reader=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"ServerPanelBridge-RestartReady");t.setDaemon(true);return t;});
        try {
            input.writeLong(parent.pid());input.writeUTF(started.toString());input.writeInt(timeout);
            input.writeUTF(directory.toString());input.writeUTF(logs.resolve("restart-console.log").toAbsolutePath().toString());
            input.writeUTF(executable);input.writeInt(arguments.length);
            for(String argument:arguments) input.writeUTF(argument);
            input.flush();
            Future<Integer> ready=reader.submit(()->helper.getInputStream().read());
            if(ready.get(10,TimeUnit.SECONDS)!=1 || !helper.isAlive()) throw new IOException("Restart helper not ready");
            return new Prepared() {
                private boolean armed;
                public synchronized void validate() throws IOException {if(!helper.isAlive()) throw new IOException("Restart helper exited before shutdown");}
                public synchronized void arm() throws IOException {
                    if(armed) throw new IOException("Restart already armed");
                    if(!helper.isAlive()) throw new IOException("Restart helper exited");
                    input.writeByte(1);input.flush();armed=true;input.close();
                }
                public synchronized void close() {if(!armed) try{input.writeByte(0);input.flush();input.close();}catch(IOException ignored){} }
            };
        } catch(Exception error) {try{input.close();}catch(IOException ignored){} throw error;}
        finally {reader.shutdownNow();}
    }
}
