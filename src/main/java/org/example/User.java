package org.example;

import java.time.LocalDateTime;

public class User {
    private int id;
    private long telegramId;
    private String username;
    private String firstName;
    private String lastName;
    private LocalDateTime createdAt;
    private int score; // ✅ To‘g‘ri javoblar sonini saqlaydi

    // 🔹 Konstruktorlar
    public User() {

    }

    public User(long telegramId, String username, String firstName, String lastName, LocalDateTime createdAt) {
        this.telegramId = telegramId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.createdAt = createdAt;
        this.score = 0; // Yangi userlar uchun ball 0 dan boshlanadi
    }

    // 🔹 GETTER va SETTER lar
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public long getTelegramId() {
        return telegramId;
    }

    public void setTelegramId(long telegramId) {
        this.telegramId = telegramId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void incrementScore() { // ✅ To‘g‘ri javob berganda +1 ball qo‘shish
        this.score++;
    }
}
