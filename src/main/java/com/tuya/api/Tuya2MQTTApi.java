package com.tuya.api;

import com.github.f4b6a3.uuid.UuidCreator;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.tuya.Tuya2MQTTConfig;
import com.tuya.Tuya2MQTTMain;
import com.tuya.models.Tuya2MQTTCloud;
import com.tuya.models.Tuya2MQTTDevices;
import com.tuya.models.Tuya2MQTTHomes;
import org.eclipse.paho.client.mqttv3.IMqttClient;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttPersistenceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.Mac;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.AlgorithmParameterSpec;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static com.tuya.Tuya2MQTTMain.config;
import static com.tuya.Tuya2MQTTMain.qos;
import static com.tuya.api.Tuya2MQTTConstants.TUYA_CLIENT_ID;
import static com.tuya.api.Tuya2MQTTConstants.TUYA_SCHEMA;
import static com.tuya.api.Tuya2MQTTConstants.URL_PATH;

public class Tuya2MQTTApi {
    static Logger logger = LoggerFactory.getLogger(Tuya2MQTTApi.class);
    JsonObject tokens;
    IMqttClient publisher;
    Tuya2MQTTCloud mqttCloud;
    List<Tuya2MQTTDevices> deviceList = new ArrayList<>();
    IMqttClient cloudPublisher = null;

