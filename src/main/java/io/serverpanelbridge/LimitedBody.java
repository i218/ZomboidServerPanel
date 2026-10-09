package io.serverpanelbridge;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final HttpResponse.BodySubscriber<byte[]> delegate=HttpResponse.BodySubscribers.ofByteArray();
    private java.util.concurrent.Flow.Subscription subscription; private long size;
    public CompletionStage<byte[]> getBody(){return delegate.getBody();}
    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription){this.subscription=subscription;delegate.onSubscribe(subscription);}
    public void onNext(List<ByteBuffer> buffers){
        for(ByteBuffer buffer:buffers) size+=buffer.remaining();
        if(size>131072){subscription.cancel();delegate.onError(new java.io.IOException("Command response too large"));}
        else delegate.onNext(buffers);
    }
    public void onError(Throwable error){delegate.onError(error);}
    public void onComplete(){delegate.onComplete();}
}
