package org.example;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DBService {

    private  static final String URL = "jdbc:postgresql://dpg-cv007f5umphs73cf57og-a.oregon-postgres.render.com:5432/telegramdb_mm4l";
    private static final String USER = "telegramdb_mm4l_user";
    private static final String PASSWORD = "CMhDzMC0RnJsoT0gyvtYO2Xy4cg8wQ1B";


    static Connection connect() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
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

    public void saveUser(User user) {
        String query = "INSERT INTO users (telegram_id, username, first_name, last_name, created_at, score) " +
                "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (telegram_id) DO NOTHING";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setLong(1, user.getTelegramId());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getFirstName());
            stmt.setString(4, user.getLastName());
            stmt.setTimestamp(5, Timestamp.valueOf(user.getCreatedAt()));
            stmt.setInt(6, user.getScore());

            stmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public List<GrammarRule> getAllGrammarRules() {
        List<GrammarRule> grammarRules = new ArrayList<>();
        String sql = "SELECT id, rule_name, description, negative_example, question_example, youtube_link FROM grammar_rules ORDER BY id ASC";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                grammarRules.add(new GrammarRule(
                        rs.getInt("id"),
                        rs.getString("rule_name"),
                        rs.getString("description"),
                        rs.getString("negative_example"),
                        rs.getString("question_example"),
                        rs.getString("youtube_link")
                ));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }
        return grammarRules;
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
        String query = "UPDATE users SET score = ? WHERE telegram_id = ?";

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setInt(1, newScore); // ✅ Umumiy natijani bazaga saqlash
            stmt.setLong(2, chatId);
            stmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }


    public List<User> getTopUsers() {
        List<User> topUsers = new ArrayList<>();
        String query = "SELECT * FROM users ORDER BY score DESC LIMIT 10"; // Eng yuqori ball olgan 10 foydalanuvchi

        try (Connection conn = connect();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                User user = new User();
                user.setTelegramId(rs.getLong("telegram_id"));
                user.setUsername(rs.getString("username"));
                user.setFirstName(rs.getString("first_name"));
                user.setLastName(rs.getString("last_name"));
                user.setCreatedAt(rs.getTimestamp("created_at").toLocalDateTime());
                user.setScore(rs.getInt("score"));
                topUsers.add(user);
            }

        } catch (SQLException e) {
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