    public static String byteArrayToHex(byte[] a) {
        StringBuilder sb = new StringBuilder(a.length * 2);
        for (byte b : a)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    public static void publishMessage(String s) {
        logger.info("message {}", s);
    }

    String request(String method, String request, String params, String body) throws RuntimeException, NoSuchAlgorithmException, InvalidKeyException, InvalidAlgorithmParameterException, NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException {

        HttpRequest req = null;
        HttpResponse<String> response = null;
        String rid = UUID.randomUUID().toString();
        String sid = "";
        MessageDigest md = MessageDigest.getInstance("MD5");
        String hash = rid + tokens.get("refresh_token").getAsString();
        md.update(hash.getBytes(StandardCharsets.UTF_8));
        String hashKey = byteArrayToHex(md.digest()).toLowerCase();

        String secret = genSecret(rid, sid, hashKey);

        String queryEncdata = "";

        if (params != null) {
            queryEncdata = aesEncrypt(params, secret);
            params = "encdata=" + URLEncoder.encode(queryEncdata, StandardCharsets.UTF_8);
        }

        String bodyEncdata = "";
        if (body != null) {
            bodyEncdata = aesEncrypt(body, secret);
            body = "{\"encdata\":\"" + bodyEncdata + "\"}";
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("X-appKey", TUYA_CLIENT_ID);
        headers.put("X-requestId", rid);
        headers.put("X-sid", sid);
        headers.put("X-time", String.valueOf(System.currentTimeMillis()));
        headers.put("X-token", tokens.get("access_token").getAsString());
        String sign = sign(hashKey, queryEncdata, bodyEncdata, headers);
        headers.put("X-sign", sign);


        try {
            HttpRequest.Builder reqBuilder;
            if ("POST".equals(method)) {
                if (body != null) {
                    reqBuilder = HttpRequest.newBuilder().uri(new URI(request)).POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
                } else {
                    reqBuilder = HttpRequest.newBuilder().uri(new URI(request)).POST(HttpRequest.BodyPublishers.noBody());
                }
                headers.forEach(reqBuilder::setHeader);
                req = reqBuilder.build();
            } else if ("GET".equals(method)) {
                if (params != null) {
                    URI uri = new URI(request + "?" + params); //URLEncoder.encode(params, StandardCharsets.UTF_8) .replace("+", "%2B")
                    //logger.info("url: {}", uri);
                    reqBuilder = HttpRequest.newBuilder().uri(uri).GET();
                } else {
                    reqBuilder = HttpRequest.newBuilder().uri(new URI(request)).GET();
                }
                headers.forEach(reqBuilder::setHeader);
                req = reqBuilder.build();
            }
            if (req != null) {
                response = HttpClient
                        .newBuilder().build().send(req, HttpResponse.BodyHandlers.ofString());
            }
        } catch (IOException | InterruptedException | URISyntaxException e) {
            throw new RuntimeException(e);
        }
        if (response != null && response.statusCode() == 200) {
            JsonObject encryptedResponse = JsonParser.parseString(response.body()).getAsJsonObject();
            if (encryptedResponse.get("success").getAsBoolean()) {
                String decrypt = aesDecrypt(encryptedResponse.get("result").getAsString(), secret);
                //logger.info("Decrypted Message {}", decrypt);
                return decrypt;
            } else
                logger.error("cannot fetch encrypted message {}, because {}", response.body(), encryptedResponse.get("msg").getAsString());

        } else return null;
        return null;
    }

    private String sign(String hashKey, String queryEncdata, String bodyEncdata, Map<String, String> headers) throws NoSuchAlgorithmException, InvalidKeyException {
        StringBuilder signString = new StringBuilder();
        String[] headersArr = {"X-appKey", "X-requestId", "X-sid", "X-time", "X-token" };
        for (String h : headersArr) {
            String val = headers.get(h);
            if (!val.isEmpty()) {
                signString.append(h).append("=").append(val).append("||");
            }
        }
        signString = new StringBuilder(signString.substring(0, signString.length() - 2));
        if (!queryEncdata.isEmpty()) {
            signString.append(queryEncdata);
        }
        if (!bodyEncdata.isEmpty()) {
            signString.append(bodyEncdata);
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec signHash = new SecretKeySpec(hashKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(signHash);
        return byteArrayToHex(mac.doFinal(signString.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private String genSecret(String rid, String sid, String hashKey) throws NoSuchAlgorithmException, InvalidKeyException {
        SecretKeySpec secretKeySpec = new SecretKeySpec(rid.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(secretKeySpec);
        String secret = byteArrayToHex(mac.doFinal(hashKey.getBytes(StandardCharsets.UTF_8)));
        secret = secret.substring(0, 16);
        return secret;
    }

    public void refreshToken() {
        try {
            Gson gson = new Gson();
            Path configJsonFile = Paths.get("config.json");
            Tuya2MQTTConfig config = gson.fromJson(new String(Files.readAllBytes(configJsonFile)), Tuya2MQTTConfig.class);
            long now = System.currentTimeMillis();
            long expiredTime = tokens.get("expire_time").getAsLong();
            long refreshTime = expiredTime - 120 * 1000;
            if (refreshTime > now) {
                Date date = new Date(now);
                Date refreshdate = new Date(expiredTime);
                DateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                // logger.info("[{}] do not need to refresh, refreshing at {}", formatter.format(date), formatter.format(refreshdate));
            } else {
                Date date = new Date(now);
                DateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
                logger.info("[{}] Refreshing token", formatter.format(date));
                String response = request("GET", tokens.get("endpoint").getAsString() + "/v1.0/m/token/" + tokens.get("refresh_token").getAsString(), null, null);
                if (response != null) {
                    JsonObject resp = JsonParser.parseString(response).getAsJsonObject().getAsJsonObject("result");
                    JsonObject tokens = JsonParser.parseString(config.getTokensObj()).getAsJsonObject();
                    tokens.addProperty("access_token", resp.get("accessToken").getAsString());
                    tokens.addProperty("refresh_token", resp.get("refreshToken").getAsString());
                    long expire_time = resp.get("expireTime").getAsInt() * 1000L + System.currentTimeMillis();
                    tokens.addProperty("expire_time", expire_time);
                    config.setTokensObj(tokens.toString());
                    String json = gson.toJson(config);
                    Files.write(configJsonFile, json.getBytes());
                    this.tokens = tokens;
                } else {
                    logger.error("Token refresh error. Please reauth");
                    System.exit(0);
                }
            }
        } catch (Exception e) {
            logger.error("refreshToken error {}", e.getLocalizedMessage());
        }
    }

    String randomNonce(int i) {
        String t = "ABCDEFGHJKMNPQRSTWXYZabcdefhijkmnprstwxyz2345678";
        int a = t.length();
        StringBuilder n = new StringBuilder();
        Random rnd = new Random();
        for (int j = 0; j < i; j++) {
            n.append(t.charAt(rnd.nextInt(a)));
        }
        return n.toString();
    }

    String aesEncrypt(String params, String secret) {
        try {
            String nonce = randomNonce(12);
            SecretKey secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "AES");
            final Cipher cipher;
            cipher = Cipher.getInstance("AES/GCM/NoPadding");
            AlgorithmParameterSpec gcmIv = new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmIv);
            byte[] cipherText = cipher.doFinal(params.getBytes(StandardCharsets.UTF_8));
            return new String(Base64.getEncoder().encode(nonce.getBytes()), StandardCharsets.UTF_8) + new String(Base64.getEncoder().encode(cipherText), StandardCharsets.UTF_8);
        } catch (NoSuchAlgorithmException e) {
            logger.error("NoSuchAlgorithmException, {}", e.getLocalizedMessage());
        } catch (NoSuchPaddingException e) {
            logger.error("NoSuchPaddingException, {}", e.getLocalizedMessage());
        } catch (InvalidAlgorithmParameterException e) {
            logger.error("InvalidAlgorithmParameterException, {}", e.getLocalizedMessage());
        } catch (IllegalBlockSizeException e) {
            logger.error("IllegalBlockSizeException, {}", e.getLocalizedMessage());
        } catch (BadPaddingException e) {
            logger.error("BadPaddingException, {}", e.getLocalizedMessage());
        } catch (InvalidKeyException e) {
            logger.error("InvalidKeyException, {}", e.getLocalizedMessage());
        }
        return null;
    }

    String aesDecrypt(String response, String secret) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidAlgorithmParameterException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
        SecretKey secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "AES");
        final Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] cipherData = Base64.getDecoder().decode(response.getBytes());
        AlgorithmParameterSpec gcmIv = new GCMParameterSpec(128, cipherData, 0, 12);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmIv);
        byte[] plainText = cipher.doFinal(cipherData, 12, cipherData.length - 12);
        return "{\"result\":" + new String(plainText, StandardCharsets.UTF_8) + "}";
    }

    public String genQrCode(String usercode) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(new URI("https://" + URL_PATH + "/v1.0/m/life/home-assistant/qrcode/tokens?clientid=" + TUYA_CLIENT_ID + "&usercode=" + usercode + "&schema=" + TUYA_SCHEMA)).POST(HttpRequest.BodyPublishers.noBody()).build();
        if (req != null) {
            HttpResponse<String> response = HttpClient
                    .newBuilder().build().send(req, HttpResponse.BodyHandlers.ofString());
            JsonObject resp = JsonParser.parseString(response.body()).getAsJsonObject();
            if (resp.get("success").getAsBoolean()) {
                return resp.get("result").getAsJsonObject().get("qrcode").getAsString();
            } else return "";
        }
        return "";
    }

    public Boolean login(String usercode, String token) throws URISyntaxException, IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder().uri(new URI("https://" + URL_PATH + "/v1.0/m/life/home-assistant/qrcode/tokens/" + token + "?clientid=" + TUYA_CLIENT_ID + "&usercode=" + usercode)).GET().build();
        if (req != null) {
            HttpResponse<String> response = HttpClient
                    .newBuilder().build().send(req, HttpResponse.BodyHandlers.ofString());
            tokens = JsonParser.parseString(response.body()).getAsJsonObject();
            boolean loginSuccess = tokens.get("success").getAsBoolean();
            long expire_time = tokens.get("result").getAsJsonObject().get("expire_time").getAsInt() * 1000L + System.currentTimeMillis();
            tokens.get("result").getAsJsonObject().addProperty("expire_time", expire_time);
            tokens = tokens.get("result").getAsJsonObject();
            return loginSuccess;
        }
        return false;
    }

