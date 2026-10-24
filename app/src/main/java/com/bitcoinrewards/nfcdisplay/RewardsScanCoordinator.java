package com.bitcoinrewards.nfcdisplay;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.FutureTask;

/** Application-owned single worker for the complete check-in lifecycle. */
final class RewardsScanCoordinator {
    interface Listener {
        void onStatus(String message);
        void onLocked(String message);
        void onRetryRequired();
        void onConfirmation(CheckInApiContract.ConfirmationResponse response,String localDestinationLabel);
        void onTerminal(String message);
    }
    private interface Work { void run() throws Exception; }
    private interface UiAction { void deliver(Listener listener); }
    private static volatile RewardsScanCoordinator instance;
    static RewardsScanCoordinator get(Context context) {
        if (instance == null) synchronized (RewardsScanCoordinator.class) {
            if (instance == null) instance = new RewardsScanCoordinator(context.getApplicationContext());
        }
        return instance;
    }

    private final Context context;
    private final PendingOperationRepository repository;
    private final OperationTaskGate gate = new OperationTaskGate();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private WeakReference<Listener> listener = new WeakReference<>(null);
    private PendingOperation operation;

    private RewardsScanCoordinator(Context context) {
        this.context=context;
        repository=new PendingOperationRepository(new PreferenceStorage(context));
    }

    synchronized void attach(Listener value) { listener=new WeakReference<>(value); resumeOrReport(); }
    synchronized void detach(Listener value) { if(listener.get()==value)listener.clear();gate.uiDetached(); }
    synchronized boolean hasOperation(){return operation!=null||PendingOperationRepository.isLocked(repository.load());}

    synchronized void resumeOrReport() {
        PendingOperationRepository.LoadResult loaded=repository.load();
        if(loaded.malformed){locked("Scanner state could not be verified. Restore the app data from a trusted backup or contact support.");return;}
        operation=loaded.operation;
        if(operation==null)return;
        if(!identityMatches(operation)){locked("This operation belongs to different scanner credentials. Restore the original store, device and scoped token to resume it.");return;}
        status("Resuming the locked reward operation…");
        runOperation();
    }

    synchronized boolean begin(WalletQrPayloadParser.ParsedPayload payload) {
        if(payload==null)return false;
        if(gate.running()||operation!=null||PendingOperationRepository.isLocked(repository.load()))return false;
        if(!SettingsActivity.configurationUsable(context)){locked("Provision this scanner with a scoped device token in settings.");return false;}
        PendingOperation prepared=PendingOperation.prepared(SettingsActivity.getStoreId(context),SettingsActivity.getCheckInDeviceId(context),SettingsActivity.getCredentialFingerprint(context),SettingsActivity.getTokenGeneration(context),payload,UUID.randomUUID(),CheckInApiContract.VERSION);
        if(!repository.save(prepared)){locked("Check-in identity could not be saved. No network request was made.");return false;}
        operation=prepared;
        status("Starting secure check-in…");
        return runOperation();
    }

    synchronized void confirm(){if(operation==null||operation.confirmationId==null||gate.running())return;PendingOperation next=operation.confirming();if(!repository.save(next)){locked("Confirmation state could not be saved safely.");return;}operation=next;status("Submitting explicit confirmation…");execute(()->terminalRequest(CheckInApiContract.confirm(base(),token(),next),"POST","Reward operation completed."));}
    synchronized void requestCancellation(){if(operation==null||operation.confirmationId==null||gate.running())return;PendingOperation next=operation.cancelling();if(!repository.save(next)){locked("Cancellation state could not be saved safely. No request was made.");return;}operation=next;status("Requesting cancellation; operation remains locked…");execute(()->{try{Response response=request(CheckInApiContract.cancel(base(),token(),next),"POST");if(response.code>=200&&response.code<300){CheckInApiContract.ConfirmationResponse parsed=CheckInApiContract.parseConfirmationResponse(response.body,next);if(parsed.terminal()&&repository.clearAuthoritative()){synchronized(this){operation=null;}terminal("Reward operation cancelled by the server.");return;}if(!repository.save(parsed.operation))throw new IllegalStateException();synchronized(this){operation=parsed.operation;}}}catch(Exception ignored){}poll(next);});}
    void shutdown(){gate.shutdown();}

