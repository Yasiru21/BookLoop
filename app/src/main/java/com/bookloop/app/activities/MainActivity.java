package com.bookloop.app.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.bookloop.app.R;
import com.bookloop.app.databinding.ActivityMainBinding;
import com.bookloop.app.fragments.HomeFragment;
import com.bookloop.app.fragments.ProfileFragment;
import com.bookloop.app.fragments.SearchFragment;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    
    // Fragments
    private final HomeFragment homeFragment = new HomeFragment();
    private final SearchFragment searchFragment = new SearchFragment();
    private final ProfileFragment profileFragment = new ProfileFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Apply saved theme
        android.content.SharedPreferences sharedPrefs = getSharedPreferences("BookLoopPrefs", android.content.Context.MODE_PRIVATE);
        int currentTheme = sharedPrefs.getInt("theme_mode", androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(currentTheme);

        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupBottomNav();
        
        // Load default fragment
        if (savedInstanceState == null) {
            loadFragment(homeFragment);
            binding.bottomNavigation.setSelectedItemId(R.id.nav_home);
        }
    }

    private void setupBottomNav() {
        binding.bottomNavigation.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            
            if (itemId == R.id.nav_home) {
                loadFragment(homeFragment);
                return true;
            } else if (itemId == R.id.nav_search) {
                loadFragment(searchFragment);
                return true;
            } else if (itemId == R.id.nav_add) {
                // Launch Add Listing Activity, don't change fragment
                startActivity(new Intent(this, AddListingActivity.class));
                return false; // Return false so the nav item isn't visibly selected
            } else if (itemId == R.id.nav_profile) {
                loadFragment(profileFragment);
                return true;
            }
            
            return false;
        });
    }

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragment_container, fragment)
                .commit();
    }
}
