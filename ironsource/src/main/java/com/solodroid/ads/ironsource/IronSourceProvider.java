package com.solodroid.ads.ironsource;

import android.app.Activity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.gms.ads.identifier.AdvertisingIdClient;
import com.ironsource.mediationsdk.ads.nativead.LevelPlayMediaView;
import com.ironsource.mediationsdk.ads.nativead.LevelPlayNativeAd;
import com.ironsource.mediationsdk.ads.nativead.LevelPlayNativeAdListener;
import com.ironsource.mediationsdk.ads.nativead.NativeAdLayout;
import com.ironsource.mediationsdk.adunit.adapter.utility.AdInfo;
import com.ironsource.mediationsdk.logger.IronSourceError;

import com.unity3d.mediation.LevelPlay;
import com.unity3d.mediation.LevelPlayAdError;
import com.unity3d.mediation.LevelPlayAdInfo;
import com.unity3d.mediation.LevelPlayAdSize;
import com.unity3d.mediation.LevelPlayConfiguration;
import com.unity3d.mediation.LevelPlayInitError;
import com.unity3d.mediation.LevelPlayInitListener;
import com.unity3d.mediation.LevelPlayInitRequest;
import com.unity3d.mediation.banner.LevelPlayBannerAdView;
import com.unity3d.mediation.banner.LevelPlayBannerAdViewListener;
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAd;
import com.unity3d.mediation.interstitial.LevelPlayInterstitialAdListener;
import com.unity3d.mediation.rewarded.LevelPlayReward;
import com.unity3d.mediation.rewarded.LevelPlayRewardedAd;
import com.unity3d.mediation.rewarded.LevelPlayRewardedAdListener;

import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdProvider;
import com.solodroid.ads.core.AdsManager;
import com.solodroid.ads.core.models.AdModel;

import java.util.ArrayList;
import java.util.List;

public class IronSourceProvider implements AdProvider {

    private static final String TAG = "IronSourceProvider";

    private boolean isInitialized = false;
    private boolean isInitializing = false;
    private boolean isInitFailed = false;

    private final List<Runnable> pendingTasks = new ArrayList<>();

    // Instance Object LevelPlay
    private LevelPlayBannerAdView levelPlayBannerAdView;
    private LevelPlayInterstitialAd levelPlayInterstitialAd;
    private LevelPlayRewardedAd levelPlayRewardedAd;
    private LevelPlayNativeAd currentNativeAd;
    private LevelPlayInterstitialAd levelPlayAppOpenAd;

    // Jembatan Listener untuk menangkap aksi saat iklan ditonton/ditutup
    private AdInternalListener interstitialShowListener;
    private AdInternalListener rewardedShowListener;
    private AdInternalListener appOpenShowListener;

