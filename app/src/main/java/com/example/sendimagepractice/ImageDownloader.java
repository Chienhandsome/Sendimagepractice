package com.example.sendimagepractice;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.InputStream;
import java.net.URL;

public class ImageDownloader {
    private static final String TAG = "ImageDownloader";
    public static Bitmap downloadImage(String url) {
        try{
            InputStream in = new URL(url).openStream();
            Log.d(TAG, "downloadImageAndConvertToBitmap success : " );
            return BitmapFactory.decodeStream(in);
        }catch (Exception e){
            Log.e(TAG, "Error downloading image, downloaded image is null, image: ", e);
            return null;
        }
    }
}
