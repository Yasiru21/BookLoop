package com.bookloop.app.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.bookloop.app.models.Book;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * DataSeeder — populates Firestore with realistic demo book listings.
 * Runs only ONCE (guarded by a SharedPreferences flag "demo_seeded").
 * Call DataSeeder.seedIfNeeded(context) from MainActivity.onCreate().
 */
public class DataSeeder {

    private static final String TAG = "DataSeeder";
    private static final String PREF_KEY = "demo_seeded_v2";

    // ── Demo "seller" accounts (fake UIDs that don't match any real user) ──────
    private static final String SELLER_1_ID    = "demo_seller_kavindu";
    private static final String SELLER_1_NAME  = "Kavindu Perera";
    private static final String SELLER_1_EMAIL = "kavindu.p@student.lk";
    private static final String SELLER_1_PHONE = "+94 77 123 4567";

    private static final String SELLER_2_ID    = "demo_seller_nimali";
    private static final String SELLER_2_NAME  = "Nimali Fernando";
    private static final String SELLER_2_EMAIL = "nimali.f@student.lk";
    private static final String SELLER_2_PHONE = "+94 76 234 5678";

    private static final String SELLER_3_ID    = "demo_seller_thisara";
    private static final String SELLER_3_NAME  = "Thisara Bandara";
    private static final String SELLER_3_EMAIL = "thisara.b@student.lk";
    private static final String SELLER_3_PHONE = "+94 71 345 6789";

    private static final String SELLER_4_ID    = "demo_seller_amaya";
    private static final String SELLER_4_NAME  = "Amaya Wickramasinghe";
    private static final String SELLER_4_EMAIL = "amaya.w@student.lk";
    private static final String SELLER_4_PHONE = "+94 75 456 7890";

    // Open Library cover API — stable, no API key needed
    private static final String OL = "https://covers.openlibrary.org/b/isbn/";

    public static void seedIfNeeded(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("BookLoopPrefs", Context.MODE_PRIVATE);
        if (prefs.getBoolean(PREF_KEY, false)) {
            Log.d(TAG, "Demo data already seeded — skipping.");
            return;
        }

        Log.d(TAG, "Seeding demo data…");
        seedBooks();
        prefs.edit().putBoolean(PREF_KEY, true).apply();
    }

    /** Wipe the flag so the seed runs again on next launch (useful during development). */
    public static void resetSeedFlag(Context context) {
        context.getSharedPreferences("BookLoopPrefs", Context.MODE_PRIVATE)
               .edit().remove(PREF_KEY).apply();
    }

