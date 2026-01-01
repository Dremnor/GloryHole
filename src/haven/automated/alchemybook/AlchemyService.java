package haven.automated.alchemybook;

import haven.*;
import org.json.JSONArray;

import java.io.OutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Field;

public class AlchemyService {
    private static final Map<String, ParsedAlchemyInfo> cachedItems = new ConcurrentHashMap<>();
    private static final Queue<HashedAlchemyInfo> sendQueue = new ConcurrentLinkedQueue<>();
    public static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);

    private static final boolean alchemyDebug = false;

    static {
        scheduler.scheduleAtFixedRate(AlchemyService::sendItems, 10L, 10, TimeUnit.SECONDS);
    }

    // Główna metoda wywoływana z GItem
    public static void checkAlchemying(List<ItemInfo> ii, Resource res) {
        Defer.later(() -> {
            try {
                if (isProcessedIngredient(ii)) {
                    handleProcessedIngredient(ii, res);
                } else if (isAlchemyIngredient(ii)) {
                    handleIngredient(ii, res);
                } else if (isPotion(ii)) {
                    handlePotion(ii, res);
                }
            } catch (Exception e) {
                if (alchemyDebug) {
                    System.out.println("Cannot create alchemy info: " + e.getMessage());
                    e.printStackTrace();
                }
            }
            return null;
        });
    }

    private static boolean isProcessedIngredient(List<ItemInfo> ii) {
        boolean hasRecipe = false;

        for (ItemInfo inf : ii) {
            String className = inf.getClass().getName();
            if (className.contains("alch.recipe.Recipe")) {
                hasRecipe = true;
                break;
            }
        }

        // Processed ingredient = musi mieć recepturę
        // Efekty są opcjonalne (może ich nie znać)
        return hasRecipe;
    }

    private static boolean isAlchemyIngredient(List<ItemInfo> ii) {
        boolean hasRecipe = false;
        boolean hasAlchemyEffect = false;

        for (ItemInfo inf : ii) {
            String className = inf.getClass().getName();
            if (className.contains("alch.recipe.Recipe")) {
                hasRecipe = true;
            }
            if (className.contains("alch.ingr_buff.BuffAttr") ||
                    className.contains("alch.ingr_heal.HealWound") ||
                    className.contains("alch.ingr_time_less.LessTime")) {
                hasAlchemyEffect = true;
            }
        }

        // Podstawowy składnik = ma efekty ALE nie ma receptury
        return hasAlchemyEffect && !hasRecipe;
    }

    private static boolean isPotion(List<ItemInfo> ii) {
        for (ItemInfo inf : ii) {
            if (inf instanceof ItemInfo.Contents) {
                ItemInfo.Contents contents = (ItemInfo.Contents) inf;
                for (ItemInfo subInfo : contents.sub) {
                    if (subInfo.getClass().getSimpleName().equals("Elixir")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static void handleIngredient(List<ItemInfo> ii, Resource res) {
        try {
            ParsedIngredientInfo info = extractIngredientEffects(ii);
            info.itemName = getItemName(ii);

            checkAndSend(info);

        } catch (Exception e) {
            if (alchemyDebug) {
                e.printStackTrace();
            }
        }
    }

    private static void handleProcessedIngredient(List<ItemInfo> ii, Resource res) {
        try {
            ParsedProcessedIngredientInfo info = new ParsedProcessedIngredientInfo();
            info.itemName = getItemName(ii);

            // Extract recipe (WYMAGANE)
            for (ItemInfo inf : ii) {
                if (inf.getClass().getName().contains("alch.recipe.Recipe")) {
                    info.madeFrom = extractRecipeIngredients(inf);
                    break;
                }
            }

            // REJECT jeśli brak receptury
            if (info.madeFrom.isEmpty()) {
                if (alchemyDebug) {
                    System.out.println("[Alchemy] Rejected processed ingredient without recipe: " + info.itemName);
                }
                return;
            }

            // Extract effects (OPCJONALNE - może być puste)
            ParsedIngredientInfo effects = extractIngredientEffects(ii);
            info.buffs = effects.buffs;
            info.heals = effects.heals;
            info.lessTime = effects.lessTime;

            checkAndSend(info);

        } catch (Exception e) {
            if (alchemyDebug) {
                e.printStackTrace();
            }
        }
    }

    private static void handlePotion(List<ItemInfo> ii, Resource res) {
        try {
            ParsedPotionInfo info = new ParsedPotionInfo();

            for (ItemInfo inf : ii) {
                if (inf instanceof ItemInfo.Contents) {
                    ItemInfo.Contents contents = (ItemInfo.Contents) inf;

                    // Extract recipe ingredients (with expanded processed ingredients)
                    for (ItemInfo subInfo : contents.sub) {
                        if (subInfo.getClass().getSimpleName().equals("Recipe")) {
                            info.madeFrom = extractRecipeIngredients(subInfo);
                        }
                    }

                    // Extract elixir effects - tylko nazwy
                    for (ItemInfo subInfo : contents.sub) {
                        if (subInfo.getClass().getSimpleName().equals("Elixir")) {
                            extractPotionEffectNames(subInfo, info);
                            break;
                        }
                    }
                }
            }

            checkAndSend(info);

        } catch (Exception e) {
            if (alchemyDebug) {
                e.printStackTrace();
            }
        }
    }

    private static String getItemName(List<ItemInfo> ii) {
        for (ItemInfo inf : ii) {
            if (inf instanceof ItemInfo.Name) {
                return ((ItemInfo.Name) inf).str.text;
            }
        }
        return "Unknown";
    }

    // Wyciąga efekty ze składnika (buffs, heals, lessTime)
    private static ParsedIngredientInfo extractIngredientEffects(List<ItemInfo> ii) {
        ParsedIngredientInfo info = new ParsedIngredientInfo();

        for (ItemInfo inf : ii) {
            String className = inf.getClass().getName();

            // BuffAttr - buff do atrybutu
            if (className.contains("BuffAttr")) {
                try {
                    Field resField = findField(inf.getClass(), "res");
                    resField.setAccessible(true);
                    Indir<?> indir = (Indir<?>) resField.get(inf);
                    Resource buffRes = (Resource) indir.get();

                    Resource.Tooltip tooltip = buffRes.layer(Resource.tooltip);
                    if (tooltip != null && !info.buffs.contains(tooltip.t)) {
                        info.buffs.add(tooltip.t);
                    }
                } catch (Exception e) {
                    if (alchemyDebug) e.printStackTrace();
                }
            }
            // HealWound - efekt leczący
            else if (className.contains("ingr_heal.HealWound")) {
                try {
                    Field resField = findField(inf.getClass(), "res");
                    resField.setAccessible(true);
                    Indir<?> indir = (Indir<?>) resField.get(inf);
                    Resource woundRes = (Resource) indir.get();

                    String woundName = woundRes.layer(Resource.tooltip) != null ?
                            woundRes.layer(Resource.tooltip).t :
                            woundRes.name;

                    if (!info.heals.contains(woundName)) {
                        info.heals.add(woundName);
                    }
                } catch (Exception e) {
                    if (alchemyDebug) e.printStackTrace();
                }
            }
            // LessTime - zmniejszenie czasu trwania eliksiru
            else if (className.contains("ingr_time_less.LessTime")) {
                String effectName = "Decreased elixir duration";
                if (!info.lessTime.contains(effectName)) {
                    info.lessTime.add(effectName);
                }
            }
        }

        return info;
    }

    // Wyciąga tylko nazwy efektów i ran (bez wartości)
    private static void extractPotionEffectNames(Object elixir, ParsedPotionInfo info) throws Exception {
        Field effsField = elixir.getClass().getDeclaredField("effs");
        effsField.setAccessible(true);
        List<?> effs = (List<?>) effsField.get(elixir);

        for (Object eff : effs) {
            String effClassName = eff.getClass().getSimpleName();

            // AttrMod - buff do atrybutu (tylko nazwa)
            if (effClassName.equals("AttrMod")) {
                Field tabField = eff.getClass().getDeclaredField("tab");
                tabField.setAccessible(true);
                List<?> tab = (List<?>) tabField.get(eff);

                for (Object mod : tab) {
                    Class<?> entryClass = mod.getClass().getSuperclass();
                    Field attrField = entryClass.getDeclaredField("attr");
                    attrField.setAccessible(true);
                    Object attribute = attrField.get(mod);

                    String attrName = getAttributeName(attribute);
                    if (!info.buffNames.contains(attrName)) {
                        info.buffNames.add(attrName);
                    }
                }
            }
            // HealWound - leczenie rany (tylko nazwa)
            else if (effClassName.equals("HealWound")) {
                Field resField = eff.getClass().getDeclaredField("res");
                resField.setAccessible(true);
                Indir<?> resIndir = (Indir<?>) resField.get(eff);
                Resource woundRes = (Resource) resIndir.get();

                String woundName = woundRes.layer(Resource.tooltip) != null ?
                        woundRes.layer(Resource.tooltip).t :
                        woundRes.name;

                if (!info.healNames.contains(woundName)) {
                    info.healNames.add(woundName);
                }
            }
            // AddWound - wound/debuff (tylko nazwa)
            else if (effClassName.equals("AddWound")) {
                Field resField = eff.getClass().getDeclaredField("res");
                resField.setAccessible(true);
                Indir<?> resIndir = (Indir<?>) resField.get(eff);
                Resource woundRes = (Resource) resIndir.get();

                String woundName = woundRes.layer(Resource.tooltip) != null ?
                        woundRes.layer(Resource.tooltip).t :
                        woundRes.name;

                if (!info.woundNames.contains(woundName)) {
                    info.woundNames.add(woundName);
                }
            }
        }
    }

    // Wyciąga recepturę z rozwinięciem processed ingredients
    private static List<String> extractRecipeIngredients(Object recipe) {
        List<String> ingredients = new ArrayList<>();

        try {
            Field inputsField = recipe.getClass().getDeclaredField("inputs");
            inputsField.setAccessible(true);
            List<?> inputs = (List<?>) inputsField.get(recipe);

            for (Object input : inputs) {
                String expandedName = expandRecipeSpec(input);
                ingredients.add(expandedName);
            }

        } catch (Exception e) {
            if (alchemyDebug) e.printStackTrace();
        }

        return ingredients;
    }

    // Rekurencyjna metoda do rozwijania Recipe$Spec - zwraca string z rozwiniętą nazwą
    private static String expandRecipeSpec(Object spec) {
        try {
            // Pobierz nazwę tego składnika
            Field resField = spec.getClass().getDeclaredField("res");
            resField.setAccessible(true);
            Object resData = resField.get(spec);

            Field resResField = resData.getClass().getDeclaredField("res");
            resResField.setAccessible(true);
            Object resIndir = resResField.get(resData);

            String itemName = "Unknown";
            if (resIndir instanceof Indir) {
                Resource res = (Resource) ((Indir<?>) resIndir).get();
                Resource.Tooltip tooltip = res.layer(Resource.tooltip);
                itemName = tooltip != null ? tooltip.t : res.name;
            }

            // Sprawdź czy ma sub-składniki
            Field inputsField = spec.getClass().getDeclaredField("inputs");
            inputsField.setAccessible(true);
            List<?> subInputs = (List<?>) inputsField.get(spec);

            if (subInputs.isEmpty()) {
                // Basic ingredient - zwróć samą nazwę
                return itemName;
            } else {
                // Processed ingredient - rozwiń rekurencyjnie
                List<String> subNames = new ArrayList<>();
                for (Object subSpec : subInputs) {
                    subNames.add(expandRecipeSpec(subSpec));
                }
                // Zwróć nazwę z listą składników
                return itemName + " (" + String.join(", ", subNames) + ")";
            }

        } catch (Exception e) {
            if (alchemyDebug) e.printStackTrace();
            return "Unknown";
        }
    }

    private static String getAttributeName(Object attribute) {
        try {
            Class<?> resattrClass = attribute.getClass().getSuperclass();
            Field resField = resattrClass.getDeclaredField("res");
            resField.setAccessible(true);
            Object resValue = resField.get(attribute);

            if (resValue instanceof Resource) {
                Resource res = (Resource) resValue;
                Resource.Tooltip tooltip = res.layer(Resource.tooltip);
                return tooltip != null ? tooltip.t : res.name;
            }
            else if (resValue instanceof Indir) {
                Resource res = (Resource) ((Indir<?>) resValue).get();
                Resource.Tooltip tooltip = res.layer(Resource.tooltip);
                return tooltip != null ? tooltip.t : res.name;
            }

            return "Unknown";

        } catch (Exception e) {
            if (alchemyDebug) e.printStackTrace();
            return "Unknown";
        }
    }

    private static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    // Hash i wysyłka
    private static void checkAndSend(ParsedAlchemyInfo info) {
        String hash = generateHash(info);
        if (hash == null) return;

        // Dla składników: ZAWSZE wysyłaj (endpoint zmerguje efekty)
        if (info instanceof ParsedIngredientInfo) {
            sendQueue.add(new HashedAlchemyInfo(hash, info));
            return;
        }

        // Dla processed ingredients: ZAWSZE wysyłaj (endpoint zmerguje efekty)
        if (info instanceof ParsedProcessedIngredientInfo) {
            sendQueue.add(new HashedAlchemyInfo(hash, info));
            return;
        }

        // Dla potionów: sprawdź cache (nie duplikuj identycznych)
        if (cachedItems.containsKey(hash)) {
            return;
        }

        cachedItems.put(hash, info);
        sendQueue.add(new HashedAlchemyInfo(hash, info));
    }

    public static boolean isValidEndpoint() {
        String raw = OptWnd.alchemyBookEndpointTextEntry.buf.line();
        if (raw == null) return false;
        raw = raw.trim();
        return raw.length() >= 5;
    }

    private static void sendItems() {
        if (sendQueue.isEmpty()) {
            return;
        }

        final String endpoint = OptWnd.alchemyBookEndpointTextEntry.buf.line();
        if (endpoint == null || !isValidEndpoint()) return;
        final java.net.URI apiBase = java.net.URI.create(endpoint.trim());

        List<ParsedAlchemyInfo> toSend = new ArrayList<>();
        while (!sendQueue.isEmpty()) {
            HashedAlchemyInfo info = sendQueue.poll();
            toSend.add(info.alchemyInfo);
        }

        if (!toSend.isEmpty()) {
            HttpURLConnection connection = null;
            try {
                String jsonPayload = new JSONArray(toSend.toArray()).toString();

                connection = (HttpURLConnection) apiBase.toURL().openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setRequestProperty("User-Agent", "H&H Client");
                connection.setDoOutput(true);

                String token = OptWnd.alchemyBookTokenTextEntry.buf.line();
                if (token != null && !(token = token.trim()).isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + token);
                }

                try (OutputStream out = connection.getOutputStream()) {
                    out.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
                }

                int code = connection.getResponseCode();

                if (alchemyDebug) {
                    System.out.println("[Alchemy] Response code: " + code);

                    try {
                        InputStream is = (code == 200) ? connection.getInputStream() : connection.getErrorStream();
                        if (is != null) {
                            byte[] bytes = new byte[is.available()];
                            is.read(bytes);
                            String responseBody = new String(bytes, StandardCharsets.UTF_8);
                            System.out.println("[Alchemy] Response: " + responseBody);
                            is.close();
                        }
                    } catch (Exception e) {
                        System.out.println("[Alchemy] Could not read response: " + e.getMessage());
                    }
                }

                if (code != 200) {
                    if (alchemyDebug) {
                        String responseMessage = connection.getResponseMessage();
                        System.out.println("[Alchemy] Failed to send alchemy items");
                        System.out.println("  URL: " + apiBase);
                        System.out.println("  HTTP " + code + " " + responseMessage);
                        System.out.println("  Items: " + toSend.size());
                    }
                }
            } catch (Exception ex) {
                if (alchemyDebug) {
                    System.out.println("[Alchemy] Exception while sending " + toSend.size() + " alchemy items to " + apiBase + ": " + ex);
                    ex.printStackTrace(System.out);
                }
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
    }

    private static String generateHash(ParsedAlchemyInfo info) {
        try {
            StringBuilder sb = new StringBuilder();

            if (info instanceof ParsedIngredientInfo) {
                ParsedIngredientInfo ing = (ParsedIngredientInfo) info;
                sb.append("ingredient;").append(ing.itemName);
            } else if (info instanceof ParsedProcessedIngredientInfo) {
                ParsedProcessedIngredientInfo proc = (ParsedProcessedIngredientInfo) info;
                // Hash = nazwa + receptura (unikalny klucz)
                sb.append("processed;").append(proc.itemName).append(";");
                proc.madeFrom.forEach(m -> sb.append(m).append(";"));
            } else if (info instanceof ParsedPotionInfo) {
                ParsedPotionInfo pot = (ParsedPotionInfo) info;
                sb.append("potion;");
                pot.buffNames.forEach(b -> sb.append("buff:").append(b).append(";"));
                pot.healNames.forEach(h -> sb.append("heal:").append(h).append(";"));
                pot.woundNames.forEach(w -> sb.append("wound:").append(w).append(";"));
                pot.madeFrom.forEach(m -> sb.append(m).append(";"));
            }

            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] hash = digest.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return getHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            if (alchemyDebug) {
                System.out.println("Cannot generate alchemy hash");
            }
        }
        return null;
    }

    private static String getHex(byte[] bytes) {
        BigInteger bigInteger = new BigInteger(1, bytes);
        return bigInteger.toString(16);
    }

    // Data classes
    private static class HashedAlchemyInfo {
        public String hash;
        public ParsedAlchemyInfo alchemyInfo;

        public HashedAlchemyInfo(String hash, ParsedAlchemyInfo alchemyInfo) {
            this.hash = hash;
            this.alchemyInfo = alchemyInfo;
        }
    }

    public static abstract class ParsedAlchemyInfo {
        public String type; // "ingredient", "processed_ingredient", "potion"

        public String getType() { return type; }
    }

    public static class ParsedIngredientInfo extends ParsedAlchemyInfo {
        public String itemName;
        public List<String> buffs = new ArrayList<>();      // Buff attributes (Smithing, Unarmed)
        public List<String> heals = new ArrayList<>();      // Heal effects (Aching Joints)
        public List<String> lessTime = new ArrayList<>();   // Time reduction (Decreased elixir duration)

        public ParsedIngredientInfo() {
            this.type = "ingredient";
        }

        public String getItemName() { return itemName; }
        public List<String> getBuffs() { return buffs; }
        public List<String> getHeals() { return heals; }
        public List<String> getLessTime() { return lessTime; }
    }

    public static class ParsedProcessedIngredientInfo extends ParsedAlchemyInfo {
        public String itemName;
        public List<String> buffs = new ArrayList<>();      // Buff attributes
        public List<String> heals = new ArrayList<>();      // Heal effects
        public List<String> lessTime = new ArrayList<>();   // Time reduction
        public List<String> madeFrom = new ArrayList<>();   // Receptura

        public ParsedProcessedIngredientInfo() {
            this.type = "processed_ingredient";
        }

        public String getItemName() { return itemName; }
        public List<String> getBuffs() { return buffs; }
        public List<String> getHeals() { return heals; }
        public List<String> getLessTime() { return lessTime; }
        public List<String> getMadeFrom() { return madeFrom; }
    }

    public static class ParsedPotionInfo extends ParsedAlchemyInfo {
        public List<String> buffNames = new ArrayList<>();     // Buffs do atrybutów (Smithing, Unarmed)
        public List<String> healNames = new ArrayList<>();     // Rany które leczy (Aching Joints)
        public List<String> woundNames = new ArrayList<>();    // Rany które zadaje (Chills & Nausea)
        public List<String> madeFrom = new ArrayList<>();      // Receptura z rozwiniętymi processed ingredients

        public ParsedPotionInfo() {
            this.type = "potion";
        }

        public List<String> getBuffNames() { return buffNames; }
        public List<String> getHealNames() { return healNames; }
        public List<String> getWoundNames() { return woundNames; }
        public List<String> getMadeFrom() { return madeFrom; }
    }
}