package com.bookloop.app.activities;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.bookloop.app.databinding.ActivityRegisterBinding;
import com.bookloop.app.models.AppUser;
import com.bookloop.app.utils.FirebaseHelper;
import com.google.firebase.auth.FirebaseAuth;

public class RegisterActivity extends AppCompatActivity {

    private ActivityRegisterBinding binding;
    private FirebaseAuth auth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRegisterBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        auth = FirebaseAuth.getInstance();

        binding.btnRegister.setOnClickListener(v -> attemptRegister());
        binding.tvLogin.setOnClickListener(v -> finish());
    }

    private void attemptRegister() {
        String name     = binding.etName.getText().toString().trim();
        String email    = binding.etEmail.getText().toString().trim();
        String phone    = binding.etPhone.getText().toString().trim();
        String uni      = binding.etUniversity.getText().toString().trim();
        String password = binding.etPassword.getText().toString().trim();
        String confirm  = binding.etConfirmPassword.getText().toString().trim();

        // Validation
        if (TextUtils.isEmpty(name)) {
            binding.etName.setError("Full name is required");
            binding.etName.requestFocus(); return;
        }
        if (TextUtils.isEmpty(email) || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.etEmail.setError("Valid email is required");
            binding.etEmail.requestFocus(); return;
        }
        if (TextUtils.isEmpty(phone) || phone.length() < 9) {
            binding.etPhone.setError("Valid phone number is required");
            binding.etPhone.requestFocus(); return;
        }
        if (TextUtils.isEmpty(uni)) {
            binding.etUniversity.setError("University name is required");
            binding.etUniversity.requestFocus(); return;
        }
        if (TextUtils.isEmpty(password) || password.length() < 6) {
            binding.etPassword.setError("Password must be at least 6 characters");
            binding.etPassword.requestFocus(); return;
        }
        if (!password.equals(confirm)) {
            binding.etConfirmPassword.setError("Passwords do not match");
            binding.etConfirmPassword.requestFocus(); return;
        }

        setLoading(true);

        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(authResult -> {
                    String uid = authResult.getUser().getUid();
                    AppUser user = new AppUser(uid, name, email, phone, uni);
                    FirebaseHelper.saveUser(user, new FirebaseHelper.OnCompleteCallback() {
                        @Override
                        public void onSuccess() {
                            setLoading(false);
                            Toast.makeText(RegisterActivity.this,
                                    "Account created! Welcome to Book Loop 📚", Toast.LENGTH_LONG).show();
                            startActivity(new Intent(RegisterActivity.this, MainActivity.class));
                            finishAffinity();
                        }
                        @Override
                        public void onError(String error) {
                            setLoading(false);
                            Toast.makeText(RegisterActivity.this,
                                    "Profile save failed: " + error, Toast.LENGTH_LONG).show();
                        }
                    });
                })
                .addOnFailureListener(e -> {
                    setLoading(false);
                    Toast.makeText(this, "Registration failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
    }

    private void setLoading(boolean isLoading) {
        binding.btnRegister.setEnabled(!isLoading);
        binding.progressBar.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        binding.btnRegister.setText(isLoading ? "Creating account..." : "Create Account");
    }
}
