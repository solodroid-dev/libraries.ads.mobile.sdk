package com.solodroid.ads.admob;

import android.app.Activity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

// NEXT-GEN SDK IMPORTS
import com.google.android.libraries.ads.mobile.sdk.MobileAds;
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAdEventCallback;
import com.google.android.libraries.ads.mobile.sdk.common.FullScreenContentError;
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig;
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback;
import com.google.android.libraries.ads.mobile.sdk.common.AdRequest;
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError;

// BANNER
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize;
import com.google.android.libraries.ads.mobile.sdk.banner.AdView;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd;
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest;

// INTERSTITIAL
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAd;
import com.google.android.libraries.ads.mobile.sdk.interstitial.InterstitialAdEventCallback;

// REWARDED
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAd;
import com.google.android.libraries.ads.mobile.sdk.rewarded.RewardedAdEventCallback;

// APP OPEN
import com.google.android.libraries.ads.mobile.sdk.appopen.AppOpenAd;

// NATIVE
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest;
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdView;
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView;

import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdProvider;
import com.solodroid.ads.core.AdsManager;
import com.solodroid.ads.core.models.AdModel;

import java.util.Arrays;
import java.util.List;

public class AdMobProvider implements AdProvider {

    private InterstitialAd mInterstitial;
    private RewardedAd mRewarded;
    private AppOpenAd mAppOpen;
    private boolean isMobileAdsInitializeCalled = false;

