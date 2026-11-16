package com.keepr.app.data;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Conservative offline suggestions from publication text, never from account/device location. */
public record ListingFacts(String price,String currency,String period,String location,String productType) {
    private static final String MONEY="US\\$|USD|EUR|MXN|CAD|GBP|\\$|€|£";
    private static final Pattern AMOUNT=Pattern.compile("(?iu)(?<![\\p{L}\\d])("+MONEY+")\\s*(\\d[\\d.,]*)(?!\\d)|(?<![\\d.,])(\\d[\\d.,]*)\\s*(USD|EUR|MXN|CAD|GBP|euros?|d[oó]lares?)(?![\\p{L}])");
    private static final Pattern PLACE=Pattern.compile("(?imu)(?:^|[\\n;|•])\\s*(?:ubicaci[oó]n|localidad|location|located in|ubicad[oa] en|recog(?:er|ida|ido) en|pickup (?:in|at))\\s*:?\\s*([^\\n;|•.!?]{2,100})");
    private static final String[][] TYPES={
        {"Apartment","apartamento|apartment|condominio|condo"}, {"House","casa|house"},
        {"Bicycle","bicicleta|bicycle|bike"}, {"Motorcycle","motocicleta|motorcycle|moto"},
        {"Car","carro|coche|automovil|sedan|car"}, {"Phone","iphone|smartphone|telefono|celular"},
        {"Computer","laptop|macbook|computadora|computer"}, {"Furniture","sofa|sillon|mesa|table|silla|chair|mueble|dresser|armario"},
        {"Appliance","nevera|refrigerador|refrigerator|lavadora|washer|secadora|dryer"},
        {"Clothing","camisa|pantalon|vestido|shirt|dress|jacket"}, {"Footwear","zapatos|zapatillas|sneakers|shoes"}
    };
    public static ListingFacts extract(String... parts) {
        String text=String.join("\n",parts); if(text.length()>131072) text=text.substring(0,131072);
        Set<String> amounts=new LinkedHashSet<>(); boolean ambiguous=false;
        Matcher matcher=AMOUNT.matcher(text);
        while(matcher.find()) {
            String before=text.substring(Math.max(0,matcher.start()-25),matcher.start()).toLowerCase(Locale.ROOT);
            String after=text.substring(matcher.end(),Math.min(text.length(),matcher.end()+24));
            if(before.matches("(?s).*(?:desde|from|hasta|up to|deposit|dep[oó]sito|down payment)\\s*:?\\s*$") || after.matches("(?s)^\\s*(?:[-–]|to|a)\\s*[$€£]?\\d.*")) ambiguous=true;
            if(after.matches("(?isu)^[km]\\b.*")) ambiguous=true;
            String raw=matcher.group(2)!=null?matcher.group(2):matcher.group(3);
            String number=normalizeAmount(raw.replaceFirst("[.,]$",""));
            if(number.isEmpty()) { ambiguous=true; continue; }
            String token=matcher.group(1)!=null?matcher.group(1):matcher.group(4);
            String currency=switch(token.toUpperCase(Locale.ROOT)) { case "US$","USD" -> "USD"; case "€","EURO","EUROS","EUR" -> "EUR"; case "£","GBP" -> "GBP"; case "DÓLAR","DÓLARES","DOLAR","DOLARES","$" -> "$"; default -> token.toUpperCase(Locale.ROOT); };
            String period="";
            if(after.matches("(?isu)^\\s*(?:/|per\\s+|al\\s+)?(?:mes|mensual|month|mo)\\b.*")) period="month";
            else if(after.matches("(?isu)^\\s*(?:/|per\\s+|por\\s+)?(?:d[ií]a|day)\\b.*")) period="day";
            else if(after.matches("(?isu)^\\s*(?:/|per\\s+|por\\s+)?(?:semana|week)\\b.*")) period="week";
            else if(after.matches("(?isu)^\\s*(?:/|per\\s+|por\\s+)?(?:año|year)\\b.*")) period="year";
            else if(after.matches("(?isu)^\\s*(?:/|per\\s+|por\\s+)?(?:hora|hour|hr)\\b.*")) period="hour";
            amounts.add(number+"\t"+currency+"\t"+period);
        }
        String price="",currency="",period="";
        if(!ambiguous && amounts.size()==1) { String[] amount=amounts.iterator().next().split("\t",-1); price=amount[0];currency=amount[1];period=amount[2]; }
        Set<String> places=new LinkedHashSet<>(); Matcher place=PLACE.matcher(text);
        while(place.find()) { String value=place.group(1).trim(); if(!value.contains("$") && !value.matches(".*\\d{5,}.*")) places.add(value); }
        String normalized=Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT);
        Set<String> types=new LinkedHashSet<>();
        for(String[] type:TYPES) if(Pattern.compile("\\b(?:"+type[1]+")\\b").matcher(normalized).find()) types.add(type[0]);
        return new ListingFacts(price,currency,period,places.size()==1?places.iterator().next():"",types.size()==1?types.iterator().next():"");
    }
    public static String normalizeAmount(String input) {
        String value=input.trim();
        if(value.matches("\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?")) value=value.replace(",","");
        else if(value.matches("\\d{1,3}(?:\\.\\d{3})+(?:,\\d{1,2})?")) value=value.replace(".","").replace(',','.');
        else if(value.matches("\\d+,\\d{1,2}")) value=value.replace(',','.');
        if(!value.matches("\\d{1,12}(?:\\.\\d{1,2})?")) return "";
        try { return new BigDecimal(value).stripTrailingZeros().toPlainString(); } catch(NumberFormatException e) { return ""; }
    }
}
