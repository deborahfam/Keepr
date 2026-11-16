package com.keepr.app.data;

import java.text.Normalizer;
import java.util.*;
import java.util.regex.*;

/** Small, auditable gazetteer. All results are suggestions, never GPS-derived facts. */
public final class HavanaLocations {
    // Municipalities: UNDP Cuba Human Development Report, annex 23 (2021).
    private static final String[][] MUNICIPALITIES={
        {"Playa","playa"},{"Plaza de la Revolución","plaza de la revolucion"},
        {"Centro Habana","centro habana"},{"La Habana Vieja","(?:la )?habana vieja"},
        {"Regla","regla"},{"La Habana del Este","(?:la )?habana del este"},
        {"Guanabacoa","guanabacoa"},{"San Miguel del Padrón","san miguel del padron"},
        {"Diez de Octubre","(?:diez|10) de octubre"},{"Cerro","(?:el )?cerro"},
        {"Marianao","marianao"},{"La Lisa","la lisa"},{"Boyeros","boyeros"},
        {"Arroyo Naranjo","arroyo naranjo"},{"Cotorro","cotorro"}
    };
    private HavanaLocations() {}
    private static String normalize(String text) { return Normalizer.normalize(text,Normalizer.Form.NFD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT); }
    public static String extract(String input) {
        String text=normalize(input); if(text.length()>131072) text=text.substring(0,131072);
        boolean havana=Pattern.compile("\\b(?:la habana|havana)\\b").matcher(text).find();
        Set<String> municipalities=new LinkedHashSet<>(); boolean vibora=false;
        Matcher neighborhood=Pattern.compile("\\b(?:la )?vi[bv]ora\\b(?!\\s+park)").matcher(text);
        while(neighborhood.find()) {
            if(spatial(text,neighborhood.start()) || havana || text.trim().matches("(?:la )?vi[bv]ora")) vibora=true;
        }
        if(vibora) municipalities.add("Diez de Octubre");
        for(String[] municipality:MUNICIPALITIES) {
            Matcher match=Pattern.compile("\\b(?:"+municipality[1]+")\\b").matcher(text);
            while(match.find()) {
                String before=text.substring(Math.max(0,match.start()-65),match.start());
                String after=text.substring(match.end(),Math.min(text.length(),match.end()+30));
                boolean direct=spatial(text,match.start());
                boolean declared=before.matches("(?s).*(?:municipio(?: de)?|ubicacion|localidad)\\s*:?\\s*$");
                boolean ordinary=municipality[0].equals("Playa") || municipality[0].equals("Regla") || municipality[0].equals("Cerro");
                if(municipality[0].equals("Regla") && before.matches("(?s).*(?:papeles|documentos|todo)\\s+en\\s+$")) continue;
                if(municipality[0].equals("Diez de Octubre")) {
                    // A date does not become a municipality merely because the post mentions Havana.
                    if(before.matches("(?s).*(?:publicad[oa]|fecha|entrega|hasta|desde|el|para el)\\s*:?\\s*$") && !declared && !direct) continue;
                    if(after.matches("(?s)^\\s+(?:de\\s+)?(?:19|20)\\d{2}\\b.*") && !declared) continue;
                    if(!(direct || declared || vibora || text.trim().matches(municipality[1]))) continue;
                } else if(ordinary) {
                    if(!(declared || (havana && direct) || after.matches("(?s)^\\s*,\\s*(?:la )?habana\\b.*"))) continue;
                } else if(!(direct || havana || text.trim().matches(municipality[1]) || before.matches("(?s).*\\b(?:casa|apartamento|inmueble|vivienda)\\b.{0,40}"))) continue;
                municipalities.add(municipality[0]);
            }
        }
        if(municipalities.size()!=1) return "";
        return (vibora?"La Víbora, ":"")+municipalities.iterator().next()+", La Habana, Cuba";
    }
    private static boolean spatial(String text,int start) {
        String before=text.substring(Math.max(0,start-55),start);
        return before.matches("(?s).*(?:\\ben|\\bmunicipio(?: de)?|\\bbarrio(?: de)?|\\blocalidad(?: de)?|\\bubicacion|\\blocation)\\s*:?\\s*(?:el |la )?$");
    }
}