    private static void seedBooks() {
        FirebaseFirestore db = FirebaseFirestore.getInstance();

        List<Book> books = Arrays.asList(

            // ─── Seller 1 listings ────────────────────────────────────────────────
            buildBook(
                "Engineering Mathematics",
                "Mathematics",
                "7th Edition",
                "Good",
                4500, 2700,
                OL + "9780831134709-L.jpg",
                SELLER_1_ID, SELLER_1_NAME, SELLER_1_EMAIL, SELLER_1_PHONE,
                "Stroud's Engineering Mathematics — pencil notes in first 3 chapters, otherwise very good condition.",
                "available",
                daysAgo(1)
            ),
            buildBook(
                "Introduction to Algorithms",
                "Computer Science",
                "3rd Edition",
                "Excellent",
                8500, 5500,
                OL + "9780262033848-L.jpg",
                SELLER_1_ID, SELLER_1_NAME, SELLER_1_EMAIL, SELLER_1_PHONE,
                "CLRS — almost brand new. Purchased but the course switched textbooks. Highly recommended for CS students.",
                "available",
                daysAgo(3)
            ),

            // ─── Seller 2 listings ────────────────────────────────────────────────
            buildBook(
                "Computer Organization and Architecture",
                "Computer Engineering",
                "10th Edition",
                "Excellent",
                6200, 4000,
                OL + "9780134101613-L.jpg",
                SELLER_2_ID, SELLER_2_NAME, SELLER_2_EMAIL, SELLER_2_PHONE,
                "William Stallings — pristine condition, no markings at all. Selling because I completed the module.",
                "available",
                daysAgo(2)
            ),
            buildBook(
                "Principles of Economics",
                "Economics",
                "8th Edition",
                "Good",
                3800, 2200,
                OL + "9781305585126-L.jpg",
                SELLER_2_ID, SELLER_2_NAME, SELLER_2_EMAIL, SELLER_2_PHONE,
                "Mankiw's Principles of Economics. Highlighted key sections, minimal writing. Cover has slight wear.",
                "available",
                daysAgo(5)
            ),
            buildBook(
                "Organic Chemistry",
                "Chemistry",
                "9th Edition",
                "Fair",
                5500, 2500,
                OL + "9780321971371-L.jpg",
                SELLER_2_ID, SELLER_2_NAME, SELLER_2_EMAIL, SELLER_2_PHONE,
                "McMurry's Organic Chemistry — heavy highlighting and notes throughout. Great for reference, all content readable.",
                "available",
                daysAgo(10)
            ),

            // ─── Seller 3 listings ────────────────────────────────────────────────
            buildBook(
                "Data Structures and Algorithm Analysis",
                "Computer Science",
                "3rd Edition",
                "Good",
                3200, 1800,
                OL + "9780132576277-L.jpg",
                SELLER_3_ID, SELLER_3_NAME, SELLER_3_EMAIL, SELLER_3_PHONE,
                "Mark Allen Weiss textbook. Some pages folded, light pencil annotations. Good for exam prep.",
                "available",
                daysAgo(4)
            ),
            buildBook(
                "Business Statistics",
                "Statistics",
                "8th Edition",
                "Excellent",
                4000, 2600,
                OL + "9780132168380-L.jpg",
                SELLER_3_ID, SELLER_3_NAME, SELLER_3_EMAIL, SELLER_3_PHONE,
                "Groebner's Business Statistics — absolutely no markings. Read once during lectures only.",
                "reserved",
                daysAgo(7)
            ),
            buildBook(
                "Fundamentals of Electric Circuits",
                "Electrical Engineering",
                "6th Edition",
                "Good",
                5800, 3500,
                OL + "9780078028229-L.jpg",
                SELLER_3_ID, SELLER_3_NAME, SELLER_3_EMAIL, SELLER_3_PHONE,
                "Alexander & Sadiku — circuit diagrams are clean, some pencil workings in margin. Essential for EE students.",
                "available",
                daysAgo(6)
            ),

            // ─── Seller 4 listings ────────────────────────────────────────────────
            buildBook(
                "Management Information Systems",
                "Information Systems",
                "15th Edition",
                "Excellent",
                4500, 3000,
                OL + "9780134639710-L.jpg",
                SELLER_4_ID, SELLER_4_NAME, SELLER_4_EMAIL, SELLER_4_PHONE,
                "Laudon & Laudon MIS textbook — purchased for final year project module, barely used. Like new.",
                "available",
                daysAgo(8)
            ),
            buildBook(
                "The Art of War",
                "Strategy",
                "1st Edition",
                "Excellent",
                650, 400,
                OL + "9780140455526-L.jpg",
                SELLER_4_ID, SELLER_4_NAME, SELLER_4_EMAIL, SELLER_4_PHONE,
                "Classic strategic thinking text — Penguin Classics edition. Pristine condition, great read.",
                "available",
                daysAgo(9)
            )
        );

        for (Book book : books) {
            db.collection("books")
              .add(book)
              .addOnSuccessListener(docRef -> {
                  docRef.update("id", docRef.getId());
                  Log.d(TAG, "Seeded: " + book.getTitle());
              })
              .addOnFailureListener(e -> Log.e(TAG, "Failed to seed: " + book.getTitle(), e));
        }
    }

    /** Helper — build a fully populated Book model with a custom timestamp. */
    private static Book buildBook(
            String title, String subject, String edition, String condition,
            double originalPrice, double sellingPrice,
            String imageUrl,
            String sellerId, String sellerName, String sellerEmail, String sellerPhone,
            String description,
            String status,
            Date date) {

        Book book = new Book(title, subject, edition, condition,
                originalPrice, sellingPrice, imageUrl,
                sellerId, sellerName, sellerEmail, sellerPhone, description);
        book.setStatus(status);
        book.setTimestamp(new Timestamp(date));
        return book;
    }

    /** Returns a Date N days before now. */
    private static Date daysAgo(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        return cal.getTime();
    }
}
