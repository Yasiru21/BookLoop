package com.bookloop.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bookloop.app.adapters.BookAdapter;
import com.bookloop.app.databinding.ActivityMainBinding;
import com.bookloop.app.models.Book;
import com.bookloop.app.utils.FirebaseHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private BookAdapter adapter;
    private List<Book> allBooks = new ArrayList<>();
    private List<Book> filteredBooks = new ArrayList<>();
    private String activeConditionFilter = "All";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupRecyclerView();
        setupSearch();
        setupFilters();
        setupBottomNav();
        loadBooks();

        binding.swipeRefresh.setOnRefreshListener(this::loadBooks);
    }

    private void setupRecyclerView() {
        adapter = new BookAdapter(filteredBooks, book -> {
            Intent intent = new Intent(this, ListingDetailActivity.class);
            intent.putExtra("book_id", book.getId());
            startActivity(intent);
        });
        binding.rvBooks.setLayoutManager(new LinearLayoutManager(this));
        binding.rvBooks.setAdapter(adapter);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilters(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void setupFilters() {
        // Condition filter chips
        binding.chipAll.setOnClickListener(v -> { activeConditionFilter = "All"; applyFilters(binding.etSearch.getText().toString()); });
        binding.chipExcellent.setOnClickListener(v -> { activeConditionFilter = "Excellent"; applyFilters(binding.etSearch.getText().toString()); });
        binding.chipGood.setOnClickListener(v -> { activeConditionFilter = "Good"; applyFilters(binding.etSearch.getText().toString()); });
        binding.chipFair.setOnClickListener(v -> { activeConditionFilter = "Fair"; applyFilters(binding.etSearch.getText().toString()); });
    }

    private void applyFilters(String query) {
        filteredBooks.clear();
        String lowerQuery = query.toLowerCase().trim();
        for (Book book : allBooks) {
            boolean matchesSearch = lowerQuery.isEmpty()
                    || book.getTitle().toLowerCase().contains(lowerQuery)
                    || book.getSubject().toLowerCase().contains(lowerQuery)
                    || book.getSellerName().toLowerCase().contains(lowerQuery);
            boolean matchesCondition = activeConditionFilter.equals("All")
                    || book.getCondition().equals(activeConditionFilter);
            if (matchesSearch && matchesCondition) {
                filteredBooks.add(book);
            }
        }
        adapter.notifyDataSetChanged();

        // Show/hide empty state
        boolean isEmpty = filteredBooks.isEmpty();
        binding.tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvBooks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    private void loadBooks() {
        binding.progressBar.setVisibility(View.VISIBLE);
        binding.tvEmptyState.setVisibility(View.GONE);

        FirebaseHelper.getAllBooksQuery()
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    allBooks.clear();
                    for (DocumentSnapshot doc : querySnapshot.getDocuments()) {
                        Book book = doc.toObject(Book.class);
                        if (book != null) {
                            book.setId(doc.getId());
                            allBooks.add(book);
                        }
                    }
                    applyFilters(binding.etSearch.getText().toString());
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefresh.setRefreshing(false);
                    binding.tvListingCount.setText(allBooks.size() + " listings available");
                })
                .addOnFailureListener(e -> {
                    binding.progressBar.setVisibility(View.GONE);
                    binding.swipeRefresh.setRefreshing(false);
                    Toast.makeText(this, "Failed to load listings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                });
    }

    private void setupBottomNav() {
        binding.fabAddListing.setOnClickListener(v ->
                startActivity(new Intent(this, AddListingActivity.class)));

        binding.btnProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        binding.btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, LoginActivity.class));
            finishAffinity();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadBooks(); // Refresh when returning from AddListing/Detail
    }
}
