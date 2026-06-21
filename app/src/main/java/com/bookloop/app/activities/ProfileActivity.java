package com.bookloop.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bookloop.app.adapters.BookAdapter;
import com.bookloop.app.databinding.ActivityProfileBinding;
import com.bookloop.app.models.AppUser;
import com.bookloop.app.models.Book;
import com.bookloop.app.utils.FirebaseHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ProfileActivity extends AppCompatActivity {

    private ActivityProfileBinding binding;
    private BookAdapter adapter;
    private List<Book> myBooks = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnLogout.setOnClickListener(v -> confirmLogout());
        binding.btnAddFirst.setOnClickListener(v -> {
            startActivity(new Intent(this, AddListingActivity.class));
        });

        setupRecyclerView();
        loadUserProfile();
        loadMyListings();
    }

    private void setupRecyclerView() {
        adapter = new BookAdapter(myBooks, book -> {
            Intent intent = new Intent(this, ListingDetailActivity.class);
            intent.putExtra("book_id", book.getId());
            startActivity(intent);
        });
        binding.rvMyListings.setLayoutManager(new LinearLayoutManager(this));
        binding.rvMyListings.setAdapter(adapter);
    }

    private void loadUserProfile() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        // Show email immediately while profile loads
        binding.tvUserEmail.setText(email != null ? email : "");

        FirebaseHelper.getCurrentUser(uid, user -> {
            if (user != null) {
                binding.tvUserName.setText(user.getName());
                binding.tvUserEmail.setText(user.getEmail());
                binding.tvUserPhone.setText(user.getPhone());
                binding.tvUserUniversity.setText(user.getUniversity());
                // Set avatar initials
                if (user.getName() != null && !user.getName().isEmpty()) {
                    binding.tvAvatar.setText(String.valueOf(user.getName().charAt(0)).toUpperCase());
                }
            }
        }, e -> Toast.makeText(this, "Could not load profile", Toast.LENGTH_SHORT).show());
    }

    private void loadMyListings() {
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        binding.progressBar.setVisibility(View.VISIBLE);

        FirebaseHelper.getMyBooksQuery(uid)
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    myBooks.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Book book = doc.toObject(Book.class);
                        if (book != null) {
                            book.setId(doc.getId());
                            myBooks.add(book);
                        }
                    }
                    adapter.notifyDataSetChanged();
                    binding.progressBar.setVisibility(View.GONE);

                    int count = myBooks.size();
                    binding.tvListingCount.setText(count + " listing" + (count == 1 ? "" : "s"));

                    if (count == 0) {
                        binding.tvEmptyState.setVisibility(View.VISIBLE);
                        binding.btnAddFirst.setVisibility(View.VISIBLE);
                        binding.rvMyListings.setVisibility(View.GONE);
                    } else {
                        binding.tvEmptyState.setVisibility(View.GONE);
                        binding.btnAddFirst.setVisibility(View.GONE);
                        binding.rvMyListings.setVisibility(View.VISIBLE);
                    }
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    Toast.makeText(this, "Failed to load your listings", Toast.LENGTH_SHORT).show();
                });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(this)
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(this, LoginActivity.class);
                    startActivity(intent);
                    finishAffinity();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadMyListings();
    }
}
