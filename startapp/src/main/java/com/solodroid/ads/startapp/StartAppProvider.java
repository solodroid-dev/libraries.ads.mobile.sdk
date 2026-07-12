package com.solodroid.ads.startapp;

import android.app.Activity;
import android.content.pm.ApplicationInfo;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdProvider;
import com.solodroid.ads.core.AdsManager;
import com.solodroid.ads.core.models.AdModel;

import com.startapp.sdk.ads.banner.Banner;
import com.startapp.sdk.ads.banner.BannerListener;
import com.startapp.sdk.ads.nativead.NativeAdDetails;
import com.startapp.sdk.ads.nativead.NativeAdPreferences;
import com.startapp.sdk.ads.nativead.StartAppNativeAd;
import com.startapp.sdk.adsbase.Ad;
import com.startapp.sdk.adsbase.StartAppAd;
import com.startapp.sdk.adsbase.StartAppSDK;
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener;
import com.startapp.sdk.adsbase.adlisteners.AdEventListener;
import com.startapp.sdk.adsbase.adlisteners.VideoListener;

import java.util.ArrayList;

public class StartAppProvider implements AdProvider {

    private static final String TAG = "StartAppProvider";

    private StartAppAd interstitialAd;
    private StartAppAd rewardedAd;
    private StartAppAd appOpenAd;

    // Memastikan StartAppNativeAd tidak terkena Garbage Collection sebelum digunakan
    private StartAppNativeAd startAppNativeAd;

