package com.woodpeckerbros.watchreminder;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class IsraeliCityResolverTest {
    @Test
    public void localizedName_translatesKnownCitiesAndSuffixes() {
        assertEquals("Petah Tikva, Israel", IsraeliCityResolver.localizedName("פתח תקווה, ישראל", true));
        assertEquals("Tel Aviv", IsraeliCityResolver.localizedName("תל אביב", true));
        assertEquals("Kiryat Shmona (approx.)", IsraeliCityResolver.localizedName("קריית שמונה (בקירוב)", true));
    }

    @Test
    public void localizedName_preservesHebrewAndUnknownNames() {
        assertEquals("צפת", IsraeliCityResolver.localizedName("צפת", false));
        assertEquals("Custom place", IsraeliCityResolver.localizedName("Custom place", true));
    }
}
