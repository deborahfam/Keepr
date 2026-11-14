package com.keepr.app.data;

import android.content.Context;
import androidx.work.*;
import com.keepr.app.domain.SavedItem;
import com.keepr.app.domain.CachedMedia;
import java.util.concurrent.TimeUnit;

/** Every discovered URL is persisted before downloading. Batches resume after process death. */
public final class SnapshotWorker extends Worker {
    public static void enqueue(Context context,String itemId) {
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(SnapshotWorker.class)
                .setInputData(new Data.Builder().putString("item_id",itemId).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS)
                .addTag("keepr-snapshot").build();
        WorkManager.getInstance(context).enqueueUniqueWork("snapshot-"+itemId,ExistingWorkPolicy.KEEP,work);
    }
    /** Run a fresh discovery after any active capture finishes, including when it failed. */
    public static void refresh(Context context,String itemId) {
        OneTimeWorkRequest work=new OneTimeWorkRequest.Builder(SnapshotWorker.class)
                .setInputData(new Data.Builder().putString("item_id",itemId).putBoolean("refresh",true).build())
                .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.LINEAR,10,TimeUnit.SECONDS).addTag("keepr-snapshot").build();
        WorkManager.getInstance(context).enqueueUniqueWork("snapshot-"+itemId,ExistingWorkPolicy.APPEND_OR_REPLACE,work);
    }
    public SnapshotWorker(Context context,WorkerParameters params) { super(context,params); }
    @Override public Result doWork() {
        String id=getInputData().getString("item_id"); if(id==null) return Result.failure();
        SQLiteSavedItemRepository repository=new SQLiteSavedItemRepository(getApplicationContext());
        long deadline=System.nanoTime()+TimeUnit.MINUTES.toNanos(2);
        try {
            SavedItem item=repository.find(id); if(item==null) return Result.success();
            if(getInputData().getBoolean("refresh",false) && getRunAttemptCount()==0) repository.retryPhotos(id);
            MetadataService service=new MetadataService();
            if(repository.needsPhotoDiscovery(id) || !repository.hasPhotoPlan(id)) {
                PreviewMetadata preview=service.fetch(item.url());
                if(isStopped()) return Result.retry();
                repository.applyPreview(id,preview); repository.planPhotos(id,preview.imageUrls()); repository.finishPhotoDiscovery(id);
            }
            for(String image:repository.pendingPhotos(id)) {
                if(repository.find(id)==null) return Result.success();
                if(isStopped() || System.nanoTime()>deadline) break;
                CachedMedia media=null;
                try {
                    byte[] bytes=service.fetchImage(image);
                    if(isStopped()) return Result.retry();
                    media=new MediaCache(getApplicationContext()).store(id,image,bytes);
                    repository.attach(media);
                } catch(Exception error) {
                    if(media!=null) new java.io.File(getApplicationContext().getFilesDir(),media.relativePath()).delete();
                    repository.failPhoto(id,image);
                }
                SQLiteSavedItemRepository.PhotoProgress progress=repository.photoProgress(id);
                setProgressAsync(new Data.Builder().putInt("saved",progress.saved()).putInt("total",progress.total()).build());
            }
            if(isStopped()) return Result.retry();
            SQLiteSavedItemRepository.PhotoProgress progress=repository.photoProgress(id);
            boolean pending=progress.pending()>0;
            String message=pending ? progress.saved()+" of "+progress.total()+" photos saved. Remaining photos are queued."
                    : progress.unexposed()>0 ? "The source reports "+progress.total()+" photos, but did not expose "+progress.unexposed()+" of them publicly. The available photos are saved; you can add the rest from your phone."
                    : progress.failed()>0 ? progress.failed()+" photo(s) could not be downloaded. Tap Retry missing photos to try again."
                    : progress.total()==0 ? "The source exposed no downloadable photos. Available text and the original link are saved." : "";
            repository.previewState(id,pending ? SavedItem.PreviewState.PENDING : (progress.failed()>0 || progress.unexposed()>0) ? SavedItem.PreviewState.FAILED : SavedItem.PreviewState.READY,message);
            return pending ? Result.retry() : Result.success();
        } catch(Exception error) {
            boolean retry=getRunAttemptCount()<2;
            repository.previewState(id,retry ? SavedItem.PreviewState.PENDING : SavedItem.PreviewState.FAILED,
                    retry ? "Saved locally. Keepr will retry the public preview when online."
                            : "The source did not provide a preview. Shared text, attached photos, and the original link are saved.");
            return retry ? Result.retry() : Result.success();
        } finally { repository.close(); }
    }
}
