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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import rikka.shizuku.Shizuku;

/** Places the app's first activity on the car display with Shizuku's shell rights. */
final class ShizukuShell {
    interface Listener {
        void onStarted();

        /** Something the user must do on the phone, or a failure; shown on the car screen. */
        void onProblem(@NonNull String message);
    }

    // The extension's ShellService, merged into the app; its class name is also its descriptor
    private static final String SHELL_SERVICE = "org.carbridge.extension.splitscreen.ShellService";
    private static final int START_ON_DISPLAY = IBinder.FIRST_CALL_TRANSACTION;
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
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final Shizuku.UserServiceArgs serviceArgs;
    private final Shizuku.OnRequestPermissionResultListener permissionListener = this::onPermissionResult;
    @Nullable private IBinder service;
    private boolean bound;
    @Nullable private Request pending;
    private final Shizuku.OnBinderReceivedListener binderListener = this::onBinderReceived;
    private boolean waitingForBinder;
    private final Runnable binderTimeout = this::onBinderTimeout;
    private final Runnable askManagerForBinder = this::askManagerForBinder;

    private static final class Request {
        final String component;
        final int displayId;
        final Listener listener;

        Request(String component, int displayId, Listener listener) {
            this.component = component;
            this.displayId = displayId;
            this.listener = listener;
        }
    }

    private final ServiceConnection connection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (binder == null || !binder.pingBinder()) return;
            service = binder;
            runPending();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
            bound = false;
        }
    };

    ShizukuShell(@NonNull Context context) {
        this.context = context;
        appName = context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
        serviceArgs = new Shizuku.UserServiceArgs(
            new ComponentName(context.getPackageName(), SHELL_SERVICE))
            .processNameSuffix("carbridge_shell")
            .daemon(false)
            .debuggable(false)
            .version(1);
    }

    void start(@NonNull ComponentName component, int displayId, @NonNull Listener listener) {
        pending = new Request(component.flattenToString(), displayId, listener);
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
        Request request = pending;
        if (request != null) {
            request.listener.onProblem("Start Shizuku on your phone. " + appName + " opens here once it's running");
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
        Request request = pending;
        if (request == null) return;
        if (Shizuku.isPreV11()) {
            pending = null;
            request.listener.onProblem("Update Shizuku on your phone, then tap here");
            return;
        }
        if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
            if (Shizuku.shouldShowRequestPermissionRationale()) {
                pending = null;
                request.listener.onProblem("Allow " + appName + " in the Shizuku app on your phone, then tap here");
                return;
            }
            Shizuku.addRequestPermissionResultListener(permissionListener);
            Shizuku.requestPermission(REQUEST_CODE);
            request.listener.onProblem("Allow " + appName + " in the Shizuku prompt on your phone");
            return;
        }
        runPending();
    }

    void release() {
        stopWaitingForBinder();
        main.removeCallbacksAndMessages(null);
        pending = null;
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        if (bound) {
            try {
                Shizuku.unbindUserService(serviceArgs, connection, true);
            } catch (RuntimeException e) {
                Log.w(TAG, "Unbinding the Shizuku service failed", e);
            }
            bound = false;
        }
        service = null;
        worker.shutdown();
    }

    private void onPermissionResult(int requestCode, int grantResult) {
        if (requestCode != REQUEST_CODE) return;
        Shizuku.removeRequestPermissionResultListener(permissionListener);
        Request request = pending;
        if (request == null) return;
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            runPending();
        } else {
            pending = null;
            request.listener.onProblem("Shizuku access was denied. Allow it in the Shizuku app, then tap here");
        }
    }

    private void runPending() {
        Request request = pending;
        if (request == null) return;
        IBinder binder = service;
        if (binder == null) {
            if (!bound) {
                try {
                    Shizuku.bindUserService(serviceArgs, connection);
                    bound = true;
                } catch (RuntimeException e) {
                    Log.w(TAG, "Binding the Shizuku service failed", e);
                    pending = null;
                    request.listener.onProblem("Couldn't connect to Shizuku. Tap to try again");
                }
            }
            return;
        }
        pending = null;
        // am start takes a moment; keep it off the main thread
        worker.execute(() -> {
            String error = transact(binder, request);
            main.post(() -> {
                if (error == null) {
                    request.listener.onStarted();
                } else {
                    Log.w(TAG, "Shell start failed: " + error);
                    request.listener.onProblem("Couldn't open the app on the car screen. Tap to try again");
                }
            });
        });
    }

    @Nullable
    private String transact(IBinder binder, Request request) {
        Parcel data = Parcel.obtain();
        Parcel reply = Parcel.obtain();
        try {
            data.writeInterfaceToken(SHELL_SERVICE);
            data.writeString(request.component);
            data.writeInt(request.displayId);
            binder.transact(START_ON_DISPLAY, data, reply, 0);
            reply.readException();
            return reply.readString();
        } catch (RemoteException | RuntimeException e) {
            service = null;
            return e.toString();
        } finally {
            data.recycle();
            reply.recycle();
        }
    }
}
