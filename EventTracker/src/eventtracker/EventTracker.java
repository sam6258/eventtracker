package eventtracker;

import com.pi4j.io.gpio.GpioController;
import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioPinDigitalOutput;
import com.pi4j.io.gpio.Pin;
import com.pi4j.io.gpio.PinState;
import com.pi4j.io.gpio.RaspiPin;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EventTracker 
{
    final static String USER_AGENT = "Mozilla/5.0";
    static String dbBaseUrl = "https://api.mlab.com/api/1/databases/hack_psu_events/collections";
    static String apiKey;

    final static GpioController gpio = GpioFactory.getInstance();
    final static GpioPinDigitalOutput green_led = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_24, "MyLED", PinState.LOW);
    final static GpioPinDigitalOutput red_led = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_23, "MyLED", PinState.LOW);
    public static void main(String[] args) throws IOException, JSONException, InterruptedException 
    {
        green_led.setShutdownOptions(true, PinState.LOW);
        red_led.setShutdownOptions(true, PinState.LOW);
        BufferedReader br = new BufferedReader(new FileReader("apiKey.key"));
        apiKey = br.readLine();
        Scanner scanner = new Scanner(System.in);
        SimpleDateFormat time_formatter = new SimpleDateFormat("yyyy-MM-dd_HH:mm");
        String currentEvent;
        System.out.print("Enter event id: ");
        currentEvent = scanner.nextLine().toLowerCase();
        String testId;
        HttpClient client = new DefaultHttpClient();
        
        while((testId = scanner.next()) != null) 
        {


            HttpGet request = new HttpGet(dbBaseUrl + "/participants/" + testId + "?apiKey=" + apiKey);

            // add request header
            request.addHeader("User-Agent", USER_AGENT);

            HttpResponse response = client.execute(request);
            BufferedReader rd = new BufferedReader(
                           new InputStreamReader(response.getEntity().getContent()));

            StringBuffer result = new StringBuffer();
            String line = "";
            while ((line = rd.readLine()) != null) 
            {
                    result.append(line);
            }

            JSONObject participantObj = new JSONObject(result.toString());
            String id;
            Boolean userExists = true;
            try 
            {
                id = participantObj.getString("_id");
                userExists = true;

            }
            catch(JSONException ex) 
            {
                userExists = false;
            }
            
            if (userExists) 
            {
                Boolean okayToAttend = true;
                JSONArray events = participantObj.getJSONArray("events");
                JSONArray timestamps = participantObj.getJSONArray("timestamp");

                for (int i = 0; i < events.length() && okayToAttend; i++)
                {
                    if (events.get(i).equals(currentEvent)) {
                        System.out.println("_id = " + testId + " has already been to event " + currentEvent);
                        System.out.println(result);
                        //display red light here
                        turn_on_led(false);
                        okayToAttend = false;
                    }
                }
                if (okayToAttend) {
                    System.out.println("_id = " + testId + " has not been to event " + currentEvent + " yet.");
                    //display green light here
                    turn_on_led(true);
                    events.put(currentEvent);
                    timestamps.put(time_formatter.format(System.currentTimeMillis()));
                    JSONObject postObj = new JSONObject();
                    postObj.put("_id", testId);
                    postObj.put("events", events);
                    postObj.put("timestamp", timestamps);
                    post_participant(postObj);
                    System.out.println(postObj);
                }
            }
            else 
            {
                System.out.println("_id = " + testId + " is a new user.");
                System.out.println("_id = " + testId + " added for event " + currentEvent);
                //display green light here
                turn_on_led(true);
                JSONObject postObj = new JSONObject();
                postObj.put("_id", testId);
                postObj.put("events", new JSONArray("[\"" + currentEvent + "\"]"));
                postObj.put("timestamp", new JSONArray("[\"" + time_formatter.format(System.currentTimeMillis()) + "\"]"));
                post_participant(postObj);
                System.out.println(postObj);
            }
        }
        gpio.shutdown();
    }
    
    public static void turn_on_led(Boolean acceptLight) throws InterruptedException {
        // create gpio controller
        if (acceptLight == true) 
        {
            green_led.pulse(1000, true);
        }
        else 
        {
            red_led.pulse(1000, true);
        }
        // provision gpio pin #01 as an output pin and turn on
        
    }
    
    public static void post_participant(JSONObject obj) throws UnsupportedEncodingException, IOException 
    {
        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(dbBaseUrl + "/participants?apiKey=" + apiKey);

        // add header
        post.setHeader("User-Agent", USER_AGENT);
        StringEntity entity = new StringEntity(obj.toString());
        entity.setContentType("application/json");
        post.setEntity(entity);
        

        HttpResponse response = client.execute(post);
        System.out.println("Response Code : " +
                            response.getStatusLine().getStatusCode());

        BufferedReader rd = new BufferedReader(
                new InputStreamReader(response.getEntity().getContent()));

        StringBuffer result = new StringBuffer();
        String line = "";
        while ((line = rd.readLine()) != null) {
                result.append(line);
        }
    }
}
