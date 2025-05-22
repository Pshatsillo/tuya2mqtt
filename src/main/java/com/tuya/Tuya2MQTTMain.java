package com.tuya;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.zxing.WriterException;
import com.tuya.api.Tuya2MQTTApi;
import com.tuya.models.Tuya2MQTTHomes;
import io.github.shashankn.qrterminal.QRCode;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Scanner;
import java.util.UUID;

public class Tuya2MQTTMain {
    static Tuya2MQTTApi api;
    public static Tuya2MQTTConfig config = null;
    static JsonObject tokens;
    static Gson gson;
    static Path configJsonFile = Paths.get("config.json");
    static Logger logger = LoggerFactory.getLogger(Tuya2MQTTMain.class);
    public static int qos = 1;
    public static void main(String[] args) {

        String publisherId = UUID.randomUUID().toString();

        IMqttClient publisher = null;


        try {
            config = new Tuya2MQTTConfig();
            api = new Tuya2MQTTApi();
            gson = new Gson();
            if (configJsonFile.toFile().exists()) {
                config = gson.fromJson(new String(Files.readAllBytes(configJsonFile)), Tuya2MQTTConfig.class);
                if(!config.getTokensObj().isEmpty()) {
                    api.setTokens(config.getTokensObj());
                }
                System.out.println("Loading config file");
                if(args.length > 0) {
                    if ("reauth".equals(args[0])) {
                        auth();
                    }
                }
            } else {
                System.out.println("config file not found. Creating...");
                String userCode = "";
                if (args.length > 0) {
                    if("reauth".equals(args[0])) {
                        auth();
                    } else {
                        userCode = args[0];
                    }
                }
                String yn = "n";
                while (true) {
                    Scanner in = new Scanner(System.in);
                    if (userCode.isEmpty()) {
                        System.out.println("Please enter User Code from Smart Life app: ");
                        userCode = in.next();
                    }
                    System.out.println("Usercode is: " + userCode + " [Y/N]:");
                    yn = in.next();
                    if ((yn.equals("Y")) || (yn.equals("y") )|| (yn.isEmpty())) {
                        config.setClientId(userCode);
                        String json = gson.toJson(config);
                        Files.write(configJsonFile, json.getBytes());
                        break;
                    }
                    userCode = "";
                }
            }
            if(config.getTokensObj().isEmpty()){
                auth();
            }
            try {
                publisher = new MqttClient("tcp://" + config.getMqttServer() + ":" + config.getMqttPort(), publisherId);
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                options.setConnectionTimeout(10);
                publisher.connect(options);
                if (publisher.isConnected()) {
                    MqttMessage message = new MqttMessage("Online".getBytes());
                    message.setQos(qos);
                    publisher.publish(config.getMqttTopic() + "/status", message);
                    publisher.setCallback(new MqttCallback() {

                        @Override
                        public void connectionLost(Throwable throwable) {

                        }

                        @Override
                        public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                            System.out.println("topic: " + s);
                            System.out.println("message content: " + new String(mqttMessage.getPayload()));
                            api.sendCommand(s, new String(mqttMessage.getPayload()));
                        }

                        @Override
                        public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

                        }
                    });
                }
            }catch (Exception e){
                logger.error("MQTT Error: {}", e.getLocalizedMessage());
            }
            while (true){
                api.refreshToken();
                if(publisher.isConnected()) {
                    Map<String, Tuya2MQTTHomes> homes = api.getHomes(publisher);
                    homes.forEach((k, v) -> {
                        api.getDevices(v.getOwnerId());
                    });
                }
                Thread.sleep(1000 * 60);
            }
        } catch (Exception e){
            MqttMessage message = new MqttMessage("Offline".getBytes());
            message.setQos(qos);
            if(publisher != null) {
                try {
                    publisher.publish(config.getMqttTopic() + "/status", message);
                } catch (MqttException ex) {
                    logger.error("Error publish mqtt: {}", e.getLocalizedMessage());
                }
                logger.error("Error: {}", e.getLocalizedMessage());
            }
        }
    }

    private static void auth() {
        try {
            System.out.println("It seems you do not logged in to Tuya via Smart Life app.");
            System.out.println("Please open app, press \"+\" button and click 'scan' to scan qr code. Then press ENTER");
            System.in.read();
            String qr = api.genQrCode(config.getClientId());
            System.out.print(QRCode.from("tuyaSmart--qrLogin?token="+ qr).generate());
            System.out.print(QRCode.from("tuyaSmart--qrLogin?token="+qr).generateHalfBlock());
            System.out.println("Waiting 10 seconds");
            Thread.sleep(10000);
            if(api.login(config.getClientId(), qr)){
                tokens = api.getTokens();
                System.out.print(tokens.toString());
                config.setTokensObj(tokens.toString());
                String json = gson.toJson(config);
                Files.write(configJsonFile, json.getBytes());
            }
        } catch (IOException | URISyntaxException | InterruptedException e) {
            logger.error("Auth error, IOException or URISyntaxException or InterruptedException: {}", e.getLocalizedMessage());
        } catch (WriterException e) {
            logger.error("Auth error, WriterException: {}", e.getLocalizedMessage());
        }
    }
}