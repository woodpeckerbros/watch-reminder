package com.woodpeckerbros.watchreminder.phone.entitlement;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Phone-side owner of the same Play entitlement as the Wear application. */
public final class PhoneEntitlementManager {
    public static final String PRODUCT_ID = "zmanio_lifetime";
    private static final long TRIAL_MS = 14L * 24L * 60L * 60L * 1000L;
    private static final String PREFS = "zmanio_entitlement";
    private static final String START = "trial_started_at";
    private static final String HIGHEST = "highest_wall_time";
    private static final String LIFETIME = "lifetime_purchased";
    private static PhoneEntitlementManager instance;

    public enum State { TRIAL_ACTIVE, TRIAL_EXPIRED, LIFETIME_PURCHASED, CHECKING, BILLING_UNAVAILABLE }
    public interface Listener { void changed(State state, Snapshot snapshot, String price, String message); }
    public interface Callback { void result(boolean success, String message); }

    public static synchronized PhoneEntitlementManager get(Context context) {
        if (instance == null) instance = new PhoneEntitlementManager(context.getApplicationContext());
        return instance;
    }

    public static final class Snapshot {
        public final long trialStartedAt;
        public final long remainingMillis;
        public final boolean lifetime;
        public final boolean accessGranted;
        Snapshot(long trialStartedAt, long remainingMillis, boolean lifetime, boolean accessGranted) {
            this.trialStartedAt = trialStartedAt;
            this.remainingMillis = remainingMillis;
            this.lifetime = lifetime;
            this.accessGranted = accessGranted;
        }
    }

    private final Context context;
    private final SharedPreferences prefs;
    private final Set<Listener> listeners = Collections.synchronizedSet(new LinkedHashSet<>());
    private BillingClient client;
    private ProductDetails product;
    private String price = "";
    private String message = "";
    private boolean connecting;
    private boolean billingAvailable;

