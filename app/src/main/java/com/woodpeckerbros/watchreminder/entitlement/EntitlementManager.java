package com.woodpeckerbros.watchreminder.entitlement;

import android.app.Activity;
import android.content.Context;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** One owner for Google Play ownership, trial state and entitlement notifications. */
public final class EntitlementManager {
    public static final String LIFETIME_PRODUCT_ID = "zmanio_lifetime";
    private static EntitlementManager instance;

    public interface Listener {
        void onEntitlementChanged(EntitlementStatus status, TrialPolicy.Snapshot trial,
                                  String localizedPrice, String billingMessage);
    }

    public interface ActionCallback {
        void onResult(boolean success, String message);
    }

    public static synchronized EntitlementManager get(Context context) {
        if (instance == null) instance = new EntitlementManager(context.getApplicationContext());
        return instance;
    }

    private final Context context;
    private final EntitlementStore store;
    private final Set<Listener> listeners = Collections.synchronizedSet(new LinkedHashSet<>());
    private BillingClient billingClient;
    private ProductDetails lifetimeProduct;
    private String localizedPrice = "";
    private boolean connecting;
    private boolean billingAvailable;
    private String billingMessage = "";

    private EntitlementManager(Context context) {
        this.context = context;
        this.store = new EntitlementStore(context);
    }

    public void start() {
        if (!store.snapshot().featureAccessGranted) EntitlementEnforcer.apply(context);
        ensureClient();
        onForeground();
    }

    /** Call from Activity.onResume: refreshes ownership even when the callback was missed. */
    public void onForeground() {
        ensureClient();
        if (billingClient.isReady()) {
            queryProductDetails();
            queryOwnedPurchases(null);
        } else if (!connecting) {
            connect();
        }
        notifyListeners();
    }

    public void addListener(Listener listener) {
        if (listener == null) return;
        listeners.add(listener);
        listener.onEntitlementChanged(status(), store.snapshot(), localizedPrice, billingMessage);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public TrialPolicy.Snapshot trial() {
        return store.snapshot();
    }

    public EntitlementStatus status() {
        TrialPolicy.Snapshot trial = store.snapshot();
        if (trial.lifetimePurchased) return EntitlementStatus.LIFETIME_PURCHASED;
        if (trial.featureAccessGranted) return EntitlementStatus.TRIAL_ACTIVE;
        if (connecting) return EntitlementStatus.CHECKING;
        return billingAvailable ? EntitlementStatus.TRIAL_EXPIRED : EntitlementStatus.BILLING_UNAVAILABLE;
    }

    public String localizedPrice() {
        return localizedPrice;
    }

    public void launchLifetimePurchase(Activity activity, ActionCallback callback) {
        if (activity == null) {
            result(callback, false, "לא ניתן לפתוח רכישה כרגע");
            return;
        }
        onForeground();
        if (lifetimeProduct == null || !billingClient.isReady()) {
            result(callback, false, "המחיר עדיין נטען מ-Google Play");
            return;
        }
        ProductDetails.OneTimePurchaseOfferDetails offer = lifetimeProduct.getOneTimePurchaseOfferDetails();
        if (offer == null) {
            result(callback, false, "המוצר אינו זמין כרגע ב-Google Play");
            return;
        }
        BillingFlowParams.ProductDetailsParams product = BillingFlowParams.ProductDetailsParams.newBuilder()
                .setProductDetails(lifetimeProduct)
                .setOfferToken(offer.getOfferToken())
                .build();
        BillingResult result = billingClient.launchBillingFlow(activity,
                BillingFlowParams.newBuilder().setProductDetailsParamsList(
                        Collections.singletonList(product)).build());
        if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
            handleBillingResult(result, callback);
        }
    }

    public void restorePurchases(ActionCallback callback) {
        onForeground();
        if (!billingClient.isReady()) {
            result(callback, false, "Google Play אינו זמין כרגע");
            return;
        }
        queryOwnedPurchases(callback);
    }

    private void ensureClient() {
        if (billingClient != null) return;
        billingClient = BillingClient.newBuilder(context)
                .setListener(new PurchasesUpdatedListener() {
                    @Override public void onPurchasesUpdated(BillingResult result, List<Purchase> purchases) {
                        if (result.getResponseCode() == BillingClient.BillingResponseCode.OK && purchases != null) {
                            processPurchases(purchases, false, null);
                        } else if (result.getResponseCode() == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
                            queryOwnedPurchases(null);
                        } else {
                            handleBillingResult(result, null);
                        }
                    }
                })
                .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                .enableAutoServiceReconnection()
                .build();
    }

