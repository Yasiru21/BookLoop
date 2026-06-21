package com.bookloop.app.models;

public class AppUser {
    private String uid;
    private String name;
    private String email;
    private String phone;
    private String university;

    // Required empty constructor for Firestore
    public AppUser() {}

    public AppUser(String uid, String name, String email, String phone, String university) {
        this.uid = uid;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.university = university;
    }

    // Getters
    public String getUid() { return uid; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getUniversity() { return university; }

    // Setters
    public void setUid(String uid) { this.uid = uid; }
    public void setName(String name) { this.name = name; }
    public void setEmail(String email) { this.email = email; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setUniversity(String university) { this.university = university; }
}
