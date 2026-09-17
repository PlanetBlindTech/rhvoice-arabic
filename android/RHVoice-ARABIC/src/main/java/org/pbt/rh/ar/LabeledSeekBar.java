package org.pbt.rh.ar;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatSeekBar;

public class LabeledSeekBar extends AppCompatSeekBar {

    private String labelText = "";
    private Paint textPaint;
    private Rect textBounds = new Rect();

    public LabeledSeekBar(Context context) {
        super(context);
        init(context, null);
    }

    public LabeledSeekBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public LabeledSeekBar(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    private void init(Context context, AttributeSet attrs) {
        textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(spToPx(16));
        textPaint.setFakeBoldText(true);

        if (attrs != null) {
            int[] attrsArray = new int[] { android.R.attr.text, android.R.attr.contentDescription };
            TypedArray ta = context.obtainStyledAttributes(attrs, attrsArray);
            String text = ta.getString(0);
            String contentDesc = ta.getString(1);
            if (text != null && !text.isEmpty()) {
                setLabelText(text);
            } else if (contentDesc != null && !contentDesc.isEmpty()) {
                setLabelText(contentDesc);
            }
            ta.recycle();
        }
    }

    public void setLabelText(String label) {
        this.labelText = label != null ? label : "";
        setContentDescription(this.labelText);
        invalidate();
    }

    public String getLabelText() {
        return labelText;
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (labelText != null && !labelText.isEmpty()) {
            float x = getWidth() / 2f;
            textPaint.getTextBounds(labelText, 0, labelText.length(), textBounds);
            float y = (getHeight() / 2f) + (textBounds.height() / 2f) - textBounds.bottom;

            textPaint.setStyle(Paint.Style.STROKE);
            textPaint.setStrokeWidth(dpToPx(3));
            textPaint.setColor(Color.argb(220, 15, 23, 42));
            canvas.drawText(labelText, x, y, textPaint);

            textPaint.setStyle(Paint.Style.FILL);
            textPaint.setColor(Color.WHITE);
            canvas.drawText(labelText, x, y, textPaint);
        }
    }

    private float spToPx(float sp) {
        return sp * getResources().getDisplayMetrics().scaledDensity;
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }
}
