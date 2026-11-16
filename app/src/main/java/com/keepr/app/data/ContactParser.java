package com.keepr.app.data;

import com.keepr.app.domain.ContactDetails;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

/** Extracts explicit publication contacts; never treats every long number as a phone. */
public final class ContactParser {
    private static final Pattern EMAIL=Pattern.compile("(?i)(?<![a-z0-9._%+\\-])[a-z0-9][a-z0-9._%+\\-]*@[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9\\-]*[a-z0-9])?)+");
    private static final Pattern PHONE=Pattern.compile("(?imu)(?:^|[\\s;|•])(?:tel[eé]fono|tel(?:ephone)?|phone|mobile|celular|llama(?:r|me)?|call)\\s*[:.]?\\s*(\\+?\\d[\\d ()\\-]{5,30}\\d|\\(\\d{2,4}\\)[\\d ()\\-]{5,25}\\d)");
    private static final Pattern WA=Pattern.compile("(?imu)(?:^|[\\s;|•])(?:whatsapp|whats app|wsp|wa)\\s*:?\\s*(\\+?\\d[\\d ()\\-]{5,30}\\d|\\(\\d{2,4}\\)[\\d ()\\-]{5,25}\\d)");
    private static final Pattern NAME=Pattern.compile("(?imu)(?:^|[\\n;|•])\\s*(?:contacto|contact|vendedor(?:a)?|seller)\\s*:\\s*([\\p{L}][\\p{L} .’'\\-]{1,99})(?=$|[\\n;|•])");
    private static final Pattern LINK=Pattern.compile("(?i)https?://[^\\s<>\"']+");
    private ContactParser() {}
    public static ContactDetails extract(String... parts) {
        String text=String.join("\n",parts); if(text.length()>131072) text=text.substring(0,131072);
        Set<String> phones=new LinkedHashSet<>(),emails=new LinkedHashSet<>(),whatsapp=new LinkedHashSet<>(),names=new LinkedHashSet<>();
        Matcher m=PHONE.matcher(text); while(m.find()) add(phones,phone(m.group(1)));
        m=WA.matcher(text); while(m.find()) add(whatsapp,phone(m.group(1)));
        m=EMAIL.matcher(text); while(m.find()) add(emails,m.group().toLowerCase(Locale.ROOT));
        m=NAME.matcher(text); while(m.find()) add(names,m.group(1).trim());
        m=LINK.matcher(text); while(m.find()) add(whatsapp,whatsappNumber(m.group().replaceFirst("[.,;]+$","")));
        return new ContactDetails(one(names),one(phones),one(emails),one(whatsapp),"Detected in publication text · review before contacting");
    }
    public static ContactDetails saved(String title,String description,String shared,String legacy) {
        ContactDetails old=extract(legacy);
        if(old.phone().isEmpty() && !phone(legacy).isEmpty()) old=new ContactDetails(old.name(),phone(legacy),old.email(),old.whatsapp(),old.source());
        return prefer(extract(title,description,shared),old);
    }
    private static void add(Set<String> values,String value) { if(!value.isEmpty()) values.add(value); }
    private static String one(Set<String> values) { return values.size()==1?values.iterator().next():""; }
    public static String phone(String input) {
        String value=input.trim(); if(!value.matches("\\+?[0-9 ()\\-]{7,35}")) return "";
        String digits=value.replaceAll("[^0-9]","");
        if(digits.length()<7 || digits.length()>15) return "";
        if(value.startsWith("+") && digits.startsWith("0")) return "";
        return (value.startsWith("+")?"+":"")+digits;
    }
    public static String email(String input) { String value=input.trim(); return value.length()<=254 && EMAIL.matcher(value).matches()?value:""; }
    public static String whatsappNumber(String input) {
        String value=input.trim();
        try {
            URI uri=URI.create(value); String host=uri.getHost(); String number="";
            if((!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) || uri.getUserInfo()!=null || uri.getPort()!=-1) return "";
            if("wa.me".equalsIgnoreCase(host)) number=uri.getPath().replaceFirst("^/","").replaceFirst("/$","");
            else if("api.whatsapp.com".equalsIgnoreCase(host) && "/send".equals(uri.getPath()) && uri.getRawQuery()!=null) {
                for(String part:uri.getRawQuery().split("&")) if(part.startsWith("phone=")) { if(!number.isEmpty()) return ""; number=URLDecoder.decode(part.substring(6),StandardCharsets.UTF_8.name()); }
            }
            if(number.startsWith("+")) number=number.substring(1);
            return number.matches("[1-9][0-9]{6,14}")?"+"+number:"";
        } catch(Exception ignored) { return ""; }
    }
    public static String whatsappUrl(String value) {
        String number=value.startsWith("http")?whatsappNumber(value):phone(value);
        return number.matches("\\+[1-9][0-9]{6,14}")?"https://wa.me/"+number.substring(1):"";
    }
    public static ContactDetails validated(String name,String phone,String email,String whatsapp) {
        name=name.trim();phone=phone.trim();email=email.trim();whatsapp=whatsapp.trim();
        if(name.length()>100) throw new IllegalArgumentException("Contact name must be under 100 characters.");
        String p=phone(phone),e=email(email),w=whatsapp.startsWith("http")?whatsappNumber(whatsapp):phone(whatsapp);
        if(!phone.isEmpty() && p.isEmpty()) throw new IllegalArgumentException("Enter a phone number with 7–15 digits.");
        if(!email.isEmpty() && e.isEmpty()) throw new IllegalArgumentException("Enter a valid email address.");
        if(!whatsapp.isEmpty() && w.isEmpty()) throw new IllegalArgumentException("Enter a WhatsApp number or a wa.me link.");
        return new ContactDetails(name,p,e,w,"Reviewed and edited by you");
    }
    public static ContactDetails prefer(ContactDetails primary,ContactDetails fallback) {
        return new ContactDetails(primary.name().isEmpty()?fallback.name():primary.name(),primary.phone().isEmpty()?fallback.phone():primary.phone(),primary.email().isEmpty()?fallback.email():primary.email(),primary.whatsapp().isEmpty()?fallback.whatsapp():primary.whatsapp(),primary.isEmpty()?fallback.source():primary.source());
    }
}
