package com.vrviu.manager;

import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;
import android.os.IBinder;

import com.vrviu.utils.SurfaceControl;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

public class CaptureHelper {

    private IBinder iDisplayCapture = null;
    private ImageReader mImageReader = null;
    private Point screenSize = null;

    public CaptureHelper(Point screenSize) {
        this.screenSize = screenSize;
        iDisplayCapture = SurfaceControl.createDisplay("capture", true);
        mImageReader = ImageReader.newInstance(screenSize.x, screenSize.y, PixelFormat.RGBA_8888, 2);

        Rect screenRect = new Rect(0, 0, screenSize.x, screenSize.y);
        SurfaceControl.setDisplaySurface(iDisplayCapture, mImageReader.getSurface(), screenRect, screenRect, 0);
    }

    public int[] screenCap(String filename) {
        if(mImageReader==null) return null;
        Image image = mImageReader.acquireLatestImage();
        if(image==null) return null;

        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int width = image.getWidth();
        int height = image.getHeight();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);

        if (filename!=null) {
            try {
                FileOutputStream out = new FileOutputStream(filename);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
                out.flush();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        int[] pixels = new int[width*height];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        image.close();
        return pixels;
    }

    public Point getScreenSize() {
        return screenSize;
    }

    public void Release() {
        if (iDisplayCapture != null) {
            SurfaceControl.destroyDisplay(iDisplayCapture);
            iDisplayCapture = null;
        }

        if(mImageReader!=null) {
            mImageReader.close();
            mImageReader = null;
        }
    }
}
