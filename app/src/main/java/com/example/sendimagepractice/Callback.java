package com.example.sendimagepractice;

import android.graphics.Bitmap;

public interface Callback {
    void onSuccess(String imgLink);
    void onError(String error);
}
