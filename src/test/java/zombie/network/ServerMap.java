package zombie.network;
public final class ServerMap {
    public static ServerMap instance=new ServerMap();
    public static int quitRequests;
    public void QueueQuit() { quitRequests++; }
}
