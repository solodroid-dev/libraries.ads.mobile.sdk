package com.app.mobileadssdk;

import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

// Import AdsManager dari modul library Anda
import com.solodroid.ads.core.AdsManager;

public class MainActivity extends AppCompatActivity {

    private FrameLayout bannerContainer;
    private FrameLayout nativeContainer;
    private Button btnInterstitial;
    private Button btnRewarded;

    private AdsManager adsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // 1. Dapatkan instance dari AdsManager
        adsManager = AdsManager.getInstance(this);

        // 2. Hubungkan View (Binding)
        bannerContainer = findViewById(R.id.banner_container);
        nativeContainer = findViewById(R.id.native_container);
        btnInterstitial = findViewById(R.id.btn_interstitial);
        btnRewarded = findViewById(R.id.btn_rewarded);

        // 3. Muat Iklan Banner dan Native saat Activity dibuka
        // Banner akan langsung masuk ke bannerContainer
        adsManager.loadBanner(this, bannerContainer);

        // Native ad dipanggil menggunakan parameter style "medium" (Bisa diganti "small" / "large")
        adsManager.loadNative(this, nativeContainer, "medium");

        // 4. Pre-load iklan Interstitial dan Rewarded agar siap ditampilkan saat tombol ditekan
        adsManager.loadInterstitial(this);
        adsManager.loadRewarded(this);

        // 5. Aksi saat tombol Interstitial ditekan
        btnInterstitial.setOnClickListener(v -> {
            // boolean useInterval = false -> Iklan akan diabaikan pengaturan intervalnya (selalu tampil)
            adsManager.showInterstitial(this, true, () -> {
                // Callback ini dipanggil saat iklan ditutup, atau jika iklan gagal/tidak tersedia
                Toast.makeText(MainActivity.this, "Aksi setelah Interstitial selesai", Toast.LENGTH_SHORT).show();
            });
        });

        // 6. Aksi saat tombol Rewarded ditekan
        btnRewarded.setOnClickListener(v -> {
            adsManager.showRewarded(this, new AdsManager.RewardFinishedListener() {
                @Override
                public void onRewardEarned() {
                    Toast.makeText(MainActivity.this, "Selamat! Anda mendapatkan Reward.", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onAdClosed() {
                    Toast.makeText(MainActivity.this, "Iklan ditutup lebih awal, Reward dibatalkan.", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onAdFailed() {
                    Toast.makeText(MainActivity.this, "Iklan belum siap, coba lagi nanti.", Toast.LENGTH_SHORT).show();
                }
            });
        });
    }
}