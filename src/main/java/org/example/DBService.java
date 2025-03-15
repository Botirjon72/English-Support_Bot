package org.example;

import java.sql.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
public class DBService {

    private static final String URL = "jdbc:postgresql://dpg-cv007f5umphs73cf57og-a.oregon-postgres.render.com:5432/telegramdb_mm4l";
    private static final String USER = "telegramdb_mm4l_user";
    private static final String PASSWORD = "CMhDzMC0RnJsoT0gyvtYO2Xy4cg8wQ1B";




    static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }



    public void saveUser(User user) {
        String query = """
            INSERT INTO users (telegram_id, username, first_name, last_name, created_at, score, last_active_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (telegram_id) 
            DO UPDATE SET 
                username = EXCLUDED.username,
                first_name = EXCLUDED.first_name,
                last_active_at = EXCLUDED.last_active_at
            """;

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, user.getTelegramId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getFirstName());
            stmt.setString(4, user.getLastName()); // 🔹 Last name yetishmayapti edi
            stmt.setTimestamp(5, Timestamp.valueOf(user.getCreatedAt()));
            stmt.setInt(6, user.getScore());
            stmt.setTimestamp(7, Timestamp.valueOf(LocalDateTime.now()));

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public void updateUser(User user) {
        String query = "UPDATE users SET username = ?, first_name = ?, last_name = ?, created_at = ?, " +
                "score = ?, best_score = ?, today_score = ?, weekly_score = ?, premium = ? " +
                "WHERE telegram_id = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getFirstName());
            stmt.setString(3, user.getLastName());
            stmt.setTimestamp(4, Timestamp.valueOf(user.getCreatedAt()));
            stmt.setInt(5, user.getScore());
            stmt.setInt(6, user.getBestScore());
            stmt.setInt(7, user.getTodayScore());
            stmt.setInt(8, user.getWeeklyScore());
            stmt.setBoolean(9, user.isPremium());
            stmt.setLong(10, user.getTelegramId());

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void addScore(long chatId, int points) {
        String query = "SELECT score, last_active_at, usage_count FROM users WHERE telegram_id = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, chatId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                int currentScore = rs.getInt("score");
                Timestamp lastActiveTimestamp = rs.getTimestamp("last_active_at");
                int usageCount = rs.getInt("usage_count"); // Necha marta ishlatilgani

                LocalDateTime lastActiveAt = (lastActiveTimestamp != null)
                        ? lastActiveTimestamp.toLocalDateTime()
                        : LocalDateTime.MIN;

                LocalDateTime now = LocalDateTime.now();
                long minutes = ChronoUnit.MINUTES.between(lastActiveAt, now);

                int newUsageCount = (minutes <= 5) ? usageCount + 1 : 1; // Agar 5 minut ichida bo‘lsa, foydalanish sonini oshiramiz

                int bonus = 0;

                if (newUsageCount >= 3 && minutes <= 5) {
                    bonus = 5; // Faqat 5 ball qo‘shamiz
                }

                int newScore = (minutes <= 5 && newUsageCount > 3) ? currentScore : currentScore + points + bonus;

                // 🔹 Ma'lumotlarni yangilash
                String updateQuery = "UPDATE users SET score = ?, last_active_at = ?, usage_count = ? WHERE telegram_id = ?";
                try (PreparedStatement updateStmt = conn.prepareStatement(updateQuery)) {
                    updateStmt.setInt(1, newScore);
                    updateStmt.setTimestamp(2, Timestamp.valueOf(now));
                    updateStmt.setInt(3, newUsageCount);
                    updateStmt.setLong(4, chatId);
                    updateStmt.executeUpdate();
                }

                System.out.printf("✅ %d uchun yangi ball: %d (+%d bonus, foydalanish soni: %d)%n",
                        chatId, newScore, bonus, newUsageCount);
            }
        } catch (SQLException e) {
            System.err.println("❌ addScore() da xatolik: " + e.getMessage());
            e.printStackTrace();
        }
    }


    public static GrammarRule getGrammarRuleById(int ruleId) {
        String query = "SELECT id, rule_name, description, negative_example, question_example, youtube_link FROM grammar_rules WHERE id = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, ruleId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new GrammarRule(
                        rs.getInt("id"),
                        rs.getString("rule_name"),
                        rs.getString("description"),
                        rs.getString("negative_example"),
                        rs.getString("question_example"),
                        rs.getString("youtube_link")
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null; // ❌ Agar topilmasa, null qaytariladi
    }


    public List<TestQuestion> getTenRandomTestsByLevel(String level) {
        List<TestQuestion> questions = new ArrayList<>();

        // Normalizatsiya qilingan level nomini olish
        String normalizedLevel = normalizeLevel(level);

        String sql = "SELECT id, question, option_a, option_b, option_c, option_d, correct_option " +
                "FROM cefr_questions WHERE level = ? ORDER BY RANDOM() LIMIT 10";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, normalizedLevel);
            ResultSet rs = pstmt.executeQuery();

            while (rs.next()) {
                String correctAnswer = rs.getString("correct_option");
                questions.add(new TestQuestion(
                        rs.getInt("id"),
                        normalizedLevel,  // Endi `normalizedLevel` ishlatiladi
                        rs.getString("question"),
                        rs.getString("option_a"),
                        rs.getString("option_b"),
                        rs.getString("option_c"),
                        rs.getString("option_d"),
                        (correctAnswer != null && !correctAnswer.isEmpty()) ? correctAnswer.charAt(0) : ' '
                ));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        System.out.println("Berilgan level: " + level);
        System.out.println("Normalizatsiya qilingan level: " + normalizedLevel);
        System.out.println("Topilgan savollar soni: " + questions.size());

        return questions;
    }


    static String normalizeLevel(String buttonText) {
        if (buttonText.contains("A1")) return "A1";
        if (buttonText.contains("A2")) return "A2";
        if (buttonText.contains("B1")) return "B1";
        if (buttonText.contains("B2")) return "B2";
        if (buttonText.contains("C1")) return "C1";
        if (buttonText.contains("C2")) return "C2";
        return buttonText; // Agar mos kelmasa, o'zgartirmay qaytaramiz
    }


    public boolean checkAnswer(int questionId, char userAnswer) {
        String sql = "SELECT correct_option FROM cefr_questions WHERE id = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setInt(1, questionId);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                char correctAnswer = rs.getString("correct_option").charAt(0);
                return Character.toUpperCase(userAnswer) == Character.toUpperCase(correctAnswer);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }


    public static List<Questions> getRandomQuestions(int limit) {
        List<Questions> questions = new ArrayList<>();
        String query = "SELECT id, question, option_a, option_b, option_c, option_d, correct_option FROM questions ORDER BY RANDOM() LIMIT ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setInt(1, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Questions question = new Questions(
                            rs.getInt("id"),
                            rs.getString("question"),
                            rs.getString("option_a"),
                            rs.getString("option_b"),
                            rs.getString("option_c"),
                            rs.getString("option_d"),
                            rs.getString("correct_option").charAt(0)
                    );
                    questions.add(question);
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return questions;
    }


    public void updateUserScore(long chatId, int newScore) {
        String query = "UPDATE users SET score = score + ?, today_score = today_score + ?, weekly_score = weekly_score + ?, best_score = GREATEST(best_score, score + ?) WHERE telegram_id = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, newScore); // Jami ballga qo‘shish
            stmt.setInt(2, newScore); // Bugungi ballga qo‘shish
            stmt.setInt(3, newScore); // Haftalik ballga qo‘shish
            stmt.setInt(4, newScore); // Best scoreni yangilash
            stmt.setLong(5, chatId);

            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public User getUserById(long telegramId) {
        String query = "SELECT * FROM users WHERE telegram_id = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, telegramId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                User user = new User(
                        rs.getLong("telegram_id"),
                        rs.getString("username"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getInt("score"),
                        rs.getTimestamp("created_at").toLocalDateTime()
                );
                user.setBestScore(rs.getInt("best_score"));
                user.setTodayScore(rs.getInt("today_score"));
                user.setWeeklyScore(rs.getInt("weekly_score"));
                user.setPremium(rs.getBoolean("premium"));
                user.setLastActiveAt(rs.getTimestamp("last_active_at").toLocalDateTime());

                return user;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void updateUserLastActive(long telegramId) {
        String query = "UPDATE users SET last_active_at = NOW() WHERE telegram_id = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, telegramId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public List<User> getTopUsers(int limit, String type) {
        List<User> topUsers = new ArrayList<>();

        // Ruxsat berilgan ustunlar
        List<String> validTypes = List.of("best", "today", "weekly", "total");
        if (!validTypes.contains(type)) {
            type = "total"; // Standart qiymat
        }

        String column = switch (type) {
            case "best" -> "best_score";
            case "today" -> "today_score";
            case "weekly" -> "weekly_score";
            default -> "score"; // Umumiy ball bo‘yicha saralanadi
        };

        String query = "SELECT telegram_id, username, first_name, last_name, score, best_score, today_score, weekly_score, premium, created_at " +
                "FROM users ORDER BY " + column + " DESC LIMIT ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, limit);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    User user = new User(
                                                rs.getLong("telegram_id"),
                                                rs.getString("username"),
                                                rs.getString("first_name"),
                                                rs.getString("last_name"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getInt("score"),
                            rs.getInt("best_score"),
                            rs.getInt("today_score"),
                            rs.getInt("weekly_score"), // ✅ To‘g‘ri olish
                            rs.getBoolean("premium")
                                        );
                    topUsers.add(user);
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ Xatolik yuz berdi: " + e.getMessage());
            e.printStackTrace();
        }
        return topUsers;
    }


    public boolean checkAnswerForQuestions(int questionId, char userAnswer) {
        String query = "SELECT correct_option FROM questions WHERE id = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, questionId);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                char correctAnswer = rs.getString("correct_option").charAt(0);
                return Character.toUpperCase(userAnswer) == Character.toUpperCase(correctAnswer);
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }




}