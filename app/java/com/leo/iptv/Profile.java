package com.leo.iptv;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

// A user-supplied account. Credentials live only in on-device SharedPreferences.
public class Profile {
    public String name, type, host, user, pass, m3uUrl, accountLine;
    public Profile(String name, String type, String host, String user, String pass,
                   String m3uUrl, String accountLine) {
        this.name = name; this.type = type; this.host = host; this.user = user;
        this.pass = pass; this.m3uUrl = m3uUrl; this.accountLine = accountLine;
    }
    public boolean isXtream() { return "XTREAM".equals(type); }
    public JSONObject toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("name", name); o.put("type", type); o.put("host", host);
            o.put("user", user); o.put("pass", pass); o.put("m3uUrl", m3uUrl);
            o.put("accountLine", accountLine);
            return o;
        } catch (Exception e) { return new JSONObject(); }
    }
    public static Profile fromJson(JSONObject o) {
        return new Profile(o.optString("name"), o.optString("type"),
                o.optString("host"), o.optString("user"), o.optString("pass"),
                o.optString("m3uUrl"), o.optString("accountLine"));
    }
    public static String listToJson(List<Profile> ps) {
        JSONArray a = new JSONArray();
        for (Profile p : ps) a.put(p.toJson());
        return a.toString();
    }
    public static List<Profile> listFromJson(String s) {
        List<Profile> out = new ArrayList<Profile>();
        if (s == null || s.length() == 0) return out;
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) out.add(fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }
}
