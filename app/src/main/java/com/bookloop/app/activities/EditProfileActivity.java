package com.bookloop.app.activities;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bookloop.app.databinding.ActivityEditProfileBinding;
import com.bookloop.app.models.AppUser;
import com.bookloop.app.utils.FirebaseHelper;
import com.google.firebase.auth.FirebaseAuth;

public class EditProfileActivity extends AppCompatActivity {

    private ActivityEditProfileBinding binding;
    private AppUser currentUser;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEditProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.btnBack.setOnClickListener(v -> finish());
        binding.btnSave.setOnClickListener(v -> saveChanges());

        loadUserProfile();
    }

    private void loadUserProfile() {
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            finish();
            return;
        }
        
        String uid = FirebaseAuth.getInstance().getCurrentUser().getUid();
        setLoading(true);

        FirebaseHelper.getCurrentUser(uid, user -> {
            setLoading(false);
            if (user != null) {
                currentUser = user;
                binding.etName.setText(user.getName());
                binding.etEmail.setText(user.getEmail());
                binding.etPhone.setText(user.getPhone());
                binding.etUniversity.setText(user.getUniversity());
            }
        }, e -> {
            setLoading(false);
            Toast.makeText(this, "Failed to load profile", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private void saveChanges() {
        if (currentUser == null) return;

        String name = binding.etName.getText().toString().trim();
        String phone = binding.etPhone.getText().toString().trim();
        String university = binding.etUniversity.getText().toString().trim();

        if (TextUtils.isEmpty(name)) {
            binding.etName.setError("Name is required");
            binding.etName.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(phone)) {
            binding.etPhone.setError("Phone is required");
            binding.etPhone.requestFocus();
            return;
        }
        if (TextUtils.isEmpty(university)) {
            binding.etUniversity.setError("University is required");
            binding.etUniversity.requestFocus();
            return;
        }

        currentUser.setName(name);
        currentUser.setPhone(phone);
        currentUser.setUniversity(university);

        setLoading(true);

        FirebaseHelper.saveUser(currentUser, new FirebaseHelper.OnCompleteCallback() {
            @Override
            public void onSuccess() {
                setLoading(false);
                Toast.makeText(EditProfileActivity.this, "Profile updated successfully!", Toast.LENGTH_SHORT).show();
                finish();
            }

            @Override
            public void onError(String error) {
                setLoading(false);
                Toast.makeText(EditProfileActivity.this, "Failed to update profile: " + error, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void setLoading(boolean isLoading) {
        binding.btnSave.setEnabled(!isLoading);
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
    }
}
