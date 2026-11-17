package org.carbridge;

import android.os.ParcelFileDescriptor;

/** Runs commands as the shell user inside Shizuku; see ShizukuMode. */
interface IShellService {
    // Transaction code Shizuku reserves for stopping a user service
    void destroy() = 16777114;

    /** Runs [command] with [input] as its stdin; returns the exit code, a newline, then the output. */
    String run(in String[] command, in ParcelFileDescriptor input) = 1;
}
