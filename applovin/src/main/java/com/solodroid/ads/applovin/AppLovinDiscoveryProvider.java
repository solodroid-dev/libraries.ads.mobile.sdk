package com.solodroid.ads.applovin;

import android.app.Activity;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.applovin.adview.AppLovinAdView;
import com.applovin.adview.AppLovinIncentivizedInterstitial;
import com.applovin.adview.AppLovinInterstitialAd;
import com.applovin.adview.AppLovinInterstitialAdDialog;
import com.applovin.sdk.AppLovinAd;
import com.applovin.sdk.AppLovinAdDisplayListener;
import com.applovin.sdk.AppLovinAdLoadListener;
import com.applovin.sdk.AppLovinAdRewardListener;
import com.applovin.sdk.AppLovinAdSize;
import com.applovin.sdk.AppLovinCmpService;
import com.applovin.sdk.AppLovinSdk;
import com.applovin.sdk.AppLovinSdkInitializationConfiguration;

import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdProvider;
import com.solodroid.ads.core.AdsManager;
import com.solodroid.ads.core.models.AdModel;

import java.util.Map;

public class AppLovinDiscoveryProvider implements AdProvider {

    private static final String TAG = "AppLovinDiscovery";

    private AppLovinAd loadedInterstitialAd;
    private AppLovinIncentivizedInterstitial incentivizedInterstitial;
    private AppLovinAd loadedAppOpenAd;

    private boolean isInitialized = false;

    @Override
    public void init(Activity activity, AdModel adModel, AdsManager.InitializationListener listener) {
        if (isInitialized) {
            activity.runOnUiThread(() -> {
                if (listener != null) listener.onInitComplete();
            });
            return;
        }

        //String sdkKey = AppLovinSdk.getInstance(activity).getSdkKey();
        String sdkKey = adModel.getMainApplovinSdkKey();
        if (sdkKey == null || sdkKey.isEmpty()) {
            sdkKey = adModel.getBackupApplovinSdkKey();
        }
        // Inisialisasi tanpa setMediationProvider("max") karena ini adalah AppLovin Discovery
        AppLovinSdkInitializationConfiguration initConfig = AppLovinSdkInitializationConfiguration.builder(sdkKey, activity)
                .build();

        AppLovinSdk.getInstance(activity).initialize(initConfig, configuration -> {
            isInitialized = true;
            Log.d(TAG, "AppLovin Discovery Initialized. Country: " + configuration.getCountryCode());
            activity.runOnUiThread(() -> {
                if (listener != null) listener.onInitComplete();
            });
        });
    }

    @Override
    public void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        activity.runOnUiThread(() -> {
            AppLovinAdView adView = new AppLovinAdView(AppLovinSdk.getInstance(activity), AppLovinAdSize.BANNER, adUnitId, activity);

            int heightPx = activity.getResources().getDimensionPixelSize(R.dimen.ads_banner_height_50);
            adView.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, heightPx));

            adView.setAdLoadListener(new AppLovinAdLoadListener() {
                @Override
                public void adReceived(AppLovinAd ad) {
                    Log.d(TAG, "Banner Loaded");
                    activity.runOnUiThread(() -> {
                        container.removeAllViews();
                        container.addView(adView);
                        if (listener != null) listener.onAdLoaded();
                    });
                }

                @Override
                public void failedToReceiveAd(int errorCode) {
                    Log.e(TAG, "Banner Failed with error code: " + errorCode);
                    activity.runOnUiThread(() -> {
                        if (listener != null) listener.onAdFailed();
                    });
                }
            });

