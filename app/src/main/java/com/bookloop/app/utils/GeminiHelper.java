package com.bookloop.app.utils;

import com.bookloop.app.BuildConfig;
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

/**
 * GeminiHelper — Integrates with the Google Gemini Vision API to provide
 * AI-based textbook price suggestions.
 *
 * Purpose: Analyze a textbook cover image along with seller-provided details
 * to recommend a fair second-hand selling price range.
 *
 * How the AI works:
 * - Sends the textbook image (base64-encoded) + structured text prompt to
 * the Gemini 1.5 Flash model via the Generative Language REST API.
 * - Gemini analyzes visible condition indicators (cover damage, stains, wear)
 * combined with metadata (original price, edition, subject) to return a
 * condition summary and a recommended LKR price range.
 *
 * Inputs: Bitmap (book image), originalPrice, edition, subject, condition
 * Outputs: String containing "CONDITION: ..." and "PRICE RANGE: Rs. X – Rs. Y"
 */
public class GeminiHelper {

    private static final String TAG = "GeminiHelper";

    // *** IMPORTANT: Replace with your actual Gemini API key from https://aistudio.google.com ***
    private static final String API_KEY = BuildConfig.GEMINI_API_KEY;

    // Using gemini-2.0-flash — current stable model
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    public interface GeminiCallback {
        void onSuccess(String result);

        void onError(String errorMessage);
    }

    private static final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    /**
     * Sends the book image and details to Gemini for price suggestion.
     *
     * API Integration Process:
     * 1. Compress and base64-encode the bitmap
     * 2. Build a multimodal JSON request (image + text parts)
     * 3. POST to Gemini REST endpoint
     * 4. Parse the text response and return via callback
     *
     * @param bookImage     Bitmap of the textbook cover
     * @param originalPrice Original price paid (as String, e.g. "2500")
     * @param edition       Edition number (e.g. "3rd Edition")
     * @param subject       Subject or module name
     * @param condition     Seller's self-assessed condition
     * @param callback      Callback for success/failure results
     */
    public static void getPriceSuggestion(Bitmap bookImage,
            String originalPrice,
            String edition,
            String subject,
            String condition,
            GeminiCallback callback) {

        // Step 1: Compress bitmap → base64
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bookImage.compress(Bitmap.CompressFormat.JPEG, 75, baos);
        String base64Image = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        // Step 2: Build the prompt
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

        // Step 3: Build JSON request body
        try {
            JSONObject requestJson = new JSONObject();
            JSONArray contents = new JSONArray();
            JSONObject content = new JSONObject();
            JSONArray parts = new JSONArray();

            // Text part
            JSONObject textPart = new JSONObject();
            textPart.put("text", textPrompt);
            parts.put(textPart);

            // Image part
            JSONObject imagePart = new JSONObject();
            JSONObject inlineData = new JSONObject();
            inlineData.put("mime_type", "image/jpeg");
            inlineData.put("data", base64Image);
            imagePart.put("inline_data", inlineData);
            parts.put(imagePart);

            content.put("parts", parts);
            contents.put(content);
            requestJson.put("contents", contents);

            // Safety and generation config
            JSONObject generationConfig = new JSONObject();
            generationConfig.put("temperature", 0.3);
            generationConfig.put("maxOutputTokens", 256);
            requestJson.put("generationConfig", generationConfig);

            // Step 4: Execute HTTP request
            // AIza format keys work via the URL parameter or x-goog-api-key header
            Request request = new Request.Builder()
                    .url(API_URL + "?key=" + API_KEY)
                    .addHeader("Content-Type", "application/json")
                    .addHeader("x-goog-api-key", API_KEY)
                    .post(RequestBody.create(
                            requestJson.toString(),
                            MediaType.parse("application/json; charset=utf-8")))
                    .build();

            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    Log.e(TAG, "Gemini API call failed", e);
                    callback.onError("Network error: " + e.getMessage());
                }

                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (response.isSuccessful()) {
                        try {
                            JSONObject json = new JSONObject(responseBody);
                            String text = json
                                    .getJSONArray("candidates")
                                    .getJSONObject(0)
                                    .getJSONObject("content")
                                    .getJSONArray("parts")
                                    .getJSONObject(0)
                                    .getString("text");
                            callback.onSuccess(text.trim());
                        } catch (Exception e) {
                            Log.e(TAG, "Parse error: " + responseBody, e);
                            callback.onError("Could not parse AI response. Please try again.");
                        }
                    } else {
                        Log.e(TAG, "Gemini API error " + response.code() + ": " + responseBody);
                        String errorMsg;
                        switch (response.code()) {
                            case 429: errorMsg = "Rate limit reached. Please wait a moment and try again."; break;
                            case 403: errorMsg = "API key permission denied. Check your key."; break;
                            case 404: errorMsg = "AI model not found. Check API configuration."; break;
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
