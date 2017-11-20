/*
Copyright 2017 David Morrissey

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

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
