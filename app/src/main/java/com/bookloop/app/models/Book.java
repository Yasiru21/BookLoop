package com.bookloop.app.models;

import com.google.firebase.Timestamp;

public class Book {
    private String id;
    private String title;
    private String subject;
    private String edition;
    private String condition; // "Excellent", "Good", "Fair", "Poor"
    private double originalPrice;
    private double sellingPrice;
    private String imageUrl;
    private String sellerId;
    private String sellerName;
    private String sellerEmail;
    private String sellerPhone;
    private String status;   // "available", "reserved", "sold"
    private Timestamp timestamp;
    private String aiPriceSuggestion;
    private String description;

    // Required empty constructor for Firestore deserialization
    public Book() {}

    public Book(String title, String subject, String edition, String condition,
                double originalPrice, double sellingPrice, String imageUrl,
                String sellerId, String sellerName, String sellerEmail,
                String sellerPhone, String description) {
        this.title = title;
        this.subject = subject;
        this.edition = edition;
        this.condition = condition;
        this.originalPrice = originalPrice;
        this.sellingPrice = sellingPrice;
        this.imageUrl = imageUrl;
        this.sellerId = sellerId;
        this.sellerName = sellerName;
        this.sellerEmail = sellerEmail;
        this.sellerPhone = sellerPhone;
        this.description = description;
        this.status = "available";
        this.timestamp = Timestamp.now();
    }

    // Getters
    public String getId() { return id; }
    public String getTitle() { return title; }
    public String getSubject() { return subject; }
    public String getEdition() { return edition; }
    public String getCondition() { return condition; }
    public double getOriginalPrice() { return originalPrice; }
    public double getSellingPrice() { return sellingPrice; }
    public String getImageUrl() { return imageUrl; }
    public String getSellerId() { return sellerId; }
    public String getSellerName() { return sellerName; }
    public String getSellerEmail() { return sellerEmail; }
    public String getSellerPhone() { return sellerPhone; }
    public String getStatus() { return status; }
    public Timestamp getTimestamp() { return timestamp; }
    public String getAiPriceSuggestion() { return aiPriceSuggestion; }
    public String getDescription() { return description; }

    // Setters
    public void setId(String id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setSubject(String subject) { this.subject = subject; }
    public void setEdition(String edition) { this.edition = edition; }
    public void setCondition(String condition) { this.condition = condition; }
    public void setOriginalPrice(double originalPrice) { this.originalPrice = originalPrice; }
    public void setSellingPrice(double sellingPrice) { this.sellingPrice = sellingPrice; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public void setSellerName(String sellerName) { this.sellerName = sellerName; }
    public void setSellerEmail(String sellerEmail) { this.sellerEmail = sellerEmail; }
    public void setSellerPhone(String sellerPhone) { this.sellerPhone = sellerPhone; }
    public void setStatus(String status) { this.status = status; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
    public void setAiPriceSuggestion(String aiPriceSuggestion) { this.aiPriceSuggestion = aiPriceSuggestion; }
    public void setDescription(String description) { this.description = description; }
}
