package com.bookloop.app.utils;

import android.graphics.Bitmap;
import android.util.Base64;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiHelper {

    private static final String TAG = "GeminiHelper";
    private static final String API_KEY = "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";
    private static final String API_URL = "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL = "meta-llama/llama-4-scout-17b-16e-instruct";

    public interface GeminiCallback {
        void onSuccess(String result);
        void onError(String errorMessage);
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    public static void getPriceSuggestion(Bitmap bookImage,
                                          String originalPrice,
                                          String edition,
                                          String subject,
                                          String condition,
                                          GeminiCallback callback) {

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bookImage.compress(Bitmap.CompressFormat.JPEG, 75, baos);
        String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        String textPrompt = "You are a second-hand textbook pricing advisor for Sri Lankan university students.\n\n"
                + "Analyze the provided textbook cover image and the details below to suggest a fair resale price in Sri Lankan Rupees (LKR).\n\n"
                + "Book Details Provided by Seller:\n"
                + "- Subject/Module: " + subject + "\n"
                + "- Edition: " + edition + "\n"
                + "- Original Price: Rs. " + originalPrice + "\n"
                + "- Seller's Condition Assessment: " + condition + "\n\n"
                + "Based on the cover image (check for visible damage, stains, tears, fading, bending) "
                + "and the provided details, give:\n\n"
                + "1. A brief condition assessment from what you can see\n"
                + "2. A recommended resale price range in LKR\n\n"
                + "Respond in EXACTLY this format (nothing else):\n"
                + "CONDITION: [brief assessment of visible book condition]\n"
                + "PRICE RANGE: Rs. [minimum] - Rs. [maximum]";

        try {
            // Build image content
            JSONObject imageUrl = new JSONObject();
            imageUrl.put("url", "data:image/jpeg;base64," + base64Image);

            JSONObject imageContent = new JSONObject();
            imageContent.put("type", "image_url");
            imageContent.put("image_url", imageUrl);

            // Build text content
            JSONObject textContent = new JSONObject();
            textContent.put("type", "text");
            textContent.put("text", textPrompt);

            // Combine into message
            JSONArray contentArray = new JSONArray();
            contentArray.put(imageContent);
            contentArray.put(textContent);

            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", contentArray);

            JSONArray messages = new JSONArray();
            messages.put(userMessage);

            // Build request body
            JSONObject requestJson = new JSONObject();
            requestJson.put("model", MODEL);
            requestJson.put("messages", messages);
            requestJson.put("max_tokens", 256);
            requestJson.put("temperature", 0.3);

            Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("Authorization", "Bearer " + API_KEY)
                    .post(RequestBody.create(
                            requestJson.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Groq API call failed", e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        try {
                            JSONObject json = new JSONObject(responseBody);
                            String text = json
                                    .getJSONArray("choices")
                                    .getJSONObject(0)
                                    .getJSONObject("message")
                                    .getString("content");
                            callback.onSuccess(text.trim());
                        } catch (Exception e) {
                            Log.e(TAG, "Parse error: " + responseBody, e);
                            callback.onError("Could not parse AI response. Please try again.");
                        }
                    } else {
                        Log.e(TAG, "Groq API error " + response.code() + ": " + responseBody);
                        String errorMsg;
                        switch (response.code()) {
                            case 429: errorMsg = "Rate limit reached. Please wait and try again."; break;
                            case 403: errorMsg = "API key permission denied."; break;
                            case 404: errorMsg = "AI model not found."; break;
                            default:  errorMsg = "AI service error (code " + response.code() + "). Try again."; break;
                        }
                        callback.onError(errorMsg);
                    }
                }
            });

        } catch (Exception e) {
            Log.e(TAG, "Request build error", e);
            callback.onError("Error preparing request: " + e.getMessage());
        }
    }
}