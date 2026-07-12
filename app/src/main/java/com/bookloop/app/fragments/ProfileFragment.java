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
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.EmailAuthProvider;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
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
    
    /**
     * Change Password — two-step flow:
     *  1. Ask for the user's CURRENT password and re-authenticate.
     *  2. On success, ask for the NEW password and call updatePassword().
     *
     * This avoids FirebaseAuthRecentLoginRequiredException which silently fails
     * when the user's session is not fresh.
     */
    private void showChangePasswordDialog() {
        final EditText currentPwInput = new EditText(requireContext());
        currentPwInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        currentPwInput.setHint("Current password");
        currentPwInput.setPadding(48, 32, 48, 16);

        new AlertDialog.Builder(requireContext())
                .setTitle("Verify Identity")
                .setMessage("Enter your current password to continue.")
                .setView(currentPwInput)
                .setPositiveButton("Next", (dialog, which) -> {
                    String currentPw = currentPwInput.getText().toString().trim();
                    if (currentPw.isEmpty()) {
                        Toast.makeText(requireContext(), "Please enter your current password", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    promptNewPassword(currentPw);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptNewPassword(String currentPassword) {
        final EditText newPwInput = new EditText(requireContext());
        newPwInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        newPwInput.setHint("New password (min 6 characters)");
        newPwInput.setPadding(48, 32, 48, 16);

        new AlertDialog.Builder(requireContext())
                .setTitle("New Password")
                .setView(newPwInput)
                .setPositiveButton("Update", (dialog, which) -> {
                    String newPw = newPwInput.getText().toString().trim();
                    if (newPw.length() < 6) {
                        Toast.makeText(requireContext(), "Password must be at least 6 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    reAuthAndUpdatePassword(currentPassword, newPw);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void reAuthAndUpdatePassword(String currentPassword, String newPassword) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null || user.getEmail() == null) {
            Toast.makeText(requireContext(), "Not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        // Re-authenticate first — required by Firebase for sensitive operations
        AuthCredential credential = EmailAuthProvider.getCredential(user.getEmail(), currentPassword);
        user.reauthenticate(credential)
                .addOnSuccessListener(aVoid -> {
                    // Now safe to update password
                    user.updatePassword(newPassword)
                            .addOnSuccessListener(v -> {
                                if (isAdded()) Toast.makeText(requireContext(),
                                        "✅ Password updated successfully!", Toast.LENGTH_SHORT).show();
                            })
                            .addOnFailureListener(e -> {
                                if (isAdded()) Toast.makeText(requireContext(),
                                        "Failed to update password: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            });
                })
                .addOnFailureListener(e -> {
                    if (isAdded()) Toast.makeText(requireContext(),
                            "❌ Current password is incorrect", Toast.LENGTH_SHORT).show();
                });
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
        // Disable RecyclerView's own scrolling — the outer NestedScrollView handles it.
        // Without this, LinearLayoutManager only measures items that fit on screen.
        binding.rvMyListings.setNestedScrollingEnabled(false);
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

                    // Sort newest-first client-side (avoids needing a Firestore composite index)
                    myBooks.sort((a, b) -> {
                        long tA = (a.getTimestamp() != null) ? a.getTimestamp().toDate().getTime() : 0;
                        long tB = (b.getTimestamp() != null) ? b.getTimestamp().toDate().getTime() : 0;
                        return Long.compare(tB, tA); // descending
                    });

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
                        android.util.Log.e("ProfileFragment", "loadMyListings failed: " + e.getMessage(), e);
                        Toast.makeText(requireContext(),
                                "Could not load listings: " + e.getMessage(),
                                Toast.LENGTH_LONG).show();
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
