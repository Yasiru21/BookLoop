package com.bookloop.app.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bookloop.app.activities.ListingDetailActivity;
import com.bookloop.app.adapters.BookAdapter;
import com.bookloop.app.databinding.FragmentSearchBinding;
import com.bookloop.app.models.Book;
import com.bookloop.app.utils.FirebaseHelper;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class SearchFragment extends Fragment {

    private FragmentSearchBinding binding;
    private BookAdapter adapter;
    private List<Book> allBooks = new ArrayList<>();
    private List<Book> filteredBooks = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        setupRecyclerView();
        setupSearch();
        
        binding.swipeRefresh.setOnRefreshListener(this::loadBooks);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBooks();
    }

    private void setupRecyclerView() {
        adapter = new BookAdapter(filteredBooks, book -> {
            Intent intent = new Intent(requireContext(), ListingDetailActivity.class);
            intent.putExtra("book_id", book.getId());
            startActivity(intent);
        });
        binding.rvBooks.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvBooks.setAdapter(adapter);
    }

    private void setupSearch() {
        binding.etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    private void applyFilter(String query) {
        filteredBooks.clear();
        String lowerQuery = query.toLowerCase().trim();
        
        for (Book book : allBooks) {
            boolean matches = lowerQuery.isEmpty()
                    || (book.getTitle() != null && book.getTitle().toLowerCase().contains(lowerQuery))
                    || (book.getDescription() != null && book.getDescription().toLowerCase().contains(lowerQuery))
                    || (book.getSubject() != null && book.getSubject().toLowerCase().contains(lowerQuery))
                    || (book.getSellerName() != null && book.getSellerName().toLowerCase().contains(lowerQuery));
            if (matches) {
                filteredBooks.add(book);
            }
        }
        adapter.notifyDataSetChanged();

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
                    if (isAdded()) {
                        applyFilter(binding.etSearch.getText() != null ? binding.etSearch.getText().toString() : "");
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefresh.setRefreshing(false);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefresh.setRefreshing(false);
                        Toast.makeText(requireContext(), "Failed to load listings", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