    public Map<String, Tuya2MQTTHomes> getHomes(IMqttClient publisher) {
        this.publisher = publisher;
        refreshToken();
        try {
            String response = request("GET", tokens.get("endpoint").getAsString() + "/v1.0/m/life/users/homes", null, null);
            Map<String, Tuya2MQTTHomes> homes = new HashMap<>();
            JsonObject homesArray = JsonParser.parseString(response).getAsJsonObject();
            MqttMessage message = new MqttMessage(response.getBytes());
            message.setQos(qos);
            publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/raw", message);
            for (JsonElement home :
                    homesArray.get("result").getAsJsonArray()) {
                Gson gson = new Gson();
                Tuya2MQTTHomes tuya2MQTTHomes = gson.fromJson(home, Tuya2MQTTHomes.class);
                homes.put(tuya2MQTTHomes.getOwnerId(), tuya2MQTTHomes);
                message = new MqttMessage(tuya2MQTTHomes.getBackground().getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/background", message);

                message = new MqttMessage(tuya2MQTTHomes.getGeoName().getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/geoName", message);

                message = new MqttMessage(String.valueOf(tuya2MQTTHomes.getGmtCreate()).getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/gmtCreate", message);

                message = new MqttMessage(String.valueOf(tuya2MQTTHomes.getGmtModified()).getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/gmtModified", message);

                message = new MqttMessage(String.valueOf(tuya2MQTTHomes.getGroupId()).getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/groupId", message);

                message = new MqttMessage(String.valueOf(tuya2MQTTHomes.getId()).getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/id", message);

                message = new MqttMessage(String.valueOf(tuya2MQTTHomes.getLat()).getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/lat", message);

                message = new MqttMessage(String.valueOf(tuya2MQTTHomes.getLon()).getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/lon", message);

                message = new MqttMessage(tuya2MQTTHomes.getName().getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/name", message);

                message = new MqttMessage(tuya2MQTTHomes.getOwnerId().getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/ownerId", message);

                message = new MqttMessage(String.valueOf(tuya2MQTTHomes.isStatus()).getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/status", message);

                message = new MqttMessage(tuya2MQTTHomes.getUid().getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + tuya2MQTTHomes.getOwnerId() + "/uid", message);
            }

            return homes;
        } catch (NoSuchAlgorithmException e) {
            logger.error("NoSuchAlgorithmException {}", e.getLocalizedMessage());
        } catch (InvalidKeyException e) {
            logger.error("InvalidKeyException {}", e.getLocalizedMessage());
        } catch (InvalidAlgorithmParameterException e) {
            logger.error("InvalidAlgorithmParameterException {}", e.getLocalizedMessage());
        } catch (NoSuchPaddingException e) {
            logger.error("NoSuchPaddingException {}", e.getLocalizedMessage());
        } catch (IllegalBlockSizeException e) {
            logger.error("IllegalBlockSizeException {}", e.getLocalizedMessage());
        } catch (BadPaddingException e) {
            logger.error("BadPaddingException {}", e.getLocalizedMessage());
        } catch (MqttPersistenceException e) {
            throw new RuntimeException(e);
        } catch (MqttException e) {
            logger.error("MqttException {}", e.getLocalizedMessage());
        }
        return null;
    }

    public void getDevices(String ownerid) {
        refreshToken();
        // response = self.api.get(f"/v1.0/m/life/ha/home/devices", {"homeId": home_id})
        //logger.info("getting device from home id {}", ownerid);
        String homeid = "{\"homeId\":\"" + ownerid + "\"}";
        try {
            String response = request("GET", tokens.get("endpoint").getAsString() + "/v1.0/m/life/ha/home/devices", homeid, null);
            JsonObject devicesArray = JsonParser.parseString(response).getAsJsonObject();
            Tuya2MQTTDevices tuya2MQTTDevices;
            deviceList = new ArrayList<>();
            for (JsonElement devices :
                    devicesArray.get("result").getAsJsonArray()) {
                Gson gson = new Gson();
                tuya2MQTTDevices = gson.fromJson(devices, Tuya2MQTTDevices.class);
                String devSpecStr = updateDeviceSpecification(tuya2MQTTDevices.getId());
                JsonObject devSpec = JsonParser.parseString(devSpecStr).getAsJsonObject();
                String devInfoStr = getDevceStatus(tuya2MQTTDevices.getId());
                JsonObject devInfo = JsonParser.parseString(devInfoStr).getAsJsonObject();
                MqttMessage message = new MqttMessage(response.getBytes());
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + ownerid + "/devices/" + tuya2MQTTDevices.getId() + "/raw", message);

                message = new MqttMessage(tuya2MQTTDevices.getName().getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + ownerid + "/devices/" + tuya2MQTTDevices.getId() + "/name", message);

                message = new MqttMessage(String.valueOf(tuya2MQTTDevices.isOnline()).getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + ownerid + "/devices/" + tuya2MQTTDevices.getId() + "/online", message);

                message = new MqttMessage(String.valueOf(tuya2MQTTDevices.getProductId()).getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + ownerid + "/devices/" + tuya2MQTTDevices.getId() + "/ProductId", message);

                message = new MqttMessage(String.valueOf(tuya2MQTTDevices.getProductName()).getBytes(StandardCharsets.UTF_8));
                message.setQos(qos);
                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + ownerid + "/devices/" + tuya2MQTTDevices.getId() + "/ProductName", message);

                publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + ownerid + "/devices/" + tuya2MQTTDevices.getId() + "/LocalKey", new MqttMessage(String.valueOf(tuya2MQTTDevices.getLocalKey()).getBytes(StandardCharsets.UTF_8)));

                for (int i = 0; i < tuya2MQTTDevices.getStatus().length; i++) {
                    message = new MqttMessage(String.valueOf(tuya2MQTTDevices.getStatus()[i].getValueAsString()).getBytes(StandardCharsets.UTF_8));
                    message.setQos(qos);
                    String code = tuya2MQTTDevices.getStatus()[i].getCode();
                    publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + ownerid + "/devices/" + tuya2MQTTDevices.getId() + "/" + code, message);
                    JsonObject typeCode = devSpec.get("result").getAsJsonObject().get("status").getAsJsonArray().asList().stream().filter(f -> f.getAsJsonObject().get("code").getAsString().equals(code)).findFirst().get().getAsJsonObject();
                    JsonObject devInfoCode = devInfo.get("result").getAsJsonObject().get("dpStatusRelationDTOS").getAsJsonArray().asList().stream().filter(f -> f.getAsJsonObject().get("dpCode").getAsString().equals(code)).findFirst().get().getAsJsonObject();
                    publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + ownerid + "/devices/" + tuya2MQTTDevices.getId() + "/" + tuya2MQTTDevices.getStatus()[i].getCode() + "/type", new MqttMessage(typeCode.get("type").getAsString().getBytes(StandardCharsets.UTF_8)));
                    publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + ownerid + "/devices/" + tuya2MQTTDevices.getId() + "/" + tuya2MQTTDevices.getStatus()[i].getCode() + "/values", new MqttMessage(typeCode.get("values").getAsString().getBytes(StandardCharsets.UTF_8)));
                    publisher.publish(Tuya2MQTTMain.config.getMqttTopic() + "/homes/" + ownerid + "/devices/" + tuya2MQTTDevices.getId() + "/" + tuya2MQTTDevices.getStatus()[i].getCode() + "/dpId", new MqttMessage(devInfoCode.get("dpId").getAsString().getBytes(StandardCharsets.UTF_8)));
                }
                //tuya2MQTTDevices.setOwnerID(ownerid);
                deviceList.add(tuya2MQTTDevices);
            }
            getMqttConfig();
        } catch (Exception e) {
            logger.info("Exception {}", e.getLocalizedMessage());
        }
    }

    private String updateDeviceSpecification(String id) {
        try {
            String response = request("GET", tokens.get("endpoint").getAsString() + "/v1.1/m/life/" + id + "/specifications", null, null);
            return response;
        } catch (Exception e) {

        }
        return null;
    }

    private String getDevceStatus(String id) {
        try {
            String response = request("GET", tokens.get("endpoint").getAsString() + "/v1.0/m/life/devices/" + id + "/status", null, null);
            return response;
        } catch (Exception e) {
        }
        return null;
    }

    void getDevicesById() {
//  response = self.api.get("/v1.0/m/life/ha/devices/detail", {"devIds": ",".join(ids)})
    }

    public JsonObject getTokens() {
        return tokens;
    }

    public void setTokens(String tokensObj) {
        tokens = JsonParser.parseString(tokensObj).getAsJsonObject();
    }

    public void getMqttConfig() {
        long now = System.currentTimeMillis();
        long refreshTime = 1L;
        if(mqttCloud !=null) {
            refreshTime = Long.parseLong(mqttCloud.getExpireTime()) - 120 * 1000;
        }
        if (refreshTime > now) {
            Date date = new Date(now);
            Date refreshdate = new Date(Long.parseLong(mqttCloud.getExpireTime()));
            DateFormat formatter = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
            logger.info("[{}] do not need to refresh mqtt connection, refreshing at {}", formatter.format(date), formatter.format(refreshdate));
        } else {
            String linkId = "tuya-device-sharing-sdk-python." + UuidCreator.getTimeBased();
            try {
                Gson gson = new Gson();
                String response = request("POST", tokens.get("endpoint").getAsString() + "/v1.0/m/life/ha/access/config", null, "{\"linkId\":\"" + linkId + "\"}");
                mqttCloud = gson.fromJson(JsonParser.parseString(response).getAsJsonObject().get("result").getAsJsonObject(), Tuya2MQTTCloud.class);
                long expireTime = Long.parseLong(mqttCloud.getExpireTime()) * 1000L + System.currentTimeMillis();
                mqttCloud.setExpireTime(expireTime);
                logger.info("uuid {}", response);
                if(cloudPublisher != null){
                    cloudPublisher.disconnect();
                    cloudPublisher.close();
                    cloudPublisher = null;
                }
                connectToMqttCloud();
            } catch (Exception e) {
                logger.warn("Exception {}", e.getLocalizedMessage());
            }
        }
    }

    private void connectToMqttCloud() {
//        if(tuya2MqttCloudConnector == null){
//            tuya2MqttCloudConnector = new Tuya2MqttCloudConnector(mqttCloud, deviceList);
//        }
//        Thread.State st = tuya2MqttCloudConnector.getState();
//        if(!tuya2MqttCloudConnector.isAlive()) {
//            tuya2MqttCloudConnector.start();
//        } else {
//            logger.info("mqtt cloud thread is alive");
//        }
        try {
            if (cloudPublisher == null) {
                cloudPublisher = new MqttClient(mqttCloud.getUrl(), mqttCloud.getClientId());
            }
            if (!cloudPublisher.isConnected()) {
                MqttConnectOptions options = new MqttConnectOptions();
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                options.setConnectionTimeout(10);
                options.setUserName(mqttCloud.getUsername());
                options.setPassword(mqttCloud.getPassword());
                cloudPublisher.connect(options);
                if (cloudPublisher.isConnected()) {
                    logger.info("Connected to tuya cloud mqtt");
                    cloudPublisher.setCallback(new MqttCallback() {
                        @Override
                        public void connectionLost(Throwable throwable) {

                        }

                        @Override
                        public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
                            System.out.println("topic: " + s);
                            System.out.println("message content: " + new String(mqttMessage.getPayload()));
                            Tuya2MQTTApi.publishMessage(new String(mqttMessage.getPayload()));
                        }

                        @Override
                        public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {

                        }
                    });
                    deviceList.forEach(device -> {
                        try {
                            cloudPublisher.subscribe(mqttCloud.getTopic().getDevId().getSub(device), 1);
                            publisher.subscribe(config.getMqttTopic() + "/homes/" + device.getOwnerId()+ "/devices/"+ device.getId() + "/set", 1);
                        } catch (MqttException e) {
                            logger.error("Mqtt error {}", e.getLocalizedMessage());
                        }
                    });

                }
            } else {

            }
        } catch (MqttException e) {
            logger.error("Cloud error {}", e.getLocalizedMessage());
            cloudPublisher = null;
        }
    }

    public void sendCommand(String deviceId, String command) {
        String[] commandSl = command.replace("{", "").replace("}", "").replace("\"", "").split(":");
        //deviceId = "bf002b9329dc464e3172ln";

        command = "{\"code\":\""+commandSl[0]+"\",\"value\":"+commandSl[1].toLowerCase()+"}";
        try {
            String response = request("POST", tokens.get("endpoint").getAsString() + "/v1.1/m/thing/" + deviceId.split("/")[4] + "/commands", null, "{\"commands\":[" + command + "]}");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}