package com.bookloop.app.fragments;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.bookloop.app.activities.EditProfileActivity;
import com.bookloop.app.activities.ListingDetailActivity;
import com.bookloop.app.activities.LoginActivity;
import com.bookloop.app.adapters.BookAdapter;
import com.bookloop.app.databinding.FragmentProfileBinding;
import com.bookloop.app.models.Book;
import com.bookloop.app.utils.FirebaseHelper;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class ProfileFragment extends Fragment {

    private FragmentProfileBinding binding;
    private BookAdapter adapter;
    private List<Book> myBooks = new ArrayList<>();
    private SharedPreferences sharedPrefs;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        binding = FragmentProfileBinding.inflate(inflater, container, false);
        sharedPrefs = requireContext().getSharedPreferences("BookLoopPrefs", Context.MODE_PRIVATE);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        binding.btnLogout.setOnClickListener(v -> confirmLogout());
        
        binding.btnEditProfile.setOnClickListener(v -> 
                startActivity(new Intent(requireContext(), EditProfileActivity.class)));
                
        setupThemeToggle();
        
        binding.btnNotifications.setOnClickListener(v -> showNotificationsDialog());
        binding.btnChangePassword.setOnClickListener(v -> showChangePasswordDialog());

        setupRecyclerView();
        loadUserProfile();
    }
    
    private void setupThemeToggle() {
        int currentTheme = sharedPrefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        if (currentTheme == AppCompatDelegate.MODE_NIGHT_NO) {
            binding.themeToggleGroup.check(com.bookloop.app.R.id.btnThemeLight);
        } else if (currentTheme == AppCompatDelegate.MODE_NIGHT_YES) {
            binding.themeToggleGroup.check(com.bookloop.app.R.id.btnThemeDark);
        } else {
            binding.themeToggleGroup.check(com.bookloop.app.R.id.btnThemeSystem);
        }

        binding.themeToggleGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked) {
                int mode = AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
                if (checkedId == com.bookloop.app.R.id.btnThemeLight) mode = AppCompatDelegate.MODE_NIGHT_NO;
                else if (checkedId == com.bookloop.app.R.id.btnThemeDark) mode = AppCompatDelegate.MODE_NIGHT_YES;
                
                sharedPrefs.edit().putInt("theme_mode", mode).apply();
                AppCompatDelegate.setDefaultNightMode(mode);
            }
        });
    }
    
    private void showNotificationsDialog() {
        boolean isEnabled = sharedPrefs.getBoolean("notifications_enabled", true);
        String[] options = {"Enabled", "Disabled"};
        int checkedItem = isEnabled ? 0 : 1;

        new AlertDialog.Builder(requireContext())
                .setTitle("Push Notifications")
                .setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
                    boolean enable = (which == 0);
                    sharedPrefs.edit().putBoolean("notifications_enabled", enable).apply();
                    Toast.makeText(requireContext(), "Notifications " + (enable ? "enabled" : "disabled"), Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .show();
    }
    
    private void showChangePasswordDialog() {
        final EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint("New Password");

        new AlertDialog.Builder(requireContext())
                .setTitle("Change Password")
                .setView(input)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newPassword = input.getText().toString().trim();
                    if (newPassword.length() < 6) {
                        Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        FirebaseAuth.getInstance().getCurrentUser().updatePassword(newPassword)
                                .addOnSuccessListener(aVoid -> Toast.makeText(requireContext(), "Password updated successfully!", Toast.LENGTH_SHORT).show())
                                .addOnFailureListener(e -> Toast.makeText(requireContext(), "Failed to update password: " + e.getMessage(), Toast.LENGTH_LONG).show());
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onResume() {
        super.onResume();
        loadUserProfile(); // Reload profile in case it was edited
        loadMyListings();
    }

    private void setupRecyclerView() {
        adapter = new BookAdapter(myBooks, book -> {
            Intent intent = new Intent(requireContext(), ListingDetailActivity.class);
            intent.putExtra("book_id", book.getId());
            startActivity(intent);
        });
        binding.rvMyListings.setLayoutManager(new LinearLayoutManager(requireContext()));
        binding.rvMyListings.setAdapter(adapter);
    }

    private void loadUserProfile() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
        
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        String email = FirebaseAuth.getInstance().getCurrentUser().getEmail();

        binding.tvUserEmail.setText(email != null ? email : "");

        FirebaseHelper.getCurrentUser(uid, user -> {
            if (user != null && isAdded()) {
                binding.tvUserName.setText(user.getName());
                binding.tvUserEmail.setText(user.getEmail());
                binding.tvUserUniversity.setText(user.getUniversity());
                if (user.getName() != null && !user.getName().isEmpty()) {
                    binding.tvAvatar.setText(String.valueOf(user.getName().charAt(0)).toUpperCase());
                }
            }
        }, e -> {
            if (isAdded()) Toast.makeText(requireContext(), "Could not load profile", Toast.LENGTH_SHORT).show();
        });
    }

    private void loadMyListings() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) return;
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
                    if (isAdded()) {
                        adapter.notifyDataSetChanged();
                        binding.progressBar.setVisibility(View.GONE);

                        int count = myBooks.size();
                        binding.tvListingCount.setText(count + " listing" + (count == 1 ? "" : "s"));

                        if (count == 0) {
                            binding.tvEmptyState.setVisibility(View.VISIBLE);
                            binding.rvMyListings.setVisibility(View.GONE);
                        } else {
                            binding.tvEmptyState.setVisibility(View.GONE);
                            binding.rvMyListings.setVisibility(View.VISIBLE);
                        }
                    }
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) {
                        binding.progressBar.setVisibility(View.GONE);
                        Toast.makeText(requireContext(), "Failed to load your listings", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void confirmLogout() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Logout")
                .setMessage("Are you sure you want to logout?")
                .setPositiveButton("Logout", (d, w) -> {
                    FirebaseAuth.getInstance().signOut();
                    Intent intent = new Intent(requireContext(), LoginActivity.class);
                    startActivity(intent);
                    if (getActivity() != null) {
                        getActivity().finishAffinity();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
