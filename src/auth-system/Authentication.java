import okhttp3.OkHttp;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Authentication implements LongPollingSingleThreadUpdateConsumer {
    Map<String, User> mock_users = new HashMap<>();
    private String admin_id = "";
    private TelegramClient telegramClient = new OkHttpTelegramClient("7197799406:AAE_h0MQl7ViddGsl6sMAI_ww-GEFYGFZtw");
    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message = update.getMessage().getText();

            String chatId = Long.toString(update.getMessage().getChatId()); /// что лучше использовать
            String userId = Long.toString(update.getMessage().getFrom().getId()); /// что лучше использовать
            String userName = update.getMessage().getFrom().getUserName();
            String firstName = update.getMessage().getFrom().getFirstName();
            String lastName = update.getMessage().getFrom().getLastName();

            if (message.equals("/start")) {
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


            } else if (message.equals("/delete_me")) {
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
                            user.setName(message);
                            user.setStatus(User.Status.AWAITING_SURNAME);
                            sendMessage(userId, "Отлично, теперь введите вашу фамилию.");
                            break;
                        case AWAITING_SURNAME:
                            user.setSurname(message);
                            user.setStatus(User.Status.AWAITING_GROUP);
                            sendMessage(userId, "Укажите вашу группу в формате \"ИУ10-66\".");
                            break;
                        case AWAITING_GROUP:
                            if (isValidGroupFormat(message)) {
                                user.setGroup(message);
                                user.setStatus(User.Status.AWAITING_NICKNAME);
                                sendMessage(userId, "Хорошо, теперь скажите, как мне к вам обращаться.\nP.s. Это буду знать только я)");
                            } else if (message.equals("Я админ")) {
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
                            user.setNickname(message);
                            sendYesNoQuestion(userId);
//                            user.setStatus(User.Status.REGISTERED);
//                            sendMessage(userId, "Спасибо за регистрацию! Вы успешно зарегистрированы.");
                            break;
                        default:
//                            sendMessage(Long.toString(update.getMessage().getChatId()),
//                                    "Привет," + userName + "\n" +
//                                            "*Тут выводим все доступные для данного пользователя кнопки*" + "\n" +
//                                            "Id group: " + chatId + "\n" +
//                                            "Id utente: " + userId + "\n" +
//                                            "UserName : " + userName + "\n" +
//                                            "FirstName : " + firstName + "\n" +
//                                            "LastName : " + lastName + "\n"
//                            );
                            break;
                    }
                }
            }
            System.out.println(userName + ": " + update.getMessage().getText());
        } else if (update.hasCallbackQuery()) {

            String call_data = update.getCallbackQuery().getData();
            String chatId = Long.toString(update.getCallbackQuery().getMessage().getChatId());

            if (call_data.equals("Auth_is_correct")) {
                sendMessage(chatId, "Отлично.\nЗаписали.");
                mock_users.get(chatId).setStatus(User.Status.REGISTERED);
            } else if (call_data.equals("Auth_is_not_correct")) {
                sendMessage(chatId, "Хорошо, давайте снова.\nВведите ваше имя.");
                mock_users.get(chatId).setStatus(User.Status.AWAITING_NAME);
            }
        }

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
//        public void setPrivilegies(Boolean is_admin) { this.is_admin = is_admin; }
        public void setStatus(Status status) { this.status = status; }
        public void setUsername(String username) { this.username = username; }
        public void setNickname(String nickname) { this.nickname = nickname; }
        public Status getStatus() { return status; }
    }

    private void sendMessage(String chatId, String text) {
        SendMessage message = new SendMessage(chatId, text);
        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private boolean isValidGroupFormat(String group) {
        Pattern pattern = Pattern.compile("^[А-Я]{2}\\d{1,2}-\\d{2,3}$");
        Matcher matcher = pattern.matcher(group);
        return matcher.find();
    }

    private void sendYesNoQuestion(String chatId) {
        User user = mock_users.get(chatId);
        SendMessage message = new SendMessage(chatId,
                "Уважаемый, " + user.nickname + ", проверьте корректность данных." + "\n" +
                "Фамилия: " + user.surname + "\n" +
                "Имя: " + user.name + "\n" +
                "Группа: " + user.group + "\n" +
                 "Всё верно?\n"
                );

        // Создаем кнопки
        List<InlineKeyboardRow> keyboard = new ArrayList<>();
        List<InlineKeyboardButton> keyboardRow = new ArrayList<>();

        InlineKeyboardButton yesButton = new InlineKeyboardButton("Да");
//        yesButton.setText("Да");
        yesButton.setCallbackData("Auth_is_correct");

        InlineKeyboardButton noButton = new InlineKeyboardButton("Нет");

        noButton.setCallbackData("Auth_is_not_correct");

        keyboardRow.add(yesButton);
        keyboardRow.add(noButton);

        InlineKeyboardRow row = new InlineKeyboardRow(keyboardRow);

        keyboard.add(row);

        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup(keyboard);

        message.setReplyMarkup(inlineKeyboardMarkup);

        try {
            telegramClient.execute(message);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

}
