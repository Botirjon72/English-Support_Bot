package org.example;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;


import com.fasterxml.jackson.databind.JsonNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.*;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.asynchttpclient.AsyncHttpClient;
import org.asynchttpclient.DefaultAsyncHttpClient;
import org.asynchttpclient.ListenableFuture;
import org.asynchttpclient.Response;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
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
    private static final String RAPIDAPI_URL = "https://chat-gpt26.p.rapidapi.com/";
    private static final String RAPIDAPI_KEY = "e362de5a5bmsha229a92cefdfe68p168425jsnb5dac5a32abe"; // ⛔ API kalitingizni xavfsiz saqlang
    private static final String RAPIDAPI_HOST = "chat-gpt26.p.rapidapi.com";
    private boolean autoMode = false;
    private boolean textSpeech = false;
    private boolean isAiMode = false;
    private final Map<Long, List<TestQuestion>> userTests = new HashMap<>();
    private final Map<Long, List<Questions>> userQuestions = new HashMap<>();
    private final Map<Long, Integer> userScores = new HashMap<>();
    private final Map<Long, Integer> userCurrentQuestion = new HashMap<>();
    private final Map<Long, Boolean> userWaitingForTranslation = new HashMap<>();
    private final Map<Long, String> userSelectedLang = new HashMap<>();
    private static final Pattern MD_V2_PATTERN = Pattern.compile("([_\\*\\[\\]\\(\\)~`>#+\\-=|{}.!])");

    DBService dbService = new DBService();// 🔹 Auto Transcription Mode

    String UserName, firstName, lastName, userName;
    LocalDateTime startTime;

    int createdAt;


    public static void main(String[] args) {
        try {
            TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
            botsApi.registerBot(new TelegramBot());
            System.out.println("Bot started");
        } catch (TelegramApiException e) {
            System.err.println("Bot failed");
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

            userName = message.getFrom().getUserName();
            firstName = message.getFrom().getFirstName();
            lastName = message.getFrom().getLastName();
            int createdAt = message.getDate();

            startTime = Instant.ofEpochSecond(createdAt)
                    .atZone(ZoneId.systemDefault()).toLocalDateTime();

            User existingUser = dbService.getUserById(chatId);
            dbService.updateUserLastActive(chatId);


            if (existingUser == null) {
                User user = new User(chatId, userName, firstName, lastName, startTime, 0, 0, 0, 0, false);
                dbService.saveUser(user);
            } else {
                existingUser.setUsername(userName);
                existingUser.setFirstName(firstName);
                existingUser.setLastName(lastName);
                existingUser.setCreatedAt(startTime);

                dbService.updateUser(existingUser);
            }

            // ✅ 1️⃣ Agar AI rejimi yoqilgan bo‘lsa va ovozli xabar bo‘lsa, OpenAI'ga yuboramiz
            if ((message.hasVoice() || message.hasAudio()) && isAiMode) {
                String fileId = message.hasVoice() ? message.getVoice().getFileId() : message.getAudio().getFileId();
                System.out.println("🔹 AI Mode ON: Ovozli xabar qabul qilindi! FileID: " + fileId);
                openAiText(chatId, fileId);
                return;
            }

            // ✅ 2️⃣ Agar Auto Mode yoqilgan bo‘lsa, ovozli xabarni avtomatik qayta ishlash
            if ((message.hasVoice() || message.hasAudio()) && autoMode) {
                String fileId = message.hasVoice() ? message.getVoice().getFileId() : message.getAudio().getFileId();
                System.out.println("🔹 Auto Mode ON: Ovozli xabar qabul qilindi! FileID: " + fileId);
                processAudio(chatId, fileId);
                return;
            }

            // ✅ 3️⃣ Agar test rejimi bo‘lsa, javoblarni qayta ishlaymiz
            if (userTests.containsKey(chatId) && userCurrentQuestion.containsKey(chatId)) {
                processUserAnswer(chatId, text);
                return;
            }

            // ✅ 4️⃣ Agar matn ovozga o‘girilishi kerak bo‘lsa
            if (message.hasText() && textSpeech) {
                System.out.println("🔹 Matn ovozga aylantirilmoqda: " + text);
                String audioUrl = convertTextToSpeech(text);

                if (audioUrl != null) {
                    sendAudioMessage(chatId, audioUrl);
                } else {
                    sendTextMessage(chatId, "⚠️ Xatolik! Matn ovozga aylantirilmadi.");
                }
                return;
            }

            // ✅ 5️⃣ Agar tarjima rejimi yoqilgan bo‘lsa
            if (userWaitingForTranslation.getOrDefault(chatId, false)) {
                System.out.println("🔹 Tarjima uchun matn olindi: " + text);
                processTranslation(chatId, text);
                return;
            }

            if (isAiMode && update.hasMessage() && update.getMessage().hasText()) {
                SendMessage warningMessage = new SendMessage();
                warningMessage.setChatId(chatId);
                warningMessage.setText("⛔ Kechirasiz, faqat audio yoki ovozli xabar yuborishingiz mumkin.");
                warningMessage.setReplyMarkup(getMainMenuInlineKeyboard());
                warningMessage.setParseMode("HTML");


                try {
                    execute(warningMessage);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }

                isAiMode = false; // AI rejimni o‘chirib qo‘yamiz
                return;
            }

            // ✅ 6️⃣ Oddiy matn xabarlarni qayta ishlash
            System.out.println("🔹 Oddiy matn xabari olindi: " + text);
            handleTextMessage(chatId, text);
        }

    }

    private void handleTextMessage(long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);

        if (isAiMode) {
            sendAudioMessage(chatId, text);
            isAiMode = false;
            return;
        }
        if (textSpeech) {
            sendAudioMessage(chatId, text);
            textSpeech = false;
            return;
        }

        userWaitingForTranslation.put(chatId, false);

        switch (text) {
            case "/start":
                textSpeech = false;
                autoMode = false;
                isAiMode = false;
                sendMessage.setParseMode("HTML");

                User user = dbService.getUserById(chatId);
                if (user == null) {
                    user = new User(chatId, userName, firstName, lastName, startTime, 0, 0, 0, 0, false);
                    dbService.saveUser(user);
                }

                dbService.addScore(chatId, 1);

                boolean isPremium = user.isPremium();
                String premiumStatus = isPremium
                        ? "💎 <b>Siz premium foydalanuvchisiz!</b>\nMaxsus xizmatlardan foydalanishingiz mumkin. ✅"
                        : "🟠 <b>Siz oddiy foydalanuvchisiz.</b>\nPremium olish orqali qo‘shimcha imkoniyatlarga ega bo‘ling!";

                sendMessage.setText(String.format("""
                        <b>👋 Assalomu alaykum!</b>\n
                        🤖 <b>Men - English Support Bot!</b>\n
                        📚 <b>Ingliz tilini o‘rganishingizga yordam beraman!</b>\n
                        <i>🔹 O‘qish, yozish, eshitish va gapirish ko‘nikmalaringizni rivojlantirish uchun maxsus xizmatlarim bor!</i>\n\n
                        🛠 <b>Mening asosiy imkoniyatlarim:</b>\n
                        🎙 <b>Ovozdan matnga aylantirish</b> – Audio xabarlaringizni matnga o‘giraman.\n
                        🌍 <b>Matn tarjimasi</b> – Ingliz va o‘zbek tillari orasida tezkor tarjima qilaman.\n
                        📖 <b>Grammatik qoidalar</b> – Ingliz tili grammatikasini tushuntiraman.\n
                        🎯 <b>Testlar</b> – Ingliz tilida bilim darajangizni tekshirishingiz mumkin.\n
                        🤖 <b>AI bilan suhbat</b> – Sun’iy intellekt bilan muloqot qilib, savollaringizga javob olasiz.\n
                        🏆 <b>Top foydalanuvchilar</b> – Botdan eng ko‘p foydalanganlarni ko‘rishingiz mumkin!\n\n
                        %s\n
                        📌 <b>Quyidagi bo‘limlardan birini tanlang:</b>
                        """, premiumStatus));

                sendMessage.setReplyMarkup(getMainInlineKeyboard());
                break;
            case "/help":
                sendMessage(chatId, """
        📖 <b>Telegram Botdan Foydalanish Qo‘llanmasi</b>
        
    Bu qo‘llanma AI yordamchi botidan qanday foydalanish haqida to‘liq ma’lumot beradi.  
    Bot yordamida siz ovozli xabarlarni matnga aylantirib, ChatGPT javobini eshitishingiz mumkin.  
        
    🔹 <b>1. Botdan foydalanish</b>  
        Bot yordamida quyidagi vazifalarni bajarishingiz mumkin:  
        ✔️ Ovozli xabarlarni matnga aylantirish  
        ✔️ AI bilan suhbatlashish (ChatGPT)  
        ✔️ AI javobini ovoz shaklida olish  
        ✔️ Asosiy menyuga qaytish  
        
    🎤 <b>2. Ovozli xabar yuborish</b>  
        1️⃣ Telegram botga kiring  
        2️⃣ Mikrofon tugmachasini bosib, savolingizni ovozli xabar sifatida yuboring  
        3️⃣ Bot xabarni qabul qilib, matnga o‘giradi va AI javobini tayyorlaydi  
        4️⃣ Tayyor bo‘lgach, sizga ChatGPT ning ovozli javobini yuboradi  
        
        ⏳ <b>Jarayon davom etmoqda...</b>  
        Sizga bildirishnoma keladi:  
        <i>"Ovozli xabar tahlil qilinmoqda, biroz kuting..."</i>  
        
    🔄 <b>Matnni aniqlash tugagandan so‘ng:</b>  
        - AI javobini matn ko‘rinishida ko‘rasiz  
        - Ovozli javob ham yuboriladi  
        
    📌 <b>3. ChatGPT bilan suhbatlashish</b>  
        Ovozli xabaringizdan so‘ng ChatGPT javobi ovozli va matn shaklida yuboriladi.  
        Agar yana savolingiz bo‘lsa, yangi ovozli xabar yuboring.  
        
    🎶 <b>Ovozli javob namunasi:</b>  
        <i>"Sizning AI yordamchingizning ovozli javobi!"</i>  
        
    ✉️ <b>Qo‘shimcha matn:</b>  
        <i>"Agar yana savolingiz bo‘lsa, yangi ovozli xabar yuboring."</i>  
        
    ⬅️ <b>4. Asosiy menyuga qaytish</b>  
        Agar asosiy menyuga qaytmoqchi bo‘lsangiz:  
        📌 "Asosiy menyuga qaytish" tugmachasini bosing.  
        📌 Bot sizni asosiy interfeysga qaytaradi.  
        
    🚀 <b>5. Qo‘llab-quvvatlash</b>  
        Agar bot bilan bog‘liq muammolarga duch kelsangiz:  
    📩 Admin bilan bog‘laning  
  
        
     ✅ <b>Tayyor! Endi botdan bemalol foydalanishingiz mumkin!</b> 🎉
    """);
              break;



            default:
                sendMessage.setParseMode("HTML");
                sendMessage.setText("⚠️ <b>Bunday buyruq mavjud emas!</b>\n\n"
                        + "🔹 Quyidagi menyudan kerakli bo‘limni tanlang:");
                sendMessage.setReplyMarkup(getMainMenuInlineKeyboard());
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

        if (message == null) {
            System.out.println("⚠️ Xatolik: callbackQuery.getMessage() null!");
            return;
        }

        String text = "";
        InlineKeyboardMarkup replyMarkup = null;
        String isAiText;

        switch (data) {
            case "convert_voice" -> {
                autoMode = true;
                dbService.addScore(chatId, 3);
                text = "🎙 <b>Ovozdan matnga aylantirish</b>\n\n" +
                        "🔹 Ushbu xizmat orqali ovozli xabarlaringizni matnga aylantirishingiz mumkin.\n\n" +
                        "<i>🗣 Ovozli xabar yuboring va biz uni matnga o‘girib beramiz.</i>";
            }

            case "instant_translation" -> {
                dbService.addScore(chatId, 3);
                text = "🌍 <b>Matn tarjima qilish</b>\n\n" +
                        "🔹 Ingliz va o‘zbek tillari o‘rtasida tezkor tarjima xizmati.\n\n" +
                        "<i>✍️ Tarjima qilinishi kerak bo‘lgan matnni yuboring.</i>";
                replyMarkup = getTranslateInlineKeyboard();
                userWaitingForTranslation.put(chatId, true);
            }

            case "level_test" -> {
                dbService.addScore(chatId, 3);
                text = "📊 <b>Ingliz tili daraja testi</b>\n\n" +
                        "🔹 Sizning ingliz tilidagi bilim darajangizni aniqlash uchun maxsus testlar.\n\n" +
                        "<i>📌 Daraja tanlang va test savollariga javob bering.</i>";
                replyMarkup = getLevelSelectionInlineKeyboard();
            }

            case "voice_Menu" -> {
                dbService.addScore(chatId, 3);
                text = "🎙 <b>Ovozli xizmatlar</b>\n\n" +
                        "🔹 Quyidagi ovozli xizmatlardan birini tanlang:";
                replyMarkup = getVoiseInlineKeyboard();
            }

            case "convert_text" -> {
                dbService.addScore(chatId, 3);
                textSpeech = true;
                text = "📜 <b>Matndan ovozga aylantirish</b>\n\n" +
                        "🔹 Siz matnni ovozli xabarga aylantirish xizmatidan foydalanmoqdasiz.\n\n" +
                        "<i>✍️ Matn yuboring va biz uni ovozli xabar shaklida qaytaramiz.</i>";
            }

            case "Grammer_reset" -> {
                dbService.addScore(chatId, 3);
                text = "📖 <b>Ingliz tili grammatikasi</b>\n\n" +
                        "🔹 Ingliz tili grammatikasi bo‘yicha qo‘llanmalar.\n\n" +
                        "<i>📌 Quyidagi ro‘yxatdan kerakli bo‘limni tanlang.</i>";
                replyMarkup = sendGrammarList(chatId);
            }

            case "info_menu" -> {
                dbService.addScore(chatId, 3);
                boolean isPremium = dbService.getUserById(chatId).isPremium();

                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setParseMode("HTML");
                sendMessage.setText("""
                            ℹ️ <b>Qo‘llanma va Ma’lumot</b>
                        
                        Assalomu alaykum! 😊 Ushbu bo‘limda siz botdan foydalanish bo‘yicha barcha kerakli ma’lumotlarni topasiz. 
                        
                        📜 <b>Bot haqida</b> – Ushbu bot nima uchun yaratilgan va qanday ishlashini bilib oling.  
                        📖 <b>Qo‘llanma</b> – Botdagi funksiyalarni qanday ishlatish haqida to‘liq qo‘llanma.  
                        💎 <b>Premium sotib olish</b> – Maxsus imkoniyatlardan foydalanish uchun premium versiyaga o‘tish (faqat oddiy foydalanuvchilar uchun).  
                        
                            🎯 Har qanday savollaringiz bo‘lsa, biz bilan bog‘laning!
                        """);

                sendMessage.setReplyMarkup(getHelpInlineKeyboard(isPremium));

                try {
                    execute(sendMessage);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            }

            case "aicallback" -> {
                dbService.addScore(chatId, 3);
                isAiMode = true;
                isAiText = "🤖 <b>AI bilan suhbat</b>\n\n" +
                        "🔹 Sun’iy intellekt bilan faqat <b>ovozli xabar</b> orqali muloqot qilishingiz mumkin.\n\n" +
                        "<i>🎤 Mikrofon tugmasini bosib, savolingizni ovozli xabar sifatida yuboring.</i>";

                sendMessage(chatId, isAiText); // Faqat AI haqida xabar yuboriladi, mainMenu chaqirilmaydi
            }

            case "back_main" -> {
                text = "🏠 <b>Bosh menyu</b>\n\n" +
                        "🔹 Asosiy menyuga qaytdingiz. Quyidagi variantlardan birini tanlashingiz mumkin:";
                replyMarkup = getMainInlineKeyboard();
            }

            case "buy_premium" -> {
                User user = dbService.getUserById(chatId);

                if (user == null) {
                    sendTextMessage(chatId, "⚠️ <b>Foydalanuvchi topilmadi!</b>");
                    return;
                }

                if (user.getScore() >= 30) {
                    user.setPremium(true);
                    user.setScore(user.getScore() - 30);
                    dbService.updateUser(user);

                    sendTextMessage(chatId, "✅ <b>Tabriklaymiz!</b>\n\n"
                            + "Siz endi <b>Premium</b> foydalanuvchisiz! 💎\n"
                            + "Premium imkoniyatlardan bemalol foydalanishingiz mumkin.");

                    MyAccount(chatId); // Yangilangan hisob ma'lumotlarini qayta chiqarish
                } else {
                    sendTextMessage(chatId, "❌ <b>Kechirasiz!</b>\n\n"
                            + "Premium sotib olish uchun kamida <b>30 ball</b> kerak.\n"
                            + "Hozirgi ballingiz: <b>" + user.getScore() + "</b>\n"
                            + "Ball yig‘ish uchun botdan faol foydalaning.");
                }
            }

            case "about_bot" -> {
                SendMessage sendMessage = new SendMessage();
                sendMessage.setChatId(chatId);
                sendMessage.setParseMode("HTML");
                sendMessage.setText("""
                            🤖 <b>Bot haqida</b>
                        
                        Assalomu alaykum! 😊 Ushbu bot sizga quyidagi imkoniyatlarni taqdim etadi:
                        
                        🔹 <b>Ovozdan matnga</b> – Siz yozgan matnni ovozga aylantiradi va aksincha.  
                        🔹 <b>Matn tarjimasi</b> – Ingliz va o‘zbek tillari orasida tezkor tarjima.  
                        🔹<b>Bilimni sinash</b> – CEFR darajalari bo‘yicha ingliz tilini sinovdan o‘tkazing!  
                        🔹 <b>Grammatika qoidalari</b> – Ingliz tili grammatikasini o‘rganish uchun qulay qo‘llanma.  
                        🔹 <b>AI suhbatdosh</b> – Sun’iy intellekt bilan ingliz tilida muloqot qiling.  
                        🔹 <b>Reyting</b> – Eng yaxshi natijalarga erishgan foydalanuvchilar ro‘yxati.  
                        
                        📌 Bot sizning til o‘rganish jarayoningizni oson va qiziqarli qilish uchun ishlab chiqilgan! 
                        
                        ❓ Yordam kerak bo‘lsa, "Qo‘llanma" bo‘limiga tashrif buyuring.
                        """);
                sendMessage.setReplyMarkup(getMainMenuInlineKeyboard());

                try {
                    execute(sendMessage);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }

            }

            case "user_guide" -> {
                sendMessage(chatId, """
        📖 <b>Telegram Botdan Foydalanish Qo‘llanmasi</b>
        
    Bu qo‘llanma AI yordamchi botidan qanday foydalanish haqida to‘liq ma’lumot beradi.  
    Bot yordamida siz ovozli xabarlarni matnga aylantirib, ChatGPT javobini eshitishingiz mumkin.  
        
    🔹 <b>1. Botdan foydalanish</b>  
        Bot yordamida quyidagi vazifalarni bajarishingiz mumkin:  
        ✔️ Ovozli xabarlarni matnga aylantirish  
        ✔️ AI bilan suhbatlashish (ChatGPT)  
        ✔️ AI javobini ovoz shaklida olish  
        ✔️ Asosiy menyuga qaytish  
        
    🎤 <b>2. Ovozli xabar yuborish</b>  
        1️⃣ Telegram botga kiring  
        2️⃣ Mikrofon tugmachasini bosib, savolingizni ovozli xabar sifatida yuboring  
        3️⃣ Bot xabarni qabul qilib, matnga o‘giradi va AI javobini tayyorlaydi  
        4️⃣ Tayyor bo‘lgach, sizga ChatGPT ning ovozli javobini yuboradi  
        
        ⏳ <b>Jarayon davom etmoqda...</b>  
        Sizga bildirishnoma keladi:  
        <i>"Ovozli xabar tahlil qilinmoqda, biroz kuting..."</i>  
        
    🔄 <b>Matnni aniqlash tugagandan so‘ng:</b>  
        - AI javobini matn ko‘rinishida ko‘rasiz  
        - Ovozli javob ham yuboriladi  
        
    📌 <b>3. ChatGPT bilan suhbatlashish</b>  
        Ovozli xabaringizdan so‘ng ChatGPT javobi ovozli va matn shaklida yuboriladi.  
        Agar yana savolingiz bo‘lsa, yangi ovozli xabar yuboring.  
        
    🎶 <b>Ovozli javob namunasi:</b>  
        <i>"Sizning AI yordamchingizning ovozli javobi!"</i>  
        
    ✉️ <b>Qo‘shimcha matn:</b>  
        <i>"Agar yana savolingiz bo‘lsa, yangi ovozli xabar yuboring."</i>  
        
    ⬅️ <b>4. Asosiy menyuga qaytish</b>  
        Agar asosiy menyuga qaytmoqchi bo‘lsangiz:  
        📌 "Asosiy menyuga qaytish" tugmachasini bosing.  
        📌 Bot sizni asosiy interfeysga qaytaradi.  
        
    🚀 <b>5. Qo‘llab-quvvatlash</b>  
        Agar bot bilan bog‘liq muammolarga duch kelsangiz:  
    📩 Admin bilan bog‘laning  
  
        
     ✅ <b>Tayyor! Endi botdan bemalol foydalanishingiz mumkin!</b> 🎉
    """);
                return;
            }

            case "back_help_menu" -> {
                replyMarkup = getMainInlineKeyboard();
            }

            case "audio_reset" -> {
                sendAudioRetryMessage(chatId);
                return;
            }

            case "retry_test" -> {
                retryTest(chatId);
                return;
            }

            case "top" -> {
                sendTopUsers(chatId);
                return;
            }

            case "questions", "Questions_reset" -> {
                sendQuestions(chatId);
                return;
            }

            case "Accaunt" -> {
                MyAccount(chatId);
                return;
            }

            default -> {
                text = "⚠️ <b>Noto‘g‘ri buyruq!</b>\n\n" +
                        "🔹 Siz tanlagan buyruq noto‘g‘ri yoki mavjud emas.\n\n" +
                        "<i>📌 Quyidagi tugmalardan birini tanlang:</i>";
                replyMarkup = getMainInlineKeyboard();
            }
        }

        message.setParseMode("HTML");
        message.setText(text);
        message.setReplyMarkup(replyMarkup);
        sendEditMessage(message);
    }

    private void sendMessage(long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.setParseMode("HTML");
        sendMessage.setReplyMarkup(getMainMenuInlineKeyboard());
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }

    }


    private void openAiText(long chatId, String fileId) {
        try {
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setParseMode("HTML");
            sendMessage.setText("⏳ <b>Jarayon davom etmoqda, biroz kuting...</b>\n\n🔍 <i>Ovozli xabar tahlil qilinmoqda...</i>");
            Message message = execute(sendMessage);

            String audioUrl = getAudioFileUrl(fileId);
            var ref = new Object() {
                String transcribedText = transcribeAudioWithDeepgram(audioUrl);
            };
            if (ref.transcribedText == null || ref.transcribedText.trim().isEmpty()) {
                ref.transcribedText = "⚠️ Ovozli xabarda matn aniqlanmadi!";
            }

            CompletableFuture.runAsync(() -> {
                String gptResponse = chatWithGPT(ref.transcribedText);
                if (gptResponse == null || gptResponse.trim().isEmpty()) {
                    gptResponse = "⚠️ ChatGPT javobi olinmadi.";
                }

                String audioPath = convertTextToSpeech(gptResponse);
                if (audioPath == null) {
                    EditMessageText finalMessage = new EditMessageText();
                    finalMessage.setChatId(chatId);
                    finalMessage.setMessageId(message.getMessageId());
                    finalMessage.setParseMode("HTML");
                    finalMessage.setText("⚠️ Ovozli xabar yaratishda xatolik yuz berdi.");
                    try {
                        execute(finalMessage);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return;
                }
                sendAiVoice(chatId, audioPath);
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendAiVoice(long chatId, String audioPath) {
        File audioFile = new File(audioPath);
        if (audioFile.length() > 50 * 1024 * 1024) {
            sendTextMessage(chatId, "⚠️ Xatolik: Fayl hajmi 50MB dan oshib ketdi!");
            return;
        }

        SendAudio sendAudio = new SendAudio();
        sendAudio.setChatId(chatId);
        sendAudio.setAudio(new InputFile(audioFile));
        sendAudio.setParseMode("HTML");
        sendAudio.setCaption("""
                🎶 <b>Sizning AI yordamchingizning ovozli javobi!</b>
                
                🔄 <i>Agar yana savolingiz bo‘lsa, yangi ovozli xabar yuboring.</i>
                """);

        try {
            execute(sendAudio);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }

        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.schedule(() -> {
            if (audioFile.exists() && !audioFile.delete()) {
                System.out.println("⚠️ Fayl o‘chirilmadi: " + audioPath);
            }
        }, 10, TimeUnit.MINUTES);
    }

    public String chatWithGPT(String userMessage) {
        try (AsyncHttpClient client = new DefaultAsyncHttpClient()) {
            // ✅ JSON so‘rovni shakllantirish
            JSONObject jsonRequest = new JSONObject()
                    .put("model", "gpt-3.5-turbo")
                    .put("messages", new JSONArray().put(new JSONObject()
                            .put("role", "user")
                            .put("content", userMessage)));

            // ✅ API ga so‘rov yuborish
            ListenableFuture<Response> futureResponse = client.prepare("POST", RAPIDAPI_URL)
                    .setHeader("x-rapidapi-key", RAPIDAPI_KEY)
                    .setHeader("x-rapidapi-host", RAPIDAPI_HOST)
                    .setHeader("Content-Type", "application/json")
                    .setBody(jsonRequest.toString())
                    .execute();

            // ✅ Javobni kutish
            Response response = futureResponse.toCompletableFuture().get(10, TimeUnit.SECONDS);

            return extractGPTResponseText(response.getResponseBody());
        } catch (TimeoutException e) {
            return "⚠️ ChatGPT serveri javob bermadi, iltimos keyinroq urinib ko‘ring.";
        } catch (Exception e) {
            e.printStackTrace();
            return "⚠️ ChatGPT bilan bog‘lanishda xatolik yuz berdi.";
        }
    }

    private String extractGPTResponseText(String jsonResponse) {
        try {
            JSONObject jsonObject = new JSONObject(jsonResponse);

            if (!jsonObject.has("choices")) {
                return "⚠️ ChatGPT javobida 'choices' mavjud emas.";
            }

            JSONArray choices = jsonObject.getJSONArray("choices");
            if (choices.length() == 0) {
                return "⚠️ ChatGPT javobi olinmadi.";
            }

            // ✅ ChatGPT javobining asosiy qismi
            return choices.getJSONObject(0).getJSONObject("message").getString("content");
        } catch (Exception e) {
            e.printStackTrace();
            return "⚠️ ChatGPT API javobini o‘qishda xatolik.";
        }
    }


    private void sendAccount(long chatId, String message, boolean isPremium) {
        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> buttons = new ArrayList<>();

        // 🔹 Asosiy tugmalar
        List<InlineKeyboardButton> row1 = new ArrayList<>();
        InlineKeyboardButton backButton = new InlineKeyboardButton("🔙 Bosh menyu");
        backButton.setCallbackData("back_main");
        row1.add(backButton);
        buttons.add(row1);

        // 🔹 Agar foydalanuvchi premium bo'lmasa, "Premium sotib olish" tugmasini qo'shamiz
        if (!isPremium) {
            List<InlineKeyboardButton> row2 = new ArrayList<>();
            InlineKeyboardButton buyPremiumButton = new InlineKeyboardButton("💎 Premium sotib olish (30 ball)");
            buyPremiumButton.setCallbackData("buy_premium");
            row2.add(buyPremiumButton);
            buttons.add(row2);
        }

        inlineKeyboard.setKeyboard(buttons);

        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setParseMode("HTML");
        sendMessage.setText(message);
        sendMessage.setReplyMarkup(inlineKeyboard);

        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void MyAccount(long chatId) {
        User user = dbService.getUserById(chatId);

        if (user == null) {
            sendTextMessage(chatId, "⚠️ <b>Foydalanuvchi topilmadi!</b>");
            return;
        }

        System.out.println("Premium status: " + user.isPremium());

        int score = user.getScore();
        int bestScore = user.getBestScore();
        int todayScore = user.getTodayScore();
        int weeklyScore = user.getWeeklyScore();
        boolean isPremium = user.isPremium();

        String rank;
        int nextLevel = 0;

        if (score >= 100) {
            rank = "🥇 <b>Grand Master</b>";
        } else if (score >= 50) {
            rank = "🥈 <b>Master</b>";
            nextLevel = 100 - score;
        } else if (score >= 30) {
            rank = "🥉 <b>Expert</b>";
            nextLevel = 50 - score;
        } else if (score >= 15) {
            rank = "🎖 <b>Advanced Learner</b>";
            nextLevel = 30 - score;
        } else {
            rank = "⭐ <b>Beginner</b>";
            nextLevel = 15 - score;
        }

        // 🔹 Premium status
        String premiumStatus = isPremium
                ? "💎 <b>Premium foydalanuvchi ✅</b>"
                : "🟠 <b>Oddiy foydalanuvchi ❌</b>";

        // 🔹 Keyingi darajaga necha ball kerakligini ko'rsatish
        String nextLevelMessage = (nextLevel > 0)
                ? "🚀 <b>Keyingi darajaga chiqish uchun yana <u>" + nextLevel + " ball</u> kerak!</b>\n"
                : "🔥 <b>Siz eng yuqori darajadasiz!</b>\n";

        // 📌 Hisob ma'lumotlarini chiroyli formatda chiqarish
        String message = String.format("""
                <b>👤 Sizning hisobingiz</b>\n
                ┏━━━━━━━━━━━━━━━━━\n
                ┃ 🎯 <b>Jami ball:</b> %d\n
                ┃ 🏆 <b>Eng yaxshi natija:</b> %d\n
                ┃ 📅 <b>Bugungi ball:</b> %d\n
                ┃ 📊 <b>Haftalik ball:</b> %d\n
                ┗━━━━━━━━━━━━━━━━━\n
                📖 <b>Daraja:</b> %s\n
                %s\n
                %s
                💡 <i>Ballaringizni premium imkoniyatlarga ishlatishingiz mumkin!</i>
                """, score, bestScore, todayScore, weeklyScore, rank, premiumStatus, nextLevelMessage);

        sendAccount(chatId, message, isPremium);
    }

    private void sendTextMessage(long chatId, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText(text);
        sendMessage.setParseMode("HTML"); // ✅ HTML format qo‘shildi

        try {
            sendMessage.setReplyMarkup(getMainMenuInlineKeyboard());
            execute(sendMessage);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
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
            sendMessageSafely(sendMessage);
            return;
        }

        // 🔹 Testlarni bazadan olish
        List<TestQuestion> questions = dbService.getTenRandomTestsByLevel(levelKey.substring(0, 2));
        if (questions.isEmpty()) {
            sendMessage.setText("❌ <b>Kechirasiz!</b> Tanlangan daraja uchun testlar mavjud emas. 🔄 Boshqa darajani tanlang.");
            sendMessage.setParseMode("HTML");
            sendMessage.setReplyMarkup(getLevelSelectionInlineKeyboard());
            sendMessageSafely(sendMessage);
            return;
        }

        // 🔹 Testni boshlash
        userTests.put(chatId, questions);
        userScores.put(chatId, 0);
        userCurrentQuestion.put(chatId, 0);

        sendMessage.setText(String.format("""
                🎮 <b>%s</b> darajadagi test boshlandi!
                
                🏆 Siz hozir bilim sinov o‘yinidasiz!  
                🧠 Har bir savol sizni yangi darajaga olib chiqadi.  
                
                🎯 Diqqat bilan o‘ylab, to‘g‘ri javob bering!  
                
                🔎 Boshladik!
                """, levelKey));

        sendMessage.setParseMode("HTML");
        sendMessageSafely(sendMessage);

        sendNextTestQuestion(chatId);
    }

    private void sendMessageSafely(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
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
            int currentScore = userScores.getOrDefault(chatId, 0) + 1; // ✅ Ballni oshiramiz
            userScores.put(chatId, currentScore); // ✅ Yangilangan ballni saqlaymiz

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
        List<User> topUsers = dbService.getTopUsers(10, "score"); // 🔹 Eng yaxshi foydalanuvchilarni olish

        if (topUsers.isEmpty()) {
            sendTextMessage(chatId, "📊 <b>Top foydalanuvchilar hali yo‘q!</b>");
            return;
        }

        StringBuilder messageText = new StringBuilder("🏆 <b>TOP 10 Foydalanuvchilar</b>\n");
        messageText.append("━━━━━━━━━━━━━━━━━━━━━\n");

        int rank = 1;
        for (User user : topUsers) {
            String status = switch (rank) {
                case 1 -> "🥇 Grand Master"; // 1-o‘rin
                case 2 -> "🥈 Master"; // 2-o‘rin
                case 3 -> "🥉 Expert"; // 3-o‘rin
                case 4, 5, 6, 7, 8, 9, 10 -> "🎖 Advanced Learner"; // 4-10-o‘rin
                default -> "⭐ Beginner"; // Keyingi o‘rinlar
            };

            String premiumBadge = user.isPremium() ? " 💎 Premium" : ""; // ✅ Premium foydalanuvchilar uchun belgi

            String firstName = (user.getFirstName() != null) ? escapeHtml(user.getFirstName()) : "<i>Ism yo‘q</i>";

            messageText.append(String.format(
                    "<b>%d.</b> %s - <b>%d</b> ball\n🏅 %s%s\n\n",
                    rank++, firstName, user.getScore(), status, premiumBadge
            ));
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

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
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

        if (message == null) {
            System.out.println("❌ Xabar null, jo‘natish bekor qilindi.");
            return;
        }

        if (message.getText() == null || message.getText().isEmpty()) {
            System.out.println("❌ Xabar matni bo‘sh, jo‘natish bekor qilindi.");
            return;
        }

        try {
            execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
            System.out.println("❌ Xabarni yangilashda xatolik: " + e.getMessage());
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
                case 0 -> progressMessage = """
                        🎙 <b>Ovoz qabul qilindi!</b>
                        🔄 <i>AI tahlilni boshladi...</i>
                        """;
                case 25 -> progressMessage = """
                        📡 <b>Signal qabul qilinyapti...</b>
                        ⏳ <i>Ovoz tahlil qilinmoqda...</i>
                        """;
                case 50 -> progressMessage = """
                        🎧 <b>AI ovozingizni tinglamoqda...</b>
                        🛠 <i>Matn shakliga moslashtirilmoqda...</i>
                        """;
                case 75 -> progressMessage = """
                        ✍ <b>So‘zlar yozib olinmoqda...</b>
                        📜 <i>Matn shakllantirilmoqda...</i>
                        """;
                case 100 -> progressMessage = """
                        ✅ <b>Tayyor!</b>
                        📄 <i>Ovozli xabaringiz matnga aylandi!</i>
                        """;
                default -> progressMessage = """
                        ⏳ <b>Jarayon davom etmoqda...</b>
                        🔎 <i>Iltimos, biroz kuting...</i>
                        """;
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
            // 🔹 Foydalanuvchiga jarayon boshlanganini bildirish
            SendMessage sendMessage = new SendMessage();
            sendMessage.setChatId(chatId);
            sendMessage.setParseMode("HTML");
            sendMessage.setText("🎙 <b>Ovozli xabaringiz tahlil qilinmoqda...</b>\n\n⏳ <i>Jarayon: 0%</i>");
            Message message = execute(sendMessage);

            int messageId = message.getMessageId();

            // 🔹 Jarayonning progressini yangilash
            Thread.sleep(1000);
            updateProgress(chatId, messageId, 25);
            Thread.sleep(1000);
            updateProgress(chatId, messageId, 50);
            Thread.sleep(1000);
            updateProgress(chatId, messageId, 75);

            // 🔹 Audio faylni transkripsiya qilish
            String audioUrl = getAudioFileUrl(fileId);
            String transcribedText = transcribeAudioWithDeepgram(audioUrl);

            // 🔹 Natijani tekshirish
            if (transcribedText == null || transcribedText.trim().isEmpty()) {
                transcribedText = "⚠️ <b>Xatolik:</b> Ovozli xabarda matn aniqlanmadi.";
            }

            updateProgress(chatId, messageId, 100);

            if (isAiMode) {
                processAiResponse(chatId, messageId, transcribedText);
            } else {
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processAiResponse(long chatId, int messageId, String userText) {
        CompletableFuture.runAsync(() -> {
            String aiResponse = chatWithGPT(userText);

            if (aiResponse == null || aiResponse.trim().isEmpty()) {
                aiResponse = "⚠️ AI javobi olinmadi.";
            }

            // 🔹 Xabar mavjudligini tekshiramiz va uni o‘chiramiz
            boolean isDeleted = deleteMessage(chatId, messageId);

            if (!isDeleted) {
                sendMessage(chatId, "🤖 <b>AI javobi:</b>\n\n" + aiResponse);
            }
        });
    }

    private boolean deleteMessage(long chatId, int messageId) {
        try {
            DeleteMessage deleteMessage = new DeleteMessage();
            deleteMessage.setChatId(chatId);
            deleteMessage.setMessageId(messageId);
            execute(deleteMessage);
            return true;
        } catch (TelegramApiException e) {
            System.out.println("⚠️ Xabar o‘chirilmadi yoki allaqachon o‘chirilgan: " + e.getMessage());
            return false;
        }
    }


    private InlineKeyboardMarkup getBackButtonInlineKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(Collections.singletonList(createInlineButton("⬅️ Ortga", "back_help_menu")));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getMainInlineKeyboard() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("🎙 Nutqni Matnga", "voice_Menu", "🌍 Matn Tarjimasi", "instant_translation"));
        keyboardRows.add(createInlineButtonRow("🧠 Intellektual Sinov", "level_test", "📖 Grammatika Qo‘llanmasi", "grammar_guide"));
        keyboardRows.add(createInlineButtonRow("❓ Savollar Bo‘limi", "questions", "🏆 Yetakchilar Ro‘yxati", "top"));
        keyboardRows.add(createInlineButtonRow("👤 Mening Profilim", "Accaunt", "🤖 AI Yordamchi", "aicallback"));
        keyboardRows.add(createInlineButtonRow("ℹ️ Ma’lumotlar", "info_menu"));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getHelpInlineKeyboard(boolean isPremium) {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("📜 Bot haqida", "about_bot", "📖 Qo‘llanma", "user_guide"));

        if (!isPremium) {
            keyboardRows.add(Collections.singletonList(createInlineButton("💎 Premium sotib olish", "buy_premium")));
        }

        keyboardRows.add(Collections.singletonList(createInlineButton("⬅️ Asosiy menyu", "back_main")));

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

    private InlineKeyboardMarkup getGrammerBackMenu() {
        InlineKeyboardMarkup keyboardMarkup = new InlineKeyboardMarkup();
        List<List<InlineKeyboardButton>> keyboardRows = new ArrayList<>();

        keyboardRows.add(createInlineButtonRow("🔄 Qayta urinish", "Grammer_reset", "🏠 Asosiy menyu", "back_main"));

        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    private InlineKeyboardMarkup getRetryTestKeyboard() {
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
