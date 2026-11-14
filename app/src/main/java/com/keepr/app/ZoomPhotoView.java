package com.keepr.app;

import android.content.Context;
import android.graphics.*;
import android.view.*;

/** One bitmap at a time; fit-to-screen, pinch zoom, pan, and double-tap reset. */
public final class ZoomPhotoView extends View {
    private Bitmap bitmap;
    private final Matrix matrix=new Matrix();
    private final ScaleGestureDetector scale;
    private final GestureDetector gestures;
    private float zoom=1;
    public ZoomPhotoView(Context context) {
        super(context); setContentDescription("Photo viewer. Pinch to zoom, drag to pan, double tap to reset.");
        scale=new ScaleGestureDetector(context,new ScaleGestureDetector.SimpleOnScaleGestureListener(){
            @Override public boolean onScale(ScaleGestureDetector detector) {
                float next=Math.max(1,Math.min(6,zoom*detector.getScaleFactor()));
                matrix.postScale(next/zoom,next/zoom,detector.getFocusX(),detector.getFocusY()); zoom=next; invalidate(); return true;
            }
        });
        gestures=new GestureDetector(context,new GestureDetector.SimpleOnGestureListener(){
            @Override public boolean onDown(MotionEvent event) { return true; }
            @Override public boolean onDoubleTap(MotionEvent event) { fit(); return true; }
            @Override public boolean onScroll(MotionEvent first,MotionEvent last,float dx,float dy) {
                if(zoom>1 && !scale.isInProgress()) { matrix.postTranslate(-dx,-dy); invalidate(); } return true;
            }
        });
    }
    public void setPhoto(Bitmap value) { bitmap=value; fit(); }
    public void clear() { bitmap=null; invalidate(); }
    private void fit() { zoom=1; matrix.reset(); if(bitmap!=null) matrix.setRectToRect(new RectF(0,0,bitmap.getWidth(),bitmap.getHeight()),new RectF(0,0,getWidth(),getHeight()),Matrix.ScaleToFit.CENTER); invalidate(); }
    @Override protected void onSizeChanged(int width,int height,int oldWidth,int oldHeight) { fit(); }
    @Override protected void onDraw(Canvas canvas) { super.onDraw(canvas); if(bitmap!=null) canvas.drawBitmap(bitmap,matrix,new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG)); }
    @Override public boolean onTouchEvent(MotionEvent event) { scale.onTouchEvent(event); gestures.onTouchEvent(event); return true; }
}