    @Override
    public void init(Activity activity, AdModel adModel, AdsManager.InitializationListener listener) {
        String appId = adModel.getMainAds().equals("startapp")
                ? adModel.getMainStartappAppId()
                : adModel.getBackupStartappAppId();

        if (appId != null && !appId.isEmpty() && !appId.equals("0")) {
            StartAppSDK.init(activity, appId, false);
            StartAppAd.disableSplash();

            // Pengecekan Debug Mode dinamis
            boolean isDebuggable = (activity.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
            if (isDebuggable) {
                StartAppSDK.setTestAdsEnabled(true);
                Log.d(TAG, "StartApp Test Mode ENABLED (Debug Mode Detected)");
            } else {
                StartAppSDK.setTestAdsEnabled(false);
                Log.d(TAG, "StartApp Test Mode DISABLED (Release Mode Detected)");
            }

            Log.d(TAG, "StartApp SDK Initialized with App ID: " + appId);

            // Langsung kembalikan callback sukses karena StartApp bersifat sinkron
            activity.runOnUiThread(() -> {
                if (listener != null) listener.onInitComplete();
            });
        } else {
            Log.e(TAG, "StartApp SDK Initialization failed: App ID is empty");
            activity.runOnUiThread(() -> {
                if (listener != null) listener.onInitComplete();
            });
        }
    }

    @Override
    public void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        Banner banner = new Banner(activity, new BannerListener() {
            @Override
            public void onReceiveAd(View view) {
                Log.d(TAG, "Banner Loaded");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onFailedToReceiveAd(View view) {
                Log.e(TAG, "Banner Failed to Load");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }

            @Override
            public void onClick(View view) {}

            @Override
            public void onImpression(View view) {}
        });

        container.removeAllViews();
        container.addView(banner);
    }

    @Override
    public void loadInterstitial(Activity activity, String adUnitId, AdInternalListener listener) {
        interstitialAd = new StartAppAd(activity);
        interstitialAd.loadAd(new AdEventListener() {
            @Override
            public void onReceiveAd(Ad ad) {
                Log.d(TAG, "Interstitial Loaded");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onFailedToReceiveAd(Ad ad) {
                Log.e(TAG, "Interstitial Failed to Load: " + ad.getErrorMessage());
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    @Override
    public void showInterstitial(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (interstitialAd != null && interstitialAd.isReady()) {
                interstitialAd.showAd(new AdDisplayListener() {
                    @Override
                    public void adHidden(Ad ad) {
                        interstitialAd = null;
                        activity.runOnUiThread(() -> {
                            if (listener != null) listener.onAdDismissed();
                        });
                    }

                    @Override
                    public void adDisplayed(Ad ad) {}

                    @Override
                    public void adClicked(Ad ad) {}

                    @Override
                    public void adNotDisplayed(Ad ad) {
                        interstitialAd = null;
                        activity.runOnUiThread(() -> {
                            if (listener != null) listener.onAdDismissed();
                        });
                    }
                });
            } else {
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    @Override
    public void loadNative(Activity activity, ViewGroup container, String adUnitId, String style, AdInternalListener listener) {
        startAppNativeAd = new StartAppNativeAd(activity);

        NativeAdPreferences nativePrefs = new NativeAdPreferences()
                .setAdsNumber(3)
                .setAutoBitmapDownload(true)
                .setPrimaryImageSize(3);

        startAppNativeAd.loadAd(nativePrefs, new AdEventListener() {
            @Override
            public void onReceiveAd(@NonNull Ad ad) {
                activity.runOnUiThread(() -> {
                    ArrayList<NativeAdDetails> nativeAdsList = startAppNativeAd.getNativeAds();
                    if (nativeAdsList != null && !nativeAdsList.isEmpty()) {
                        NativeAdDetails nativeAdData = nativeAdsList.get(0);

                        // Dukungan parameter style
                        int layoutResId;
                        String safeStyle = (style != null) ? style.toLowerCase() : "medium";
                        switch (safeStyle) {
                            case "small":
                                layoutResId = R.layout.startapp_native_small;
                                break;
                            case "large":
                                layoutResId = R.layout.startapp_native_large;
                                break;
                            case "medium":
                            default:
                                layoutResId = R.layout.startapp_native_medium;
                                break;
                        }

                        View adView = activity.getLayoutInflater().inflate(layoutResId, null);

                        int margin = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_left);
                        ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        params.setMargins(margin, margin, margin, margin);
                        adView.setLayoutParams(params);

                        // Mapping Data Layout StartApp
                        ImageView imgIcon = adView.findViewById(R.id.ad_app_icon);
                        ImageView imgMedia = adView.findViewById(R.id.ad_media);
                        TextView txtTitle = adView.findViewById(R.id.ad_headline);
                        TextView txtBody = adView.findViewById(R.id.ad_body);
                        Button btnAction = adView.findViewById(R.id.ad_call_to_action);

                        if (imgIcon != null && nativeAdData.getImageBitmap() != null) {
                            imgIcon.setImageBitmap(nativeAdData.getImageBitmap());
                            imgIcon.setVisibility(View.VISIBLE);
                        } else if (imgIcon != null) {
                            imgIcon.setVisibility(View.GONE);
                        }

                        if (imgMedia != null && nativeAdData.getImageBitmap() != null) {
                            imgMedia.setImageBitmap(nativeAdData.getImageBitmap());
                            imgMedia.setVisibility(View.VISIBLE);
                        } else if (imgMedia != null) {
                            imgMedia.setVisibility(View.GONE);
                        }

                        if (txtTitle != null) txtTitle.setText(nativeAdData.getTitle());

                        if (txtBody != null) {
                            if (nativeAdData.getDescription() != null) {
                                txtBody.setText(nativeAdData.getDescription());
                                txtBody.setVisibility(View.VISIBLE);
                            } else {
                                txtBody.setVisibility(View.INVISIBLE);
                            }
                        }

                        if (btnAction != null) {
                            btnAction.setText(nativeAdData.isApp() ? "Install" : "Open");
                        }

                        nativeAdData.registerViewForInteraction(adView);

                        container.removeAllViews();
                        container.addView(adView);

                        Log.d(TAG, "Native Loaded with style: " + safeStyle);
                        if (listener != null) listener.onAdLoaded();
                    } else {
                        Log.e(TAG, "Native Ad List is Empty");
                        if (listener != null) listener.onAdFailed();
                    }
                });
            }

            @Override
            public void onFailedToReceiveAd(Ad ad) {
                Log.e(TAG, "Native Failed to Load: " + ad.getErrorMessage());
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    @Override
    public void loadRewarded(Activity activity, String adUnitId, AdInternalListener listener) {
        rewardedAd = new StartAppAd(activity);
        rewardedAd.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, new AdEventListener() {
            @Override
            public void onReceiveAd(Ad ad) {
                Log.d(TAG, "Rewarded Loaded");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onFailedToReceiveAd(Ad ad) {
                Log.e(TAG, "Rewarded Failed to Load: " + ad.getErrorMessage());
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    @Override
    public void showRewarded(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (rewardedAd != null && rewardedAd.isReady()) {
                rewardedAd.setVideoListener(new VideoListener() {
                    @Override
                    public void onVideoCompleted() {
                        Log.d(TAG, "Reward Earned");
                        activity.runOnUiThread(() -> {
                            if (listener != null) listener.onRewardEarned();
                        });
                    }
                });

                rewardedAd.showAd(new AdDisplayListener() {
                    @Override
                    public void adHidden(Ad ad) {
                        rewardedAd = null;
                        activity.runOnUiThread(() -> {
                            if (listener != null) listener.onAdDismissed();
                        });
                    }

                    @Override
                    public void adDisplayed(Ad ad) {}

                    @Override
                    public void adClicked(Ad ad) {}

                    @Override
                    public void adNotDisplayed(Ad ad) {
                        rewardedAd = null;
                        activity.runOnUiThread(() -> {
                            // Anggap batal / error jika tidak tayang
                            if (listener != null) listener.onAdDismissed();
                        });
                    }
                });
            } else {
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    @Override
    public void loadAppOpen(Activity activity, String adUnitId, AdInternalListener listener) {
        appOpenAd = new StartAppAd(activity);
        appOpenAd.loadAd(new AdEventListener() {
            @Override
            public void onReceiveAd(Ad ad) {
                Log.d(TAG, "App Open Loaded");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onFailedToReceiveAd(Ad ad) {
                Log.e(TAG, "App Open Failed to Load: " + ad.getErrorMessage());
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    @Override
    public void showAppOpen(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (appOpenAd != null && appOpenAd.isReady()) {
                appOpenAd.showAd(new AdDisplayListener() {
                    @Override
                    public void adHidden(Ad ad) {
                        appOpenAd = null;
                        activity.runOnUiThread(() -> {
                            if (listener != null) listener.onAdDismissed();
                        });
                    }

                    @Override
                    public void adDisplayed(Ad ad) {}

                    @Override
                    public void adClicked(Ad ad) {}

                    @Override
                    public void adNotDisplayed(Ad ad) {
                        appOpenAd = null;
                        activity.runOnUiThread(() -> {
                            if (listener != null) listener.onAdDismissed();
                        });
                    }
                });
            } else {
                if (listener != null) listener.onAdDismissed();
            }
        });
    }
}