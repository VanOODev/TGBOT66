import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class Main {
    private static final String API_URL = "https://api.telegram.org/bot7197799406:AAE_h0MQl7ViddGsl6sMAI_ww-GEFYGFZtw/";
    private static long lastUpdateId = 0;

    public static void main(String[] args) {
        Authentication bot = new Authentication(API_URL);
        while (true) {
            try {
                URL url = new URL(API_URL + "getUpdates?offset=" + (lastUpdateId + 1));
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
                JSONArray resultArray = response.getJSONArray("result");

                for (int i = 0; i < resultArray.length(); i++) {
                    JSONObject update = resultArray.getJSONObject(i);
                    lastUpdateId = update.getLong("update_id");
                    bot.handleUpdate(update);
                }

                Thread.sleep(1000);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
