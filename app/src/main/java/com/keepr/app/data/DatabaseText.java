package com.keepr.app.data;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

/** Read complete SQLite TEXT values without placing a large row in CursorWindow. */
final class DatabaseText {
    static final int CHUNK=16384;
    static String read(SQLiteDatabase db,String id,String column,String prefix) {
        if(!column.equals("description") && !column.equals("shared_text")) throw new IllegalArgumentException("Unsupported text column");
        if(prefix==null || prefix.codePointCount(0,prefix.length())<CHUNK) return prefix==null?"":prefix;
        StringBuilder text=new StringBuilder(prefix); int offset=CHUNK+1;
        while(true) {
            try(Cursor c=db.rawQuery("SELECT substr("+column+",?,?) FROM SavedItem WHERE id=?",new String[]{String.valueOf(offset),String.valueOf(CHUNK),id})) {
                if(!c.moveToFirst()) break;
                String piece=c.getString(0); if(piece==null || piece.isEmpty()) break;
                text.append(piece); int count=piece.codePointCount(0,piece.length()); offset+=count; if(count<CHUNK) break;
            }
        }
        return text.toString();
    }
    static String projection(String column) {
        return column.equals("description") || column.equals("shared_text")?"substr("+column+",1,"+CHUNK+") AS "+column:column;
    }
}
