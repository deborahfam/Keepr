package com.keepr.app.data;

import com.keepr.app.domain.CollectionQuery;
import com.keepr.app.domain.SavedItem;
import java.util.*;

/** Parameterized database filters shared by pages, counts and bulk selection. */
public record FeedQuery(String folder,String text,SavedItem.Status status,String tag,String source,long from,long until,CollectionQuery.Sort sort) {
    static final String AUTH="substr(url,instr(url,'://')+3)";
    static final String HOST="substr("+AUTH+",1,instr("+AUTH+"||'/','/')-1)";
    static final String NO_PORT="CASE WHEN substr("+HOST+",1,1)='[' THEN substr("+HOST+",1,instr("+HOST+",']')) ELSE substr("+HOST+",1,instr("+HOST+"||':',':')-1) END";
    static final String SOURCE="CASE WHEN url='' THEN CASE WHEN site_name='' THEN 'Shared content' ELSE site_name END ELSE CASE WHEN lower(substr("+NO_PORT+",1,4))='www.' THEN lower(substr("+NO_PORT+",5)) ELSE lower("+NO_PORT+") END END";
    record Sql(String where,String[] args,String order) {}
    static String casePattern(String value) {
        StringBuilder pattern=new StringBuilder();
        value.codePoints().forEach(point->{
            int lower=Character.toLowerCase(point),upper=Character.toUpperCase(point),title=Character.toTitleCase(point);
            if(lower!=upper) { pattern.append('[').appendCodePoint(lower).appendCodePoint(upper); if(title!=upper && title!=lower) pattern.appendCodePoint(title); pattern.append(']'); }
            else if(point=='*' || point=='?' || point=='[' || point==']') pattern.append('[').appendCodePoint(point).append(']');
            else pattern.appendCodePoint(point);
        });
        return pattern.toString();
    }
    Sql sql() {
        StringBuilder where=new StringBuilder("id NOT IN (SELECT saved_item_id FROM Trash)"); List<String> args=new ArrayList<>();
        if(!folder.equals("All")) { where.append(" AND COALESCE((SELECT name FROM Category WHERE id=SavedItem.category_id),'Other')=?"); args.add(folder); }
        if(status!=null) { where.append(" AND status=?"); args.add(status.name()); }
        if(!tag.isEmpty()) { where.append(" AND EXISTS(SELECT 1 FROM SavedItemTag it JOIN Tag t ON t.id=it.tag_id WHERE it.saved_item_id=SavedItem.id AND t.name GLOB ?)"); args.add(casePattern(tag)); }
        if(!source.isEmpty()) { where.append(" AND ("+SOURCE+") GLOB ?"); args.add(casePattern(source)); }
        where.append(" AND created_at>=? AND created_at<?"); args.add(Long.toString(from)); args.add(Long.toString(until));
        if(!text.trim().isEmpty()) {
            where.append(" AND (title||' '||url||' '||notes||' '||description||' '||shared_text||' '||location||' '||product_type||' '||price||' '||contact_name||' '||contact_phone||' '||contact_email||' '||contact_whatsapp||' '||COALESCE((SELECT group_concat(t.name,' ') FROM SavedItemTag it JOIN Tag t ON t.id=it.tag_id WHERE it.saved_item_id=SavedItem.id),'')) GLOB ?"); args.add("*"+casePattern(text.trim())+"*");
        }
        String order="created_at "+(sort==CollectionQuery.Sort.OLDEST?"ASC":"DESC")+",id";
        if(sort==CollectionQuery.Sort.PRICE_LOW || sort==CollectionQuery.Sort.PRICE_HIGH) {
            String p="trim(price)",direction=sort==CollectionQuery.Sort.PRICE_HIGH?" DESC":" ASC";
            String valid=p+"!='' AND "+p+" NOT GLOB '*[^0-9.]*' AND "+p+" GLOB '[0-9]*' AND "+p+" NOT LIKE '%.%.%' AND "+p+" NOT LIKE '%.'";
            String integer="ltrim(substr("+p+",1,instr("+p+"||'.','.')-1),'0')";
            String fraction="CASE WHEN instr("+p+",'.')=0 THEN '' ELSE rtrim(substr("+p+",instr("+p+",'.')+1),'0') END";
            // Text components retain decimal precision instead of converting money to a float.
            order="CASE WHEN "+valid+" THEN 0 ELSE 1 END,CASE WHEN "+valid+" THEN currency ELSE '' END COLLATE NOCASE,"+
                "CASE WHEN "+valid+" THEN length("+integer+") END"+direction+",CASE WHEN "+valid+" THEN "+integer+" END"+direction+",CASE WHEN "+valid+" THEN "+fraction+" END"+direction+",created_at DESC,id";
        }
        return new Sql(where.toString(),args.toArray(new String[0]),order);
    }
}
