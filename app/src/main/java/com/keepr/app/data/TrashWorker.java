package com.keepr.app.data;
import android.content.Context;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
public final class TrashWorker extends Worker {
    public TrashWorker(Context context,WorkerParameters parameters) { super(context,parameters); }
    public static void schedule(Context context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("keepr-trash-cleanup",ExistingPeriodicWorkPolicy.KEEP,new PeriodicWorkRequest.Builder(TrashWorker.class,1,TimeUnit.DAYS).build());
    }
    @Override public Result doWork() {
        try(SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(getApplicationContext())) { repository.purgeExpired(); return Result.success(); }
        catch(Exception error) { return Result.retry(); }
    }
}
