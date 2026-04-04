package com.cloudfin.core;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Process;
import java.lang.ProcessBuilder;

public class CoreService extends Service {

    private static final String CHANNEL_ID = "cloudfin_core_channel";
    private static final int NOTIFICATION_ID = 10001;
    private static final String CORE_BINARY = "cloudfin-core";
    private Process coreProcess = null;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification("启动中...").build());
        startCore();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        stopCore();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Cloudfin Core", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Cloudfin Core 后台运行通知");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(channel);
        }
    }

    private NotificationCompat.Builder buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cloudfin Core")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE);
    }

    private void startCore() {
        new Thread(() -> {
            try {
                File filesDir = getFilesDir();
                File coreFile = new File(filesDir, CORE_BINARY);

                if (!coreFile.exists() || !coreFile.canExecute()) {
                    InputStream in = getAssets().open(CORE_BINARY);
                    OutputStream out = openFileOutput(CORE_BINARY, MODE_PRIVATE);
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) > 0) {
                        out.write(buf, 0, len);
                    }
                    in.close();
                    out.close();
                    coreFile.setExecutable(true);
                }

                ProcessBuilder pb = new ProcessBuilder(coreFile.getAbsolutePath());
                pb.directory(filesDir);
                pb.redirectErrorStream(true);
                coreProcess = pb.start();

                NotificationManager nm = getSystemService(NotificationManager.class);
                nm.notify(NOTIFICATION_ID, buildNotification("运行中 | 端口 19001").build());

            } catch (Exception e) {
                NotificationManager nm = getSystemService(NotificationManager.class);
                nm.notify(NOTIFICATION_ID, buildNotification("启动失败: " + e.getMessage()).build());
            }
        }).start();
    }

    private void stopCore() {
        if (coreProcess != null) {
            coreProcess.destroy();
            coreProcess = null;
        }
    }
}