    private void connect() {
        connecting = true;
        notifyListeners();
        billingClient.startConnection(new BillingClientStateListener() {
            @Override public void onBillingSetupFinished(BillingResult result) {
                connecting = false;
                billingAvailable = result.getResponseCode() == BillingClient.BillingResponseCode.OK;
                if (billingAvailable) {
                    billingMessage = "";
                    queryProductDetails();
                    queryOwnedPurchases(null);
                } else {
                    billingMessage = result.getDebugMessage();
                }
                notifyListeners();
            }

            @Override public void onBillingServiceDisconnected() {
                // Auto reconnection is enabled; preserve last verified entitlement until Play responds.
                connecting = false;
                billingAvailable = false;
                billingMessage = "Google Play disconnected";
                notifyListeners();
            }
        });
    }

    private void queryProductDetails() {
        if (!billingClient.isReady()) return;
        QueryProductDetailsParams.Product product = QueryProductDetailsParams.Product.newBuilder()
                .setProductId(LIFETIME_PRODUCT_ID)
                .setProductType(BillingClient.ProductType.INAPP)
                .build();
        billingClient.queryProductDetailsAsync(QueryProductDetailsParams.newBuilder()
                        .setProductList(Collections.singletonList(product)).build(),
                (result, detailsResult) -> {
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                        billingMessage = result.getDebugMessage();
                        notifyListeners();
                        return;
                    }
                    lifetimeProduct = null;
                    localizedPrice = "";
                    for (ProductDetails details : detailsResult.getProductDetailsList()) {
                        if (LIFETIME_PRODUCT_ID.equals(details.getProductId())) {
                            lifetimeProduct = details;
                            ProductDetails.OneTimePurchaseOfferDetails offer = details.getOneTimePurchaseOfferDetails();
                            if (offer != null) localizedPrice = offer.getFormattedPrice();
                            break;
                        }
                    }
                    notifyListeners();
                });
    }

    private void queryOwnedPurchases(ActionCallback callback) {
        if (!billingClient.isReady()) return;
        billingClient.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                        .setProductType(BillingClient.ProductType.INAPP).build(),
                (result, purchases) -> {
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                        billingMessage = result.getDebugMessage();
                        notifyListeners();
                        result(callback, false, "לא הצלחתי לאמת רכישות מול Google Play");
                        return;
                    }
                    processPurchases(purchases, true, callback);
                });
    }

    private void processPurchases(List<Purchase> purchases, boolean authoritativeQuery,
                                  ActionCallback callback) {
        boolean purchased = false;
        if (purchases != null) {
            for (Purchase purchase : purchases) {
                if (!purchase.getProducts().contains(LIFETIME_PRODUCT_ID)) continue;
                if (PurchaseEntitlementPolicy.grantsLifetime(LIFETIME_PRODUCT_ID, purchase.getPurchaseState())) {
                    purchased = true;
                    acknowledgeIfNeeded(purchase);
                }
                // PENDING intentionally does not grant anything.
            }
        }
        boolean wasGranted = store.snapshot().featureAccessGranted;
        if (purchased || authoritativeQuery) store.setLifetimePurchased(
                PurchaseEntitlementPolicy.lifetimeAfterPurchaseQuery(
                        store.snapshot().lifetimePurchased, authoritativeQuery, purchased));
        boolean nowGranted = store.snapshot().featureAccessGranted;
        if (PurchaseEntitlementPolicy.shouldResumeReminderDelivery(wasGranted, nowGranted)
                || (wasGranted && !nowGranted) || purchased) EntitlementEnforcer.apply(context);
        billingMessage = "";
        notifyListeners();
        if (callback != null) {
            result(callback, purchased,
                    purchased ? "הרכישה שוחזרה והגישה נפתחה" : "לא נמצאה רכישת Lifetime בחשבון Google Play זה");
        }
    }

    private void acknowledgeIfNeeded(Purchase purchase) {
        if (purchase.isAcknowledged()) return;
        billingClient.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken()).build(),
                result -> {
                    if (result.getResponseCode() != BillingClient.BillingResponseCode.OK) {
                        billingMessage = result.getDebugMessage();
                        notifyListeners();
                    }
                });
    }

    private void handleBillingResult(BillingResult result, ActionCallback callback) {
        int code = result == null ? BillingClient.BillingResponseCode.ERROR : result.getResponseCode();
        if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            billingMessage = "הרכישה בוטלה";
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            queryOwnedPurchases(callback);
            return;
        } else {
            billingMessage = result == null ? "Billing error" : result.getDebugMessage();
        }
        notifyListeners();
        result(callback, false, billingMessage);
    }

    private void notifyListeners() {
        EntitlementStatus status = status();
        TrialPolicy.Snapshot trial = store.snapshot();
        List<Listener> copy;
        synchronized (listeners) { copy = new ArrayList<>(listeners); }
        for (Listener listener : copy) {
            listener.onEntitlementChanged(status, trial, localizedPrice, billingMessage);
        }
    }

    private static void result(ActionCallback callback, boolean success, String message) {
        if (callback != null) callback.onResult(success, message == null ? "" : message);
    }
}
