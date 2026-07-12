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

import com.bookloop.app.R;
import com.bookloop.app.activities.ListingDetailActivity;
import com.bookloop.app.adapters.BookAdapter;
import com.bookloop.app.databinding.DialogSearchFiltersBinding;
import com.bookloop.app.databinding.FragmentSearchBinding;
import com.bookloop.app.models.Book;
import com.bookloop.app.utils.FirebaseHelper;
import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SearchFragment extends Fragment {

    // ── Sort options ──────────────────────────────────────────────────────────
    private static final int    SORT_NEWEST   = 0;
    private static final int    SORT_PRICE_UP = 1;
    private static final int    SORT_PRICE_DN = 2;
    private static final String[] SORT_LABELS = {"⇅ Newest", "⇅ Price ↑", "⇅ Price ↓"};

    private FragmentSearchBinding binding;
    private BookAdapter           adapter;

    private final List<Book> allBooks      = new ArrayList<>();
    private final List<Book> filteredBooks = new ArrayList<>();

    // ── Active filter state ───────────────────────────────────────────────────
    /** Currently selected condition filter — null means "All conditions" */
    private String activeCondition = null;
    /** Currently selected status filter — null means "All statuses" */
    private String activeStatus    = null;
    /** Min price bound (0 = no lower bound) */
    private double minPrice        = 0;
    /** Max price bound (Double.MAX_VALUE = no upper bound) */
    private double maxPrice        = Double.MAX_VALUE;
    /** Currently selected sort order */
    private int    activeSortOrder = SORT_NEWEST;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentSearchBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        setupRecyclerView();
        setupSearch();
        setupConditionChips();
        setupSortButton();
        setupFiltersButton();

        binding.swipeRefresh.setOnRefreshListener(this::loadBooks);
    }

    @Override
    public void onResume() {
        super.onResume();
        loadBooks();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    // ── Setup helpers ─────────────────────────────────────────────────────────

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
                applyFilters();
            }
            @Override public void afterTextChanged(Editable s) {}
        });
    }

    /** Wire up the five condition chip buttons. */
    private void setupConditionChips() {
        binding.chipAll.setOnClickListener(v       -> selectCondition(null));
        binding.chipExcellent.setOnClickListener(v -> selectCondition("Excellent"));
        binding.chipGood.setOnClickListener(v      -> selectCondition("Good"));
        binding.chipFair.setOnClickListener(v      -> selectCondition("Fair"));
        binding.chipPoor.setOnClickListener(v      -> selectCondition("Poor"));
        updateChipVisuals();
    }

    /** Show a popup menu with sort choices when the Sort button is tapped. */
    private void setupSortButton() {
        binding.btnSortOrder.setOnClickListener(v -> {
            android.widget.PopupMenu popup =
                    new android.widget.PopupMenu(requireContext(), binding.btnSortOrder);
            popup.getMenu().add(0, SORT_NEWEST,   0, "Newest First");
            popup.getMenu().add(0, SORT_PRICE_UP, 1, "Price: Low → High");
            popup.getMenu().add(0, SORT_PRICE_DN, 2, "Price: High → Low");
            popup.setOnMenuItemClickListener(item -> {
                activeSortOrder = item.getItemId();
                binding.btnSortOrder.setText(SORT_LABELS[activeSortOrder]);
                applyFilters();
                return true;
            });
            popup.show();
        });
    }

    /** Opens the Advanced Filters bottom sheet. */
    private void setupFiltersButton() {
        binding.btnFilters.setOnClickListener(v -> showFiltersBottomSheet());
    }

    // ── Filter bottom sheet ───────────────────────────────────────────────────

    private void showFiltersBottomSheet() {
        BottomSheetDialog sheet = new BottomSheetDialog(requireContext());
        DialogSearchFiltersBinding sheetBinding =
                DialogSearchFiltersBinding.inflate(LayoutInflater.from(requireContext()));
        sheet.setContentView(sheetBinding.getRoot());

        // Pre-fill price inputs with current values
        if (minPrice > 0)           sheetBinding.etMinPrice.setText(String.valueOf((int) minPrice));
        if (maxPrice < Double.MAX_VALUE) sheetBinding.etMaxPrice.setText(String.valueOf((int) maxPrice));

        // ── Status chip state (reflect current activeStatus) ─────────────────
        final String[] selectedStatus = {activeStatus};
        updateStatusChips(sheetBinding, selectedStatus[0]);

        sheetBinding.statusAll.setOnClickListener(v -> {
            selectedStatus[0] = null;
            updateStatusChips(sheetBinding, null);
        });
        sheetBinding.statusAvailable.setOnClickListener(v -> {
            selectedStatus[0] = "available";
            updateStatusChips(sheetBinding, "available");
        });
        sheetBinding.statusReserved.setOnClickListener(v -> {
            selectedStatus[0] = "reserved";
            updateStatusChips(sheetBinding, "reserved");
        });
        sheetBinding.statusSold.setOnClickListener(v -> {
            selectedStatus[0] = "sold";
            updateStatusChips(sheetBinding, "sold");
        });

        // ── Quick-pick price presets ──────────────────────────────────────────
        sheetBinding.presetUnder500.setOnClickListener(v -> {
            sheetBinding.etMinPrice.setText("");
            sheetBinding.etMaxPrice.setText("500");
        });
        sheetBinding.preset500to2000.setOnClickListener(v -> {
            sheetBinding.etMinPrice.setText("500");
            sheetBinding.etMaxPrice.setText("2000");
        });
        sheetBinding.preset2000to5000.setOnClickListener(v -> {
            sheetBinding.etMinPrice.setText("2000");
            sheetBinding.etMaxPrice.setText("5000");
        });
        sheetBinding.presetOver5000.setOnClickListener(v -> {
            sheetBinding.etMinPrice.setText("5000");
            sheetBinding.etMaxPrice.setText("");
        });

        // ── Clear All ─────────────────────────────────────────────────────────
        sheetBinding.btnClearAll.setOnClickListener(v -> {
            sheetBinding.etMinPrice.setText("");
            sheetBinding.etMaxPrice.setText("");
            selectedStatus[0] = null;
            updateStatusChips(sheetBinding, null);
        });

        // ── Apply ─────────────────────────────────────────────────────────────
        sheetBinding.btnApplyFilters.setOnClickListener(v -> {
            // Parse price bounds
            String minStr = sheetBinding.etMinPrice.getText() != null
                    ? sheetBinding.etMinPrice.getText().toString().trim() : "";
            String maxStr = sheetBinding.etMaxPrice.getText() != null
                    ? sheetBinding.etMaxPrice.getText().toString().trim() : "";

            minPrice = minStr.isEmpty() ? 0 : Double.parseDouble(minStr);
            maxPrice = maxStr.isEmpty() ? Double.MAX_VALUE : Double.parseDouble(maxStr);

            // Validate
            if (minPrice > 0 && maxPrice < Double.MAX_VALUE && minPrice > maxPrice) {
                Toast.makeText(requireContext(),
                        "Min price cannot be greater than Max price", Toast.LENGTH_SHORT).show();
                return;
            }

            activeStatus = selectedStatus[0];
            applyFilters();
            updateFiltersButtonBadge();
            sheet.dismiss();
        });

        sheet.show();
    }

    /** Update availability chip visual state inside the bottom sheet. */
    private void updateStatusChips(DialogSearchFiltersBinding b, String status) {
        setStatusChipActive(b.statusAll,       status == null);
        setStatusChipActive(b.statusAvailable, "available".equals(status));
        setStatusChipActive(b.statusReserved,  "reserved".equals(status));
        setStatusChipActive(b.statusSold,      "sold".equals(status));
    }

    private void setStatusChipActive(MaterialButton chip, boolean active) {
        if (active) {
            chip.setBackgroundTintList(
                    requireContext().getColorStateList(R.color.primary));
            chip.setTextColor(0xFFFFFFFF);
        } else {
            chip.setBackgroundTintList(
                    requireContext().getColorStateList(R.color.surface_variant));
            chip.setTextColor(
                    requireContext().getResources().getColor(R.color.text_secondary, null));
        }
    }

    /**
     * Update the Filters button label to show how many extra filters are active
     * (price range and/or status count as separate active filters).
     */
    private void updateFiltersButtonBadge() {
        if (binding == null) return;
        int activeCount = 0;
        if (minPrice > 0 || maxPrice < Double.MAX_VALUE) activeCount++;
        if (activeStatus != null)                        activeCount++;

        if (activeCount == 0) {
            binding.btnFilters.setText("🎛 Filters");
        } else {
            binding.btnFilters.setText("🎛 Filters (" + activeCount + ")");
        }
    }

    // ── Filter / sort logic ───────────────────────────────────────────────────

    /** Called whenever a condition chip is tapped. */
    private void selectCondition(String condition) {
        activeCondition = condition;
        updateChipVisuals();
        applyFilters();
    }

    /**
     * Apply all active filters (text, condition, price range, status) and
     * the selected sort order to {@link #allBooks}, then refresh the list.
     */
    private void applyFilters() {
        String lowerQuery = binding.etSearch.getText() != null
                ? binding.etSearch.getText().toString().toLowerCase().trim()
                : "";

        filteredBooks.clear();

        for (Book book : allBooks) {
            // ── Text search ─────────────────────────────────────────────────
            boolean textMatch = lowerQuery.isEmpty()
                    || (book.getTitle()      != null && book.getTitle().toLowerCase().contains(lowerQuery))
                    || (book.getSubject()    != null && book.getSubject().toLowerCase().contains(lowerQuery))
                    || (book.getSellerName() != null && book.getSellerName().toLowerCase().contains(lowerQuery))
                    || (book.getDescription()!= null && book.getDescription().toLowerCase().contains(lowerQuery));

            // ── Condition filter ─────────────────────────────────────────────
            boolean conditionMatch = activeCondition == null
                    || activeCondition.equalsIgnoreCase(book.getCondition());

            // ── Price range filter ───────────────────────────────────────────
            double price = book.getSellingPrice();
            boolean priceMatch = price >= minPrice && price <= maxPrice;

            // ── Availability / status filter ─────────────────────────────────
            String bookStatus = book.getStatus() != null ? book.getStatus().toLowerCase() : "available";
            boolean statusMatch = activeStatus == null
                    || activeStatus.equalsIgnoreCase(bookStatus);

            if (textMatch && conditionMatch && priceMatch && statusMatch) {
                filteredBooks.add(book);
            }
        }

        // ── Sort ─────────────────────────────────────────────────────────────
        switch (activeSortOrder) {
            case SORT_PRICE_UP:
                filteredBooks.sort(Comparator.comparingDouble(Book::getSellingPrice));
                break;
            case SORT_PRICE_DN:
                filteredBooks.sort((a, b) ->
                        Double.compare(b.getSellingPrice(), a.getSellingPrice()));
                break;
            default: // SORT_NEWEST — Firestore returns newest first
                break;
        }

        adapter.notifyDataSetChanged();

        // ── Result count label ────────────────────────────────────────────────
        int count = filteredBooks.size();
        String label = count == 0 ? "No results"
                : count == 1     ? "1 listing found"
                : count + " listings found";
        binding.tvResultCount.setText(label);

        // ── Empty state ───────────────────────────────────────────────────────
        boolean isEmpty = filteredBooks.isEmpty();
        binding.tvEmptyState.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        binding.rvBooks.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
    }

    // ── Condition chip visuals ────────────────────────────────────────────────

    private void updateChipVisuals() {
        setChipActive(binding.chipAll,       activeCondition == null);
        setChipActive(binding.chipExcellent, "Excellent".equals(activeCondition));
        setChipActive(binding.chipGood,      "Good".equals(activeCondition));
        setChipActive(binding.chipFair,      "Fair".equals(activeCondition));
        setChipActive(binding.chipPoor,      "Poor".equals(activeCondition));
    }

    private void setChipActive(MaterialButton chip, boolean active) {
        if (active) {
            chip.setBackgroundTintList(
                    requireContext().getColorStateList(R.color.primary));
            chip.setTextColor(
                    requireContext().getResources().getColor(android.R.color.white, null));
        } else {
            chip.setBackgroundTintList(
                    requireContext().getColorStateList(R.color.surface_variant));
            chip.setTextColor(
                    requireContext().getResources().getColor(R.color.text_secondary, null));
        }
    }

    // ── Firebase ──────────────────────────────────────────────────────────────

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
                        applyFilters();
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefresh.setRefreshing(false);
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        binding.swipeRefresh.setRefreshing(false);
                        Toast.makeText(requireContext(),
                                "Failed to load listings", Toast.LENGTH_SHORT).show();
                    }
                });
    }
}
