package com.bookloop.app.activities;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;

import com.bookloop.app.R;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final int SPLASH_DELAY_MS = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Apply the user's saved theme BEFORE setContentView to avoid a flash
        android.content.SharedPreferences prefs =
                getSharedPreferences("BookLoopPrefs", android.content.Context.MODE_PRIVATE);
        int savedTheme = prefs.getInt("theme_mode",
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(savedTheme);

        setContentView(R.layout.activity_splash);

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
            if (currentUser != null) {
                // User is already logged in
                startActivity(new Intent(this, MainActivity.class));
            } else {
                // First time or logged out
                startActivity(new Intent(this, LoginActivity.class));
            }
            finish();
        }, SPLASH_DELAY_MS);
    }
}
