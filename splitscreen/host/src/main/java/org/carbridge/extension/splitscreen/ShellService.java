package org.carbridge.extension.splitscreen;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.os.Binder;
import android.os.Build;
import android.os.Parcel;
import android.os.RemoteException;
import android.util.Log;
import android.view.InputEvent;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Shizuku user service: Shizuku runs it in a separate process with shell rights. It owns the car
 * display, because only the shell may create a trusted public one: Android shows permission
 * prompts, dialogs of other apps and the app's own pop-ups there, unlike on a display the app
 * owns. It starts only this app's activities, and sends input only to its own display.
 */
public final class ShellService extends Binder {
    // Duplicated in the payload's ShizukuShell, which can't reference this class
    static final String DESCRIPTOR = "org.carbridge.extension.splitscreen.ShellService";
    static final int START_ON_DISPLAY = FIRST_CALL_TRANSACTION;
    static final int CREATE_DISPLAY = FIRST_CALL_TRANSACTION + 1;
    static final int RESIZE_DISPLAY = FIRST_CALL_TRANSACTION + 2;
    static final int RELEASE_DISPLAY = FIRST_CALL_TRANSACTION + 3;
    static final int INJECT_INPUT = FIRST_CALL_TRANSACTION + 4;
    private static final String TAG = "CarBridgeSplit";
    private static final String SHELL_PACKAGE = "com.android.shell";

    // Hidden DisplayManager flags, as scrcpy uses them for its virtual displays
    private static final int FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int FLAG_TRUSTED = 1 << 10;
    private static final int FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int FLAG_ALWAYS_UNLOCKED = 1 << 12;
    private static final int FLAG_TOUCH_FEEDBACK_DISABLED = 1 << 13;
    private static final int FLAG_OWN_FOCUS = 1 << 14;
    // InputManager.INJECT_INPUT_EVENT_MODE_ASYNC
    private static final int INJECT_ASYNC = 0;
    // Shizuku's UserService destroy() transaction
    private static final int DESTROY = 16777115;
    private static final Pattern COMPONENT = Pattern.compile("[\\w.]+/[\\w.$]+");

    private final Context context;
    private final String packageName;
    @Nullable private VirtualDisplay display;
    @Nullable private Object inputManager;
    @Nullable private Method injectInputEvent;
    @Nullable private Method setDisplayId;

    public ShellService(@NonNull Context context) {
        this.context = context;
        packageName = context.getPackageName();
    }

