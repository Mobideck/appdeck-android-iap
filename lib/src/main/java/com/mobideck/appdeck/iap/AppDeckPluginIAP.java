package com.mobideck.appdeck.iap;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.vending.billing.IInAppBillingService;
import com.mobideck.appdeck.plugin.ApiCall;
import com.mobideck.appdeck.plugin.Plugin;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Date;

public class AppDeckPluginIAP implements Plugin {

    public static String TAG = "AppDeckPluginIAP";

    private final Boolean ENABLE_DEBUG_LOGGING = true;

    private String licenceKey;

    IInAppBillingService mService;
    ServiceConnection mServiceConn;
    Activity activity;

    private ApiCall lastApiCall;
    private String lastProductType;

    @Override
    public void onActivityCreate(Activity activity) {
        mServiceConn = new ServiceConnection() {
            @Override
            public void onServiceDisconnected(ComponentName name) {
                mService = null;
            }

            @Override
            public void onServiceConnected(ComponentName name,
                                           IBinder service) {
                mService = IInAppBillingService.Stub.asInterface(service);
            }
        };
        Intent serviceIntent =
                new Intent("com.android.vending.billing.InAppBillingService.BIND");
        serviceIntent.setPackage("com.android.vending");
        activity.bindService(serviceIntent, mServiceConn, Context.BIND_AUTO_CREATE);
        this.activity = activity;
    }

    @Override
    public void onActivityPause(Activity activity) {

    }

    @Override
    public void onActivityResume(Activity activity) {

    }