    private PhoneEntitlementManager(Context context) {
        this.context = context;
        this.prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void start() { snapshot(); ensureClient(); onForeground(); }
    public void onForeground() {
        ensureClient();
        if (client.isReady()) { queryProduct(); queryPurchases(null); }
        else if (!connecting) connect();
        notifyListeners();
    }
    public void addListener(Listener listener) { listeners.add(listener); notifyListener(listener); }
    public void removeListener(Listener listener) { listeners.remove(listener); }
    public Snapshot snapshot() {
        long now = Math.max(0L, System.currentTimeMillis());
        long start = prefs.getLong(START, 0L);
        long highest = prefs.getLong(HIGHEST, 0L);
        long effectiveNow = Math.max(now, highest);
        if (start <= 0L || start > effectiveNow) start = effectiveNow;
        if (start != prefs.getLong(START, 0L) || effectiveNow != highest) {
            prefs.edit().putLong(START, start).putLong(HIGHEST, effectiveNow).commit();
        }
        boolean lifetime = prefs.getBoolean(LIFETIME, false);
        long remaining = lifetime ? Long.MAX_VALUE : Math.max(0L, TRIAL_MS - (effectiveNow - start));
        return new Snapshot(start, remaining, lifetime, lifetime || remaining > 0L);
    }
    public State state() {
        Snapshot snapshot = snapshot();
        if (snapshot.lifetime) return State.LIFETIME_PURCHASED;
        if (snapshot.accessGranted) return State.TRIAL_ACTIVE;
        return connecting ? State.CHECKING : billingAvailable ? State.TRIAL_EXPIRED : State.BILLING_UNAVAILABLE;
    }
    public String price() { return price; }
    public boolean hasAccess() { return snapshot().accessGranted; }

    /** Adopt a watch's first activation without ever moving it later. */
    public void importTrialMetadata(JSONObject metadata) {
        if (metadata == null) return;
        long imported = metadata.optLong("trialStartedAt", 0L);
        long now = Math.max(0L, System.currentTimeMillis());
        if (imported <= 0L || imported > now) return;
        long existing = prefs.getLong(START, 0L);
        prefs.edit().putLong(START, existing > 0L ? Math.min(existing, imported) : imported)
                .putLong(HIGHEST, Math.max(now, prefs.getLong(HIGHEST, 0L))).commit();
        notifyListeners();
    }

    public void purchase(Activity activity, Callback callback) {
        onForeground();
        if (!client.isReady() || product == null) { callback(callback, false, "Google Play is still loading"); return; }
        ProductDetails.OneTimePurchaseOfferDetails offer = product.getOneTimePurchaseOfferDetails();
        if (offer == null) { callback(callback, false, "Product is unavailable in Google Play"); return; }
        BillingFlowParams.ProductDetailsParams details = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(product).setOfferToken(offer.getOfferToken()).build();
        BillingResult result = client.launchBillingFlow(activity, BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(details)).build());
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) handleResult(result, callback);
    }

    public void restore(Callback callback) { onForeground(); if (!client.isReady()) callback(callback, false, "Google Play is unavailable"); else queryPurchases(callback); }

    private void ensureClient() {
        if (client != null) return;
        client = BillingClient.newBuilder(context)
                .setListener((result, purchases) -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) process(purchases, false, null);
                    else if (result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) queryPurchases(null);
                    else handleResult(result, null);
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection().build();
    }
    private void connect() {
        connecting = true; notifyListeners();
        client.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                connecting = false; billingAvailable = result.getResponseCode() == BillingClient.BillingResponseCode.OK;
                message = billingAvailable ? "" : result.getDebugMessage();
                if (billingAvailable) { queryProduct(); queryPurchases(null); }
                notifyListeners();
            }
            @Override public void onBillingServiceDisconnected() { connecting = false; billingAvailable = false; notifyListeners(); }
        });
    }
    private void queryProduct() {
        QueryProductDetailsParams.Product requested = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build();
        client.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder().setProductList(Collections.singletonList(requested)).build(),
                (result, detailsResult) -> {
                    if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        product = null; price = "";
                        for (ProductDetails details : detailsResult.getProductDetailsList()) if (PRODUCT_ID.equals(details.getProductId())) {
                            product = details;
                            ProductDetails.OneTimePurchaseOfferDetails offer = details.getOneTimePurchaseOfferDetails();
                            if (offer != null) price = offer.getFormattedPrice();
                        }
                    } else message = result.getDebugMessage();
                    notifyListeners();
                });
    }
    private void queryPurchases(Callback callback) {
        client.queryPurchasesAsync(QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
                (result, purchases) -> { if (result.getResponseCode() == BillingClient.BillingResponseCode.OK) process(purchases, true, callback); else { message = result.getDebugMessage(); callback(callback, false, "Could not verify purchases"); notifyListeners(); } });
    }
    private void process(List<Purchase> purchases, boolean authoritative, Callback callback) {
        boolean owned = false;
        for (Purchase purchase : purchases) if (purchase.getProducts().contains(PRODUCT_ID)
                && purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) { owned = true; acknowledge(purchase); }
        if (owned || authoritative) prefs.edit().putBoolean(LIFETIME, owned).commit();
        message = ""; notifyListeners();
        if (callback != null) callback(callback, owned, owned ? "Purchase restored" : "No lifetime purchase was found");
    }
    private void acknowledge(Purchase purchase) {
        if (!purchase.isAcknowledged()) client.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.getPurchaseToken()).build(), result -> { if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) { message = result.getDebugMessage(); notifyListeners(); } });
    }
    private void handleResult(BillingResult result, Callback callback) {
        if (result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) { queryPurchases(callback); return; }
        message = result.getResponseCode() == BillingClient.BillingResponseCode.USER_CANCELED ? "Purchase cancelled" : result.getDebugMessage();
        callback(callback, false, message); notifyListeners();
    }
    private void notifyListeners() { List<Listener> copy; synchronized (listeners) { copy = new ArrayList<>(listeners); } for (Listener listener : copy) notifyListener(listener); }
    private void notifyListener(Listener listener) { listener.changed(state(), snapshot(), price, message); }
    private static void callback(Callback callback, boolean success, String message) { if (callback != null) callback.result(success, message); }
}
