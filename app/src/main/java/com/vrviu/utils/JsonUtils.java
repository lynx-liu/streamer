package com.vrviu.utils;

import org.json.JSONException;
import org.json.JSONObject;

public class JsonUtils {
    static public String get(JSONObject jsonObject,String key,String defval){
        try {
            return jsonObject.getString(key);
        } catch (JSONException e) {
            return defval;
        }
    }

    static public int get(JSONObject jsonObject,String key,int defval){
        try {
            return jsonObject.getInt(key);
        } catch (JSONException e) {
            return defval;
        }
    }

    static public double get(JSONObject jsonObject,String key,double defval){
        try {
            return jsonObject.getDouble(key);
        } catch (JSONException e) {
            return defval;
        }
    }
}
