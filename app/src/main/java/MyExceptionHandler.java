package com.hfm.app;

import android.content.Context;
import android.content.Intent;
import android.os.Process;
import androidx.annotation.NonNull;
import java.io.PrintWriter;
import java.io.StringWriter;

public class MyExceptionHandler implements Thread.UncaughtExceptionHandler {

    private final Context myContext;
    private final Thread.UncaughtExceptionHandler defaultUEH;

    public static final String EXTRA_CRASH_REPORT = "com.hfm.app.CRASH_REPORT";

    public MyExceptionHandler(Context context) {
        this.myContext = context;
        // Keep a reference to the default handler.
        this.defaultUEH = Thread.getDefaultUncaughtExceptionHandler();
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        // Convert the stack trace to a string.
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String stackTraceString = sw.toString();

        // Create an intent to launch our CrashReportActivity.
        Intent intent = new Intent(myContext, CrashReportActivity.class);
        intent.putExtra(EXTRA_CRASH_REPORT, stackTraceString);

        // These flags are crucial for starting an activity from a non-activity context.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        myContext.startActivity(intent);

        // Kill the current process to prevent the default "App has stopped" dialog.
        Process.killProcess(Process.myPid());
        System.exit(10); // A non-zero exit code indicates an error.
    }
}