import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class Main {
    public static void main(String[] args) {
        try {
            String botToken = "7197799406:AAE_h0MQl7ViddGsl6sMAI_ww-GEFYGFZtw";
            TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication();
            botsApplication.registerBot(botToken, new Authentication());
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
}
