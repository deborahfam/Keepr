package com.keepr.app.data;
import org.junit.Test;
import static org.junit.Assert.*;
public class HavanaLocationsTest {
    @Test public void recognizesUsersExactExampleAndSpellingVariant() {
        for(String name:new String[]{"La Vivora","La Víbora","la vibora","LA VIVORA"}) assertEquals("La Víbora, Diez de Octubre, La Habana, Cuba",ListingFacts.extract("Casa de tres plantas en "+name+", 10 de octubre").location());
    }
    @Test public void recognizesMunicipalityWithLocationContext() { assertEquals("Diez de Octubre, La Habana, Cuba",HavanaLocations.extract("Casa en 10 de octubre")); }
    @Test public void datesAreNotMunicipalities() {
        for(String text:new String[]{"Publicado el 10 de octubre de 2026 en La Habana","Entrega el 10 de octubre","Disponible hasta el diez de octubre","La Habana: oferta del 10 de octubre de 2026"}) assertEquals(text,"",HavanaLocations.extract(text));
    }
    @Test public void coversAllFifteenMunicipalities() {
        String[] names={"Playa","Plaza de la Revolución","Centro Habana","La Habana Vieja","Regla","La Habana del Este","Guanabacoa","San Miguel del Padrón","Diez de Octubre","Cerro","Marianao","La Lisa","Boyeros","Arroyo Naranjo","Cotorro"};
        for(String name:names) assertEquals(name,name+", La Habana, Cuba",HavanaLocations.extract("Municipio: "+name));
    }
    @Test public void avoidsGenericWordsAndViboraPark() {
        for(String text:new String[]{"Casa cerca de la playa","Documentos en regla en La Habana","Subiendo un cerro","Casa en Víbora Park","Vendo una víbora"}) assertEquals(text,"",HavanaLocations.extract(text));
    }
    @Test public void conflictingLocationsRemainUnresolved() { assertEquals("",HavanaLocations.extract("Casa en La Víbora y otra casa en Marianao")); }
    @Test public void explicitLocationKeepsPriority() { assertEquals("San Juan",ListingFacts.extract("Casa inspirada en La Víbora","Location: San Juan").location()); }
    @Test public void accentsAndWordBoundariesAreHandled() {
        assertEquals("San Miguel del Padrón, La Habana, Cuba",HavanaLocations.extract("Casa en SAN MIGUEL DEL PADRON"));
        assertEquals("",HavanaLocations.extract("Casa en MarianaoXYZ"));
    }
}
