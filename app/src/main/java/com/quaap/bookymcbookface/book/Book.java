package com.quaap.bookymcbookface.book;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.quaap.bookymcbookface.FsTools;


/**
 * Copyright (C) 2017   Tom Kliethermes
 *
 * This file is part of BookyMcBookface and is is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License as published by the
 * Free Software Foundation; either version 3 of the License, or (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 */

public abstract class Book {
    private static final String FONTSIZE = "fontsize";
    private static final String SECTION_ID_OFFSET = "sectionIDOffset";
    private static final String SECTION_ID = "sectionID";
    public static final String BG_COLOR = "BG_COLOR";
    private String title;
    private File file;

    private File dataDir;
    private SharedPreferences data;
    private Context context;

    private List<String> sectionIDs;
    private int currentSectionIDPos = 0;

    private String subbook;
    private File thisBookDir;

    public Book(Context context) {
        this.dataDir = context.getFilesDir();
        this.context = context;
        sectionIDs = new ArrayList<>();
    }

    protected abstract void load() throws IOException;

    public abstract Map<String,String> getToc();

    protected abstract BookMetadata getMetaData() throws IOException;

    protected abstract List<String> getSectionIds();

    protected abstract Uri getUriForSectionID(String id);

    //protected abstract Uri getUriForSection(String section);

    //protected abstract String getSectionIDForSection(String section);

    protected abstract ReadPoint locateReadPoint(String section);

    public void load(File file) {
        this.file = file;
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

    public Uri getFirstSection() {
        clearSectionOffset();
        currentSectionIDPos = 0;
        saveCurrentSectionID();
        return getUriForSectionID(sectionIDs.get(currentSectionIDPos));
    }

    public Uri getCurrentSection() {
        restoreCurrentSectionID();
        if (currentSectionIDPos >= sectionIDs.size()) {
            currentSectionIDPos = 0;
            saveCurrentSectionID();
        }

        if (currentSectionIDPos>=sectionIDs.size()) {
            currentSectionIDPos = 0;
            Toast.makeText(context,"Something went wrong. Please report this book as a bug",Toast.LENGTH_LONG).show();
        }
        return getUriForSectionID(sectionIDs.get(currentSectionIDPos));
    }

    public void setFontsize(int fontsize) {
        data.edit().putInt(FONTSIZE, fontsize).apply();
    }

    public int getFontsize() {
        return data.getInt(FONTSIZE, -1);
    }

    public void clearFontsize() {
        data.edit().remove(FONTSIZE).apply();
    }

    public void setSectionOffset(int offset) {
        data.edit().putInt(SECTION_ID_OFFSET, offset).apply();
    }

    public int getSectionOffset() {
        return data.getInt(SECTION_ID_OFFSET, -1);
    }

    public void clearSectionOffset() {
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


    public Uri getNextSection() {
        if (currentSectionIDPos + 1 < sectionIDs.size()) {
            clearSectionOffset();
            currentSectionIDPos++;
            saveCurrentSectionID();
            return getUriForSectionID(sectionIDs.get(currentSectionIDPos));
        }
        return null;
    }

    public Uri getPreviousSection() {
        if (currentSectionIDPos - 1 >= 0) {
            clearSectionOffset();
            currentSectionIDPos--;
            saveCurrentSectionID();
            return getUriForSectionID(sectionIDs.get(currentSectionIDPos));
        }
        return null;
    }

    public Uri gotoSectionID(String id) {
        int pos = sectionIDs.indexOf(id);
        if (pos>-1 && pos < sectionIDs.size()) {
            currentSectionIDPos = pos;
            saveCurrentSectionID();
            return getUriForSectionID(sectionIDs.get(currentSectionIDPos));
        }
        return null;
    }

    public Uri handleClickedLink(String clickedLink) {
        ReadPoint readPoint = locateReadPoint(clickedLink);

        if (readPoint!=null) {
            gotoSectionID(readPoint.getId());
            clearSectionOffset();
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


    private static String makeFName(File file) {
        return file.getPath().replaceAll("/|\\\\","_");
    }

    public static SharedPreferences getStorage(Context context, File file) {
        return context.getSharedPreferences(makeFName(file), Context.MODE_PRIVATE);
    }

    private static File getBookDir(Context context, File file) {
        String subbook = "book" + makeFName(file);
        return new File(context.getFilesDir(), subbook);
    }

    public static boolean remove(Context context, File file) {
        try {
            FsTools.deleteDir(getBookDir(context, file));
            if (Build.VERSION.SDK_INT >= 24) {
                return context.deleteSharedPreferences(makeFName(file));
            } else {
                return getStorage(context, file).edit().clear().commit();
            }
        } catch (Exception e) {
            Log.e("Book", e.getMessage(),e);
        }
        return false;
    }

    public boolean remove() {
        FsTools.deleteDir(getThisBookDir());
        return data.edit().clear().commit();
    }

    public File getThisBookDir() {
        return thisBookDir;
    }

    public String getTitle() {
        return title;
    }

    protected void setTitle(String title) {
        this.title = title;
    }

    public File getFile() {
        return file;
    }

    protected void setFile(File file) {
        this.file = file;
    }


    public File getDataDir() {
        return dataDir;
    }

    protected Context getContext() {
        return context;
    }

    protected SharedPreferences getSharedPreferences() {
        return data;
    }



    public static String getFileExtensionRX() {
        return ".*\\.(epub|txt|html?)";
    }

    public static Book getBookHandler(Context context, String filename) throws IOException {
        Book book = null;
        if (filename.toLowerCase().endsWith(".epub")) {
            book = new EpubBook(context);
        } else if (filename.toLowerCase().endsWith(".txt")) {
            book = new TxtBook(context);
        } else if (filename.toLowerCase().endsWith(".html") || filename.toLowerCase().endsWith(".htm")) {
            book = new HtmlBook(context);
        }

        return book;

    }

    public static BookMetadata getBookMetaData(Context context, String filename) throws IOException {

        Book book = getBookHandler(context, filename);
        if (book!=null) {
            book.setFile(new File(filename));

            return book.getMetaData();
        }

        return null;

    }

    public class ReadPoint {
        private String id;
        private Uri point;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Uri getPoint() {
            return point;
        }

        public void setPoint(Uri point) {
            this.point = point;
        }
    }
}
