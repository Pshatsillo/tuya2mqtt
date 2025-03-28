package com.tuya;

public class Tuya2MQTTConfig {
    public Tuya2MQTTConfig() {
    }
    private String mqttServer = "localhost";
    private String mqttPort = "1883";
    private String mqttUsername = "";
    private String mqttPassword = "";
    private String mqttTopic = "tuya2mqtt";
    private boolean enableHomeassistant = true;
    private String homeassistantTopic = "homeassistant";
    private String clientId = "";
    private String tokensObj = "";

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public void setTokensObj(String tokensObj) {
        this.tokensObj = tokensObj;
    }

    public String getMqttServer() {
        return mqttServer;
    }

    public String getMqttUsername() {
        return mqttUsername;
    }

    public String getMqttPassword() {
        return mqttPassword;
    }

    public String getMqttTopic() {
        return mqttTopic;
    }

    public boolean isEnableHomeassistant() {
        return enableHomeassistant;
    }

    public String getHomeassistantTopic() {
        return homeassistantTopic;
    }

    public String getClientId() {
        return clientId;
    }

    public String getTokensObj() {
        return tokensObj;
    }

    public void save() {

    }

    public String getMqttPort() {
        return mqttPort;
    }
}
