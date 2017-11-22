package com.davemorrissey.labs.subscaleview.test;

import android.app.ActionBar;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.FragmentActivity;
import android.view.MenuItem;

import java.util.List;

public abstract class AbstractFragmentsActivity extends FragmentActivity {

    private static final String BUNDLE_PAGE = "page";

    private int page;

    private final int title;
    private final int layout;
    private final List<Page> notes;

    protected abstract void onPageChanged(int page);

    protected AbstractFragmentsActivity(int title, int layout, List<Page> notes) {
        this.title = title;
        this.layout = layout;
        this.notes = notes;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(layout);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setTitle(getString(title));
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
        if (savedInstanceState != null && savedInstanceState.containsKey(BUNDLE_PAGE)) {
            page = savedInstanceState.getInt(BUNDLE_PAGE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotes();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(BUNDLE_PAGE, page);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        finish();
        return true;
    }

    public void next() {
        page++;
        updateNotes();
    }

    public void previous() {
        page--;
        updateNotes();
    }

    private void updateNotes() {
        if (page > notes.size() - 1) {
            return;
        }
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(notes.get(page).getSubtitle());
        }
        onPageChanged(page);
    }

}
