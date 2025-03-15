package org.example;

import java.time.LocalDateTime;

public class User {
    private int id;
    private long telegramId;
    private String username;
    private String firstName;
    private String lastName;
    private LocalDateTime createdAt;
    private LocalDateTime lastActiveAt; // 🔹 Yangi maydon
    private int score;
    private int bestScore;
    private int todayScore;
    private int weeklyScore;
    private boolean premium; // ✅ Premium boolean bo‘lishi kerak


    public User(long telegramId, String username, String firstName, String lastName, LocalDateTime createdAt,
                int score, int bestScore, int todayScore, int weeklyScore, boolean premium) {
        this.telegramId = telegramId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.createdAt = createdAt;
        this.score = score;
        this.bestScore = bestScore;
        this.todayScore = todayScore;
        this.weeklyScore = weeklyScore;
        this.premium = premium;
    }

    public User(long telegramId, String username, String firstName, String lastName, int score, LocalDateTime createdAt) {
        this.telegramId = telegramId;
        this.username = username;
        this.firstName = firstName;
        this.lastName = lastName;
        this.createdAt = createdAt;
        this.score = score;

    }

    public boolean isPremium() {
        return premium;
    }

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

    public int getBestScore() {
        return bestScore;
    }

    public void setBestScore(int bestScore) {
        this.bestScore = bestScore;
    }

    public int getTodayScore() {
        return todayScore;
    }

    public void setTodayScore(int todayScore) {
        this.todayScore = todayScore;
    }

    public int getWeeklyScore() {
        return weeklyScore;
    }

    public void setWeeklyScore(int weeklyScore) {
        this.weeklyScore = weeklyScore;
    }

    public void setPremium(boolean premium) {
        this.premium = premium;
    }

    public LocalDateTime getLastActiveAt() {
        return lastActiveAt;
    }

    public void setLastActiveAt(LocalDateTime lastActiveAt) {
        this.lastActiveAt = lastActiveAt;
    }
}
