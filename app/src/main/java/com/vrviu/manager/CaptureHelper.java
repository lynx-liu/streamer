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

    public CaptureHelper(Point screenSize, Point videoSize, int orientation) {
        Rect screenRect = new Rect(0, 0, screenSize.x, screenSize.y);

        iDisplayCapture = SurfaceControl.createDisplay("capture", true);
        if(screenSize.x< screenSize.y && orientation==0) {
            mImageReader = ImageReader.newInstance(videoSize.y, videoSize.x, PixelFormat.RGBA_8888, 2);
            SurfaceControl.setDisplaySurface(iDisplayCapture, mImageReader.getSurface(), screenRect, new Rect(0,0, videoSize.y, videoSize.x), 3);
        } else {
            mImageReader = ImageReader.newInstance(videoSize.x, videoSize.y, PixelFormat.RGBA_8888, 2);
            SurfaceControl.setDisplaySurface(iDisplayCapture, mImageReader.getSurface(), screenRect, new Rect(0, 0, videoSize.x, videoSize.y), 0);
        }
    }

    private Bitmap screenCap(String filename) {
        Image image = mImageReader.acquireLatestImage();
        if(image==null) return null;

        int width = image.getWidth();
        int height = image.getHeight();
        final Image.Plane[] planes = image.getPlanes();
        final ByteBuffer buffer = planes[0].getBuffer();
        int pixelStride = planes[0].getPixelStride();
        int rowStride = planes[0].getRowStride();
        int rowPadding = rowStride - pixelStride * width;
        Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
        bitmap.copyPixelsFromBuffer(buffer);
        bitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height);
        image.close();

        if (filename!=null && bitmap != null) {
            try {
                FileOutputStream out = new FileOutputStream(filename);
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                out.flush();
                out.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return bitmap;
    }

    public void Release() {
        if (iDisplayCapture != null) {
            SurfaceControl.setDisplaySurface(iDisplayCapture, null, new Rect(), new Rect(), 0);
            SurfaceControl.destroyDisplay(iDisplayCapture);
            iDisplayCapture = null;
        }

        if(mImageReader!=null) {
            mImageReader.close();
            mImageReader = null;
        }
    }
}
