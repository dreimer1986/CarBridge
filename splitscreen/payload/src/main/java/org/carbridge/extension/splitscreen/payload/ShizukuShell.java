package org.carbridge.extension.splitscreen.payload;

import static org.carbridge.extension.splitscreen.payload.AppScreen.TAG;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.InputEvent;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/** The extension's ShellService, run by Shizuku with shell rights: it owns the car display. */
final class ShizukuShell {
    interface Listener {
        void onReady();

        /** Something the user must do on the phone, or a failure; shown on the car screen. */
        void onProblem(@NonNull String message);
    }

    interface StartListener {
        void onStarted();

        void onProblem(@NonNull String message);
    }

    // The extension's ShellService, merged into the app; its class name is also its descriptor
    private static final String SHELL_SERVICE = "org.carbridge.extension.splitscreen.ShellService";
    // Transaction codes, duplicated from ShellService, which the payload can't reference
    private static final int START_ON_DISPLAY = IBinder.FIRST_CALL_TRANSACTION;
    private static final int CREATE_DISPLAY = IBinder.FIRST_CALL_TRANSACTION + 1;
    private static final int RESIZE_DISPLAY = IBinder.FIRST_CALL_TRANSACTION + 2;
    private static final int RELEASE_DISPLAY = IBinder.FIRST_CALL_TRANSACTION + 3;
    private static final int INJECT_INPUT = IBinder.FIRST_CALL_TRANSACTION + 4;
    // Raise with every ShellService change, or Shizuku keeps running the old one
    private static final int SERVICE_VERSION = 2;
    private static final int REQUEST_CODE = 0xCB;
    // How long to wait for Shizuku before asking the user to start it
    private static final long BINDER_WAIT_MS = 2000;
    // The Shizuku app's binder hand-out, see its ShellBinderRequestHandler
    private static final String MANAGER_PACKAGE = "moe.shizuku.privileged.api";
    private static final String MANAGER_REQUEST_BINDER = "rikka.shizuku.intent.action.REQUEST_BINDER";
    private static final int MANAGER_REPLY = 1;
    private static final long MANAGER_ASK_MS = 3000;

