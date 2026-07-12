package com.app.mobileadssdk;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.solodroid.ads.core.AdInternalListener;
import com.solodroid.ads.core.AdsManager;
import com.solodroid.ads.core.utils.AdsPrefManager;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private AdsPrefManager adsPrefManager;
    private boolean isNavigationProcessed = false;
    private final Handler timeoutHandler = new Handler(Looper.getMainLooper());

    // PERBAIKAN 1: Jadikan Runnable eksplisit agar bisa dibatalkan dengan akurat 100%
    private final Runnable navigationRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d("SplashActivity", "Timeout 5 detik tercapai, lanjut ke Main.");
            navigateToMain();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash); // Pastikan layout ini ada

        adsPrefManager = new AdsPrefManager(this);

        fetchAdsConfig();
    }

    private void fetchAdsConfig() {
        ApiService apiService = ApiClient.getClient(this).create(ApiService.class);
        Call<AdResponse> call = apiService.getAdsConfig();

        call.enqueue(new Callback<AdResponse>() {
            @Override
            public void onResponse(@NonNull Call<AdResponse> call, @NonNull Response<AdResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    AdResponse adResponse = response.body();
                    adsPrefManager.saveAdsData(adResponse.getAds());
                    handleAdsAndNavigation();
                    Log.d("SplashActivity", "onResponse success with ad status: " + adResponse.getAds().isAdStatus());
                } else {
                    Log.e("SplashActivity", "Response server error.");
                    proceedWithOfflineData();
                }
            }

            @Override
            public void onFailure(@NonNull Call<AdResponse> call, @NonNull Throwable t) {
                Log.e("SplashActivity", "Gagal koneksi: " + t.getMessage());
                proceedWithOfflineData();
            }
        });
    }

    private void proceedWithOfflineData() {
        if (adsPrefManager.getAdsData() != null) {
            Log.d("SplashActivity", "Menggunakan data cache offline.");
            handleAdsAndNavigation();
        } else {
            Log.d("SplashActivity", "Tidak ada cache, langsung masuk ke Main.");
            navigateToMain();
        }
    }

    private void handleAdsAndNavigation() {
        AdsManager.getInstance(this).init(this, new AdsManager.InitializationListener() {
            @Override
            public void onInitComplete() {
                AdsManager.getInstance(SplashActivity.this).loadInterstitial(SplashActivity.this);
                AdsManager.getInstance(SplashActivity.this).loadRewarded(SplashActivity.this);

                // PERBAIKAN 2: Gunakan runnable eksplisit untuk hitungan mundur 5 detik
                timeoutHandler.postDelayed(navigationRunnable, 5000);

                AdsManager.getInstance(SplashActivity.this).loadAppOpen(SplashActivity.this, new AdInternalListener() {
                    @Override
                    public void onAdLoaded() {
                        if (!isNavigationProcessed) {
                            // PERBAIKAN 3: Hentikan timer secara spesifik
                            timeoutHandler.removeCallbacks(navigationRunnable);
                            Log.d("SplashActivity", "App Open berhasil dimuat, timer dibatalkan.");

                            AdsManager.getInstance(SplashActivity.this).showAppOpen(
                                    SplashActivity.this,
                                    new AdsManager.AdFinishedListener() {
                                        @Override
                                        public void onFinished() {
                                            Log.d("SplashActivity", "App Open ditutup.");
                                            navigateToMain(); // Baru panggil Main saat iklan ditutup
                                        }
                                    }
                            );
                        }
                    }

                    @Override
                    public void onAdFailed() {
                        Log.e("SplashActivity", "App Open gagal dimuat.");
                        navigateToMain();
                    }
                });
            }
        });
    }

    // PERBAIKAN 4: Tambahkan synchronized untuk mencegah Activity terbuka ganda
    private synchronized void navigateToMain() {
        if (!isNavigationProcessed) {
            isNavigationProcessed = true;

            // Hapus timer lagi untuk berjaga-jaga
            timeoutHandler.removeCallbacks(navigationRunnable);

            Intent intent = new Intent(SplashActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        timeoutHandler.removeCallbacks(navigationRunnable);
        super.onDestroy();
    }
}