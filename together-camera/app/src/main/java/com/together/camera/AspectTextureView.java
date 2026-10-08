package com.together.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

public class AspectTextureView extends TextureView {
    private int ratioWidth = 0;
    private int ratioHeight = 0;

    public AspectTextureView(Context context) { super(context); }
    public AspectTextureView(Context context, AttributeSet attrs) { super(context, attrs); }

    public void setAspectRatio(int width, int height) {
        if (width < 0 || height < 0) throw new IllegalArgumentException("Size cannot be negative");
        ratioWidth = width;
        ratioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width = MeasureSpec.getSize(widthMeasureSpec);
        if (ratioWidth == 0 || ratioHeight == 0) {
            setMeasuredDimension(width, MeasureSpec.getSize(heightMeasureSpec));
            return;
        }
        int height = width * ratioHeight / ratioWidth;
        setMeasuredDimension(width, height);
    }
}
