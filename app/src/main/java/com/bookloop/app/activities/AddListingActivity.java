package com.bookloop.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.bookloop.app.databinding.ActivityAddListingBinding;
import com.bookloop.app.models.AppUser;
import com.bookloop.app.models.Book;
import com.bookloop.app.utils.FirebaseHelper;
import com.bookloop.app.utils.GeminiHelper;
import com.google.firebase.auth.FirebaseAuth;

import java.io.IOException;

public class AddListingActivity extends AppCompatActivity {

    private ActivityAddListingBinding binding;
    private Uri selectedImageUri;
    private Bitmap selectedBitmap;
    private AppUser currentUser;

    private final String[] CONDITIONS = {"Excellent", "Good", "Fair", "Poor"};

    // Image picker launchers
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    loadPreviewImage();
                }
            });

    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedBitmap = (Bitmap) result.getData().getExtras().get("data");
                    binding.ivBookCover.setImageBitmap(selectedBitmap);
                    binding.tvImageHint.setVisibility(View.GONE);
                }
            });

    private final ActivityResultLauncher<String[]> permissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                Boolean granted = result.getOrDefault(
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                                ? Manifest.permission.READ_MEDIA_IMAGES
                                : Manifest.permission.READ_EXTERNAL_STORAGE, false);
                if (Boolean.TRUE.equals(granted)) openGallery();
                else Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddListingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupConditionSpinner();
        loadCurrentUser();

        binding.ivBookCover.setOnClickListener(v -> showImagePickerDialog());
        binding.btnGetAiSuggestion.setOnClickListener(v -> getAiPriceSuggestion());
        binding.btnPublishListing.setOnClickListener(v -> publishListing());
        binding.btnBack.setOnClickListener(v -> finish());
    }

    private void setupConditionSpinner() {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, CONDITIONS);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        binding.spinnerCondition.setAdapter(adapter);
    }

    private void loadCurrentUser() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        FirebaseHelper.getCurrentUser(uid,
                user -> currentUser = user,
                e -> Toast.makeText(this, "Could not load profile", Toast.LENGTH_SHORT).show());
    }

    private void showImagePickerDialog() {
        String[] options = {"Take Photo", "Choose from Gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Select Book Image")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) openCamera();
                    else checkPermissionAndOpenGallery();
                })
                .show();
    }

    private void openCamera() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        cameraLauncher.launch(intent);
    }

    private void checkPermissionAndOpenGallery() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openGallery();
        } else {
            permissionLauncher.launch(new String[]{permission});
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void loadPreviewImage() {
        try {
            selectedBitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), selectedImageUri);
            binding.ivBookCover.setImageBitmap(selectedBitmap);
            binding.tvImageHint.setVisibility(View.GONE);
        } catch (IOException e) {
            Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * AI Price Suggestion — calls Gemini Vision API with image + book details.
     * Shows a loading state and populates the selling price field with the AI's recommendation.
     */
    private void getAiPriceSuggestion() {
        if (selectedBitmap == null) {
            Toast.makeText(this, "Please upload a book cover image first", Toast.LENGTH_SHORT).show();
            return;
        }
        String originalPrice = binding.etOriginalPrice.getText().toString().trim();
        String edition       = binding.etEdition.getText().toString().trim();
        String subject       = binding.etSubject.getText().toString().trim();
        String condition     = binding.spinnerCondition.getSelectedItem().toString();

        if (TextUtils.isEmpty(originalPrice)) {
            binding.etOriginalPrice.setError("Enter original price for AI suggestion");
            binding.etOriginalPrice.requestFocus();
            return;
        }

        setAiLoading(true);
        binding.tvAiResult.setVisibility(View.GONE);

        GeminiHelper.getPriceSuggestion(selectedBitmap, originalPrice, edition, subject, condition,
                new GeminiHelper.GeminiCallback() {
                    @Override
                    public void onSuccess(String result) {
                        runOnUiThread(() -> {
                            setAiLoading(false);
                            binding.tvAiResult.setVisibility(View.VISIBLE);
                            binding.tvAiResult.setText("🤖 AI Suggestion:\n" + result);

                            // Auto-fill selling price from AI suggestion
                            extractAndFillPrice(result);
                        });
                    }
                    @Override
                    public void onError(String errorMessage) {
                        runOnUiThread(() -> {
                            setAiLoading(false);
                            binding.tvAiResult.setVisibility(View.VISIBLE);
                            binding.tvAiResult.setText("⚠️ " + errorMessage);
                        });
                    }
                });
    }

    private void extractAndFillPrice(String aiResult) {
        // Try to extract the minimum price from "PRICE RANGE: Rs. X - Rs. Y"
        try {
            String lower = aiResult.toLowerCase();
            int priceIdx = lower.indexOf("price range:");
            if (priceIdx >= 0) {
                String sub = aiResult.substring(priceIdx + 12).trim();
                sub = sub.replaceAll("[Rrs.\\s]", "").split("-")[0].replaceAll("[^0-9]", "").trim();
                if (!sub.isEmpty()) {
                    binding.etSellingPrice.setText(sub);
                }
            }
        } catch (Exception ignored) {}
    }

    private void setAiLoading(boolean loading) {
        binding.btnGetAiSuggestion.setEnabled(!loading);
        binding.aiProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnGetAiSuggestion.setText(loading ? "Analyzing..." : "🤖 Get AI Price Suggestion");
    }

    private void publishListing() {
        String title        = binding.etTitle.getText().toString().trim();
        String subject      = binding.etSubject.getText().toString().trim();
        String edition      = binding.etEdition.getText().toString().trim();
        String condition    = binding.spinnerCondition.getSelectedItem().toString();
        String origPriceStr = binding.etOriginalPrice.getText().toString().trim();
        String sellPriceStr = binding.etSellingPrice.getText().toString().trim();
        String description  = binding.etDescription.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(title)) { binding.etTitle.setError("Title required"); binding.etTitle.requestFocus(); return; }
        if (TextUtils.isEmpty(subject)) { binding.etSubject.setError("Subject required"); binding.etSubject.requestFocus(); return; }
        if (TextUtils.isEmpty(origPriceStr)) { binding.etOriginalPrice.setError("Original price required"); binding.etOriginalPrice.requestFocus(); return; }
        if (TextUtils.isEmpty(sellPriceStr)) { binding.etSellingPrice.setError("Selling price required"); binding.etSellingPrice.requestFocus(); return; }
        if (selectedImageUri == null && selectedBitmap == null) {
            Toast.makeText(this, "Please add a book cover photo", Toast.LENGTH_SHORT).show(); return;
        }

        double originalPrice, sellingPrice;
        try {
            originalPrice = Double.parseDouble(origPriceStr);
            sellingPrice  = Double.parseDouble(sellPriceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid price format", Toast.LENGTH_SHORT).show(); return;
        }

        setPublishLoading(true);

        String sellerName  = currentUser != null ? currentUser.getName() : "Unknown";
        String sellerEmail = currentUser != null ? currentUser.getEmail() : "";
        String sellerPhone = currentUser != null ? currentUser.getPhone() : "";
        String sellerId    = FirebaseAuth.getInstance().getCurrentUser().getUid();

        if (selectedImageUri != null) {
            // Upload image to Firebase Storage, then save book
            FirebaseHelper.uploadBookImage(selectedImageUri, new FirebaseHelper.OnUploadCallback() {
                @Override
                public void onSuccess(String downloadUrl) {
                    saveBook(title, subject, edition, condition, originalPrice, sellingPrice,
                            downloadUrl, sellerId, sellerName, sellerEmail, sellerPhone, description);
                }
                @Override
                public void onError(String error) {
                    runOnUiThread(() -> {
                        setPublishLoading(false);
                        Toast.makeText(AddListingActivity.this,
                                "Image upload failed: " + error, Toast.LENGTH_LONG).show();
                    });
                }
                @Override
                public void onProgress(int percent) {
                    runOnUiThread(() -> binding.uploadProgressBar.setProgress(percent));
                }
            });
        } else {
            // No URI (came from camera), save without image URL for now
            saveBook(title, subject, edition, condition, originalPrice, sellingPrice,
                    "", sellerId, sellerName, sellerEmail, sellerPhone, description);
        }
    }

    private void saveBook(String title, String subject, String edition, String condition,
                          double originalPrice, double sellingPrice, String imageUrl,
                          String sellerId, String sellerName, String sellerEmail,
                          String sellerPhone, String description) {
        Book book = new Book(title, subject, edition, condition, originalPrice,
                sellingPrice, imageUrl, sellerId, sellerName, sellerEmail, sellerPhone, description);

        // Store AI suggestion if available
        String aiText = binding.tvAiResult.getText().toString();
        if (!aiText.isEmpty()) book.setAiPriceSuggestion(aiText);

        FirebaseHelper.addBook(book, new FirebaseHelper.OnCompleteCallback() {
            @Override
            public void onSuccess() {
                runOnUiThread(() -> {
                    setPublishLoading(false);
                    Toast.makeText(AddListingActivity.this,
                            "Listing published! 🎉", Toast.LENGTH_LONG).show();
                    finish();
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    setPublishLoading(false);
                    Toast.makeText(AddListingActivity.this,
                            "Failed to publish: " + error, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void setPublishLoading(boolean loading) {
        binding.btnPublishListing.setEnabled(!loading);
        binding.uploadProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnPublishListing.setText(loading ? "Publishing..." : "Publish Listing");
    }
}
