package org.example;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;


import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;


public class TelegramBot extends TelegramLongPollingBot {
    private static final String BOT_TOKEN = "7522527691:AAF4zKCEsmcxeqnT_nXqgzhtcREINjJuFj8"; // 🔹 Telegram bot tokeningiz
    private static final String DEEPGRAM_API_KEY = "22ab72b0bffc48bd565bbcf9451ff55fcb2a035c"; // 🔹 Deepgram API kalitingiz
    private boolean autoMode = false;
    private boolean textSpeech = false;
    DBService dbService = new DBService();// 🔹 Auto Transcription Mode
    private final Map<Long, List<TestQuestion>> userTests = new HashMap<>();
    private final Map<Long, List<Questions>> userQuestions = new HashMap<>();
    private final Map<Long, Integer> userScores = new HashMap<>();
    private final Map<Long, Integer> userCurrentQuestion = new HashMap<>();
    private final Map<Long, Boolean> userWaitingForTranslation = new HashMap<>();
    private final Map<Long, String> userSelectedLang = new HashMap<>();
    private static final Pattern MD_V2_PATTERN = Pattern.compile("([_\\*\\[\\]\\(\\)~`>#+\\-=|{}.!])");



    String UserName, firstName, lastName;
    int createdAt;


    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new TelegramBot());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasCallbackQuery()) {
            processCallbackQuery(update.getCallbackQuery());
            return;
        }

        if (update.hasMessage()) {
            Message message = update.getMessage();
            long chatId = message.getChatId();
            String text = message.hasText() ? message.getText() : "";

            chatId = message.getChatId();
            UserName = message.getFrom().getUserName();
            firstName = message.getFrom().getFirstName();
            lastName = message.getFrom().getLastName();
            createdAt = message.getDate();
            LocalDateTime startTime = Instant.ofEpochSecond(createdAt)
                    .atZone(ZoneId.systemDefault()).toLocalDateTime(); // 📆 Uni LocalDateTime ga aylantiramiz


            User user = new User(chatId, UserName, firstName, lastName, startTime);
            dbService.saveUser(user);
            if ((message.hasVoice() || message.hasAudio()) && autoMode) {
                String fileId = message.hasVoice() ? message.getVoice().getFileId() : message.getAudio().getFileId();
                processAudio(chatId, fileId);
                return;
            }

            if (userTests.containsKey(chatId) && userCurrentQuestion.containsKey(chatId)) {
                processUserAnswer(chatId, text);
                return;
            }
            if (message.hasText() && textSpeech) {

                // 🗣 Matnni ovozli faylga o‘girish
                String audioUrl = convertTextToSpeech(text);

                if (audioUrl != null) {
                    sendAudioMessage(chatId, audioUrl);
                } else {
                    sendTextMessage(chatId, "⚠️ Xatolik! Matn ovozga aylantirilmadi.");
                }
            }


            if (userWaitingForTranslation.getOrDefault(chatId, false)) {
                processTranslation(chatId, text);
                return;
            }


            handleTextMessage(chatId, text);
        }
    }

    private void handleTextMessage(long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);

        if (textSpeech) { // ✅ Faqat agar textSpeech true bo‘lsa, ovozli xabarni yuborish
            sendAudioMessage(chatId, text);
            textSpeech = false; // ✅ Keyingi matnlarni ovozga aylantirmaslik uchun flagni o‘chiramiz
            return;
        }
        userWaitingForTranslation.put(chatId, false);

        switch (text) {
            case "/start":
                textSpeech = false;
                autoMode = false;
                sendMessage.setParseMode("HTML");

                sendMessage.setText("<b>👋 Assalomu alaykum, " + firstName + "!</b>\n\n"
                        + "🤖 <b>Men - English Support Bot!</b> Ingliz tilini o‘rganishingizga yordam beradigan aqlli yordamchiman. "
                        + "Quyidagi imkoniyatlardan foydalanishingiz mumkin:\n\n"
                        + "🎙 <b>Ovozdan matnga</b> – ovozli xabarni matn shakliga keltirish\n"
                        + "🌍 <b>Matn tarjimasi</b> – ingliz va o‘zbek tillari orasida tarjima qilish\n"
                        + "📖 <b>Grammatik qoidalar</b> – ingliz tilining asosiy grammatik qoidalarini tushuntirish\n"
                        + "🎯 <b>Testlar</b> – ingliz tilidagi bilim darajangizni aniqlash uchun testlar\n\n"
                        + "📌 <b>Quyidagi bo‘limlardan birini tanlang:</b>");

                sendMessage.setReplyMarkup(getMainInlineKeyboard());
                break;
            case "/help":
                sendPdfFile(chatId);
                break;

            default:
                sendMessage.setParseMode("HTML");
                sendMessage.setText("⚠️ <b>Bunday buyruq mavjud emas!</b>\n\n"
                        + "📌 Iltimos, quyidagi menyudan kerakli bo‘limni tanlang yoki aniqroq so‘rov yuboring.");
                sendMessage.setReplyMarkup(getMainInlineKeyboard());
                break;
        }


        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void processCallbackQuery(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String data = callbackQuery.getData();


        // 📌 **Grammatik qoida tanlandi**
        if (data.equals("grammar_guide")) {
            sendGrammarMenu(chatId);
            return;
        }

        if (data.startsWith("grammar_")) {
            processGrammarSelection(chatId, data);
            return;
        }

        // 📌 **Tarjima bo‘limi**
        if (data.startsWith("translate_")) {
            processTranslationSelection(chatId, data);
            return;
        }

        // 📌 **Testni boshlash**
        if (data.startsWith("level_")) {
            startTest(chatId, data);
            return;
        }

        // 📌 **Foydalanuvchi javobi (test)**
        if (data.startsWith("answer_")) {
            if (userTests.containsKey(chatId)) {
                processTestQuestionAnswer(callbackQuery);
            } else if (userQuestions.containsKey(chatId)) {
                processQuestionsAnswer(callbackQuery);
            }
            return;
        }


        handleInlineMenuActions(chatId, callbackQuery);
    }

    private void handleInlineMenuActions(long chatId, CallbackQuery callbackQuery) {
        String data = callbackQuery.getData();
        EditMessageText message = new EditMessageText();
        message.setChatId(chatId);
        message.setMessageId(callbackQuery.getMessage().getMessageId());

        switch (data) {
            case "convert_voice" -> {
                autoMode = true;
                message.setParseMode("HTML");
                message.setText("""
                        🎙 <b>Ovozdan matnga konvertatsiya</b>
                            
                        📌 Bot yuborgan ovozli xabaringizni matn shakliga keltirib beradi.
                        
                        ✨ <i>Ovozli xabar yuboring va matn sifatida qaytarib oling!</i>
                        """);
            }
            case "instant_translation" -> {
                message.setParseMode("HTML");
                message.setText("""
                            🌍 <b>Matn tarjima qilish</b>
                        
                            📌 Inglizcha ↔ O‘zbekcha matnlarni tarjima qilish imkoniyati.
                        
                            ✅ <i>Til yo‘nalishini tanlang va tarjima qilmoqchi bo‘lgan matnni yuboring!</i>
                        """);
                message.setReplyMarkup(getTranslateInlineKeyboard());
                userWaitingForTranslation.put(chatId, true);
            }
            case "level_test" -> {
                message.setParseMode("HTML");
                message.setText("""
                            📊 <b>Ingliz tili testlari</b>
                            
                            📌 CEFR darajalari bo‘yicha testlar orqali bilimingizni sinang!
                        
                            🔹 A1 – Beginner
                            🔹 A2 – Elementary
                            🔹 B1 – Intermediate
                            🔹 B2 – Upper-Intermediate
                            🔹 C1 – Advanced
                            🔹 C2 – Proficient
                        
                            ✅ <i>Darajani tanlang va testni boshlang!</i>
                        """);
                message.setReplyMarkup(getLevelSelectionInlineKeyboard());
            }
            case "voice_Menu" -> {
                message.setParseMode("HTML");
                message.setText("""
                              🎙 <b>Ovozli xizmatlar</b>
                            
                            📌 Siz quyidagi funksiyalardan birini tanlashingiz mumkin:
                        
                            🔹 <b>Ovozdan matnga</b> – ovozli xabarni matn shakliga aylantirish.
                            
                            🔹 <b>Matndan ovozga</b> – matnni ovoz shakliga keltirish.
                        
                            ✅ <i>Ishlatmoqchi bo‘lgan funksiyani tanlang!</i>
                        """);
                message.setReplyMarkup(getVoiseInlineKeyboard());
            }
            case "convert_text" -> {
                textSpeech = true;
                message.setParseMode("HTML");
                message.setText("""
                        📜 <b>Matndan ovozga aylantirish</b>
                            
                        📌 Yozgan matningizni audio formatga o‘girish imkoniyati mavjud.
                        
                        ✅ <i>Matn yuboring va bot uni audio shaklga keltirib beradi!</i>
                        """);
            }
            case "Grammer_reset" -> {
                message.setParseMode("HTML");
                message.setText("""
                            📖 <b>Ingliz tili grammatikasi</b>
                            
                            📌 Grammatik qoidalarni o‘rganing va bilimlaringizni mustahkamlang.
                        
                            ✅ <i>Mavzuni tanlang va qoidalarni o‘rganishni boshlang!</i>
                        """);
                message.setReplyMarkup(sendGrammarList(chatId));
            }
            case "about_help" -> {
                sendPdfFile(chatId);
            }
            case "facts" -> sendRandomFact(chatId);
            case "back_main" -> {
                message.setParseMode("HTML");
                message.setText("""
                         🤖 Men - English Support Bot! Ingliz tilini o‘rganishingizga yordam beradigan aqlli yordamchiman. Quyidagi imkoniyatlardan foydalanishingiz mumkin:
                        
                        🎙 Ovozdan matnga – ovozli xabarni matn shakliga keltirish
                        
                        🌍 Matn tarjimasi – ingliz va o‘zbek tillari orasida tarjima qilish
                            
                        📖 Grammatik qoidalar – ingliz tilining asosiy grammatik qoidalarini tushuntirish
                            
                        🎯 Testlar – ingliz tilidagi bilim darajangizni aniqlash uchun testlar
                        
                        📌 Quyidagi bo‘limlardan birini tanlang:
                        """);
                message.setReplyMarkup(getMainInlineKeyboard());
            }
            case "audio_reset" -> sendAudioRetryMessage(chatId);
            case "retry_test" -> retryTest(chatId);

            case "top" -> sendTopUsers(chatId);

            case "questions" -> sendQuestions(chatId);

            case "Questions_reset" -> sendQuestions(chatId);

            default -> {
                message.setParseMode("HTML");
                message.setText("""
                            ⚠️ <b>Noto‘g‘ri buyruq!</b>
                            📌 Kerakli bo‘limni tanlang yoki aniqroq so‘rov yuboring.
                        
                            ✅ <i>Asosiy menyudan kerakli bo‘limni tanlang!</i>
                        """);
                message.setReplyMarkup(getMainInlineKeyboard());
            }
        }


        sendEditMessage(message);
    }




    private void startTest(long chatId, String levelData) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);

        // 🔹 Tugmadan kelgan ma'lumotni CEFR darajasiga o‘tkazish
        Map<String, String> levelMap = Map.of(
                "level_a1", "A1 (Boshlang‘ich)",
                "level_a2", "A2 (Elementar)",
                "level_b1", "B1 (O‘rta)",
                "level_b2", "B2 (O‘rta-yuqori)",
                "level_c1", "C1 (Ilg‘or)",
                "level_c2", "C2 (Mukammal)"
        );

        String levelKey = levelMap.getOrDefault(levelData, "");

        if (levelKey.isEmpty()) {
            sendMessage.setText("⚠️ <b>Iltimos, mavjud darajalardan birini tanlang!</b> ✅");
            sendMessage.setParseMode("HTML");
            sendMessage.setReplyMarkup(getLevelSelectionInlineKeyboard());
            try {
                execute(sendMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        // 🔹 Testlarni bazadan olish
        List<TestQuestion> questions = dbService.getTenRandomTestsByLevel(levelKey.substring(0, 2));
        if (questions.isEmpty()) {
            sendMessage.setText("❌ <b>Kechirasiz!</b> Tanlangan daraja uchun testlar mavjud emas. 🔄 Boshqa darajani tanlang.");
            sendMessage.setParseMode("HTML");
            sendMessage.setReplyMarkup(getLevelSelectionInlineKeyboard());
            try {
                execute(sendMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
            return;
        }

        // 🔹 Testni boshlash
        userTests.put(chatId, questions);
        userScores.put(chatId, 0);
        userCurrentQuestion.put(chatId, 0);

        sendMessage.setText("📚 <b>" + levelKey + "</b> darajadagi test boshlandi!\n\n📝 Sizga 10 ta savol beriladi. Har bir to‘g‘ri javob 1 ballga teng.\n\n🔎 Tayyor bo‘lsangiz, boshladik!");
        sendMessage.setParseMode("HTML");

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        sendNextTestQuestion(chatId);
    }

    private void sendNextTestQuestion(long chatId) {
        List<TestQuestion> questions = userTests.get(chatId);
        int currentIndex = userCurrentQuestion.getOrDefault(chatId, 0);

        // ❗ Xatolikni oldini olish
        if (questions == null || questions.isEmpty()) {
            sendTextMessage(chatId, "❌ <b>Xatolik:</b> Savollar topilmadi yoki mavjud emas.");
            return;
        }

        // 🔹 Test tugaganligini tekshirish
        if (currentIndex >= questions.size()) {
            finishTest(chatId);
            return;
        }

        TestQuestion question = questions.get(currentIndex);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        row1.add(createInlineButton("A) " + question.getOptionA(), "answer_A_" + question.getId()));
        row1.add(createInlineButton("B) " + question.getOptionB(), "answer_B_" + question.getId()));

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        row2.add(createInlineButton("C) " + question.getOptionC(), "answer_C_" + question.getId()));
        row2.add(createInlineButton("D) " + question.getOptionD(), "answer_D_" + question.getId()));

        keyboard.add(row1);
        keyboard.add(row2);
        inlineKeyboardMarkup.setKeyboard(keyboard);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setParseMode("HTML");
        sendMessage.setText("📌 <b>Test " + (currentIndex + 1) + "/" + questions.size() + ":</b>\n\n"
                + question.getQuestion() + "\n\n"
                + "👇 <b>Javobni tanlang</b>:");

        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(sendMessage);
            userCurrentQuestion.put(chatId, currentIndex + 1); // 🔹 Keyingi savolga o'tish
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void checkAndProcessAnswer(long chatId, String userAnswer) {
        List<TestQuestion> questions = userTests.get(chatId);
        int currentIndex = userCurrentQuestion.get(chatId);

        if (currentIndex < questions.size()) {
            TestQuestion currentQuestion = questions.get(currentIndex);
            boolean isCorrect = dbService.checkAnswer(currentQuestion.getId(), userAnswer.toUpperCase().charAt(0));

            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setParseMode("HTML"); // ✅ HTML formatini ishlatish

            if (isCorrect) {
                sendMessage.setText("✅ <b>To‘g‘ri javob!</b> 🎉");
            } else {
                sendMessage.setText(
                        "❌ <b>Noto‘g‘ri javob!</b> 😞\n\n" +
                                "✔️ <b>To‘g‘ri javob:</b> <code>" + currentQuestion.getCorrectAnswer() + "</code>"
                );
            }

            try {
                execute(sendMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

            // ✅ Keyingi savolga o‘tish yoki testni tugatish
            userCurrentQuestion.put(chatId, currentIndex + 1);
            if (currentIndex + 1 < questions.size()) {
                sendNextTestQuestion(chatId);
            } else {
                finishTest(chatId); // ✅ Test tugasa yakunlash funksiyasini chaqirish
            }
        }
    }

    private void processTestQuestionAnswer(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String userAnswer = callbackQuery.getData().substring(7); // "answer_A_123" -> "A"

        List<TestQuestion> questions = userTests.get(chatId);
        int currentIndex = userCurrentQuestion.getOrDefault(chatId, 0) - 1; // ✅ Indexni to‘g‘ri olish

        // ❗ Xatolikni oldini olish
        if (questions == null || questions.isEmpty() || currentIndex < 0 || currentIndex >= questions.size()) {
            sendTextMessage(chatId, "⚠️ <b>Xatolik:</b> Savol topilmadi yoki noto‘g‘ri indeks.");
            return;
        }

        TestQuestion currentQuestion = questions.get(currentIndex);
        boolean isCorrect = dbService.checkAnswer(currentQuestion.getId(), userAnswer.charAt(0));

        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId);
        editMessage.setMessageId(callbackQuery.getMessage().getMessageId());
        editMessage.setParseMode("HTML");

        if (isCorrect) {
            int currentScore = userScores.getOrDefault(chatId, 0) + 1; // ✅ Oldingi ball ustiga +1 qo‘shish
            userScores.put(chatId, currentScore); // ✅ Foydalanuvchining ballini yangilash
            editMessage.setText("✅ <b>To‘g‘ri javob!</b> 🎉");
        } else {
            editMessage.setText("❌ <b>Noto‘g‘ri javob!</b> 😞\n\n"
                    + "✔️ <b>To‘g‘ri javob:</b> <code>" + currentQuestion.getCorrectAnswer() + "</code>\n\n"
                    + "🔄 Keyingi savolga o‘ting! 💪");
        }

        try {
            execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        // 🔹 Keyingi savolga o‘tish yoki testni tugatish
        userCurrentQuestion.put(chatId, currentIndex + 1);
        if (currentIndex + 1 < questions.size()) {
            sendNextTestQuestion(chatId);
        } else {
            finishTest(chatId); // ✅ Test tugaganida yakuniy natijani chiqarish
        }
    }

    private void finishTest(long chatId) {
        int score = userScores.getOrDefault(chatId, 0);
        int totalQuestions = userTests.get(chatId).size();
        double percentage = ((double) score / totalQuestions) * 100;

        StringBuilder resultMessage = new StringBuilder();
        resultMessage.append("<b>✅ Test Yakunlandi!</b> 🎯\n\n")
                .append("<b>📊 Sizning natijangiz:</b> <code>").append(score).append("/").append(totalQuestions).append("</code>\n")
                .append("<b>📈 Foiz:</b> <code>").append(String.format("%.1f", percentage)).append("%</code>\n\n");

        if (percentage >= 60) {
            resultMessage.append("🎉 <b>Tabriklaymiz!</b> Sizning bilimingiz yuqori darajada! 🔥");
        } else {
            resultMessage.append("❌ <b>Afsuski, siz imtihondan o'tolmadingiz.</b> 😞\n📚 <i>Ko'proq mashq qiling!</i>");
        }

        resultMessage.append("\n\n❗ <i>Eslatib o'tamiz, bu sizning aniq darajangiz emas.</i> \n")
                .append("📚 <b>O'qishlaringizga omad tilayman! 😊</b>");

        InlineKeyboardMarkup keyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton retryButton = new InlineKeyboardButton();
        retryButton.setText("🔄 Testni Qayta Boshlash");
        retryButton.setCallbackData("retry_test");
        row1.add(retryButton);

        List<InlineKeyboardButton> row2 = new ArrayList<>();
        InlineKeyboardButton mainMenuButton = new InlineKeyboardButton();
        mainMenuButton.setText("🏠 Asosiy Menyu");
        mainMenuButton.setCallbackData("back_main");
        row2.add(mainMenuButton);

        buttons.add(row1);
        buttons.add(row2);
        keyboard.setKeyboard(buttons);

        // ✅ Xabarni yuborish
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setParseMode("HTML"); // ✅ HTML format ishlatish
        sendMessage.setText(resultMessage.toString());
        sendMessage.setReplyMarkup(keyboard);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        // 🔹 Test ma'lumotlarini tozalash
        userTests.remove(chatId);
        userScores.remove(chatId);
        userCurrentQuestion.remove(chatId);
    }

    private void finishQuesting(long chatId) {
        int score = userScores.getOrDefault(chatId, 0);
        dbService.updateUserScore(chatId, score);

        // 🔹 Progres panel
        String progressBar = "🏅 ";
        for (int i = 1; i <= 10; i++) {
            if (i <= score) {
                progressBar += "🟩"; // ✅ To‘g‘ri javoblar
            } else {
                progressBar += "⬜"; // ❌ Noto‘g‘ri javoblar
            }
        }

        String resultMessage = "🎉 <b>Test yakunlandi!</b>\n\n"
                + "📊 <b>Natijangiz:</b>\n\n"
                + progressBar + "\n\n"
                + "📢 Siz <b>" + score + "</b> ta savolga to‘g‘ri javob berdingiz!\n\n"
                + "💪 Yana test ishlashni xohlaysizmi?";

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setParseMode("HTML");
        sendMessage.setText(resultMessage);
        sendMessage.setReplyMarkup(getRetryTestKeyboard());

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void processQuestionsAnswer(CallbackQuery callbackQuery) {
        long chatId = callbackQuery.getMessage().getChatId();
        String userAnswer = callbackQuery.getData().substring(7); // "answer_A_123" -> "A"

        List<Questions> questions = userQuestions.get(chatId);
        int currentIndex = userCurrentQuestion.getOrDefault(chatId, 0) - 1;

        if (questions == null || questions.isEmpty() || currentIndex < 0 || currentIndex >= questions.size()) {
            sendTextMessage(chatId, "⚠️ <b>Xatolik:</b> Savol topilmadi yoki noto‘g‘ri indeks.");
            return;
        }

        Questions currentQuestion = questions.get(currentIndex);
        boolean isCorrect = dbService.checkAnswerForQuestions(currentQuestion.getId(), userAnswer.charAt(0));

        EditMessageText editMessage = new EditMessageText();
        editMessage.setChatId(chatId);
        editMessage.setMessageId(callbackQuery.getMessage().getMessageId());
        editMessage.setParseMode("HTML");

        int currentScore = userScores.getOrDefault(chatId, 0); // ✅ Avvalgi natijani olish
        if (isCorrect) {
            currentScore++; // ✅ Ballni faqat bitta qo‘shish
            userScores.put(chatId, currentScore); // ✅ Mahalliy xotirada yangilash
            dbService.updateUserScore(chatId, currentScore); // ✅ Faqat umumiy natijani yangilash
            editMessage.setText("✅ <b>To‘g‘ri javob!</b> Sizga <b>+1 ball</b> qo‘shildi. 🎉");
        } else {
            editMessage.setText("❌ <b>Noto‘g‘ri javob!</b> 😞\n\n"
                    + "❓ <b>Savol:</b> " + currentQuestion.getQuestion() + "\n\n"
                    + "✔️ <b>To‘g‘ri javob:</b> <code>" + currentQuestion.getCorrectOption() + "</code>\n\n"
                    + "🔄 Keyingi savolga o‘ting! 💪");
        }

        try {
            execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        // ✅ Keyingi savolga o'tish yoki testni tugatish
        if (currentIndex + 1 < questions.size()) {
            sendNextQuestion(chatId);
        } else {
            finishQuesting(chatId);
        }
    }

    private void sendQuestions(long chatId) {
        List<Questions> questions = dbService.getRandomQuestions(10); // ✅ 10 ta tasodifiy savol olish

        if (questions == null || questions.isEmpty()) {
            sendTextMessage(chatId, "❌ Xatolik: Test savollari topilmadi.");
            return;
        }

        userQuestions.put(chatId, questions);
        userScores.put(chatId, 0);
        userCurrentQuestion.put(chatId, 0);

        sendNextQuestion(chatId);
    }

    private void sendNextQuestion(long chatId) {
        List<Questions> questions = userQuestions.get(chatId);
        int currentIndex = userCurrentQuestion.getOrDefault(chatId, 0);

        if (questions == null || questions.isEmpty() || currentIndex >= questions.size()) {
            finishQuesting(chatId);
            return;
        }

        Questions question = questions.get(currentIndex);



        // 🔹 Inline tugmalarni yaratish
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboard = new ArrayList<>();

        keyboard.add(List.of(createInlineButton("A) " + question.getOptionA(), "answer_A_" + question.getId())));
        keyboard.add(List.of(createInlineButton("B) " + question.getOptionB(), "answer_B_" + question.getId())));
        keyboard.add(List.of(createInlineButton("C) " + question.getOptionC(), "answer_C_" + question.getId())));
        keyboard.add(List.of(createInlineButton("D) " + question.getOptionD(), "answer_D_" + question.getId())));

        inlineKeyboardMarkup.setKeyboard(keyboard);


        // ✅ Savolni yuborish uchun matnni chiroyli shakllantirish
        String questionText = """
            📌 <b>Test %d/%d</b>
            
            🔥<b>Faqat oldinga! Harakat - muvaffaqiyat kaliti! </b>


            ❓ <b>Savol:</b> %s

            
            🔽 <b>Javobni tanlang</b>:
            """.formatted(currentIndex + 1, questions.size(), question.getQuestion());


        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setParseMode("HTML");
        sendMessage.setText(questionText);

        sendMessage.setReplyMarkup(inlineKeyboardMarkup);

        try {
            execute(sendMessage);
            userCurrentQuestion.put(chatId, currentIndex + 1); // ✅ Keyingi savolga o'tish
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendTopUsers(long chatId) {
        List<User> topUsers = dbService.getTopUsers(); // 🔹 Eng yaxshi foydalanuvchilarni olish

        if (topUsers.isEmpty()) {
            sendTextMessage(chatId, "📊 <b>Top foydalanuvchilar hali yo‘q!</b>");
            return;
        }

        StringBuilder messageText = new StringBuilder("🏆 <b>Top 10 foydalanuvchilar:</b>\n\n");

        int rank = 1;
        for (User user : topUsers) {
            String status = switch (rank) {
                case 1 -> "🥇 <b>Grand Master</b> 🎖"; // 🏆 1-o‘rin
                case 2 -> "🥈 <b>Master</b> 🏅"; // 2-o‘rin
                case 3 -> "🥉 <b>Expert</b> 🎯"; // 3-o‘rin
                case 4, 5, 6, 7, 8, 9, 10 -> "🎖 <b>Advanced Learner</b>"; // 4-10-o‘rin
                default -> "⭐ <b>Beginner</b>"; // Keyingi o‘rinlar
            };

            messageText.append(rank++).append(". ")
                    .append(user.getFirstName() != null ? "<b>" + user.getFirstName() + "</b>" : "<i>Ism yo‘q</i>")
                    .append(" - ").append(user.getScore()).append(" ball ")
                    .append("\n   ").append(status).append("\n\n");
        }

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setParseMode("HTML");
        sendMessage.setText(messageText.toString());
        sendMessage.setReplyMarkup(getMainMenuInlineKeyboard());

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendGrammarMenu(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("📖 *Grammatik bo‘limdan foydalanish uchun mavzuni tanlang:*");

        InlineKeyboardMarkup markup = sendGrammarList(chatId);
        if (markup.getKeyboard().isEmpty()) {
            sendTextMessage(chatId, "⚠️ Xatolik! Grammatik qoidalar topilmadi.");
            return;
        }

        message.setReplyMarkup(markup);
        sendMessage(message);
    }

    private void processGrammarSelection(long chatId, String data) {
        String ruleIdStr = data.replace("grammar_", "").trim();
        if (ruleIdStr.matches("\\d+")) {
            int ruleId = Integer.parseInt(ruleIdStr);
            sendGrammarRule(chatId, ruleId);
        } else {
            sendTextMessage(chatId, "⚠️ *Noto‘g‘ri grammatika ID!* Iltimos, tugmalardan birini tanlang.");
        }
    }

    private void processTranslationSelection(long chatId, String data) {
        userSelectedLang.put(chatId, data.replace("translate_", ""));
        userWaitingForTranslation.put(chatId, true);

        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("📝 Tarjima qilmoqchi bo‘lgan so‘z yoki matnni kiriting:");

        sendMessage(message);
    }

    private void sendRandomFact(long chatId) {
        String fact = fetchRandomFact();
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode("HTML");
        message.setText("🧠 <b>Bugungi qiziqarli fakt:</b>\n\n" + fact + "\n\n👉 <i>Boshqa bir fakt o‘rganishni xohlaysizmi?</i>");
        message.setReplyMarkup(getFactInlineKeyboard());

        sendMessage(message);
    }

    private void sendAudioRetryMessage(long chatId) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setParseMode("HTML");
        message.setText("🎤 <b>Audio faylingizni yuboring!</b>\n\n📌 Bot ovozli xabarni matnga aylantirib beradi.");
        message.setReplyMarkup(getVoiseInlineKeyboard());
        sendMessage(message);
    }

    private void sendMessage(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            System.out.println("❌ Xabar yuborishda xatolik yuz berdi: " + e.getMessage());
        }
    }

    private void sendEditMessage(EditMessageText message) {


        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            System.out.println("❌ Xabarni yangilashda xatolik: " + e.getMessage());
        }
    }

    private void sendPdfFile(long chatId) {
        File pdfFile = new File("src/main/java/org/example/TelegramBot.pdf"); // PDF fayl yo‘li

        if (!pdfFile.exists()) {
            sendTextMessage(chatId, "❌ Xatolik: PDF fayl topilmadi.");
            return;
        }

        SendDocument sendDocument = new SendDocument();
        sendDocument.setChatId(chatId);
        sendDocument.setDocument(new InputFile(pdfFile));
        sendDocument.setCaption("📄 English Support Bot haqida qo‘llanma\n" +
                "Qo'lanma to'liq emas !");
        try {
            execute(sendDocument);

        } catch (TelegramApiException e) {
            e.printStackTrace();
            sendTextMessage(chatId, "⚠️ Xatolik yuz berdi: PDF yuborilmadi.");
        }
    }

    private void sendGrammarRule(long chatId, int ruleId) {
        GrammarRule rule = DBService.getGrammarRuleById(ruleId);

        if (rule != null) {
            StringBuilder response = new StringBuilder("📖 *" + escapeMarkdownV2(rule.getRuleName()) + "*\n\n");
            response.append("📌 *Tavsif:* " + escapeMarkdownV2(rule.getDescription()) + "\n\n");

            if (rule.getNegativeExample() != null && !rule.getNegativeExample().isEmpty()) {
                response.append("❌ *Noto‘g‘ri misol:* \n")
                        .append(escapeMarkdownV2(rule.getNegativeExample().replace("\\n", "\n"))).append("\n\n");
            }
            if (rule.getQuestionExample() != null && !rule.getQuestionExample().isEmpty()) {
                response.append("❓ *Savol misoli:* \n")
                        .append(escapeMarkdownV2(rule.getQuestionExample().replace("\\n", "\n"))).append("\n\n");
            }
            if (rule.getYoutubeLink() != null && !rule.getYoutubeLink().isEmpty()) {
                response.append("▶️ [📺 Video qo‘llanma](").append(rule.getYoutubeLink()).append(")\n");
            }

            SendMessage message = new SendMessage();
            message.setChatId(chatId);
            message.setText(response.toString());
            message.setParseMode("MarkdownV2");
            message.setReplyMarkup(getGrammerBackMenu());

            try {
                execute(message);
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        } else {
            sendTextMessage(chatId, "⚠️ *Xatolik!* Grammatik qoida topilmadi.");
        }
    }

    private void sendAudioMessage(long chatId, String audioPath) {
        File audioFile = new File(audioPath);


        // 🔹 Fayl hajmini tekshirish (Telegram 50MB cheklov)
        if (audioFile.length() > 50 * 1024 * 1024) {
            sendTextMessage(chatId, "⚠️ Xatolik: Fayl hajmi 50MB dan oshib ketdi!");
            return;
        }

        SendAudio sendAudio = new SendAudio();
        sendAudio.setChatId(chatId);
        sendAudio.setAudio(new InputFile(audioFile));
        sendAudio.setCaption("🎵 Sizning audio faylingiz tayyor!");
        sendAudio.setReplyMarkup(getAudioBackMenu());

        try {
            execute(sendAudio); // 🔹 Audio yuborish
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }


        // 🔹 Fayl yuborilgandan keyin o‘chirish
        if (audioFile.delete()) {
            System.out.println("✅ Fayl o‘chirildi: " + audioPath);
        } else {
            audioFile.deleteOnExit();
            System.out.println("⚠️ Fayl o‘chirishda muammo: " + audioPath);
        }
    }

    private void processTranslation(long chatId, String text) {
        userWaitingForTranslation.put(chatId, false); // 🔹 Tarjima rejimini o‘chiramiz

        // 🔹 Foydalanuvchi til juftligini tanlaganligini tekshirish
        String langPair = userSelectedLang.getOrDefault(chatId, "en_uz").trim(); // ✅ Standart qiymat: inglizcha → o‘zbekcha

        // 🔹 Til juftligini ajratib olish
        String[] langParts = langPair.split("[_-]");
        if (langParts.length != 2) {
            sendTextMessage(chatId, "⚠️ Xatolik! Tarjima uchun noto‘g‘ri til juftligi tanlandi.");
            return;
        }

        // 🔹 Tarjima qilish
        String translatedText = translateText(text, langPair);

        // 🔹 Tarjima natijasi matnini shakllantirish
        StringBuilder responseMessage = new StringBuilder();
        responseMessage.append("<b>🔹 Asl matn:</b> <i>").append(text).append("</i>\n\n");
        responseMessage.append("<b>✅ Tarjima:</b> <i>").append(translatedText).append("</i>\n\n");
        responseMessage.append("🌍 Til yo‘nalishi: <b>").append(langParts[0].toUpperCase()).append(" → ").append(langParts[1].toUpperCase()).append("</b>");

        // 🔹 Tarjima natijasini yuborish
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(responseMessage.toString());
        sendMessage.setParseMode("HTML");
        sendMessage.setReplyMarkup(getTranslationInlineKeyboard());

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void retryTest(long chatId) {
        // 🔹 Foydalanuvchi natijalarini o‘chirish
        userTests.remove(chatId);
        userScores.remove(chatId);
        userCurrentQuestion.remove(chatId);

        // 🔹 Foydalanuvchini test tanlash menyusiga qaytarish
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setParseMode("HTML"); // ✅ HTML formatini qo‘llash
        sendMessage.setText(
                "🔄 <b>Testni qayta boshlash</b>\n\n" +
                        "📊 <i>Iltimos, darajani tanlang:</i>"
        );
        sendMessage.setReplyMarkup(getLevelSelectionInlineKeyboard()); // ✅ Inline daraja tanlash menyusi

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void updateProgress(long chatId, int messageId, int progress) {
        try {
            String progressMessage;
            switch (progress) {
                case 0 -> progressMessage = "🎙 <b>Ovoz qabul qilindi!</b>\n🔄 <i>Jarayon boshlandi...</i>";
                case 25 ->
                        progressMessage = "📡 <b>Signal qabul qilinyapti...</b>\n⏳ <i>Tahlil jarayoni davom etmoqda...</i>";
                case 50 ->
                        progressMessage = "🎧 <b>AI ovozingizni tinglamoqda...</b>\n🛠 <i>Matn shakliga moslashtirilmoqda...</i>";
                case 75 -> progressMessage = "✍ <b>So‘zlar yozib olinmoqda...</b>\n📜 <i>Matn shakllantirilmoqda...</i>";
                case 100 -> progressMessage = "✅ <b>Tayyor!</b>\n📄 <i>Ovozli xabaringiz matnga aylandi!</i>";
                default -> progressMessage = "⏳ <b>Jarayon davom etmoqda...</b>";
            }

            EditMessageText editMessage = new EditMessageText();
            editMessage.setChatId(chatId);
            editMessage.setMessageId(messageId);
            editMessage.setParseMode("HTML");
            editMessage.setText(progressMessage + "\n\n📊 <b>Tugallanish darajasi:</b> <code>" + progress + "%</code>");

            execute(editMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void processAudio(long chatId, String fileId) {
        try {
            // 🔹 Foydalanuvchiga transkripsiya jarayoni boshlanganini bildirish
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setParseMode("HTML"); // ✅ HTML formatidan foydalanamiz
            sendMessage.setText("🎙 <b>Ovozli xabaringiz tahlil qilinmoqda...</b>\n\n⏳ <i>Jarayon: 0%</i>");
            Message message = execute(sendMessage);

            int messageId = message.getMessageId();

            // 🔹 Bosqichma-bosqich jarayonni yangilash
            Thread.sleep(1000);
            updateProgress(chatId, messageId, 0);
            Thread.sleep(1000);
            updateProgress(chatId, messageId, 25);
            Thread.sleep(1000);
            updateProgress(chatId, messageId, 50);
            Thread.sleep(1000);
            updateProgress(chatId, messageId, 75);

            // 🔹 Audio fayl URL manzilini olish va Deepgram orqali transkripsiya qilish
            String audioUrl = getAudioFileUrl(fileId);
            String transcribedText = transcribeAudioWithDeepgram(audioUrl);

            // 🔹 Agar transkripsiya bo‘sh bo‘lsa, xatolik xabari chiqarish
            if (transcribedText == null || transcribedText.trim().isEmpty()) {
                transcribedText = "⚠️ <b>Xatolik:</b> Ovozli xabarda matn aniqlanmadi yoki transkripsiya amalga oshmadi.";
            }

            updateProgress(chatId, messageId, 100);

            try {
                // 🔹 Yakuniy natijani foydalanuvchiga yuborish
                EditMessageText finalMessage = new EditMessageText();
                finalMessage.setChatId(chatId);
                finalMessage.setMessageId(messageId);
                finalMessage.setParseMode("HTML"); // ✅ HTML formatidan foydalanamiz
                finalMessage.setText(
                        "✅ <b>Transkripsiya yakunlandi!</b>\n\n" +
                                "🗣 <b>Aniqlangan matn:</b>\n<pre>" + transcribedText + "</pre>\n\n" +
                                "📥 <i>Matndan foydalanishingiz yoki nusxalashingiz mumkin.</i>"
                );
                finalMessage.setReplyMarkup(getMainInlineKeyboard());

                execute(finalMessage);
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendTextMessage(long chatId, String s) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(s);

        try {
            sendMessage.setReplyMarkup(getMainMenuInlineKeyboard());
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private InlineKeyboardMarkup getMainInlineKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("🎙 Ovoz → Matn", "voice_Menu", "\uD83C\uDF0D Matn Tarjimasi", "instant_translation"));
        keyboardRows.add(createInlineButtonRow("\uD83D\uDCD6 Grammatika Qoidalari", "grammar_guide", "\uD83D\uDCDD Bilimni Sinash", "level_test"));
        keyboardRows.add(createInlineButtonRow("ℹ\uFE0F Yordam / Qo‘llanma", "about_help", "🎲 Faktlar", "facts"));
        keyboardRows.add(createInlineButtonRow("❓ Savollar","questions","\uD83C\uDFC6 Liderlar","top"));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getVoiseInlineKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("🎙 Ovoz → Matn", "convert_voice", "📝 Matn → Ovoz", "convert_text"));
        keyboardRows.add(Collections.singletonList(createInlineButton("⬅️ Asosiy menyu", "back_main")));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getLevelSelectionInlineKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("🔵 A1", "level_a1", "🟢 A2", "level_a2"));
        keyboardRows.add(createInlineButtonRow("🟡 B1", "level_b1", "🟠 B2", "level_b2"));
        keyboardRows.add(createInlineButtonRow("🔴 C1", "level_c1", "⚫ C2", "level_c2"));
        keyboardRows.add(Collections.singletonList(createInlineButton("⬅️ Asosiy menyu", "back_main")));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getFactInlineKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("🔄 Yana fakt", "facts", "⬅️ Asosiy menyu", "back_main"));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getGrammerBackMenu() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("🔄 Qayta urinish", "Grammer_reset", "🏠 Asosiy menyu", "back_main"));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getRetryTestKeyboard(){
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("🔄 Qayta Topshirish", "Questions_reset", "🏠 Asosiy menyu", "back_main"));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getAudioBackMenu() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("🔄 Qayta urinish", "audio_reset"));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getTranslationInlineKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("🔄 Qayta tarjima", "instant_translation", "🏠 Asosiy menyu", "back_main"));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getTranslateInlineKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("🇬🇧 English → Uzbek", "translate_en_uz"));
        keyboardRows.add(createInlineButtonRow("🇺🇿 Uzbek → English", "translate_uz_en"));
        keyboardRows.add(createInlineButtonRow("⬅️ Asosiy menyuga qaytish", "back_main"));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getMainMenuInlineKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("⬅️ Asosiy menyuga qaytish", "back_main"));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private List<InlineKeyboardButton> createInlineButtonRow(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);

        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(button);

        return row;

    }

    private InlineKeyboardMarkup sendGrammarList(long chatId) {
        String query = "SELECT id, rule_name FROM grammar_rules ORDER BY id ASC";
        InlineKeyboardMarkup markup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        try (Connection conn = DBService.connect();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                int ruleId = rs.getInt("id");
                String ruleName = rs.getString("rule_name");

                InlineKeyboardButton button = new InlineKeyboardButton();
                button.setText(ruleName);
                button.setCallbackData("grammar_" + ruleId);

                buttons.add(Collections.singletonList(button)); // Har bir tugmani alohida qatorga joylash
            }
            InlineKeyboardButton backButton = new InlineKeyboardButton();
            backButton.setText("⬅️ Asosiy menyuga qaytish");
            backButton.setCallbackData("back_main");
            buttons.add(Collections.singletonList(backButton));

        } catch (Exception e) {
            e.printStackTrace();
            sendTextMessage(chatId, "⚠️ Xatolik! Grammatik qoidalarni yuklashda muammo yuz berdi.");
        }

        markup.setKeyboard(buttons);
        return markup; // ✅ InlineKeyboardMarkup qaytariladi
    }

    private List<InlineKeyboardButton> createInlineButtonRow(String text1, String callbackData1, String text2, String callbackData2) {
        List<InlineKeyboardButton> row = new ArrayList<>();
        row.add(createInlineButton(text1, callbackData1));
        row.add(createInlineButton(text2, callbackData2));
        return row;
    }

    private InlineKeyboardButton createInlineButton(String text, String callbackData) {
        InlineKeyboardButton button = new InlineKeyboardButton();
        button.setText(text);
        button.setCallbackData(callbackData);
        return button;
    }

    private String getAudioFileUrl(String fileId) throws TelegramApiException {
        GetFile getFile = new GetFile();
        getFile.setFileId(fileId);
        org.telegram.telegrambots.meta.api.objects.File file = execute(getFile);
        return "https://api.telegram.org/file/bot" + getBotToken() + "/" + file.getFilePath();
    }

    private void processUserAnswer(long chatId, String userAnswer) {
        checkAndProcessAnswer(chatId, userAnswer);
    }

    public String translateText(String text, String langPair) {
        try {
            String[] langParts = langPair.split("[_-]");
            if (langParts.length != 2) {
                return "⚠️ Xato: Tarjima tili noto‘g‘ri formatda.";
            }

            String fromLang = langParts[0].trim();
            String toLang = langParts[1].trim();

            // ✅ API URL (`v1` versiya uchun)
            String apiUrl = "https://google-translate113.p.rapidapi.com/api/v1/translator/text";

            // ✅ JSON so‘rov yaratish (to‘g‘ri format)
            String requestBody = String.format("{\"from\":\"%s\", \"to\":\"%s\", \"text\":\"%s\"}", fromLang, toLang, text);

            // ✅ HTTP so‘rovni yuborish
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Content-Type", "application/json")
                    .header("X-RapidAPI-Key", "e362de5a5bmsha229a92cefdfe68p168425jsnb5dac5a32abe") // API kalitingizni qo‘ying!
                    .header("X-RapidAPI-Host", "google-translate113.p.rapidapi.com")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(response.body());

            // ✅ Tarjima natijasini olish (`trans` maydonidan)
            if (node.has("trans")) {
                return node.get("trans").asText();
            } else {
                return "⚠️ Tarjima topilmadi.";
            }

        } catch (Exception e) {
            e.printStackTrace();
            return "❌ Tarjima qilishda xatolik yuz berdi! 🚨";
        }
    }

    private String transcribeAudioWithDeepgram(String audioUrl) throws IOException {
        String apiUrl = "https://api.deepgram.com/v1/listen";
        String jsonBody = "{ \"url\": \"" + audioUrl + "\", \"language\": \"en\" }";

        try (CloseableHttpClient client = HttpClients.createDefault()) {
            HttpPost httpPost = new HttpPost(apiUrl);
            httpPost.setHeader("Authorization", "Token " + DEEPGRAM_API_KEY);
            httpPost.setHeader("Content-Type", "application/json");
            httpPost.setEntity(new StringEntity(jsonBody));

            try (CloseableHttpResponse response = client.execute(httpPost)) {
                String responseString = EntityUtils.toString(response.getEntity());

                System.out.println("Deepgram API Response: " + responseString);

                ObjectMapper mapper = new ObjectMapper();
                DeepgramResponse deepgramResponse = mapper.readValue(responseString, DeepgramResponse.class);

                return deepgramResponse.getResults().getChannels().get(0).getAlternatives().get(0).getTranscript();
            }
        }
    }

    private String fetchRandomFact() {
        try {
            URL url = new URL("https://api.api-ninjas.com/v1/facts");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("accept", "application/json");
            connection.setRequestProperty("X-Api-Key", "T88hKUe2bZXLMDp03DbuEw==BeHVY71o29sX6URe");

            if (connection.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;

                while ((line = in.readLine()) != null) {
                    response.append(line);
                }
                in.close();

                Gson gson = new Gson();
                JsonArray factsArray = gson.fromJson(response.toString(), JsonArray.class);

                if (factsArray.size() > 0) {
                    JsonObject factObject = factsArray.get(0).getAsJsonObject();
                    String fact = factObject.get("fact").getAsString();

                    return "🧠 *Qiziqarli fakt!* 🌍\n\n" +
                            "📌 " + fact + "\n\n" +
                            "✨ *Buni bilarmidingiz? Endi buni boshqalarga ham aytib berish vaqti keldi!* 😎";
                } else {
                    return "😕 *Kechirasiz, bugun fakt topilmadi...*\n\n" +
                            "💡 *Ammo xafa bo‘lmang!* Balki siz bugun o‘zingiz qiziqarli narsa o‘rganarsiz! 📚";
                }
            } else {
                return "⚠️ *Xatolik yuz berdi!*\n\n" +
                        "⛔ HTTP javob kodi: " + connection.getResponseCode() + "\n\n" +
                        "🔄 *Iltimos, keyinroq qayta urinib ko‘ring!*";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "❌ *Oops! Nimadir noto‘g‘ri ketdi...*\n\n" +
                    "🤖 *AI ham ba’zida xato qiladi! Balki qayta urinib ko‘rarsiz?* 🔄";
        }
    }

    private String convertTextToSpeech(String text) {
        try {
            String apiUrl = "https://api.deepgram.com/v1/speak?model=aura-asteria-en";
            String apiKey = DEEPGRAM_API_KEY;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("Authorization", "Token " + apiKey)
                    .header("Content-Type", "text/plain")
                    .POST(HttpRequest.BodyPublishers.ofString(text))
                    .build();

            HttpResponse<Path> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofFile(Paths.get("audio.mp3")));

            if (response.statusCode() == 200) {
                return "audio.mp3"; // ✅ Fayl yaratildi
            } else {
                return null; // ❌ Xatolik
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private String escapeMarkdownV2(String text) {
        return MD_V2_PATTERN.matcher(text).replaceAll("\\\\$1");
    }


    @Override
    public String getBotUsername() {
        return "@EnglishSupportN1_bot";
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }
}
