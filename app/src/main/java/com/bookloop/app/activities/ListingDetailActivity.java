package com.bookloop.app.activities;

import android.content.Intent;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.bookloop.app.databinding.ActivityListingDetailBinding;
import com.bookloop.app.models.Book;
import com.bookloop.app.utils.FirebaseHelper;
import com.bumptech.glide.Glide;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class ListingDetailActivity extends AppCompatActivity {

    private ActivityListingDetailBinding binding;
    private Book currentBook;
    private String bookId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityListingDetailBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        bookId = getIntent().getStringExtra("book_id");
        if (bookId == null) { finish(); return; }

        binding.btnBack.setOnClickListener(v -> finish());
        loadBookDetails();
    }

    private void loadBookDetails() {
        binding.progressBar.setVisibility(View.VISIBLE);

        FirebaseFirestore.getInstance()
                .collection("books")
                .document(bookId)
                .get()
                .addOnSuccessListener(doc -> {
                    binding.progressBar.setVisibility(View.GONE);
                    currentBook = doc.toObject(Book.class);
                    if (currentBook != null) {
                        currentBook.setId(doc.getId());
                        populateUI();
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load listing", Toast.LENGTH_SHORT).show();
                    finish();
                });
    }

    private void populateUI() {
        binding.tvTitle.setText(currentBook.getTitle());
        binding.tvSubject.setText(currentBook.getSubject());
        binding.tvEdition.setText("Edition: " + currentBook.getEdition());
        binding.tvCondition.setText(currentBook.getCondition());
        binding.tvOriginalPrice.setText("Original: Rs. " + String.format("%.0f", currentBook.getOriginalPrice()));
        binding.tvOriginalPrice.setPaintFlags(binding.tvOriginalPrice.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        binding.tvSellingPrice.setText("Rs. " + String.format("%.0f", currentBook.getSellingPrice()));
        binding.tvSellerName.setText(currentBook.getSellerName());
        binding.tvSellerEmail.setText(currentBook.getSellerEmail());
        binding.tvDescription.setText(currentBook.getDescription() != null && !currentBook.getDescription().isEmpty()
                ? currentBook.getDescription() : "No additional description provided.");

        // Condition badge color
        setConditionBadgeColor(currentBook.getCondition());

        // Status badge
        String status = currentBook.getStatus() != null ? currentBook.getStatus() : "available";
        binding.tvStatus.setText(status.substring(0, 1).toUpperCase() + status.substring(1));
        binding.tvStatus.setBackgroundResource(
                status.equals("available") ? com.bookloop.app.R.drawable.badge_available :
                status.equals("reserved") ? com.bookloop.app.R.drawable.badge_reserved :
                        com.bookloop.app.R.drawable.badge_sold);

        // AI suggestion
        if (currentBook.getAiPriceSuggestion() != null && !currentBook.getAiPriceSuggestion().isEmpty()) {
            binding.cardAiSuggestion.setVisibility(View.VISIBLE);
            binding.tvAiSuggestion.setText(currentBook.getAiPriceSuggestion());
        } else {
            binding.cardAiSuggestion.setVisibility(View.GONE);
        }

        // Book image
        if (currentBook.getImageUrl() != null && !currentBook.getImageUrl().isEmpty()) {
            Glide.with(this).load(currentBook.getImageUrl())
                    .placeholder(com.bookloop.app.R.drawable.ic_book_placeholder)
                    .into(binding.ivBookCover);
        }

        // Contact buttons
        binding.btnCallSeller.setOnClickListener(v -> callSeller());
        binding.btnEmailSeller.setOnClickListener(v -> emailSeller());
        binding.btnWhatsapp.setOnClickListener(v -> whatsAppSeller());

        // Show edit/delete if current user is the seller
        String currentUid = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getUid() : "";
        if (currentUid.equals(currentBook.getSellerId())) {
            binding.layoutSellerActions.setVisibility(View.VISIBLE);
            binding.btnMarkSold.setOnClickListener(v -> updateStatus("sold"));
            binding.btnMarkReserved.setOnClickListener(v -> updateStatus("reserved"));
            binding.btnMarkAvailable.setOnClickListener(v -> updateStatus("available"));
            binding.btnDeleteListing.setOnClickListener(v -> confirmDelete());
        } else {
            binding.layoutSellerActions.setVisibility(View.GONE);
        }
    }

    private void setConditionBadgeColor(String condition) {
        int bgRes;
        switch (condition) {
            case "Excellent": bgRes = com.bookloop.app.R.drawable.badge_excellent; break;
            case "Good":      bgRes = com.bookloop.app.R.drawable.badge_good; break;
            case "Fair":      bgRes = com.bookloop.app.R.drawable.badge_fair; break;
            default:          bgRes = com.bookloop.app.R.drawable.badge_poor; break;
        }
        binding.tvCondition.setBackgroundResource(bgRes);
    }

    private void callSeller() {
        String phone = currentBook.getSellerPhone();
        if (phone == null || phone.isEmpty()) {
            Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show(); return;
        }
        startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + phone)));
    }

    private void emailSeller() {
        String email = currentBook.getSellerEmail();
        if (email == null || email.isEmpty()) {
            Toast.makeText(this, "No email available", Toast.LENGTH_SHORT).show(); return;
        }
        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(Uri.parse("mailto:" + email));
        intent.putExtra(Intent.EXTRA_SUBJECT, "Regarding: " + currentBook.getTitle());
        startActivity(Intent.createChooser(intent, "Send email"));
    }

    private void whatsAppSeller() {
        String phone = currentBook.getSellerPhone();
        if (phone == null || phone.isEmpty()) {
            Toast.makeText(this, "No phone number available", Toast.LENGTH_SHORT).show(); return;
        }
        try {
            String url = "https://api.whatsapp.com/send?phone=94" + phone.replaceFirst("^0", "")
                    + "&text=Hi! I'm interested in your textbook listing: " + currentBook.getTitle();
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(this, "WhatsApp is not installed", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateStatus(String newStatus) {
        FirebaseHelper.updateBookStatus(bookId, newStatus, new FirebaseHelper.OnCompleteCallback() {
            @Override public void onSuccess() {
                Toast.makeText(ListingDetailActivity.this,
                        "Status updated to " + newStatus, Toast.LENGTH_SHORT).show();
                loadBookDetails();
            }
            @Override public void onError(String error) {
                Toast.makeText(ListingDetailActivity.this,
                        "Update failed: " + error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete Listing")
                .setMessage("Are you sure you want to delete this listing? This cannot be undone.")
                .setPositiveButton("Delete", (d, w) -> {
                    FirebaseHelper.deleteBook(bookId, new FirebaseHelper.OnCompleteCallback() {
                        @Override public void onSuccess() {
                            Toast.makeText(ListingDetailActivity.this,
                                    "Listing deleted", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                        @Override public void onError(String error) {
                            Toast.makeText(ListingDetailActivity.this,
                                    "Delete failed: " + error, Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}
