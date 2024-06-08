package com.frugs.yomo;

import android.app.Application;
import android.content.Context;

import androidx.annotation.Nullable;

import com.frugs.yomo.syosetu.SyosetuService;

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

public class BookyApp extends Application {

    @Nullable private BookDb db;
    @Nullable private SyosetuService syosetuService;

    @Override
    public void onCreate() {
        super.onCreate();

        db = new BookDb(this);
        syosetuService = new SyosetuService(this);
    }

    public static BookDb getDB(Context context) {
        return ((BookyApp)context.getApplicationContext()).db;
    }

    public static SyosetuService getSyosetuService(Context context) {
        return ((BookyApp)context.getApplicationContext()).syosetuService;
    }


    @Override
    public void onTerminate() {
        if (db!=null) db.close();

        super.onTerminate();
    }
}