    @Override
    public void init(Activity activity, AdModel adModel, AdsManager.InitializationListener listener) {
        if (isInitialized) {
            activity.runOnUiThread(() -> {
                if (listener != null) listener.onInitComplete();
            });
            return;
        }

        if (isInitializing) return;

        String appKey = adModel.getMainAds().equals("ironsource")
                ? adModel.getMainIronsourceAppKey()
                : adModel.getBackupIronsourceAppKey();

        if (appKey != null && !appKey.isEmpty() && !appKey.equals("0")) {

            isInitializing = true;
            LevelPlayInitRequest initRequest = new LevelPlayInitRequest.Builder(appKey).build();

            LevelPlay.init(activity, initRequest, new LevelPlayInitListener() {
                @Override
                public void onInitSuccess(@NonNull LevelPlayConfiguration configuration) {
                    isInitialized = true;
                    isInitializing = false;
                    isInitFailed = false;
                    Log.d(TAG, "LevelPlay (ironSource) initialize complete with appKey: " + appKey);

                    activity.runOnUiThread(() -> {
                        // Sinyal sukses ke AdsManager
                        if (listener != null) listener.onInitComplete();

                        // Eksekusi antrean pending task
                        List<Runnable> tasksCopy = new ArrayList<>(pendingTasks);
                        pendingTasks.clear();
                        for (Runnable task : tasksCopy) {
                            task.run();
                        }
                    });
                }

                @Override
                public void onInitFailed(@NonNull LevelPlayInitError error) {
                    isInitializing = false;
                    isInitFailed = true;
                    Log.e(TAG, "LevelPlay initialize failed: " + error.getErrorMessage());

                    activity.runOnUiThread(() -> {
                        // Tetap panggil listener agar aplikasi tidak stuck di Splash Screen
                        if (listener != null) listener.onInitComplete();

                        List<Runnable> tasksCopy = new ArrayList<>(pendingTasks);
                        pendingTasks.clear();
                        for (Runnable task : tasksCopy) {
                            task.run();
                        }
                    });
                }
            });
        } else {
            activity.runOnUiThread(() -> {
                if (listener != null) listener.onInitComplete();
            });
        }

        new Thread(() -> {
            try {
                AdvertisingIdClient.Info adInfo = AdvertisingIdClient.getAdvertisingIdInfo(activity);
                String adId = adInfo.getId();
                Log.d("IronSourceProvider", "Advertising ID: " + adId);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    @Override
    public void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Banner load delayed. Waiting for LevelPlay initialization...");
            pendingTasks.add(() -> loadBanner(activity, container, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        container.removeAllViews();

        LevelPlayBannerAdView.Config adConfig = new LevelPlayBannerAdView.Config.Builder()
                .setAdSize(LevelPlayAdSize.BANNER)
                .setPlacementName(adUnitId)
                .build();

        levelPlayBannerAdView = new LevelPlayBannerAdView(activity, adUnitId, adConfig);

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        container.addView(levelPlayBannerAdView, 0, layoutParams);

        levelPlayBannerAdView.setBannerListener(new LevelPlayBannerAdViewListener() {
            @Override
            public void onAdLoaded(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onBannerAdLoaded");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdLoadFailed(@NonNull LevelPlayAdError error) {
                Log.e(TAG, "onBannerAdLoadFailed: " + error.getErrorMessage());
                activity.runOnUiThread(() -> {
                    container.removeAllViews();
                    if (listener != null) listener.onAdFailed();
                });
            }

            @Override public void onAdDisplayed(@NonNull LevelPlayAdInfo adInfo) {}
            @Override public void onAdDisplayFailed(@NonNull LevelPlayAdInfo adInfo, @NonNull LevelPlayAdError error) {}
            @Override public void onAdClicked(@NonNull LevelPlayAdInfo adInfo) {}
            @Override public void onAdExpanded(@NonNull LevelPlayAdInfo adInfo) {}
            @Override public void onAdCollapsed(@NonNull LevelPlayAdInfo adInfo) {}
            @Override public void onAdLeftApplication(@NonNull LevelPlayAdInfo adInfo) {}
        });

        levelPlayBannerAdView.loadAd();
    }

    @Override
    public void loadInterstitial(Activity activity, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Interstitial load delayed. Waiting for LevelPlay initialization...");
            pendingTasks.add(() -> loadInterstitial(activity, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        levelPlayInterstitialAd = new LevelPlayInterstitialAd(adUnitId);
        levelPlayInterstitialAd.setListener(new LevelPlayInterstitialAdListener() {
            @Override
            public void onAdLoaded(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onInterstitialAdLoaded");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdLoadFailed(@NonNull LevelPlayAdError error) {
                Log.e(TAG, "onInterstitialAdLoadFailed: " + error.getErrorMessage());
                activity.runOnUiThread(() -> {
                    levelPlayInterstitialAd = null;
                    if (listener != null) listener.onAdFailed();
                });
            }

            @Override
            public void onAdDisplayed(@NonNull LevelPlayAdInfo adInfo) {}

            @Override
            public void onAdDisplayFailed(@NonNull LevelPlayAdError error, @NonNull LevelPlayAdInfo adInfo) {
                Log.e(TAG, "onInterstitialAdDisplayFailed: " + error.getErrorMessage());
                activity.runOnUiThread(() -> {
                    levelPlayInterstitialAd = null;
                    if (interstitialShowListener != null) {
                        interstitialShowListener.onAdDismissed();
                        interstitialShowListener = null;
                    }
                });
            }

            @Override
            public void onAdClicked(@NonNull LevelPlayAdInfo adInfo) {}

            @Override
            public void onAdClosed(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onInterstitialAdClosed");
                activity.runOnUiThread(() -> {
                    levelPlayInterstitialAd = null;
                    if (interstitialShowListener != null) {
                        interstitialShowListener.onAdDismissed();
                        interstitialShowListener = null;
                    }
                });
            }

            @Override public void onAdInfoChanged(@NonNull LevelPlayAdInfo adInfo) {}
        });

        levelPlayInterstitialAd.loadAd();
    }

    @Override
    public void showInterstitial(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (levelPlayInterstitialAd != null && levelPlayInterstitialAd.isAdReady()) {
                this.interstitialShowListener = listener;
                levelPlayInterstitialAd.showAd(activity);
            } else {
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    // PERBAIKAN: Menambahkan dukungan parameter Style pada Native Ad
    @Override
    public void loadNative(Activity activity, ViewGroup container, String adUnitId, String style, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Native Ad load delayed. Waiting for LevelPlay initialization...");
            pendingTasks.add(() -> loadNative(activity, container, adUnitId, style, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (currentNativeAd != null) {
            currentNativeAd.destroyAd();
        }

        LevelPlayNativeAdListener nativeAdListener = new LevelPlayNativeAdListener() {
            @Override
            public void onAdLoaded(LevelPlayNativeAd nativeAd, AdInfo adInfo) {
                Log.d(TAG, "onNativeAdLoaded");

                activity.runOnUiThread(() -> {
                    int layoutResId;
                    String safeStyle = (style != null) ? style.toLowerCase() : "medium";

                    switch (safeStyle) {
                        case "small":
                            layoutResId = R.layout.ironsource_native_small;
                            break;
                        case "large":
                            layoutResId = R.layout.ironsource_native_large;
                            break;
                        case "medium":
                        default:
                            layoutResId = R.layout.ironsource_native_medium;
                            break;
                    }

                    LayoutInflater inflater = LayoutInflater.from(activity);
                    NativeAdLayout nativeAdLayout = (NativeAdLayout) inflater.inflate(layoutResId, null);

                    TextView titleView = nativeAdLayout.findViewById(R.id.ad_headline);
                    TextView bodyView = nativeAdLayout.findViewById(R.id.ad_body);
                    ImageView iconView = nativeAdLayout.findViewById(R.id.ad_app_icon);
                    Button ctaView = nativeAdLayout.findViewById(R.id.ad_call_to_action);
                    LevelPlayMediaView mediaView = nativeAdLayout.findViewById(R.id.ad_media);

                    if (nativeAd.getTitle() != null && titleView != null) {
                        titleView.setText(nativeAd.getTitle());
                        nativeAdLayout.setTitleView(titleView);
                    }

                    if (nativeAd.getBody() != null && bodyView != null) {
                        bodyView.setText(nativeAd.getBody());
                        nativeAdLayout.setBodyView(bodyView);
                        bodyView.setVisibility(View.VISIBLE);
                    } else if (bodyView != null) {
                        bodyView.setVisibility(View.INVISIBLE);
                    }

                    if (nativeAd.getIcon() != null && nativeAd.getIcon().getDrawable() != null && iconView != null) {
                        iconView.setImageDrawable(nativeAd.getIcon().getDrawable());
                        nativeAdLayout.setIconView(iconView);
                        iconView.setVisibility(View.VISIBLE);
                    } else if (iconView != null) {
                        iconView.setVisibility(View.GONE);
                    }

                    if (nativeAd.getCallToAction() != null && ctaView != null) {
                        ctaView.setText(nativeAd.getCallToAction());
                        nativeAdLayout.setCallToActionView(ctaView);
                    }

                    if (mediaView != null) {
                        nativeAdLayout.setMediaView(mediaView);
                    }

                    nativeAdLayout.registerNativeAdViews(nativeAd);

                    int marginLeft = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_left);
                    int marginTop = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_top);
                    int marginRight = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_right);
                    int marginBottom = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_bottom);

                    ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(marginLeft, marginTop, marginRight, marginBottom);
                    nativeAdLayout.setLayoutParams(params);

                    container.removeAllViews();
                    container.addView(nativeAdLayout);

                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdLoadFailed(LevelPlayNativeAd nativeAd, IronSourceError error) {
                Log.e(TAG, "onNativeAdLoadFailed: " + error.getErrorMessage());
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }

            @Override public void onAdImpression(LevelPlayNativeAd nativeAd, AdInfo adInfo) {}
            @Override public void onAdClicked(LevelPlayNativeAd nativeAd, AdInfo adInfo) {}
        };

        currentNativeAd = new LevelPlayNativeAd.Builder()
                .withPlacementName(adUnitId)
                .withListener(nativeAdListener)
                .build();

        currentNativeAd.loadAd();
    }

    @Override
    public void loadRewarded(Activity activity, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "Rewarded load delayed. Waiting for LevelPlay initialization...");
            pendingTasks.add(() -> loadRewarded(activity, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        levelPlayRewardedAd = new LevelPlayRewardedAd(adUnitId);
        levelPlayRewardedAd.setListener(new LevelPlayRewardedAdListener() {
            @Override
            public void onAdLoaded(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onRewardedAdLoaded");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdLoadFailed(@NonNull LevelPlayAdError error) {
                Log.e(TAG, "onRewardedAdLoadFailed: " + error.getErrorMessage());
                activity.runOnUiThread(() -> {
                    levelPlayRewardedAd = null;
                    if (listener != null) listener.onAdFailed();
                });
            }

            @Override
            public void onAdDisplayed(@NonNull LevelPlayAdInfo adInfo) {}

            @Override
            public void onAdDisplayFailed(@NonNull LevelPlayAdError error, @NonNull LevelPlayAdInfo adInfo) {
                Log.e(TAG, "onRewardedAdDisplayFailed: " + error.getErrorMessage());
                activity.runOnUiThread(() -> {
                    levelPlayRewardedAd = null;
                    if (rewardedShowListener != null) {
                        rewardedShowListener.onAdDismissed();
                        rewardedShowListener = null;
                    }
                });
            }

            @Override
            public void onAdClicked(@NonNull LevelPlayAdInfo adInfo) {}

            @Override
            public void onAdRewarded(@NonNull LevelPlayReward reward, @NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onRewardedAdRewarded: " + reward.getName());
                activity.runOnUiThread(() -> {
                    if (rewardedShowListener != null) {
                        rewardedShowListener.onRewardEarned();
                    }
                });
            }

            @Override
            public void onAdClosed(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onRewardedAdClosed");
                activity.runOnUiThread(() -> {
                    levelPlayRewardedAd = null;
                    if (rewardedShowListener != null) {
                        rewardedShowListener.onAdDismissed();
                        rewardedShowListener = null;
                    }
                });
            }

            @Override public void onAdInfoChanged(@NonNull LevelPlayAdInfo adInfo) {}
        });

        levelPlayRewardedAd.loadAd();
    }

    @Override
    public void showRewarded(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (levelPlayRewardedAd != null && levelPlayRewardedAd.isAdReady()) {
                this.rewardedShowListener = listener;
                levelPlayRewardedAd.showAd(activity);
            } else {
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    @Override
    public void loadAppOpen(Activity activity, String adUnitId, AdInternalListener listener) {
        if (isInitFailed) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        if (!isInitialized) {
            Log.d(TAG, "App Open load delayed. Waiting for LevelPlay initialization...");
            pendingTasks.add(() -> loadAppOpen(activity, adUnitId, listener));
            return;
        }

        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        // Fallback ke Interstitial
        levelPlayAppOpenAd = new LevelPlayInterstitialAd(adUnitId);
        levelPlayAppOpenAd.setListener(new LevelPlayInterstitialAdListener() {
            @Override
            public void onAdLoaded(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onAppOpenAdLoaded (Fallback)");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdLoadFailed(@NonNull LevelPlayAdError error) {
                Log.e(TAG, "onAppOpenAdLoadFailed: " + error.getErrorMessage());
                activity.runOnUiThread(() -> {
                    levelPlayAppOpenAd = null;
                    if (listener != null) listener.onAdFailed();
                });
            }

            @Override
            public void onAdDisplayed(@NonNull LevelPlayAdInfo adInfo) {}

            @Override
            public void onAdDisplayFailed(@NonNull LevelPlayAdError error, @NonNull LevelPlayAdInfo adInfo) {
                Log.e(TAG, "onAppOpenAdDisplayFailed: " + error.getErrorMessage());
                activity.runOnUiThread(() -> {
                    levelPlayAppOpenAd = null;
                    if (appOpenShowListener != null) {
                        appOpenShowListener.onAdDismissed();
                        appOpenShowListener = null;
                    }
                });
            }

            @Override
            public void onAdClicked(@NonNull LevelPlayAdInfo adInfo) {}

            @Override
            public void onAdClosed(@NonNull LevelPlayAdInfo adInfo) {
                Log.d(TAG, "onAppOpenAdClosed");
                activity.runOnUiThread(() -> {
                    levelPlayAppOpenAd = null;
                    if (appOpenShowListener != null) {
                        appOpenShowListener.onAdDismissed();
                        appOpenShowListener = null;
                    }
                });
            }

            @Override public void onAdInfoChanged(@NonNull LevelPlayAdInfo adInfo) {}
        });

        levelPlayAppOpenAd.loadAd();
    }

    @Override
    public void showAppOpen(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (levelPlayAppOpenAd != null && levelPlayAppOpenAd.isAdReady()) {
                this.appOpenShowListener = listener;
                levelPlayAppOpenAd.showAd(activity);
            } else {
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    @Override
    public void showPrivacyOptions(Activity activity) {}

    @Override
    public boolean isPrivacyOptionsRequired(Activity activity) {
        return false;
    }
}