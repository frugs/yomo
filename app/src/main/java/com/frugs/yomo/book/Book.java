package com.frugs.yomo.book;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.Checksum;


/**
 * Copyright (C) 2017   Tom Kliethermes
 * <p>
 * This file is part of BookyMcBookface and is is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 * <p>
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

public abstract class Book {
    private static final String FONTSIZE = "fontsize";
    private static final String SECTION_ID_OFFSET = "sectionIDOffset";
    private static final String SECTION_ID = "sectionID";
    private static final String BG_COLOR = "BG_COLOR";
    private String title;
    private File file;

    private final File dataDir;

    private String dataFileName;
    private SharedPreferences data;
    private final Context context;

    private List<String> sectionIDs;
    private int currentSectionIDPos = 0;

    private File thisBookDir;

    Book(Context context) {
        this.dataDir = context.getFilesDir();
        this.context = context;
        sectionIDs = new ArrayList<>();
    }

    protected abstract void load() throws IOException;

    public abstract Map<String, String> getToc();

    protected abstract List<String> getSectionIds();

    protected abstract Uri getUriForSectionID(String id);

    //protected abstract Uri getUriForSection(String section);

    //protected abstract String getSectionIDForSection(String section);

    protected abstract ReadPoint locateReadPoint(String section);

    public void load(File file) {
        this.file = file;
        dataFileName = getProperFName(context, file);
        data = getStorage(context, file);

        thisBookDir = getBookDir(context, file);
        thisBookDir.mkdirs();
        try {
            load();
        } catch (IOException e) {
            e.printStackTrace();
        }
        sectionIDs = getSectionIds();
        restoreCurrentSectionID();
    }

    public boolean hasDataDir() {
        return data != null;
    }

    public Uri getCurrentSection() {
        try {
            restoreCurrentSectionID();
            if (currentSectionIDPos >= sectionIDs.size()) {
                currentSectionIDPos = 0;
                saveCurrentSectionID();
            }

            if (sectionIDs.size() == 0) {
                return null;
            }
            return getUriForSectionID(sectionIDs.get(currentSectionIDPos));
        } catch (Throwable t) {
            Log.e("Booky", t.getMessage(), t);
            return null;
        }
    }

    public void setFontSize(int fontSize) {
        data.edit().putInt(FONTSIZE, fontSize).apply();
    }

    public int getFontSize() {
        return data.getInt(FONTSIZE, -1);
    }

    public void setSectionOffset(int offset) {
        data.edit().putInt(SECTION_ID_OFFSET, offset).apply();
    }

    public int getSectionOffset() {
        if (data == null) {
            return 0;
        }
        return data.getInt(SECTION_ID_OFFSET, -1);
    }

    private void clearSectionOffset() {
        data.edit().remove(SECTION_ID_OFFSET).apply();
    }


    public void setBackgroundColor(int color) {
        data.edit().putInt(BG_COLOR, color).apply();
    }

    public int getBackgroundColor() {
        return data.getInt(BG_COLOR, Integer.MAX_VALUE);
    }

    public void clearBackgroundColor() {
        data.edit().remove(BG_COLOR).apply();
    }


    public void setFlag(String key, boolean value) {
        data.edit().putBoolean(key, value).apply();
    }

    public boolean getFlag(String key, boolean value) {
        return data.getBoolean(key, value);
    }

    public boolean hasNextSection() {
        return currentSectionIDPos + 1 < sectionIDs.size();
    }

    public boolean hasPreviousSection() {
        return currentSectionIDPos - 1 >= 0;
    }

    public Uri gotoNextSection() {
        try {
            if (hasNextSection()) {
                return gotoSectionID(sectionIDs.get(currentSectionIDPos + 1));
            }
        } catch (Throwable t) {
            Log.e("Booky", t.getMessage(), t);
        }
        return null;
    }

    public Uri gotoPreviousSection() {
        try {
            if (hasPreviousSection()) {
                return gotoSectionID(sectionIDs.get(currentSectionIDPos - 1));
            }
        } catch (Throwable t) {
            Log.e("Booky", t.getMessage(), t);
        }
        return null;
    }

    @Nullable
    private Uri gotoSectionID(String id) {
        try {
            int pos = sectionIDs.indexOf(id);
            if (pos > -1 && pos < sectionIDs.size()) {
                clearSectionOffset();
                currentSectionIDPos = pos;
                saveCurrentSectionID();
                return getUriForSectionID(sectionIDs.get(currentSectionIDPos));
            }
        } catch (Throwable t) {
            Log.e("Booky", t.getMessage(), t);
        }

        return null;
    }

    public Uri handleClickedLink(String clickedLink) {
        ReadPoint readPoint = locateReadPoint(clickedLink);

        if (readPoint != null) {
            gotoSectionID(readPoint.getId());
            return readPoint.getPoint();
        }
        return null;
    }


    private void saveCurrentSectionID() {
        Log.d("Book", "saving section " + currentSectionIDPos);
        data.edit().putInt(SECTION_ID, currentSectionIDPos).apply();
    }

    private void restoreCurrentSectionID() {
        currentSectionIDPos = data.getInt(SECTION_ID, currentSectionIDPos);
        Log.d("Book", "Loaded section " + currentSectionIDPos);
    }

    private static String makeOldFName(File file) {
        return file.getPath().replaceAll("[/\\\\]", "_");
    }

    private static final String reservedChars = "[/\\\\:?\"'*|<>+\\[\\]()]";

    private static String makeFName(File file) {
        String fname = file.getPath().replaceAll(reservedChars, "_");
        if (fname.getBytes().length > 60) {
            //for very long names, we take the first part and the last part and the crc.
            // should be unique.
            int len = 30;
            if (fname.length() <= len) {  //just in case I'm missing some utf bytes vs length weirdness here
                len = fname.length() - 1;
            }
            fname = fname.substring(0, len) + fname.substring(fname.length() - len / 2) + crc32(fname);
        }
        return fname;
    }

    private static long crc32(String input) {
        byte[] bytes = input.getBytes();
        Checksum checksum = new CRC32();
        checksum.update(bytes, 0, bytes.length);
        return checksum.getValue();
    }

    //fix long/invalid filenames while maintaining those that somehow worked.
    private static String getProperFName(Context context, File file) {
        String fname;
        if (hasOldBookDir(context, file)) {
            fname = makeOldFName(file);
            Log.d("Book", "using old fname " + fname);
        } else {
            fname = makeFName(file);
            Log.d("Book", "using new fname " + fname);
        }
        return fname;
    }

    private static boolean hasOldBookDir(Context context, File file) {
        String subbook = "book" + makeOldFName(file);
        return new File(context.getFilesDir(), subbook).exists();
    }

    private static File getBookDir(Context context, File file) {
        String fname = getProperFName(context, file);
        String subbook = "book" + fname;
        return new File(context.getFilesDir(), subbook);
    }

    private static SharedPreferences getStorage(Context context, File file) {
        String fname = getProperFName(context, file);
        return context.getSharedPreferences(fname, Context.MODE_PRIVATE);
    }

    public static void remove(Context context, File file) {
        try {
            deleteDir(getBookDir(context, file));
            String fName = getProperFName(context, file);
            context.deleteSharedPreferences(fName);
        } catch (Exception e) {
            Log.e("Book", e.getMessage(), e);
        }
    }

    public static void deleteDir(File dir) {
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDir(file);
                    } else {
                        file.delete();
                    }
                }
            }
        }

        dir.delete();
    }

    File getThisBookDir() {
        return thisBookDir;
    }

    public String getTitle() {
        return title;
    }

    protected void setTitle(String title) {
        this.title = title;
    }

    File getFile() {
        return file;
    }


    public File getDataDir() {
        return dataDir;
    }

    protected Context getContext() {
        return context;
    }

    protected String getDataFileName() {
        return dataFileName;
    }

    protected SharedPreferences getSharedPreferences() {
        return data;
    }

    protected int getCurrentSectionIDPos() {
        return currentSectionIDPos;
    }

    public static Book getBookHandler(Context context, String filename) {
        Book book = null;

        if (filename.toLowerCase().endsWith(".ncode")) {
            book = new SyosetuBook(context);
        }

        return book;
    }

    protected class ReadPoint {
        private String id;
        private Uri point;

        String getId() {
            return id;
        }

        void setId(String id) {
            this.id = id;
        }

        Uri getPoint() {
            return point;
        }

        void setPoint(Uri point) {
            this.point = point;
        }
    }
}
