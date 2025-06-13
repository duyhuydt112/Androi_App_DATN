package com.p4f.objecttracking;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class AssetUtils {
    public static File copyAssetToFile(Context context, String assetName) throws IOException {
        File file = new File(context.getFilesDir(), assetName);
        if (file.exists()) return file;

        InputStream is = context.getAssets().open(assetName);
        FileOutputStream os = new FileOutputStream(file);

        byte[] buffer = new byte[4096];
        int length;
        while ((length = is.read(buffer)) > 0) {
            os.write(buffer, 0, length);
        }

        is.close();
        os.close();
        return file;
    }

//    File proto = AssetUtils.copyAssetToFile(this, "MobileNetSSD_deploy.prototxt.txt");
//    File model = AssetUtils.copyAssetToFile(this, "MobileNetSSD_deploy.caffemodel");

}