    private final Context context;
    private final String appName;
    private final Runnable onLost;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs serviceArgs;
    private final Shizuku.OnRequestPermissionResultListener permissionListener = this::onPermissionResult;
    private final Shizuku.OnBinderReceivedListener binderListener = this::onBinderReceived;
    private final Runnable binderTimeout = this::onBinderTimeout;
    private final Runnable askManagerForBinder = this::askManagerForBinder;
    @Nullable private IBinder service;
    private boolean bound;
    private boolean waitingForBinder;
    @Nullable private Listener pending;

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (binder == null || !binder.pingBinder()) return;
            service = binder;
            Listener listener = pending;
            pending = null;
            if (listener != null) listener.onReady();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
            Log.w(TAG, "The Shizuku service stopped");
            onLost.run();
        }
    };

    /** [onLost] runs when the service dies, taking the car display with it. */
    ShizukuShell(@NonNull Context context, @NonNull Runnable onLost) {
        this.context = context;
        this.onLost = onLost;
        appName = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
        serviceArgs = new Shizuku.UserServiceArgs(
            new ComponentName(context.getPackageName(), SHELL_SERVICE))
            .processNameSuffix("carbridge_shell")
            .daemon(false)
            .debuggable(false)
            .version(SERVICE_VERSION);
    }

    boolean isReady() {
        return service != null;
    }

    /** Calls [listener] once the service runs, asking the user for Shizuku first if needed. */
    void connect(@NonNull Listener listener) {
        if (service != null) {
            listener.onReady();
            return;
        }
        pending = listener;
        if (Shizuku.pingBinder()) {
            usePending();
            return;
        }
        // Stays registered past the timeout: after Shizuku starts, its binder can take a while
        if (!waitingForBinder) {
            waitingForBinder = true;
            Shizuku.addBinderReceivedListener(binderListener, main);
            askManagerForBinder.run();
        }
        main.removeCallbacks(binderTimeout);
        main.postDelayed(binderTimeout, BINDER_WAIT_MS);
    }

    /** Returns the new display's id, or -1. */
    int createDisplay(int width, int height, int dpi, @NonNull Surface surface) {
        Parcel reply = call(CREATE_DISPLAY, data -> {
            data.writeInt(width);
            data.writeInt(height);
            data.writeInt(dpi);
            data.writeTypedObject(surface, 0);
        }, 0);
        if (reply == null) return -1;
        try {
            return reply.readInt();
        } finally {
            reply.recycle();
        }
    }

    void resizeDisplay(int width, int height, int dpi, @NonNull Surface surface) {
        Parcel reply = call(RESIZE_DISPLAY, data -> {
            data.writeInt(width);
            data.writeInt(height);
            data.writeInt(dpi);
            data.writeTypedObject(surface, 0);
        }, 0);
        if (reply != null) reply.recycle();
    }

    void releaseDisplay() {
        call(RELEASE_DISPLAY, data -> { }, IBinder.FLAG_ONEWAY);
    }

    /** Sends [event] to the car display as real input; false when the service isn't running. */
    boolean inject(@NonNull InputEvent event, int displayId) {
        if (service == null) return false;
        call(INJECT_INPUT, data -> {
            data.writeInt(displayId);
            data.writeTypedObject(event, 0);
        }, IBinder.FLAG_ONEWAY);
        return true;
    }

    /** Starts [component] on [displayId] with the shell's rights. Needs [connect] first. */
    void start(@NonNull ComponentName component, int displayId, @NonNull StartListener listener) {
        IBinder binder = service;
        if (binder == null) {
            listener.onProblem("Couldn't connect to Shizuku. Tap to try again");
            return;
        }
        // am start takes a moment; keep it off the main thread
        worker.execute(() -> {
            String error = startOnDisplay(binder, component.flattenToString(), displayId);
            main.post(() -> {
                if (error == null) {
                    listener.onStarted();
                } else {
                    Log.w(TAG, "Shell start failed: " + error);
                    listener.onProblem("Couldn't open the app on the car screen. Tap to try again");
                }
            });
        });
    }

    void release() {
        stopWaitingForBinder();
        main.removeCallbacksAndMessages(null);
        pending = null;
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        if (bound) {
            try {
                // Removing the service also releases its display
                Shizuku.unbindUserService(serviceArgs, connection, true);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unbinding the Shizuku service failed", e);
            }
            bound = false;
        }
        service = null;
        worker.shutdown();
    }

    /**
     * Shizuku only sends its binder to an app when the app's process starts or changes state, so
     * a process that was already running when Shizuku started never gets it. The Shizuku app
     * hands it out on request, as it does for its rish shell; ask until it has one.
     */
    private void askManagerForBinder() {
        Binder reply = new Binder() {
            @Override
            protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel out, int flags)
                    throws RemoteException {
                if (code != MANAGER_REPLY) return super.onTransact(code, data, out, flags);
                IBinder binder = data.readStrongBinder();
                if (binder != null && binder.pingBinder()) {
                    main.post(() -> {
                        if (!Shizuku.pingBinder()) Shizuku.onBinderReceived(binder, context.getPackageName());
                    });
                }
                return true;
            }
        };
        Bundle extras = new Bundle();
        extras.putBinder("binder", reply);
        Intent request = new Intent(MANAGER_REQUEST_BINDER).setPackage(MANAGER_PACKAGE).putExtra("data", extras);
        try {
            context.sendBroadcast(request);
        } catch (RuntimeException e) {
            Log.w(TAG, "Asking the Shizuku app for its binder failed", e);
        }
        main.postDelayed(askManagerForBinder, MANAGER_ASK_MS);
    }

    // The request stays pending, so the app still opens once the binder arrives
    private void onBinderTimeout() {
        Listener listener = pending;
        if (listener != null) {
            listener.onProblem("Start Shizuku on your phone. " + appName + " opens here once it's running");
        }
    }

    private void onBinderReceived() {
        stopWaitingForBinder();
        if (pending != null) usePending();
    }

    private void stopWaitingForBinder() {
        main.removeCallbacks(binderTimeout);
        main.removeCallbacks(askManagerForBinder);
        if (waitingForBinder) {
            waitingForBinder = false;
            Shizuku.removeBinderReceivedListener(binderListener);
        }
    }

    private void usePending() {
        Listener listener = pending;
        if (listener == null) return;
        if (Shizuku.isPreV11()) {
            pending = null;
            listener.onProblem("Update Shizuku on your phone, then tap here");
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                pending = null;
                listener.onProblem("Allow " + appName + " in the Shizuku app on your phone, then tap here");
                return;
            }
            Shizuku.addRequestPermissionResultListener(permissionListener);
            Shizuku.requestPermission(REQUEST_CODE);
            listener.onProblem("Allow " + appName + " in the Shizuku prompt on your phone");
            return;
        }
        bind();
    }

    private void onPermissionResult(int requestCode, int grantResult) {
        if (requestCode != REQUEST_CODE) return;
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Listener listener = pending;
        if (listener == null) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            bind();
        } else {
            pending = null;
            listener.onProblem("Shizuku access was denied. Allow it in the Shizuku app, then tap here");
        }
    }

    private void bind() {
        if (bound) return;
        try {
            Shizuku.bindUserService(serviceArgs, connection);
            bound = true;
        } catch (RuntimeException e) {
            Log.w(TAG, "Binding the Shizuku service failed", e);
            Listener listener = pending;
            pending = null;
            if (listener != null) listener.onProblem("Couldn't connect to Shizuku. Tap to try again");
        }
    }

    private interface Writer {
        void write(Parcel data);
    }

    /** Returns the reply, positioned after its exception header, or null on failure or oneway. */
    @Nullable
    private Parcel call(int code, Writer writer, int flags) {
        IBinder binder = service;
        if (binder == null) return null;
        boolean oneway = (flags & IBinder.FLAG_ONEWAY) != 0;
        Parcel data = Parcel.obtain();
        Parcel reply = oneway ? null : Parcel.obtain();
        try {
            data.writeInterfaceToken(SHELL_SERVICE);
            writer.write(data);
            binder.transact(code, data, reply, flags);
            if (reply == null) return null;
            reply.readException();
            return reply;
        } catch (RemoteException | RuntimeException e) {
            Log.w(TAG, "Shell call " + code + " failed", e);
            if (reply != null) reply.recycle();
            return null;
        } finally {
            data.recycle();
        }
    }

    @Nullable
    private static String startOnDisplay(IBinder binder, String component, int displayId) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SHELL_SERVICE);
            data.writeString(component);
            data.writeInt(displayId);
            binder.transact(START_ON_DISPLAY, data, reply, 0);
            reply.readException();
            return reply.readString();
        } catch (RemoteException | RuntimeException e) {
            return e.toString();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
