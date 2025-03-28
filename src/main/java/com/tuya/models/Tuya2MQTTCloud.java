package com.tuya.models;

public class Tuya2MQTTCloud {
    String clientId = "";
    String expireTime = "";
    String password = "";
    Topic topic;
    String url = "";
    String username = "";

    public String getClientId() {
        return clientId;
    }

    public String getExpireTime() {
        return expireTime;
    }

    public String getPassword() {
        return password;
    }

    public Topic getTopic() {
        return topic;
    }

    public String getUrl() {
        return url;
    }

    public String getUsername() {
        return username;
    }

    public class Topic {
       DevId devId;

        public DevId getDevId() {
            return devId;
        }

        private class DevId {
            String pub = "";
            String  sub = "";

            public String getPub() {
                return pub;
            }

            public String getSub() {
                return sub;
            }
        }
    }
}