            adView.loadNextAd();
        });
    }

    @Override
    public void loadInterstitial(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        activity.runOnUiThread(() -> {
            AppLovinSdk.getInstance(activity).getAdService().loadNextAdForZoneId(adUnitId, new AppLovinAdLoadListener() {
                @Override
                public void adReceived(AppLovinAd ad) {
                    Log.d(TAG, "Interstitial Loaded");
                    activity.runOnUiThread(() -> {
                        loadedInterstitialAd = ad;
                        if (listener != null) listener.onAdLoaded();
                    });
                }

                @Override
                public void failedToReceiveAd(int errorCode) {
                    Log.e(TAG, "Interstitial Failed with error code: " + errorCode);
                    activity.runOnUiThread(() -> {
                        loadedInterstitialAd = null;
                        if (listener != null) listener.onAdFailed();
                    });
                }
            });
        });
    }

    @Override
    public void showInterstitial(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (loadedInterstitialAd != null) {
                AppLovinInterstitialAdDialog interstitialAdDialog = AppLovinInterstitialAd.create(AppLovinSdk.getInstance(activity), activity);
                interstitialAdDialog.setAdDisplayListener(new AppLovinAdDisplayListener() {
                    @Override
                    public void adDisplayed(AppLovinAd ad) {}

                    @Override
                    public void adHidden(AppLovinAd ad) {
                        activity.runOnUiThread(() -> {
                            loadedInterstitialAd = null;
                            if (listener != null) listener.onAdDismissed();
                        });
                    }
                });

                interstitialAdDialog.showAndRender(loadedInterstitialAd);
            } else {
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    // PERBAIKAN: Menambahkan parameter "String style" agar selaras dengan interface
    @Override
    public void loadNative(Activity activity, ViewGroup container, String adUnitId, String style, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        activity.runOnUiThread(() -> {
            // Trik MREC untuk slot Native Ad. Karena fallback ke banner MREC, style layout diabaikan.
            AppLovinAdView mrecAdView = new AppLovinAdView(AppLovinSdk.getInstance(activity), AppLovinAdSize.MREC, adUnitId, activity);

            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            int marginLeft = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_left);
            int marginTop = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_top);
            int marginRight = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_right);
            int marginBottom = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_bottom);
            params.setMargins(marginLeft, marginTop, marginRight, marginBottom);
            params.gravity = android.view.Gravity.CENTER;
            mrecAdView.setLayoutParams(params);

            mrecAdView.setAdLoadListener(new AppLovinAdLoadListener() {
                @Override
                public void adReceived(AppLovinAd ad) {
                    Log.d(TAG, "Native (MREC Fallback) Loaded");
                    activity.runOnUiThread(() -> {
                        container.removeAllViews();
                        container.addView(mrecAdView);
                        if (listener != null) listener.onAdLoaded();
                    });
                }

                @Override
                public void failedToReceiveAd(int errorCode) {
                    Log.e(TAG, "Native (MREC Fallback) Failed with error code: " + errorCode);
                    activity.runOnUiThread(() -> {
                        if (listener != null) listener.onAdFailed();
                    });
                }
            });

            mrecAdView.loadNextAd();
        });
    }

    @Override
    public void loadRewarded(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        activity.runOnUiThread(() -> {
            incentivizedInterstitial = AppLovinIncentivizedInterstitial.create(adUnitId, AppLovinSdk.getInstance(activity));
            incentivizedInterstitial.preload(new AppLovinAdLoadListener() {
                @Override
                public void adReceived(AppLovinAd ad) {
                    Log.d(TAG, "Rewarded Loaded");
                    activity.runOnUiThread(() -> {
                        if (listener != null) listener.onAdLoaded();
                    });
                }

                @Override
                public void failedToReceiveAd(int errorCode) {
                    Log.e(TAG, "Rewarded Failed with error code: " + errorCode);
                    activity.runOnUiThread(() -> {
                        incentivizedInterstitial = null;
                        if (listener != null) listener.onAdFailed();
                    });
                }
            });
        });
    }

    @Override
    public void showRewarded(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (incentivizedInterstitial != null && incentivizedInterstitial.isAdReadyToDisplay()) {
                AppLovinAdRewardListener rewardListener = new AppLovinAdRewardListener() {
                    @Override
                    public void userRewardVerified(AppLovinAd ad, Map<String, String> response) {
                        Log.d(TAG, "Reward Earned (AppLovin Discovery)");
                        activity.runOnUiThread(() -> {
                            if (listener != null) listener.onRewardEarned();
                        });
                    }

                    @Override public void userOverQuota(AppLovinAd ad, Map<String, String> response) {}
                    @Override public void userRewardRejected(AppLovinAd ad, Map<String, String> response) {}
                    @Override public void validationRequestFailed(AppLovinAd ad, int responseCode) {}
                };

                AppLovinAdDisplayListener displayListener = new AppLovinAdDisplayListener() {
                    @Override
                    public void adDisplayed(AppLovinAd ad) {}

                    @Override
                    public void adHidden(AppLovinAd ad) {
                        activity.runOnUiThread(() -> {
                            incentivizedInterstitial = null;
                            if (listener != null) listener.onAdDismissed();
                        });
                    }
                };

                incentivizedInterstitial.show(activity, rewardListener, null, displayListener, null);
            } else {
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    @Override
    public void loadAppOpen(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        activity.runOnUiThread(() -> {
            AppLovinSdk.getInstance(activity).getAdService().loadNextAdForZoneId(adUnitId, new AppLovinAdLoadListener() {
                @Override
                public void adReceived(AppLovinAd ad) {
                    Log.d(TAG, "App Open (Fallback) Loaded");
                    activity.runOnUiThread(() -> {
                        loadedAppOpenAd = ad;
                        if (listener != null) listener.onAdLoaded();
                    });
                }

                @Override
                public void failedToReceiveAd(int errorCode) {
                    Log.e(TAG, "App Open (Fallback) Failed with error code: " + errorCode);
                    activity.runOnUiThread(() -> {
                        loadedAppOpenAd = null;
                        if (listener != null) listener.onAdFailed();
                    });
                }
            });
        });
    }

    @Override
    public void showAppOpen(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (loadedAppOpenAd != null) {
                AppLovinInterstitialAdDialog appOpenDialog = AppLovinInterstitialAd.create(AppLovinSdk.getInstance(activity), activity);
                appOpenDialog.setAdDisplayListener(new AppLovinAdDisplayListener() {
                    @Override
                    public void adDisplayed(AppLovinAd ad) {}

                    @Override
                    public void adHidden(AppLovinAd ad) {
                        activity.runOnUiThread(() -> {
                            loadedAppOpenAd = null;
                            if (listener != null) listener.onAdDismissed();
                        });
                    }
                });

                appOpenDialog.showAndRender(loadedAppOpenAd);
            } else {
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    @Override
    public void showPrivacyOptions(Activity activity) {
        AppLovinCmpService cmpService = AppLovinSdk.getInstance(activity).getCmpService();
        if (cmpService.hasSupportedCmp()) {
            cmpService.showCmpForExistingUser(activity, error -> {
                if (error != null) {
                    Log.w(TAG, "CMP Error: " + error.getMessage());
                }
            });
        }
    }

    @Override
    public boolean isPrivacyOptionsRequired(Activity activity) {
        return AppLovinSdk.getInstance(activity).getCmpService().hasSupportedCmp();
    }
}