package com.keepr.app;

import android.content.Context;
import com.keepr.app.domain.SavedItem;

final class StatusLabels {
    private StatusLabels() {}
    static String label(Context context,SavedItem.Status status) {
        return context.getString(switch(status) {
            case ACTIVE -> R.string.status_active;
            case REACHED_OUT -> R.string.status_reached_out;
            case PURCHASED -> R.string.status_purchased;
            case ARCHIVED -> R.string.status_archived;
        });
    }
    static String[] choices(Context context) {
        SavedItem.Status[] statuses=SavedItem.Status.values(); String[] labels=new String[statuses.length];
        for(int i=0;i<statuses.length;i++) labels[i]=label(context,statuses[i]); return labels;
    }
}
