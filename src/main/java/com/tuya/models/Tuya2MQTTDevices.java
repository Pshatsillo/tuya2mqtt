package com.tuya.models;

import com.google.gson.annotations.SerializedName;

public class Tuya2MQTTDevices {

    @SerializedName("active_time")
    private int activeTime = 0;
    @SerializedName("biz_type")
    private int bizType = 0;
    private String category = "";
    @SerializedName("create_time")
    private int createTime = 0;
    private String icon = "";
    private String id = "";
    private String ip = "";
    private String lat = "";
    @SerializedName("local_key")
    private String localKey = "";
    private String lon = "";
    private String name = "";
    private boolean online = false;
    @SerializedName("owner_id")
    private String ownerId = "";
    @SerializedName("product_id")
    private String productId = "";
    @SerializedName("product_name")
    private String productName = "";
    private Status[] status = null;
    private boolean sub = false;
    @SerializedName("time_zone")
    private String timeZone = "";
    private String uid = "";
    @SerializedName("update_time")
    private long updateTime = 0L;
    private String uuid = "";

    public int getActiveTime() {
        return activeTime;
    }

    public int getBizType() {
        return bizType;
    }

    public String getCategory() {
        return category;
    }

    public int getCreateTime() {
        return createTime;
    }

    public String getIcon() {
        return icon;
    }

    public String getId() {
        return id;
    }

    public String getIp() {
        return ip;
    }

    public String getLat() {
        return lat;
    }

    public String getLocalKey() {
        return localKey;
    }

    public String getLon() {
        return lon;
    }

    public String getName() {
        return name;
    }

    public boolean isOnline() {
        return online;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public String getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }

    public Status[] getStatus() {
        return status;
    }

    public boolean isSub() {
        return sub;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public String getUid() {
        return uid;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public String getUuid() {
        return uuid;
    }

    public class Status {
        private String code = "";
        @SerializedName("value")
        private String valueAsString = "";
        public String getCode() {
            return code;
        }
        public String getValueAsString() {
            return valueAsString;
        }
    }
}