    @Override
    protected boolean onTransact(int code, @NonNull Parcel data, @Nullable Parcel reply, int flags)
            throws RemoteException {
        if (code == DESTROY) {
            releaseDisplay();
            System.exit(0);
            return true;
        }
        switch (code) {
            case START_ON_DISPLAY: {
                data.enforceInterface(DESCRIPTOR);
                String error = start(data.readString(), data.readInt());
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeString(error);
                }
                return true;
            }
            case CREATE_DISPLAY: {
                data.enforceInterface(DESCRIPTOR);
                int width = data.readInt();
                int height = data.readInt();
                int dpi = data.readInt();
                Surface surface = data.readTypedObject(Surface.CREATOR);
                int displayId = createDisplay(width, height, dpi, surface);
                if (reply != null) {
                    reply.writeNoException();
                    reply.writeInt(displayId);
                }
                return true;
            }
            case RESIZE_DISPLAY: {
                data.enforceInterface(DESCRIPTOR);
                int width = data.readInt();
                int height = data.readInt();
                int dpi = data.readInt();
                Surface surface = data.readTypedObject(Surface.CREATOR);
                resizeDisplay(width, height, dpi, surface);
                if (reply != null) reply.writeNoException();
                return true;
            }
            case RELEASE_DISPLAY:
                data.enforceInterface(DESCRIPTOR);
                releaseDisplay();
                if (reply != null) reply.writeNoException();
                return true;
            case INJECT_INPUT: {
                data.enforceInterface(DESCRIPTOR);
                int displayId = data.readInt();
                InputEvent event = data.readTypedObject(InputEvent.CREATOR);
                if (event != null) inject(event, displayId);
                return true;
            }
            default:
                return super.onTransact(code, data, reply, flags);
        }
    }

    /** Returns the new display's id, or -1. */
    private synchronized int createDisplay(int width, int height, int dpi, @Nullable Surface surface) {
        releaseDisplay();
        try {
            // The display's owner must be the calling uid's package, and the shell runs this
            Context shell = context.createPackageContext(SHELL_PACKAGE, 0);
            DisplayManager manager = shell.getSystemService(DisplayManager.class);
            display = manager.createVirtualDisplay("CarBridge", width, height, dpi, surface, displayFlags());
            return display.getDisplay().getDisplayId();
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            Log.e(TAG, "Creating the car display failed", e);
            return -1;
        }
    }

    private static int displayFlags() {
        int flags = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
            | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
            | FLAG_SUPPORTS_TOUCH;
        if (Build.VERSION.SDK_INT >= 33) {
            // Trusted: system dialogs may show; always unlocked: works while the phone is locked
            flags |= FLAG_TRUSTED | FLAG_OWN_DISPLAY_GROUP | FLAG_ALWAYS_UNLOCKED | FLAG_TOUCH_FEEDBACK_DISABLED;
        }
        // Its own focus, so keys like Back reach the car display's app, not the phone's
        if (Build.VERSION.SDK_INT >= 34) flags |= FLAG_OWN_FOCUS;
        return flags;
    }

    private synchronized void resizeDisplay(int width, int height, int dpi, @Nullable Surface surface) {
        if (display == null) return;
        display.resize(width, height, dpi);
        display.setSurface(surface);
    }

    private synchronized void releaseDisplay() {
        if (display == null) return;
        // Android moves the display's activities to the phone screen
        display.release();
        display = null;
    }

    private synchronized void inject(InputEvent event, int displayId) {
        if (display == null || display.getDisplay().getDisplayId() != displayId) return;
        try {
            if (injectInputEvent == null) loadInput();
            setDisplayId.invoke(event, displayId);
            injectInputEvent.invoke(inputManager, event, INJECT_ASYNC);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Injecting input failed", e);
        }
    }

    private void loadInput() throws ReflectiveOperationException {
        Class<?> global;
        try {
            // Android 14 moved injection out of InputManager
            global = Class.forName("android.hardware.input.InputManagerGlobal");
        } catch (ClassNotFoundException e) {
            global = Class.forName("android.hardware.input.InputManager");
        }
        inputManager = global.getMethod("getInstance").invoke(null);
        injectInputEvent = global.getMethod("injectInputEvent", InputEvent.class, int.class);
        setDisplayId = InputEvent.class.getMethod("setDisplayId", int.class);
    }

    /** Returns null on success, otherwise what went wrong. */
    @Nullable
    private String start(@Nullable String component, int displayId) {
        if (component == null || !COMPONENT.matcher(component).matches()
                || !component.startsWith(packageName + "/") || displayId <= 0) {
            return "Rejected start request: " + component + " on display " + displayId;
        }
        ProcessBuilder builder = new ProcessBuilder(
            "/system/bin/am", "start", "--display", String.valueOf(displayId),
            // FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_MULTIPLE_TASK
            "-f", "0x18000000", "-n", component)
            .redirectErrorStream(true);
        try {
            Process process = builder.start();
            String output = read(process.getInputStream());
            int exit = process.waitFor();
            // am reports most failures on stdout with exit code 0
            if (exit != 0 || output.contains("Error") || output.contains("Exception")) {
                return output.trim();
            }
            return null;
        } catch (IOException e) {
            return e.toString();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return e.toString();
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        for (int n; (n = in.read(buffer)) != -1; ) out.write(buffer, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }
}
