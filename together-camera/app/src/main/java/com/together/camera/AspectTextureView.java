package com.together.camera;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;
import android.view.View;

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
        int widthMode = View.MeasureSpec.getMode(widthMeasureSpec);
        int heightMode = View.MeasureSpec.getMode(heightMeasureSpec);
        int width = View.MeasureSpec.getSize(widthMeasureSpec);
        int height = View.MeasureSpec.getSize(heightMeasureSpec);

        if (ratioWidth <= 0 || ratioHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            return;
        }

        if (widthMode == View.MeasureSpec.EXACTLY && heightMode == View.MeasureSpec.EXACTLY) {
            setMeasuredDimension(width, height);
            return;
        }

        if (widthMode == View.MeasureSpec.EXACTLY) {
            int wantedHeight = Math.round(width * (ratioHeight / (float) ratioWidth));
            if (heightMode == View.MeasureSpec.AT_MOST) wantedHeight = Math.min(wantedHeight, height);
            setMeasuredDimension(width, wantedHeight);
            return;
        }

        if (heightMode == View.MeasureSpec.EXACTLY) {
            int wantedWidth = Math.round(height * (ratioWidth / (float) ratioHeight));
            if (widthMode == View.MeasureSpec.AT_MOST) wantedWidth = Math.min(wantedWidth, width);
            setMeasuredDimension(wantedWidth, height);
            return;
        }

        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
