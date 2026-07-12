package com.app.mobileadssdk;

import retrofit2.Call;
import retrofit2.http.GET;

public interface ApiService {
    // Mengarah ke path setelah Base URL
    @GET("json/ads.json")
    Call<AdResponse> getAdsConfig();
}