    private synchronized boolean runOperation(){if(operation==null||gate.running())return false;PendingOperation expected=operation;return execute(()->{PendingOperation polling=expected;if(expected.phase==PendingOperation.Phase.SUBMITTING){Response response=request(CheckInApiContract.checkIn(base(),token(),expected),"POST");if(response.code<200||response.code>=300)throw new IllegalStateException();polling=CheckInApiContract.parseCheckInResponse(response.body).correlate(expected);if(!repository.save(polling))throw new IllegalStateException();synchronized(this){operation=polling;}}poll(polling);});}
    private void poll(PendingOperation initial){status("Waiting for the exact server-authoritative reward amount…");PendingOperation expected=initial;for(int attempt=0;attempt<60&&!Thread.currentThread().isInterrupted();attempt++){try{if(!identityMatches(expected))throw new IllegalStateException();Response response=request(CheckInApiContract.pending(base(),token(),expected),"GET");if(response.code==200){CheckInApiContract.ConfirmationResponse parsed=CheckInApiContract.parseConfirmationResponse(response.body,expected);if(parsed.terminal()){if(repository.clearAuthoritative()){synchronized(this){operation=null;}terminal("Reward operation reached an authoritative terminal state.");}else locked("Terminal state could not be saved safely.");return;}if(!repository.save(parsed.operation)){locked("Confirmation state could not be saved safely.");return;}expected=parsed.operation;synchronized(this){operation=expected;}if(parsed.presentConfirmation()){confirmation(parsed);return;}status("Server is reconciling the submitted operation…");}else if(response.code!=204)break;Thread.sleep(2000);}catch(InterruptedException e){Thread.currentThread().interrupt();return;}catch(Exception e){break;}}retryRequired();}
    private void terminalRequest(CheckInApiContract.Request request,String method,String message){PendingOperation expected; synchronized(this){expected=operation;}try{Response response=request(request,method);if(response.code>=200&&response.code<300){CheckInApiContract.ConfirmationResponse parsed=CheckInApiContract.parseConfirmationResponse(response.body,expected);if(parsed.terminal()&&repository.clearAuthoritative()){synchronized(this){operation=null;}terminal(message);return;}if(!repository.save(parsed.operation))throw new IllegalStateException();expected=parsed.operation;synchronized(this){operation=expected;}}}catch(Exception ignored){}poll(expected);}

    private synchronized boolean execute(Work work){final FutureTask<?>[] holder=new FutureTask<?>[1];FutureTask<Void> task=new FutureTask<>(()->{try{work.run();}catch(Exception e){retryRequired();}finally{gate.completed(holder[0]);}return null;});holder[0]=task;if(!gate.claim(task))return false;executor.execute(task);return true;}
    private Response request(CheckInApiContract.Request request,String method)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(request.url).openConnection();OperationTaskGate.Disconnectable handle=c::disconnect;gate.connection(handle);try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(request.connectTimeoutMs);c.setReadTimeout(request.readTimeoutMs);c.setRequestMethod(method);c.setRequestProperty("Accept","application/json");c.setRequestProperty("Authorization",request.authorization);if(request.body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(request.body.getBytes(StandardCharsets.UTF_8));}}int code=c.getResponseCode();if(code>=300&&code<400)throw new IllegalArgumentException();InputStream in=code>=400?c.getErrorStream():c.getInputStream();return new Response(code,readBounded(in,request.maxResponseBytes));}finally{gate.clearConnection(handle);c.disconnect();}}
    static String readBounded(InputStream in,int cap)throws Exception{if(in==null)return "";try(InputStream source=in;ByteArrayOutputStream out=new ByteArrayOutputStream()){byte[] buffer=new byte[2048];int total=0,n;while((n=source.read(buffer))!=-1){total+=n;if(total>cap)throw new IllegalArgumentException();out.write(buffer,0,n);}return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    private boolean identityMatches(PendingOperation op){return op.matchesIdentity(SettingsActivity.getStoreId(context),SettingsActivity.getCheckInDeviceId(context),SettingsActivity.getCredentialFingerprint(context),SettingsActivity.getTokenGeneration(context))&&SettingsActivity.configurationUsable(context);}
    private String base(){return SettingsActivity.getBtcpayUrl(context);} private String token(){return SettingsActivity.getCheckInToken(context);}
    private void status(String value){post(l->l.onStatus(value));} private void locked(String value){post(l->l.onLocked(value));} private void retryRequired(){post(Listener::onRetryRequired);} private void confirmation(CheckInApiContract.ConfirmationResponse value){String label; synchronized(this){label=operation==null?"Scanned payout destination":operation.localDestinationLabel;}post(l->l.onConfirmation(value,label));} private void terminal(String value){post(l->l.onTerminal(value));}
    private void post(UiAction action){main.post(()->{Listener value; synchronized(this){value=listener.get();}if(value!=null)action.deliver(value);});}
    private static final class Response{final int code;final String body;Response(int code,String body){this.code=code;this.body=body;}}
    private static final class PreferenceStorage implements PendingOperationRepository.Storage{private static final String KEY="pending_operation_v2";private final SharedPreferences p;PreferenceStorage(Context c){p=c.getSharedPreferences(SettingsActivity.PREFS_NAME,Context.MODE_PRIVATE);}public boolean commit(String value){return p.edit().putString(KEY,value).commit();}public String read(){return p.getString(KEY,null);}public boolean clear(){return p.edit().remove(KEY).commit();}}
}