    @Override
    public void init(Activity activity, AdModel adModel, AdsManager.InitializationListener listener) {
        AdMobGdpr adMobGdpr = new AdMobGdpr(activity);
        adMobGdpr.gatherConsent(() -> {
            if (isMobileAdsInitializeCalled) {
                return;
            }
            isMobileAdsInitializeCalled = true;

            String admobAppId = adModel.getMainAdmobAppId();
            if (admobAppId == null || admobAppId.isEmpty()) {
                admobAppId = adModel.getBackupAdmobAppId();
            }

            if (admobAppId != null && !admobAppId.isEmpty()) {
                InitializationConfig config = new InitializationConfig.Builder(admobAppId).build();
                MobileAds.initialize(activity, config, initializationStatus -> {
                    Log.d("AdMobProvider", "AdMob Initialized successfully");
                    activity.runOnUiThread(() -> {
                        if (listener != null) listener.onInitComplete();
                    });
                });
            } else {
                Log.e("AdMobProvider", "Gagal inisialisasi AdMob Next-Gen: App ID kosong!");
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onInitComplete();
                });
            }
        });
    }

    @Override
    public void loadBanner(Activity activity, ViewGroup container, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdView adView = new AdView(activity);
        BannerAdRequest adRequest = new BannerAdRequest.Builder(adUnitId, getAdSize(activity)).build();

        container.removeAllViews();
        container.addView(adView);

        adView.loadAd(adRequest, new AdLoadCallback<BannerAd>() {
            @Override
            public void onAdLoaded(@NonNull BannerAd ad) {
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    private AdSize getAdSize(Activity activity) {
        DisplayMetrics displayMetrics = activity.getResources().getDisplayMetrics();
        float adWidthPixels = displayMetrics.widthPixels;
        float density = displayMetrics.density;
        int adWidth = (int) (adWidthPixels / density);
        return AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(activity, adWidth);
    }

    @Override
    public void loadInterstitial(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdRequest adRequest = new AdRequest.Builder(adUnitId).build();
        InterstitialAd.load(adRequest, new AdLoadCallback<InterstitialAd>() {
            @Override
            public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                mInterstitial = interstitialAd;
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                mInterstitial = null;
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    @Override
    public void showInterstitial(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (mInterstitial != null) {
                mInterstitial.setAdEventCallback(
                        new InterstitialAdEventCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                mInterstitial = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError fullScreenContentError) {
                                mInterstitial = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }
                        }
                );
                mInterstitial.show(activity);
            } else {
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    @Override
    public void loadNative(Activity activity, ViewGroup container, String adUnitId, String style, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        List<NativeAd.NativeAdType> adTypes = Arrays.asList(NativeAd.NativeAdType.NATIVE);
        NativeAdRequest adRequest = new NativeAdRequest.Builder(adUnitId, adTypes).build();

        NativeAdLoader.load(adRequest, new NativeAdLoaderCallback() {
            @Override
            public void onNativeAdLoaded(@NonNull NativeAd nativeAd) {
                activity.runOnUiThread(() -> {
                    int layoutResId;
                    String safeStyle = (style != null) ? style.toLowerCase() : "medium";

                    switch (safeStyle) {
                        case "small":
                            layoutResId = R.layout.admob_native_small;
                            break;
                        case "large":
                            layoutResId = R.layout.admob_native_large;
                            break;
                        case "medium":
                        default:
                            layoutResId = R.layout.admob_native_medium;
                            break;
                    }

                    View adView = activity.getLayoutInflater().inflate(layoutResId, null);

                    int marginLeft = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_left);
                    int marginTop = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_top);
                    int marginRight = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_right);
                    int marginBottom = activity.getResources().getDimensionPixelSize(R.dimen.ads_native_margin_bottom);

                    ViewGroup.MarginLayoutParams params = new ViewGroup.MarginLayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                    );
                    params.setMargins(marginLeft, marginTop, marginRight, marginBottom);
                    adView.setLayoutParams(params);

                    populateNativeAdView(nativeAd, (NativeAdView) adView);

                    container.removeAllViews();
                    container.addView(adView);

                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }

            @Override
            public void onAdLoadingCompleted() {}
        });
    }

    private void populateNativeAdView(NativeAd nativeAd, NativeAdView adView) {
        adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
        adView.setBodyView(adView.findViewById(R.id.ad_body));
        adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
        adView.setIconView(adView.findViewById(R.id.ad_app_icon));

        if (adView.getHeadlineView() != null) {
            ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());
        }

        if (adView.getBodyView() != null) {
            if (nativeAd.getBody() == null) {
                adView.getBodyView().setVisibility(View.INVISIBLE);
            } else {
                adView.getBodyView().setVisibility(View.VISIBLE);
                ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
            }
        }

        if (adView.getCallToActionView() != null) {
            if (nativeAd.getCallToAction() == null) {
                adView.getCallToActionView().setVisibility(View.INVISIBLE);
            } else {
                adView.getCallToActionView().setVisibility(View.VISIBLE);
                ((Button) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
            }
        }

        if (adView.getIconView() != null) {
            if (nativeAd.getIcon() == null) {
                adView.getIconView().setVisibility(View.GONE);
            } else {
                ((ImageView) adView.getIconView()).setImageDrawable(nativeAd.getIcon().getDrawable());
                adView.getIconView().setVisibility(View.VISIBLE);
            }
        }

        MediaView mediaView = adView.findViewById(R.id.ad_media);
        adView.registerNativeAd(nativeAd, mediaView);
    }

    @Override
    public void loadRewarded(Activity activity, String adUnitId, AdInternalListener listener) {
        if (adUnitId == null || adUnitId.equals("0") || adUnitId.isEmpty()) {
            if (listener != null) listener.onAdFailed();
            return;
        }

        AdRequest adRequest = new AdRequest.Builder(adUnitId).build();
        RewardedAd.load(adRequest, new AdLoadCallback<RewardedAd>() {
            @Override
            public void onAdLoaded(@NonNull RewardedAd rewardedAd) {
                mRewarded = rewardedAd;
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                mRewarded = null;
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    @Override
    public void showRewarded(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (mRewarded != null) {
                mRewarded.setAdEventCallback(
                        new RewardedAdEventCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                mRewarded = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError fullScreenContentError) {
                                mRewarded = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }
                        }
                );

                mRewarded.show(activity, rewardItem -> {
                    activity.runOnUiThread(() -> {
                        if (listener != null) {
                            listener.onRewardEarned();
                            Log.d("AdMob", "Reward earned: " + rewardItem.getAmount());
                        }
                    });
                });
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

        AdRequest adRequest = new AdRequest.Builder(adUnitId).build();
        AppOpenAd.load(adRequest, new AdLoadCallback<AppOpenAd>() {
            @Override
            public void onAdLoaded(@NonNull AppOpenAd ad) {
                mAppOpen = ad;
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdLoaded();
                });
            }

            @Override
            public void onAdFailedToLoad(@NonNull LoadAdError e) {
                mAppOpen = null;
                activity.runOnUiThread(() -> {
                    if (listener != null) listener.onAdFailed();
                });
            }
        });
    }

    @Override
    public void showAppOpen(Activity activity, AdInternalListener listener) {
        activity.runOnUiThread(() -> {
            if (mAppOpen != null) {
                mAppOpen.setAdEventCallback(
                        new AppOpenAdEventCallback() {
                            @Override
                            public void onAdDismissedFullScreenContent() {
                                mAppOpen = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }

                            @Override
                            public void onAdFailedToShowFullScreenContent(@NonNull FullScreenContentError fullScreenContentError) {
                                mAppOpen = null;
                                activity.runOnUiThread(() -> {
                                    if (listener != null) listener.onAdDismissed();
                                });
                            }
                        });
                mAppOpen.show(activity);
            } else {
                if (listener != null) listener.onAdDismissed();
            }
        });
    }

    @Override
    public void showPrivacyOptions(Activity activity) {
        AdMobGdpr adMobGdpr = new AdMobGdpr(activity);
        if (adMobGdpr.isPrivacyOptionsRequired()) {
            adMobGdpr.showPrivacyOptionsForm(activity, formError -> {
                if (formError != null) {
                    Log.w("AdMobProvider", "Error showing privacy options form: " + formError.getMessage());
                }
            });
        }
    }

    @Override
    public boolean isPrivacyOptionsRequired(Activity activity) {
        AdMobGdpr adMobGdpr = new AdMobGdpr(activity);
        return adMobGdpr.isPrivacyOptionsRequired();
    }
}