package com.github.zacharydhamilton.producer;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.ArrayList;
import java.util.zip.GZIPInputStream;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

public class GzipReader {
    public static ArrayList<JsonObject> readJson(String gzipFilePath) {
        ArrayList<JsonObject> array = new ArrayList<>();
        try (Reader reader = new InputStreamReader(new GZIPInputStream(new FileInputStream(gzipFilePath)), "UTF-8")) {
            JsonArray jsonArray = JsonParser.parseReader(reader).getAsJsonArray();
            for (JsonElement jsonElement : jsonArray) {
                JsonObject jsonObject = jsonElement.getAsJsonObject();
                array.add(jsonObject);
            }
            return array;
        } catch (Exception exception) {
            exception.printStackTrace();
        }
        return array;
    }

    // This only works with single-lined JSON files
    public static ArrayList<JsonObject> readNDJson(String gzipFilePath) {
        ArrayList<JsonObject> array = new ArrayList<>();
        try {
            FileInputStream fis = new FileInputStream(gzipFilePath);
            GZIPInputStream gis = new GZIPInputStream(fis);
            InputStreamReader isr = new InputStreamReader(gis);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
                JsonObject object = JsonParser.parseString(line).getAsJsonObject();
                array.add(object);
            }
        } catch (IOException exception) {
            exception.printStackTrace();
        } catch (JsonSyntaxException exception) {
            System.out.print("Error parsing line: " + exception.getMessage());
        }
        return array;
    }
}
