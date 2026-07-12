package com.bookloop.app.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
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
import androidx.core.content.FileProvider;

import com.bookloop.app.databinding.ActivityAddListingBinding;
import com.bookloop.app.models.AppUser;
import com.bookloop.app.models.Book;
import com.bookloop.app.utils.FirebaseHelper;
import com.bookloop.app.utils.GeminiHelper;
import com.google.firebase.auth.FirebaseAuth;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class AddListingActivity extends AppCompatActivity {

    private ActivityAddListingBinding binding;
    private Uri selectedImageUri;   // Always set when an image is chosen (gallery or camera)
    private Bitmap selectedBitmap;  // Decoded bitmap used for AI price suggestion
    private Uri cameraPhotoUri;     // Temp URI for the camera output file
    private AppUser currentUser;

    private final String[] CONDITIONS = {"Excellent", "Good", "Fair", "Poor"};

    // ── Activity result launchers ──────────────────────────────────────────────

    /** Gallery picker — returns a content URI directly */
    private final ActivityResultLauncher<Intent> galleryLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedImageUri = result.getData().getData();
                    loadPreviewImage();
                }
            });

    /**
     * Camera launcher — photo is written to {@link #cameraPhotoUri} (full resolution).
     * On success we just promote cameraPhotoUri → selectedImageUri so the upload
     * path in publishListing() works identically to the gallery path.
     */
    private final ActivityResultLauncher<Intent> cameraLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && cameraPhotoUri != null) {
                    selectedImageUri = cameraPhotoUri;
                    loadPreviewImage();
                }
            });

    /** Gallery storage permission request */
    private final ActivityResultLauncher<String[]> galleryPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), result -> {
                String perm = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                        ? Manifest.permission.READ_MEDIA_IMAGES
                        : Manifest.permission.READ_EXTERNAL_STORAGE;
                Boolean granted = result.getOrDefault(perm, false);
                if (Boolean.TRUE.equals(granted)) openGallery();
                else Toast.makeText(this, "Storage permission required", Toast.LENGTH_SHORT).show();
            });

    /** Camera permission request */
    private final ActivityResultLauncher<String> cameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) openCamera();
                else Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show();
            });

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAddListingBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupConditionSpinner();
        loadCurrentUser();

        binding.ivBookCover.setOnClickListener(v -> showImagePickerDialog());
        binding.tvImageHint.setOnClickListener(v -> showImagePickerDialog());
        binding.btnGetAiSuggestion.setOnClickListener(v -> getAiPriceSuggestion());
        binding.btnPublishListing.setOnClickListener(v -> publishListing());
        binding.btnBack.setOnClickListener(v -> finish());
    }

    // ── Setup ──────────────────────────────────────────────────────────────────

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

    // ── Image picker ───────────────────────────────────────────────────────────

    private void showImagePickerDialog() {
        String[] options = {"📷  Take Photo", "🖼️  Choose from Gallery"};
        new AlertDialog.Builder(this)
                .setTitle("Select Book Image")
                .setItems(options, (dialog, which) -> {
                    if (which == 0) checkCameraPermissionAndOpen();
                    else            checkGalleryPermissionAndOpen();
                })
                .show();
    }

    private void checkCameraPermissionAndOpen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            openCamera();
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }

    private void checkGalleryPermissionAndOpen() {
        String permission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;
        if (ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED) {
            openGallery();
        } else {
            galleryPermissionLauncher.launch(new String[]{permission});
        }
    }

    /**
     * Opens the camera with a full-resolution output URI.
     * The photo is saved to a temp file in getCacheDir() via FileProvider so it
     * can be shared with the system camera app (required on Android 7+).
     */
    private void openCamera() {
        try {
            // Create a temp file to hold the full-res photo
            File photoFile = File.createTempFile("book_cover_", ".jpg", getCacheDir());
            cameraPhotoUri = FileProvider.getUriForFile(
                    this,
                    getPackageName() + ".fileprovider",
                    photoFile
            );

            Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri);
            // Grant the camera app write access to our URI
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            // Only launch if a camera app is available
            if (intent.resolveActivity(getPackageManager()) != null) {
                cameraLauncher.launch(intent);
            } else {
                Toast.makeText(this, "No camera app found", Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Toast.makeText(this, "Could not create temp file for camera", Toast.LENGTH_SHORT).show();
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    /**
     * Decodes {@link #selectedImageUri} into {@link #selectedBitmap} and shows a
     * preview. Uses BitmapFactory with an InputStream to avoid the deprecated
     * MediaStore.Images.Media.getBitmap() API.
     */
    private void loadPreviewImage() {
        if (selectedImageUri == null) return;
        binding.tvImageHint.setVisibility(View.GONE);
        try (InputStream is = getContentResolver().openInputStream(selectedImageUri)) {
            selectedBitmap = BitmapFactory.decodeStream(is);
            binding.ivBookCover.setImageBitmap(selectedBitmap);
        } catch (IOException e) {
            Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show();
        }
    }

    // ── AI Price Suggestion ────────────────────────────────────────────────────

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
        try {
            String lower = aiResult.toLowerCase();
            int priceIdx = lower.indexOf("price range:");
            if (priceIdx >= 0) {
                String sub = aiResult.substring(priceIdx + 12).trim();
                sub = sub.replaceAll("[Rrs.\\s]", "").split("-")[0].replaceAll("[^0-9]", "").trim();
                if (!sub.isEmpty()) binding.etSellingPrice.setText(sub);
            }
        } catch (Exception ignored) {}
    }

    private void setAiLoading(boolean loading) {
        binding.btnGetAiSuggestion.setEnabled(!loading);
        binding.aiProgressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        binding.btnGetAiSuggestion.setText(loading ? "Analyzing…" : "🤖  Get AI Price Suggestion");
    }

    // ── Publish Listing ────────────────────────────────────────────────────────

    private void publishListing() {
        String title        = binding.etTitle.getText().toString().trim();
        String subject      = binding.etSubject.getText().toString().trim();
        String edition      = binding.etEdition.getText().toString().trim();
        String condition    = binding.spinnerCondition.getSelectedItem().toString();
        String origPriceStr = binding.etOriginalPrice.getText().toString().trim();
        String sellPriceStr = binding.etSellingPrice.getText().toString().trim();
        String description  = binding.etDescription.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(title))        { binding.etTitle.setError("Title required");                binding.etTitle.requestFocus();          return; }
        if (TextUtils.isEmpty(subject))      { binding.etSubject.setError("Subject required");            binding.etSubject.requestFocus();        return; }
        if (TextUtils.isEmpty(origPriceStr)) { binding.etOriginalPrice.setError("Original price required"); binding.etOriginalPrice.requestFocus(); return; }
        if (TextUtils.isEmpty(sellPriceStr)) { binding.etSellingPrice.setError("Selling price required"); binding.etSellingPrice.requestFocus();   return; }
        if (selectedImageUri == null) {
            Toast.makeText(this, "Please add a book cover photo", Toast.LENGTH_SHORT).show();
            return;
        }

        double originalPrice, sellingPrice;
        try {
            originalPrice = Double.parseDouble(origPriceStr);
            sellingPrice  = Double.parseDouble(sellPriceStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid price format", Toast.LENGTH_SHORT).show();
            return;
        }

        setPublishLoading(true);

        String sellerName  = currentUser != null ? currentUser.getName()  : "Unknown";
        String sellerEmail = currentUser != null ? currentUser.getEmail() : "";
        String sellerPhone = currentUser != null ? currentUser.getPhone() : "";
        String sellerId    = FirebaseAuth.getInstance().getCurrentUser().getUid();

        // Upload image (both gallery URI and camera URI work the same way now)
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
    }

    private void saveBook(String title, String subject, String edition, String condition,
                          double originalPrice, double sellingPrice, String imageUrl,
                          String sellerId, String sellerName, String sellerEmail,
                          String sellerPhone, String description) {
        Book book = new Book(title, subject, edition, condition, originalPrice,
                sellingPrice, imageUrl, sellerId, sellerName, sellerEmail, sellerPhone, description);

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
        binding.btnPublishListing.setText(loading ? "Publishing…" : "Publish Listing");
    }
}
