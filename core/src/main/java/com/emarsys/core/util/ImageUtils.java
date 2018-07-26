package com.emarsys.core.util;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.webkit.URLUtil;

import com.emarsys.core.DeviceInfo;

import java.io.File;

public class ImageUtils {

    public static Bitmap loadBitmap(Context context, String imageUrl) {
        Bitmap result = null;
        if (ImageUtils.validateParameters(context, imageUrl)) {
            String fileUrl = downloadImage(context, imageUrl);
            result = loadBitmap(fileUrl, Integer.MAX_VALUE);
            if (fileUrl != null && isRemoteUrl(imageUrl)) {
                FileUtils.delete(fileUrl);
            }
        }
        return result;
    }

    public static Bitmap loadOptimizedBitmap(Context context, String imageUrl) {
        Bitmap result = null;
        if (ImageUtils.validateParameters(context, imageUrl)) {
            String fileUrl = downloadImage(context, imageUrl);
            DeviceInfo deviceInfo = new DeviceInfo(context);
            result = loadBitmap(fileUrl, deviceInfo.getDisplayMetrics().widthPixels);
            if (fileUrl != null && isRemoteUrl(imageUrl)) {
                FileUtils.delete(fileUrl);
            }
        }
        return result;
    }

    private static boolean validateParameters(Context context, String imageUrl) {
        boolean result = true;
        if (context == null || imageUrl == null) {
            result = false;
        } else if (!URLUtil.isHttpsUrl(imageUrl)) {
            result = new File(imageUrl).exists();
        }
        return result;
    }

    private static String downloadImage(Context context, String imageUrl) {
        String fileUrl = imageUrl;
        if (isRemoteUrl(imageUrl)) {
            fileUrl = FileUtils.download(context, imageUrl);
        }
        return fileUrl;
    }

    private static boolean isRemoteUrl(String imageUrl) {
        return URLUtil.isHttpsUrl(imageUrl);
    }

    private static Bitmap loadBitmap(String imageFileUrl, int width) {
        final BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(imageFileUrl, options);

        options.inSampleSize = calculateInSampleSize(options, width);

        options.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(imageFileUrl, options);
    }

    static int calculateInSampleSize(BitmapFactory.Options options, int reqWidth) {
        final int width = options.outWidth;
        int inSampleSize = 1;
        while (reqWidth <= width / inSampleSize) {
            inSampleSize *= 2;
        }
        return inSampleSize;
    }

}
