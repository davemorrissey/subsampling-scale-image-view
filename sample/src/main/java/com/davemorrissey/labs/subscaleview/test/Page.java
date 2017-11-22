package com.davemorrissey.labs.subscaleview.test;

public class Page {

    private final int text;

    private final int subtitle;

    public Page(int subtitle, int text) {
        this.subtitle = subtitle;
        this.text = text;
    }

    public int getText() {
        return text;
    }

    public int getSubtitle() {
        return subtitle;
    }
}