    @Override
    public void onActivityResult(Activity activity, int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001) {
            int responseCode = data.getIntExtra("RESPONSE_CODE", 0);
            String purchaseData = data.getStringExtra("INAPP_PURCHASE_DATA");
            String dataSignature = data.getStringExtra("INAPP_DATA_SIGNATURE");

            if (resultCode == Activity.RESULT_OK) {
                try {
                    JSONObject jo = new JSONObject(purchaseData);
                    String sku = jo.getString("productId");
                    String orderId = jo.getString("orderId");
                    Log.i(TAG, "product " + sku + " have been bought");

                    JSONObject result = new JSONObject();
                    result.put("productId", sku);
                    result.put("transactionId", orderId);
                    result.put("receipt", purchaseData);
                    result.put("signature", dataSignature);
                    result.put("productType", lastProductType);

                    JSONArray resultArray = new JSONArray();
                    resultArray.put(result);

                    if (lastApiCall != null) {
                        lastApiCall.sendCallbackWithResult("success", resultArray);
                        lastApiCall = null;
                    }
                }
                catch (JSONException e) {
                    Log.e(TAG, "Failed to parse purchase data.");
                    e.printStackTrace();
                    if (lastApiCall != null) {
                        lastApiCall.sendCallBackWithError(e.getMessage());
                        lastApiCall = null;
                    }
                }
            }
        }
    }

    @Override
    public void onActivityDestroy(Activity activity) {
        if (mService != null) {
            activity.unbindService(mServiceConn);
        }
        this.activity = null;
    }

    @Override
    public ArrayList<String> getCommands() {

        ArrayList<String> commands = new ArrayList<>();
        commands.add("iapsetup");
        commands.add("iaplistproduct");
        commands.add("iappurchase");
        commands.add("iapconsume");
        commands.add("iapsubscription");
        commands.add("iaprestore");
        commands.add("iapgetreceipt");
        return commands;
    }

    private void errorHandler(final ApiCall call, final String error) {
        Handler mainHandler = new Handler(activity.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                call.sendCallBackWithError(error);
            }
        };
        mainHandler.post(myRunnable);
    }

    private void successHandler(final ApiCall call, final JSONArray result) {
        Handler mainHandler = new Handler(activity.getMainLooper());
        Runnable myRunnable = new Runnable() {
            @Override
            public void run() {
                call.sendCallbackWithResult("success", result);
            }
        };
        mainHandler.post(myRunnable);
    }

    public boolean iapsetup(final ApiCall call)
    {
        licenceKey = call.paramObject.optString("androidLicenceKey", null);
        return true;
    }

    private int listproduct(JSONArray results, Bundle querySkus, String productType) throws RemoteException, JSONException {

        // send request
        Bundle skuDetails = mService.getSkuDetails(3, activity.getPackageName(), productType, querySkus);

        int response = skuDetails.getInt("RESPONSE_CODE");

        if (response != 0) {
            return response;
        }

        ArrayList<String> responseList = skuDetails.getStringArrayList("DETAILS_LIST");

        for (String thisResponse : responseList) {
            JSONObject object = new JSONObject(thisResponse);
            String productId = object.getString("productId");
            String title = object.getString("title");
            String price = object.getString("price");
            String description = object.getString("price");

            JSONObject result = new JSONObject();
            result.put("productId", productId);
            result.put("productType", productType);
            result.put("title", title);
            result.put("price", price);
            result.put("description", description);

            results.put(result);
        }

        return 0;
    }

    public boolean iaplistproduct(final ApiCall call)
    {
        if (licenceKey == null) {
            call.sendCallBackWithError("licence key not set");
            return true;
        }

        final JSONArray productIds = call.paramObject.optJSONArray("productIds");

        if (productIds == null) {
            call.sendCallBackWithError("missing productsIds");
            return true;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {

                // prepare request to play store
                ArrayList<String> skuList = new ArrayList<String> ();
                for (int i = 0; i < productIds.length(); i++) {
                    String productId = productIds.optString(i);
                    if (productId != null)
                        skuList.add(productId);
                }
                Bundle querySkus = new Bundle();
                querySkus.putStringArrayList("ITEM_ID_LIST", skuList);
                try {
                    JSONArray results = new JSONArray();
                    // list consumable
                    int response = listproduct(results, querySkus, "inapp");
                    if (response != 0) {
                        errorHandler(call, AppDeckPluginIAP.billingErrorCodeToString(response));
                        return;
                    }
                    // list subscriptions
                    response = listproduct(results, querySkus, "subs");
                    if (response != 0) {
                        errorHandler(call, AppDeckPluginIAP.billingErrorCodeToString(response));
                        return;
                    }

                    JSONArray resultsArray = new JSONArray();
                    resultsArray.put(results);
                    successHandler(call, resultsArray);
                } catch (Exception e) {
                    e.printStackTrace();
                    errorHandler(call, e.getLocalizedMessage());
                }
            }
        }, "iappurchase").start();


        return true;
    }

    private boolean buy(final ApiCall call, String productType) {
        if (licenceKey == null) {
            call.sendCallBackWithError("licence key not set");
            return true;
        }

        final String productId = call.paramObject.optString("productId", null);

        if (productId == null) {
            call.sendCallBackWithError("missing productId");
            return true;
        }

        Log.d(TAG, "iappurchase:"+productId+" type:"+productType);

        try {
            lastProductType = productType;
            Bundle buyIntentBundle = mService.getBuyIntent(3, activity.getPackageName(), productId, productType, licenceKey);

            int response = buyIntentBundle.getInt("RESPONSE_CODE");

            if (response != 0) {
                errorHandler(call, AppDeckPluginIAP.billingErrorCodeToString(response));
                return true;
            }

            PendingIntent pendingIntent = buyIntentBundle.getParcelable("BUY_INTENT");

            call.setResult(Boolean.valueOf(true));

            lastApiCall = call;

            activity.startIntentSenderForResult(pendingIntent.getIntentSender(),
                    1001, new Intent(), Integer.valueOf(0), Integer.valueOf(0),
                    Integer.valueOf(0));

        } catch (RemoteException e) {
            e.printStackTrace();
            call.sendCallBackWithError(e.getMessage());
        } catch (IntentSender.SendIntentException e) {
            e.printStackTrace();
            call.sendCallBackWithError(e.getMessage());
        }

        return true;
    }

    public boolean iappurchase(final ApiCall call)
    {
        return buy(call, "inapp");
    }

    public boolean iapsubscription(final ApiCall call)
    {
        return buy(call, "subs");
    }

    public boolean iapconsume(final ApiCall call)
    {
        if (licenceKey == null) {
            call.sendCallBackWithError("licence key not set");
            return true;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {

                try {
                    String productType = call.paramObject.getString("productType");
                    String receipt = call.paramObject.getString("receipt");
                    String signature = call.paramObject.getString("signature");

                    JSONObject object = new JSONObject(receipt);

                    String token = object.getString("purchaseToken");

                    int response = mService.consumePurchase(3, activity.getPackageName(), token);
                    if (response != 0) {
                        errorHandler(call, AppDeckPluginIAP.billingErrorCodeToString(response));
                        return;
                    }
                    successHandler(call, new JSONArray());

                } catch (Exception e) {
                    e.printStackTrace();
                    errorHandler(call, e.getLocalizedMessage());
                }
            }
        }, "iappurchase").start();

        return true;
    }

    private int restore(JSONArray results, String type) throws RemoteException, JSONException {
        Bundle ownedItems = mService.getPurchases(3, activity.getPackageName(), type, null);

        int response = ownedItems.getInt("RESPONSE_CODE");

        if (response != 0)
            return response;

        ArrayList<String> ownedSkus =
                ownedItems.getStringArrayList("INAPP_PURCHASE_ITEM_LIST");
        ArrayList<String>  purchaseDataList =
                ownedItems.getStringArrayList("INAPP_PURCHASE_DATA_LIST");
        ArrayList<String>  signatureList =
                ownedItems.getStringArrayList("INAPP_DATA_SIGNATURE_LIST");
        String continuationToken =
                ownedItems.getString("INAPP_CONTINUATION_TOKEN");

        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        for (int i = 0; i < purchaseDataList.size(); ++i) {
            String purchaseData = purchaseDataList.get(i);
            String signature = signatureList.get(i);
            String sku = ownedSkus.get(i);

            JSONObject object = new JSONObject(purchaseData);
            int purchaseState = object.getInt("purchaseState");
            String transactionId = object.getString("orderId");
            int purchaseTime = object.getInt("purchaseTime");
            String purchaseDate = sdf.format(new Date(purchaseTime));


            JSONObject result = new JSONObject();
            result.put("productId", sku);
            result.put("state", purchaseState);
            result.put("transactionId", transactionId);
            result.put("date", purchaseDate);
            result.put("productType", type);
            result.put("receipt", purchaseData);
            result.put("signature", signature);

            results.put(result);


            // do something with this purchase information
            // e.g. display the updated list of products owned by user
        }

        // if continuationToken != null, call getPurchases again
        // and pass in the token to retrieve more items

        return 0;
    }

    public boolean iaprestore(final ApiCall call)
    {
        if (licenceKey == null) {
            call.sendCallBackWithError("licence key not set");
            return true;
        }

        try {
            JSONArray results = new JSONArray();

            int response = restore(results, "inapp");

            if (response != 0) {
                errorHandler(call, AppDeckPluginIAP.billingErrorCodeToString(response));
                return true;
            }

            response = restore(results, "subs");

            if (response != 0) {
                errorHandler(call, AppDeckPluginIAP.billingErrorCodeToString(response));
                return true;
            }

            JSONArray resultsArray = new JSONArray();
            resultsArray.put(results);

            successHandler(call, resultsArray);

        } catch (Exception e) {
            e.printStackTrace();
            call.sendCallBackWithError(e.getMessage());
        }

        return true;
    }

    public boolean iapgetreceipt(final ApiCall call)
    {
        if (licenceKey == null) {
            call.sendCallBackWithError("licence key not set");
            return true;
        }

        return false;
    }

    public static String billingErrorCodeToString(int errorCode) {
        if (errorCode == 0)
            return "Success";
        if (errorCode == 1)
            return "User pressed back or canceled a dialog";
        if (errorCode == 2)
            return "Network connection is down";
        if (errorCode == 3)
            return "Billing API version is not supported for the type requested";
        if (errorCode == 4)
            return "Requested product is not available for purchase";
        if (errorCode == 5)
            return "Invalid arguments provided to the API. This error can also indicate that the application was not correctly signed or properly set up for In-app Billing in Google Play, or does not have the necessary permissions in its manifest";
        if (errorCode == 6)
            return "Fatal error during the API action";
        if (errorCode == 7)
            return "Failure to purchase since item is already owned";
        if (errorCode == 8)
            return "Failure to consume since item is not owned";
        return "Unknow error "+errorCode;
    }

}
