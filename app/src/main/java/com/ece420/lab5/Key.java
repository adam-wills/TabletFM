package com.ece420.lab5;

import android.graphics.RectF;

public class Key {

    public double sound;
    public RectF rect;
    public boolean down;

    public Key(RectF rect, double sound){
        this.sound = sound;
        this.rect = rect;
    }
}
