package com.woodpeckerbros.watchreminder;

public final class IsraeliCityResolver {
    private static final double MAX_APPROXIMATE_DISTANCE_KM = 45.0;

    private static final City[] CITIES = {
            new City("ירושלים", "Jerusalem", 31.7683, 35.2137),
            new City("תל אביב", "Tel Aviv", 32.0853, 34.7818),
            new City("רמת גן", "Ramat Gan", 32.0684, 34.8248),
            new City("גבעתיים", "Givatayim", 32.0719, 34.8106),
            new City("בני ברק", "Bnei Brak", 32.0833, 34.8333),
            new City("פתח תקווה", "Petah Tikva", 32.0840, 34.8878),
            new City("חולון", "Holon", 32.0158, 34.7874),
            new City("בת ים", "Bat Yam", 32.0238, 34.7519),
            new City("ראשון לציון", "Rishon LeZion", 31.9730, 34.7925),
            new City("רחובות", "Rehovot", 31.8928, 34.8113),
            new City("יבנה", "Yavne", 31.8770, 34.7384),
            new City("גן יבנה", "Gan Yavne", 31.7874, 34.7066),
            new City("אשדוד", "Ashdod", 31.8014, 34.6435),
            new City("אשקלון", "Ashkelon", 31.6688, 34.5743),
            new City("קריית גת", "Kiryat Gat", 31.6100, 34.7642),
            new City("שדרות", "Sderot", 31.5250, 34.5969),
            new City("נתיבות", "Netivot", 31.4230, 34.5890),
            new City("אופקים", "Ofakim", 31.3141, 34.6203),
            new City("באר שבע", "Beersheba", 31.2530, 34.7915),
            new City("דימונה", "Dimona", 31.0700, 35.0336),
            new City("ירוחם", "Yeruham", 30.9872, 34.9314),
            new City("ערד", "Arad", 31.2589, 35.2128),
            new City("מצפה רמון", "Mitzpe Ramon", 30.6102, 34.8019),
            new City("אילת", "Eilat", 29.5577, 34.9519),
            new City("בית שמש", "Beit Shemesh", 31.7457, 34.9861),
            new City("מודיעין", "Modi'in", 31.8969, 35.0063),
            new City("לוד", "Lod", 31.9510, 34.8953),
            new City("רמלה", "Ramla", 31.9292, 34.8656),
            new City("אלעד", "El'ad", 32.0523, 34.9512),
            new City("מעלה אדומים", "Ma'ale Adumim", 31.7780, 35.2987),
            new City("אריאל", "Ariel", 32.1065, 35.1845),
            new City("הרצליה", "Herzliya", 32.1663, 34.8433),
            new City("רעננה", "Ra'anana", 32.1848, 34.8713),
            new City("כפר סבא", "Kfar Saba", 32.1782, 34.9076),
            new City("נתניה", "Netanya", 32.3215, 34.8532),
            new City("חדרה", "Hadera", 32.4340, 34.9196),
            new City("זכרון יעקב", "Zikhron Ya'akov", 32.5712, 34.9514),
            new City("חיפה", "Haifa", 32.7940, 34.9896),
            new City("קריית אתא", "Kiryat Ata", 32.8056, 35.1064),
            new City("עכו", "Acre", 32.9278, 35.0818),
            new City("נהריה", "Nahariya", 33.0059, 35.0941),
            new City("כרמיאל", "Karmiel", 32.9199, 35.2901),
            new City("נצרת", "Nazareth", 32.6996, 35.3035),
            new City("מגדל העמק", "Migdal HaEmek", 32.6751, 35.2393),
            new City("עפולה", "Afula", 32.6091, 35.2892),
            new City("בית שאן", "Beit She'an", 32.4971, 35.4960),
            new City("טבריה", "Tiberias", 32.7940, 35.5312),
            new City("צפת", "Safed", 32.9646, 35.4960),
            new City("כפר שמאי", "Kfar Shamai", 32.9569, 35.4571),
            new City("מירון", "Meron", 32.9870, 35.4403),
            new City("אור הגנוז", "Or HaGanuz", 33.0058, 35.4466),
            new City("בר יוחאי", "Bar Yochai", 32.9971, 35.4487),
            new City("קריית שמונה", "Kiryat Shmona", 33.2073, 35.5708),
            new City("קצרין", "Katzrin", 32.9920, 35.6910)
    };

    private IsraeliCityResolver() {
    }

    public static String nearestName(double latitude, double longitude) {
        City nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (City city : CITIES) {
            double distance = distanceKm(latitude, longitude, city.latitude, city.longitude);
            if (distance < nearestDistance) {
                nearest = city;
                nearestDistance = distance;
            }
        }
        return nearest != null && nearestDistance <= MAX_APPROXIMATE_DISTANCE_KM
                ? nearest.name + " (בקירוב)"
                : null;
    }

    public static String localizedName(String name, boolean english) {
        if (!english || name == null) {
            return name;
        }
        String normalized = name.trim();
        boolean approximate = normalized.endsWith(" (בקירוב)");
        if (approximate) {
            normalized = normalized.substring(0, normalized.length() - " (בקירוב)".length());
        }
        boolean israel = normalized.endsWith(", ישראל");
        if (israel) {
            normalized = normalized.substring(0, normalized.length() - ", ישראל".length());
        }
        for (City city : CITIES) {
            if (city.name.equals(normalized)) {
                return city.englishName + (israel ? ", Israel" : "") + (approximate ? " (approx.)" : "");
            }
        }
        return name;
    }

    private static double distanceKm(double latitude1, double longitude1, double latitude2, double longitude2) {
        double latitudeDelta = Math.toRadians(latitude2 - latitude1);
        double longitudeDelta = Math.toRadians(longitude2 - longitude1);
        double sinLatitude = Math.sin(latitudeDelta / 2.0);
        double sinLongitude = Math.sin(longitudeDelta / 2.0);
        double a = sinLatitude * sinLatitude
                + Math.cos(Math.toRadians(latitude1))
                * Math.cos(Math.toRadians(latitude2))
                * sinLongitude * sinLongitude;
        return 6371.0088 * 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
    }

    private static final class City {
        final String name;
        final String englishName;
        final double latitude;
        final double longitude;

        City(String name, String englishName, double latitude, double longitude) {
            this.name = name;
            this.englishName = englishName;
            this.latitude = latitude;
            this.longitude = longitude;
        }
    }
}
