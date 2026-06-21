package com.bookloop.app.utils;

import android.net.Uri;
import android.util.Log;

import com.bookloop.app.models.AppUser;
import com.bookloop.app.models.Book;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;
import java.util.UUID;

/**
 * FirebaseHelper — Centralized utility class for Firebase operations.
 * Handles Firestore reads/writes and Storage uploads.
 */
public class FirebaseHelper {

    private static final String TAG = "FirebaseHelper";
    private static final String COLLECTION_BOOKS = "books";
    private static final String COLLECTION_USERS = "users";

    private static FirebaseFirestore db = FirebaseFirestore.getInstance();
    private static FirebaseStorage storage = FirebaseStorage.getInstance();
    private static FirebaseAuth auth = FirebaseAuth.getInstance();

    // ─── Callbacks ───────────────────────────────────────────────────────────

    public interface OnCompleteCallback {
        void onSuccess();

        void onError(String error);
    }

    public interface OnUploadCallback {
        void onSuccess(String downloadUrl);

        void onError(String error);

        void onProgress(int percent);
    }

    // ─── User Operations ─────────────────────────────────────────────────────

    /** Save a new user profile to Firestore */
    public static void saveUser(AppUser user, OnCompleteCallback callback) {
        db.collection(COLLECTION_USERS)
                .document(user.getUid())
                .set(user)
                .addOnSuccessListener(unused -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "saveUser failed", e);
                    callback.onError(e.getMessage());
                });
    }

    /** Fetch the current user's profile from Firestore */
    public static void getCurrentUser(String uid, com.google.android.gms.tasks.OnSuccessListener<AppUser> onSuccess,
            com.google.android.gms.tasks.OnFailureListener onFailure) {
        db.collection(COLLECTION_USERS)
                .document(uid)
                .get()
                .addOnSuccessListener(doc -> {
                    AppUser user = doc.toObject(AppUser.class);
                    onSuccess.onSuccess(user);
                })
                .addOnFailureListener(onFailure);
    }

    // ─── Book Operations ─────────────────────────────────────────────────────

    /** Add a new book listing to Firestore */
    public static void addBook(Book book, OnCompleteCallback callback) {
        db.collection(COLLECTION_BOOKS)
                .add(book)
                .addOnSuccessListener(docRef -> {
                    // Update the document with its own ID for easy reference
                    docRef.update("id", docRef.getId())
                            .addOnSuccessListener(v -> callback.onSuccess())
                            .addOnFailureListener(e -> callback.onSuccess()); // ID update failure is non-critical
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "addBook failed", e);
                    callback.onError(e.getMessage());
                });
    }

    /** Update an existing book listing */
    public static void updateBook(Book book, OnCompleteCallback callback) {
        db.collection(COLLECTION_BOOKS)
                .document(book.getId())
                .set(book)
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "updateBook failed", e);
                    callback.onError(e.getMessage());
                });
    }

    /** Delete a book listing */
    public static void deleteBook(String bookId, OnCompleteCallback callback) {
        db.collection(COLLECTION_BOOKS)
                .document(bookId)
                .delete()
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "deleteBook failed", e);
                    callback.onError(e.getMessage());
                });
    }

    /** Update book listing status (available / reserved / sold) */
    public static void updateBookStatus(String bookId, String status, OnCompleteCallback callback) {
        db.collection(COLLECTION_BOOKS)
                .document(bookId)
                .update("status", status)
                .addOnSuccessListener(v -> callback.onSuccess())
                .addOnFailureListener(e -> {
                    Log.e(TAG, "updateBookStatus failed", e);
                    callback.onError(e.getMessage());
                });
    }

    /** Get query for all available books, ordered by newest first */
    public static Query getAllBooksQuery() {
        return db.collection(COLLECTION_BOOKS)
                .orderBy("timestamp", Query.Direction.DESCENDING);
    }

    /** Get query for books belonging to the current seller */
    public static Query getMyBooksQuery(String sellerId) {
        return db.collection(COLLECTION_BOOKS)
                .whereEqualTo("sellerId", sellerId)
                .orderBy("timestamp", Query.Direction.DESCENDING);
    }

    // ─── Storage Operations ──────────────────────────────────────────────────

    /** Upload a book cover image to Firebase Storage */
    public static void uploadBookImage(Uri imageUri, OnUploadCallback callback) {
        String fileName = "books/" + UUID.randomUUID().toString() + ".jpg";
        StorageReference ref = storage.getReference().child(fileName);

        ref.putFile(imageUri)
                .addOnProgressListener(snapshot -> {
                    int progress = (int) (100.0 * snapshot.getBytesTransferred() / snapshot.getTotalByteCount());
                    callback.onProgress(progress);
                })
                .addOnSuccessListener(taskSnapshot -> ref.getDownloadUrl()
                        .addOnSuccessListener(uri -> callback.onSuccess(uri.toString()))
                        .addOnFailureListener(e -> callback.onError("Failed to get download URL: " + e.getMessage())))
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Image upload failed", e);
                    callback.onError("Upload failed: " + e.getMessage());
                });
    }
}
