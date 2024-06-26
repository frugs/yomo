package com.frugs.yomo;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.SpannableString;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.frugs.yomo.book.Book;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

public class BookListActivity extends AppCompatActivity {

    private static final String SORTORDER_KEY = "sortorder";
    private static final String LASTSHOW_STATUS_KEY = "LastshowStatus";
    private static final String STARTWITH_KEY = "startwith";

    private static final int STARTLASTREAD = 1;
    private static final int STARTOPEN = 2;
    private static final int STARTALL = 3;

    private static final String ACTION_SHOW_OPEN = "com.frugs.yomo.SHOW_OPEN_BOOKS";
    private static final String ACTION_SHOW_UNREAD = "com.frugs.yomo.SHOW_UNREAD_BOOKS";
    public static final String ACTION_SHOW_LAST_STATUS = "com.frugs.yomo.SHOW_LAST_STATUS";

    private SharedPreferences data;

    private BookAdapter bookAdapter;

    private BookListAdderHandler viewAdder;
    private TextView tv;

    private BookDb db;
    private int recentread;
    private boolean showingSearch;

    private int showStatus = BookDb.STATUS_ANY;

    public final String SHOW_STATUS = "showStatus";

    public final static String prefname = "booklist";

    private boolean openLastread = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_book_list);

        tv = findViewById(R.id.progress_text);
        checkStorageAccess(false);

        data = getSharedPreferences(prefname, Context.MODE_PRIVATE);

        viewAdder = new BookListAdderHandler(this);

        if (!data.contains(SORTORDER_KEY)) {
            setSortOrder(SortOrder.Default);
        }

        //getApplicationContext().deleteDatabase(BookDb.DBNAME);

        db = BookyApp.getDB(this);

        RecyclerView listHolder = findViewById(R.id.book_list_holder);
        listHolder.setLayoutManager(new LinearLayoutManager(this));
        listHolder.setItemAnimator(new DefaultItemAnimator());

        bookAdapter = new BookAdapter(this, db, new ArrayList<Integer>());
        bookAdapter.setOnClickListener(view -> readBook((int) view.getTag()));
        bookAdapter.setOnLongClickListener(view -> {
            longClickBook(view);
            return false;
        });

        listHolder.setAdapter(bookAdapter);

        processIntent(getIntent());

        //Log.d("BOOKLIST", "onCreate end");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        //Log.d("BOOKLIST", "onNewIntent");
        super.onNewIntent(intent);
        processIntent(intent);
    }

    private void processIntent(Intent intent) {

        recentread = db.getMostRecentlyRead();

        showStatus = BookDb.STATUS_ANY;

        openLastread = false;

        boolean hadSpecialOpen = false;
        //Intent intent = getIntent();
        if (intent != null) {
            if (intent.getAction() != null) {
                switch (intent.getAction()) {
                    case ACTION_SHOW_OPEN:
                        showStatus = BookDb.STATUS_STARTED;
                        hadSpecialOpen = true;
                        break;
                    case ACTION_SHOW_UNREAD:
                        showStatus = BookDb.STATUS_NONE;
                        hadSpecialOpen = true;
                        break;
                    case ACTION_SHOW_LAST_STATUS:
                        showStatus = data.getInt(LASTSHOW_STATUS_KEY, BookDb.STATUS_ANY);
                        hadSpecialOpen = true;
                        break;
                }

            }
        }

        if (!hadSpecialOpen) {

            switch (data.getInt(STARTWITH_KEY, STARTLASTREAD)) {
                case STARTLASTREAD:
                    if (recentread != -1 && data.getBoolean(ReaderActivity.READEREXITEDNORMALLY, true))
                        openLastread = true;
                    break;
                case STARTOPEN:
                    showStatus = BookDb.STATUS_STARTED;
                    break;
                case STARTALL:
                    showStatus = BookDb.STATUS_ANY;
            }
        }


    }

    @Override
    protected void onResume() {
        //Log.d("BOOKLIST", "onResume");
        super.onResume();
        if (openLastread) {
            openLastread = false;
            viewAdder.postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        BookDb.BookRecord book = db.getBookRecord(recentread);
                        getReader(book, true);
                        //finish();
                    } catch (Exception e) {
                        data.edit().putInt(STARTWITH_KEY, STARTALL).apply();
                    }
                }
            }, 200);
        } else {
            populateBooks(showStatus);
        }
    }

    @Override
    public void onBackPressed() {
        if (showingSearch || showStatus != BookDb.STATUS_ANY) {
            setTitle(R.string.app_name);
            populateBooks();
            showingSearch = false;
        } else {
            super.onBackPressed();
        }
    }

    private void setSortOrder(SortOrder sortOrder) {
        data.edit().putString(SORTORDER_KEY, sortOrder.name()).apply();
    }

    @NonNull
    private SortOrder getSortOrder() {

        try {
            return SortOrder.valueOf(data.getString(SORTORDER_KEY, SortOrder.Default.name()));
        } catch (IllegalArgumentException e) {
            Log.e("Booklist", e.getMessage(), e);
            return SortOrder.Default;
        }
    }

    private void populateBooks() {
        populateBooks(BookDb.STATUS_ANY);
    }

    private void populateBooks(int status) {
        showStatus = status;
        data.edit().putInt(LASTSHOW_STATUS_KEY, showStatus).apply();

        boolean showRecent = false;
        int title = R.string.app_name;
        switch (status) {
            case BookDb.STATUS_SEARCH:
                String lastSearch = data.getString("__LAST_SEARCH_STR__", "");
                if (!lastSearch.trim().isEmpty()) {
                    boolean stitle = data.getBoolean("__LAST_TITLE__", true);
                    boolean sauthor = data.getBoolean("__LAST_AUTHOR__", true);
                    searchBooks(lastSearch, stitle, sauthor);
                    return;
                }
            case BookDb.STATUS_ANY:
                title = R.string.book_status_any;
                showRecent = true;
                showingSearch = false;
                break;
            case BookDb.STATUS_NONE:
                title = R.string.book_status_none;
                showingSearch = false;
                break;
            case BookDb.STATUS_STARTED:
                title = R.string.book_status_started;
                showRecent = true;
                showingSearch = false;
                break;
            case BookDb.STATUS_DONE:
                title = R.string.book_status_completed2;
                showingSearch = false;
                break;
            case BookDb.STATUS_LATER:
                title = R.string.book_status_later2;
                showingSearch = false;
                break;
        }
        BookListActivity.this.setTitle(title);

        SortOrder sortorder = getSortOrder();
        final List<Integer> books = db.getBookIds(sortorder, status);
        populateBooks(books, showRecent);

        invalidateOptionsMenu();
    }


    private void searchBooks(String searchfor, boolean stitle, boolean sauthor) {
        showStatus = BookDb.STATUS_SEARCH;
        data.edit().putInt(LASTSHOW_STATUS_KEY, showStatus).apply();
        List<Integer> books = db.searchBooks(searchfor, stitle, sauthor);
        populateBooks(books, false);
        BookListActivity.this.setTitle(getString(R.string.search_res_title, searchfor, books.size()));
        showingSearch = true;
        invalidateOptionsMenu();
    }

    private void populateBooks(final List<Integer> books, boolean showRecent) {

        if (showRecent) {
            recentread = db.getMostRecentlyRead();
            if (recentread >= 0) {
                //viewAdder.displayBook(recentread);
                books.remove((Integer) recentread);
                books.add(0, recentread);
            }
        }

        bookAdapter.setBooks(books);
    }


    private void updateViewTimes() {
        bookAdapter.notifyItemRangeChanged(0, bookAdapter.getItemCount());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.options, menu);

        SortOrder sortorder = getSortOrder();

        switch (sortorder) {
            case Default:
                menu.findItem(R.id.menu_sort_default).setChecked(true);
                break;
            case Author:
                menu.findItem(R.id.menu_sort_author).setChecked(true);
                break;
            case Title:
                menu.findItem(R.id.menu_sort_title).setChecked(true);
                break;
            case Added:
                menu.findItem(R.id.menu_sort_added).setChecked(true);
                break;
        }

        switch (data.getInt(STARTWITH_KEY, STARTLASTREAD)) {
            case STARTALL:
                menu.findItem(R.id.menu_start_all_books).setChecked(true);
                break;
            case STARTOPEN:
                menu.findItem(R.id.menu_start_open_books).setChecked(true);
                break;
            case STARTLASTREAD:
                menu.findItem(R.id.menu_start_last_read).setChecked(true);
                break;
        }

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        menu.findItem(R.id.menu_add).setVisible(!showingSearch);
        menu.findItem(R.id.menu_sort).setVisible(!showingSearch);

        switch (showStatus) {
            case BookDb.STATUS_ANY:
                menu.findItem(R.id.menu_all_books).setChecked(true);
                break;
            case BookDb.STATUS_DONE:
                menu.findItem(R.id.menu_completed_books).setChecked(true);
                break;
            case BookDb.STATUS_LATER:
                menu.findItem(R.id.menu_later_books).setChecked(true);
                break;
            case BookDb.STATUS_NONE:
                menu.findItem(R.id.menu_unopen_books).setChecked(true);
                break;
            case BookDb.STATUS_STARTED:
                menu.findItem(R.id.menu_open_books).setChecked(true);
                break;
        }

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int status = BookDb.STATUS_ANY;
        boolean pop = false;
        int itemId = item.getItemId();
        if (itemId == R.id.menu_about) {
            showMsg(BookListActivity.this, getString(R.string.about), getString(R.string.about_app));
        } else if (itemId == R.id.menu_sort_default) {
            item.setChecked(true);
            setSortOrder(SortOrder.Default);
            pop = true;
        } else if (itemId == R.id.menu_sort_author) {
            item.setChecked(true);
            setSortOrder(SortOrder.Author);
            pop = true;
        } else if (itemId == R.id.menu_sort_title) {
            item.setChecked(true);
            setSortOrder(SortOrder.Title);
            pop = true;
        } else if (itemId == R.id.menu_sort_added) {
            item.setChecked(true);
            setSortOrder(SortOrder.Added);
            pop = true;
        } else if (itemId == R.id.menu_completed_books) {
            pop = true;
            status = BookDb.STATUS_DONE;
        } else if (itemId == R.id.menu_later_books) {
            pop = true;
            status = BookDb.STATUS_LATER;
        } else if (itemId == R.id.menu_open_books) {
            pop = true;
            status = BookDb.STATUS_STARTED;
        } else if (itemId == R.id.menu_unopen_books) {
            pop = true;
            status = BookDb.STATUS_NONE;
        } else if (itemId == R.id.menu_search_books) {
            showSearch();
        } else if (itemId == R.id.menu_all_books) {
            pop = true;
            status = BookDb.STATUS_ANY;
        } else if (itemId == R.id.menu_start_all_books) {
            data.edit().putInt(STARTWITH_KEY, STARTALL).apply();
        } else if (itemId == R.id.menu_start_open_books) {
            data.edit().putInt(STARTWITH_KEY, STARTOPEN).apply();
        } else if (itemId == R.id.menu_start_last_read) {
            data.edit().putInt(STARTWITH_KEY, STARTLASTREAD).apply();
        } else if (itemId == R.id.menu_open_syosetu || itemId == R.id.menu_add) {
            Intent intent = new Intent(this, SyosetuActivity.class);
            startActivity(intent);
        } else {
            return super.onOptionsItemSelected(item);
        }


        final int statusf = status;
        if (pop) {
            viewAdder.postDelayed(() -> {
                populateBooks(statusf);
                invalidateOptionsMenu();
            }, 120);
        }

        invalidateOptionsMenu();
        return true;
    }


    public static String maxlen(String text, int maxlen) {
        if (text != null && text.length() > maxlen) {
            int minus = text.length() > 3 ? 3 : 0;

            return text.substring(0, maxlen - minus) + "...";
        }
        return text;
    }

    private void readBook(final int bookid) {

        final BookDb.BookRecord book = db.getBookRecord(bookid);

        if (book != null && book.filename != null) {
            //data.edit().putString(LASTREAD_KEY, BOOK_PREFIX + book.id).apply();

            final long now = System.currentTimeMillis();
            db.updateLastRead(bookid, now);
            recentread = bookid;

            viewAdder.postDelayed(new Runnable() {
                @Override
                public void run() {

                    getReader(book, true);

                }
            }, 300);

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N_MR1) {

                try {

                    ShortcutManager shortcutManager = (ShortcutManager) getSystemService(Context.SHORTCUT_SERVICE);
                    if (shortcutManager != null) {
                        Intent readBook = getReader(book, false);


                        ShortcutInfo readShortcut = new ShortcutInfo.Builder(this, "id1").setShortLabel(getString(R.string.shortcut_latest)).setLongLabel(getString(R.string.shortcut_latest_title, maxlen(book.title, 24))).setIcon(Icon.createWithResource(BookListActivity.this, R.mipmap.ic_launcher_round)).setIntent(readBook).build();


                        shortcutManager.setDynamicShortcuts(Collections.singletonList(readShortcut));
                    }
                } catch (Exception e) {
                    Log.e("Booky", e.getMessage(), e);
                }
            }


        }
    }

    private Intent getReader(BookDb.BookRecord book, boolean start) {
        Intent readBook = new Intent(BookListActivity.this, ReaderActivity.class);
        readBook.putExtra(ReaderActivity.FILENAME, book.filename);
        readBook.setAction(Intent.ACTION_VIEW);
        if (start) {
            bookAdapter.notifyItemIdChanged(book.id);
            startActivity(readBook);
        }
        return readBook;
    }


    private void removeBook(int bookId, boolean delete) {
        BookDb.BookRecord book = db.getBookRecord(bookId);
        if (book == null) {
            Toast.makeText(this, "Bug? The book doesn't seem to be in the database", Toast.LENGTH_LONG).show();
            return;
        }
        if (book.filename != null && book.filename.length() > 0) {
            Book.remove(this, new File(book.filename));
        }
        if (delete) {
            db.removeBook(bookId);
            if (bookAdapter != null) {
                bookAdapter.notifyItemIdRemoved(bookId);
            }
        }

        recentread = db.getMostRecentlyRead();
    }

    private void showProgress(int added) {

        if (tv.getVisibility() != View.VISIBLE) {
            tv.setVisibility(View.VISIBLE);
            tv.setOnTouchListener((v, event) -> true);
        }
        if (added > 0) {
            tv.setText(getString(R.string.added_numbooks, added));
        } else {
            tv.setText(R.string.loading);
        }
    }

    private void hideProgress() {
        tv.setVisibility(View.GONE);
    }


    private void longClickBook(final View view) {
        final int bookid = (int) view.getTag();
        PopupMenu menu = new PopupMenu(this, view);
        menu.getMenu().add(R.string.open_book).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                readBook(bookid);
                return false;
            }
        });

        final int status = db.getStatus(bookid);
        final long lastread = db.getLastReadTime(bookid);

        if (status != BookDb.STATUS_DONE) {
            menu.getMenu().add(R.string.mark_completed).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    if (lastread > 0) {
                        removeBook(bookid, false);
                    } else {
                        db.updateLastRead(bookid, System.currentTimeMillis());
                    }
                    updateBookStatus(bookid, view, BookDb.STATUS_DONE);

                    return false;
                }
            });
        }

        if (status != BookDb.STATUS_LATER && status != BookDb.STATUS_DONE) {
            menu.getMenu().add(R.string.mark_later).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    updateBookStatus(bookid, view, BookDb.STATUS_LATER);
                    return false;
                }
            });
        }

        if (status == BookDb.STATUS_LATER || status == BookDb.STATUS_DONE) {
            menu.getMenu().add(R.string.un_mark).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {

                    updateBookStatus(bookid, view, lastread > 0 ? BookDb.STATUS_STARTED : BookDb.STATUS_NONE);
                    return false;
                }
            });
        }


        if (status == BookDb.STATUS_STARTED) {

            menu.getMenu().add(R.string.close_book).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem menuItem) {
                    removeBook(bookid, false);
                    updateBookStatus(bookid, view, BookDb.STATUS_NONE);
                    //updateViewTimes();
                    return false;
                }
            });
        }


        menu.getMenu().add(R.string.remove_book).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                //((ViewGroup)view.getParent()).removeView(view);
                removeBook(bookid, true);
                return false;
            }
        });
        menu.show();
    }

    private void updateBookStatus(int bookid, View view, int status) {
        db.updateStatus(bookid, status);
        if (bookAdapter != null) bookAdapter.notifyItemIdChanged(bookid);
//        listHolder.removeView(view);
//        listHolder.addView(view);
        //       updateViewTimes();
    }

    private boolean checkStorageAccess(boolean yay) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, yay ? REQUEST_READ_EXTERNAL_STORAGE : REQUEST_READ_EXTERNAL_STORAGE_NOYAY);
            return false;
        }
        return true;
    }

    private static final int REQUEST_READ_EXTERNAL_STORAGE_NOYAY = 4333;
    private static final int REQUEST_READ_EXTERNAL_STORAGE = 4334;

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        boolean yay = true;
        switch (requestCode) {
            case REQUEST_READ_EXTERNAL_STORAGE_NOYAY:
                yay = false;
            case REQUEST_READ_EXTERNAL_STORAGE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    if (yay) Toast.makeText(this, "Yay", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Boo", Toast.LENGTH_LONG).show();
                }

        }
    }

    private static void showMsg(Context context, String title, String message) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(title);

        final TextView messageview = new TextView(context);
        messageview.setPadding(32, 8, 32, 8);

        final SpannableString s = new SpannableString(message);
        Linkify.addLinks(s, Linkify.ALL);
        messageview.setText(s);
        messageview.setMovementMethod(LinkMovementMethod.getInstance());
        messageview.setTextSize(18);

        builder.setView(messageview);

        builder.setNeutralButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog dialog = builder.create();
        dialog.show();
    }

    private void showSearch() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle(android.R.string.search_go);

        LayoutInflater inflater = this.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.search, null);
        builder.setView(dialogView);

        final EditText editText = dialogView.findViewById(R.id.search_text);
        final RadioButton author = dialogView.findViewById(R.id.search_author);
        final RadioButton title = dialogView.findViewById(R.id.search_title);
        final RadioButton authortitle = dialogView.findViewById(R.id.search_authortitle);

        builder.setPositiveButton(android.R.string.search_go, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                String searchfor = editText.getText().toString();

                if (!searchfor.trim().isEmpty()) {
                    boolean stitle = title.isChecked() || authortitle.isChecked();
                    boolean sauthor = author.isChecked() || authortitle.isChecked();
                    data.edit().putString("__LAST_SEARCH_STR__", searchfor).putBoolean("__LAST_TITLE__", stitle).putBoolean("__LAST_AUTHOR__", sauthor).apply();

                    searchBooks(searchfor, stitle, sauthor);
                } else {
                    dialogInterface.cancel();
                }
            }
        });

        builder.setNegativeButton(android.R.string.cancel, null);

        editText.setFocusable(true);
        final AlertDialog alertDialog = builder.create();
        alertDialog.show();

        title.setChecked(data.getBoolean("__LAST_TITLE__", false));
        author.setChecked(data.getBoolean("__LAST_AUTHOR__", false));

        String lastSearch = data.getString("__LAST_SEARCH_STR__", "");
        editText.setText(lastSearch);
        editText.setSelection(lastSearch.length());
        editText.setSelection(0, lastSearch.length());

        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!lastSearch.isEmpty());

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).setEnabled(!editText.getText().toString().trim().isEmpty());
            }
        });


        final InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        editText.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        editText.setImeActionLabel(getString(android.R.string.search_go), EditorInfo.IME_ACTION_SEARCH);

        editText.setOnEditorActionListener(new TextView.OnEditorActionListener() {

            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (event != null && event.getAction() != KeyEvent.ACTION_DOWN) {
                    return false;
                } else if (actionId == EditorInfo.IME_ACTION_SEARCH || event == null || event.getKeyCode() == KeyEvent.KEYCODE_ENTER) {
                    if (!editText.getText().toString().trim().isEmpty()) {
                        editText.clearFocus();

                        if (imm != null) imm.hideSoftInputFromWindow(editText.getWindowToken(), 0);
                        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).callOnClick();
                    }
                    return true;
                }

                return false;
            }
        });

        editText.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (imm != null) imm.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT);
            }
        }, 100);

    }

    private static class BookListAdderHandler extends Handler {

        private static final int SHOW_PROGRESS = 1002;
        private static final int HIDE_PROGRESS = 1003;
        private final WeakReference<BookListActivity> weakReference;

        BookListAdderHandler(BookListActivity blInstance) {
            weakReference = new WeakReference<>(blInstance);
        }


        void showProgress(int progress) {
            Message msg = new Message();
            msg.arg1 = BookListAdderHandler.SHOW_PROGRESS;
            msg.arg2 = progress;
            sendMessage(msg);
        }

        void hideProgress() {
            Message msg = new Message();
            msg.arg1 = BookListAdderHandler.HIDE_PROGRESS;
            sendMessage(msg);
        }

        @Override
        public void handleMessage(Message msg) {
            BookListActivity blInstance = weakReference.get();
            if (blInstance != null) {
                switch (msg.arg1) {

                    case SHOW_PROGRESS:
                        blInstance.showProgress(msg.arg2);
                        break;
                    case HIDE_PROGRESS:
                        blInstance.hideProgress();
                        break;
                }
            }
        }
    }

}
