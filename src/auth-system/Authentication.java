import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Authentication {

    public Authentication(String api_url) {
        API_URL = api_url;
    }
    private static String API_URL;
    private Map<String, User> mock_users = new HashMap<>();
    private String admin_id = "";

    public JSONArray getUpdates(long offset) {
        JSONArray resultArray = new JSONArray();
        try {
            URL url = new URL(API_URL + "getUpdates?offset=" + offset);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder content = new StringBuilder();
            while ((inputLine = in.readLine()) != null) {
                content.append(inputLine);
            }

            in.close();
            conn.disconnect();

            JSONObject response = new JSONObject(content.toString());
            resultArray = response.getJSONArray("result");

        } catch (Exception e) {
            e.printStackTrace();
        }
        return resultArray;
    }

    public void handleUpdate(JSONObject update) {
        if (update.has("message")) {
            JSONObject message = update.getJSONObject("message");
            String text = message.getString("text");
            String chatId = String.valueOf(message.getJSONObject("chat").getLong("id"));
            String userId = String.valueOf(message.getJSONObject("from").getLong("id"));
            String userName = message.getJSONObject("from").optString("username", "");
            String firstName = message.getJSONObject("from").optString("first_name", "");
            String lastName = message.getJSONObject("from").optString("last_name", "");

            if (text.equals("/start")) {
                if (!mock_users.containsKey(chatId)) {
                    User newUser = new User();
                    newUser.setUsername(userName);
                    mock_users.put(chatId, newUser);
                    sendMessage(chatId, "Добро пожаловать! Для начала регистрации, пожалуйста, введите ваше имя.");
                } else {
                    User user = mock_users.get(userId);
                    switch (user.getStatus()) {
                        case AWAITING_NAME:
                            sendMessage(userId, "Вы остановились на вводе имени");
                            break;
                        case AWAITING_SURNAME:
                            sendMessage(userId, "Вы остановились на вводе фамилии");
                            break;
                        case AWAITING_GROUP:
                            sendMessage(userId, "Вы остановились на вводе группы");
                            break;
                        default:
                            sendMessage(userId, String.format("Доброго времени суток, %s. Давайте приступим к продуктивной работе.", user.nickname));
                            break;
                    }
                }
            } else if (text.equals("/delete_me")) {
                if (!mock_users.containsKey(chatId)) {
                    sendMessage(chatId, "Вас и так не существует.");
                } else {
                    mock_users.remove(userId);
                    sendMessage(chatId, "Хорошо, забыли.");
                }
            } else if (mock_users.containsKey(chatId)) {
                if (mock_users.get(userId).getStatus() != User.Status.REGISTERED) {
                    User user = mock_users.get(userId);
                    switch (user.getStatus()) {
                        case AWAITING_NAME:
                            user.setName(text);
                            user.setStatus(User.Status.AWAITING_SURNAME);
                            sendMessage(userId, "Отлично, теперь введите вашу фамилию.");
                            break;
                        case AWAITING_SURNAME:
                            user.setSurname(text);
                            user.setStatus(User.Status.AWAITING_GROUP);
                            sendMessage(userId, "Укажите вашу группу в формате 'ИУ10-66'.");
                            break;
                        case AWAITING_GROUP:
                            if (isValidGroupFormat(text)) {
                                user.setGroup(text);
                                user.setStatus(User.Status.AWAITING_NICKNAME);
                                sendMessage(userId, "Хорошо, теперь скажите, как мне к вам обращаться.\nP.s. Это буду знать только я)");
                            } else if (text.equals("Я админ")) {
                                if (this.admin_id.isEmpty()) {
                                    this.admin_id = userId;
                                    user.setStatus(User.Status.AWAITING_NICKNAME);
                                    user.setGroup("Преподаватель");
                                    sendMessage(userId, "Хорошо, уговорили. Теперь вы админ.\nСкажите, как мне к вам обращаться.\nP.s. Это буду знать только я)");
                                } else {
                                    User admin = mock_users.get(admin_id);
                                    sendMessage(userId, String.format("Админ для этого чата уже задан.\nЭто %s.\nВведите вашу группу или попросите админа освободить позицию", admin.username));
                                }
                            } else {
                                sendMessage(userId, "Пожалуйста, введите номер группы в корректном формате.");
                            }
                            break;

                        case AWAITING_NICKNAME:
                            user.setNickname(text);
                            sendYesNoQuestion(userId);
                            break;
                        default:
                            break;
                    }
                }
            }
        } else if (update.has("callback_query")) {
            JSONObject callbackQuery = update.getJSONObject("callback_query");
            String callData = callbackQuery.getString("data");
            String chatId = String.valueOf(callbackQuery.getJSONObject("message").getJSONObject("chat").getLong("id"));
            handleCallbackQuery(chatId, callData);
        }
    }

    private void handleCallbackQuery(String chatId, String callData) {
        if (!mock_users.containsKey(chatId)){
            sendMessage(chatId, "Нужно быть зарегестрированным, прежде чем выбирать такое.");
            return;
        }
        if (callData.equals("Auth_is_correct")) {
            sendMessage(chatId, "Отлично.\nЗаписали.");
            mock_users.get(chatId).setStatus(User.Status.REGISTERED);
        } else if (callData.equals("Auth_is_not_correct")) {
            sendMessage(chatId, "Хорошо, давайте снова.\nВведите ваше имя.");
            mock_users.get(chatId).setStatus(User.Status.AWAITING_NAME);
        }
    }

    private void sendMessage(String chatId, String text) {
        try {
            System.out.println("Attempting to send message to chat ID: " + chatId);
            HttpURLConnection conn = getHttpURLConnection(chatId, text);

            int responseCode = conn.getResponseCode();
            System.out.println("Response Code: " + responseCode);

            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String inputLine;
                StringBuilder response = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();
                System.out.println("Response: " + response.toString());
            } else {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
                String inputLine;
                StringBuilder errorResponse = new StringBuilder();

                while ((inputLine = in.readLine()) != null) {
                    errorResponse.append(inputLine);
                }
                in.close();
                System.out.println("Error Response: " + errorResponse.toString());
            }

            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @NotNull
    private static HttpURLConnection getHttpURLConnection(String chatId, String text) throws IOException {
        String urlString = API_URL + "sendMessage";
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setDoOutput(true);

        String jsonInputString = "{\"chat_id\":\"" + chatId + "\",\"text\":\"" + text + "\"}";

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        return conn;
    }


    private void sendYesNoQuestion(String chatId) {
        User user = mock_users.get(chatId);
        String messageText = "Уважаемый, " + user.nickname + ", проверьте корректность данных.\n" +
                "Фамилия: " + user.surname + "\n" +
                "Имя: " + user.name + "\n" +
                "Группа: " + user.group + "\n" +
                "Всё верно?\n";

        String urlString = API_URL + "sendMessage";
        String jsonInputString = "{\"chat_id\":\"" + chatId + "\",\"text\":\"" + messageText + "\","
                + "\"reply_markup\":{\"inline_keyboard\":[[{\"text\":\"Да\",\"callback_data\":\"Auth_is_correct\"},{\"text\":\"Нет\",\"callback_data\":\"Auth_is_not_correct\"}]]}}";

        try {
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            conn.getResponseCode();
            conn.disconnect();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private boolean isValidGroupFormat(String group) {
        Pattern pattern = Pattern.compile("^[А-Я]{2}\\d{1,2}-\\d{2,3}$");
        Matcher matcher = pattern.matcher(group);
        return matcher.find();
    }

    static class User {
        private String name;
        private String surname;
        private String group;
        private String username;
        private String nickname;
        private Status status;

        enum Status {
            AWAITING_NAME, AWAITING_SURNAME, AWAITING_GROUP, AWAITING_NICKNAME, REGISTERED
        }

        public User() {
            this.status = Status.AWAITING_NAME;
        }

        public void setName(String name) { this.name = name; }
        public void setSurname(String surname) { this.surname = surname; }
        public void setGroup(String group) { this.group = group; }
        public void setStatus(Status status) { this.status = status; }
        public void setUsername(String username) { this.username = username; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public Status getStatus() { return status; }
    }
}
