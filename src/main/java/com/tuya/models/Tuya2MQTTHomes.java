package com.tuya.models;

public class Tuya2MQTTHomes {
    private String background = "";
    private String geoName = "";
    private int gmtCreate = 0;
    private int gmtModified = 0;
    private int groupId = 0;
    private int id = 0;
    private int lat = 0;
    private int lon = 0;
    private String name = "";
    private String ownerId = "";
    private boolean status = false;
    private String uid = "";

    public String getBackground() {
        return background;
    }

    public String getGeoName() {
        return geoName;
    }

    public int getGmtCreate() {
        return gmtCreate;
    }

    public int getGmtModified() {
        return gmtModified;
    }

    public int getGroupId() {
        return groupId;
    }

    public int getId() {
        return id;
    }

    public int getLat() {
        return lat;
    }

    public int getLon() {
        return lon;
    }

    public String getName() {
        return name;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public boolean isStatus() {
        return status;
    }

    public String getUid() {
        return uid;
    }
}
