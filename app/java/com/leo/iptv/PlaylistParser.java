package com.leo.iptv;

import java.util.ArrayList;
import java.util.List;

// Parses a user-supplied M3U playlist. group-title drives the category and,
// for movies/series, the type. tvg-logo drives the logo.
public class PlaylistParser {
    private static String attr(String line, String key) {
        String needle = key + "=\"";
        int i = line.indexOf(needle);
        if (i < 0) return "";
        int s = i + needle.length();
        int e = line.indexOf('"', s);
        return e < 0 ? "" : line.substring(s, e);
    }
    public static List<Item> parseM3U(String text) {
        List<Item> out = new ArrayList<Item>();
        if (text == null) return out;
        String name = "", group = "", logo = "";
        for (String raw : text.split("\\r?\\n")) {
            String line = raw.trim();
            if (line.startsWith("#EXTINF")) {
                name = attr(line, "tvg-name");
                group = attr(line, "group-title");
                logo = attr(line, "tvg-logo");
                if (name.length() == 0) {
                    int c = line.lastIndexOf(',');
                    if (c >= 0 && c + 1 < line.length()) name = line.substring(c + 1).trim();
                }
                if (name.length() == 0) name = "Unnamed channel";
                if (group.length() == 0) group = "General";
            } else if (line.startsWith("http")) {
                String type = "LIVE";
                String gl = group.toLowerCase();
                if (gl.contains("movie") || gl.contains("film") || gl.contains("vod")) type = "MOVIE";
                else if (gl.contains("series") || gl.contains("serie")) type = "SERIES";
                Item it = new Item(name, group, type, line, "");
                it.logo = logo;
                out.add(it);
                name = ""; group = ""; logo = "";
            }
        }
        return out;
    }
}
