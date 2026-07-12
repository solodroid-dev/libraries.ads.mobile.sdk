package com.app.mobileadssdk;

import com.google.gson.annotations.SerializedName;
import com.solodroid.ads.core.models.AdModel;

public class AdResponse {

    @SerializedName("ads")
    private AdModel ads;

    public AdModel getAds() {
        return ads;
    }
}