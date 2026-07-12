package com.app.mobileadssdk;

import android.content.Context;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;

import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Response;
import okhttp3.ResponseBody;

public class AssetInterceptor implements Interceptor {

    Context context;

    public AssetInterceptor(Context context) {
        this.context = context;
    }

    @NonNull
    @Override
    public Response intercept(Chain chain) throws IOException {
        String jsonString = loadStringFromAssets();
        MediaType mediaType = MediaType.parse("application/json");
        ResponseBody body = ResponseBody.create(jsonString, mediaType);
        return new Response.Builder()
                .code(200)
                .message("OK")
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .addHeader("content-type", "application/json")
                .body(body)
                .build();
    }

    private String loadStringFromAssets() throws IOException {
        InputStream is = context.getAssets().open("ads.json");
        int size = is.available();
        byte[] buffer = new byte[size];
        is.read(buffer);
        is.close();
        return new String(buffer, "UTF-8");
    }
}