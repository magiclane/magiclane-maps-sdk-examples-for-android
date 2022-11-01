package de.codecrafters.tableview.toolkit;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import de.codecrafters.tableview.TableHeaderAdapter;


/**
 * Simple implementation of the {@link TableHeaderAdapter}. This adapter will render the given header
 * Strings as {@link TextView}.
 *
 * @author ISchwarz
 */
public final class SimpleTableHeaderAdapter extends TableHeaderAdapter {

    private final String[] headers;
    private int paddingLeft;
    private int paddingTop;
    private int paddingRight;
    private int paddingBottom;
    private int textSize;
    private int typeface = Typeface.BOLD;
    private int textColor = Color.parseColor("#FF444444");

    /**
     * Creates a new SimpleTableHeaderAdapter.
     *
     * @param context The context to use inside this {@link TableHeaderAdapter}.
     * @param headers The header labels that shall be rendered.
     */
    public SimpleTableHeaderAdapter(final Context context, final String... headers)
    {
        super(context);
        this.headers = headers;

        if ((getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) == Configuration.SCREENLAYOUT_SIZE_LARGE)
        {
            textSize = 20;
        }
        else
        {
            textSize = 12;
        }
    }


    public SimpleTableHeaderAdapter(final Context context, final int... headerStringResources) {
        super(context);
        this.headers = new String[headerStringResources.length];

        for (int i = 0; i < headerStringResources.length; i++) {
            headers[i] = context.getString(headerStringResources[i]);
        }
    }

    /**
     * Sets the padding that will be used for all table headers.
     *
     * @param left   The padding on the left side.
     * @param top    The padding on the top side.
     * @param right  The padding on the right side.
     * @param bottom The padding on the bottom side.
     */
    public void setPaddings(final int left, final int top, final int right, final int bottom) {
        paddingLeft = left;
        paddingTop = top;
        paddingRight = right;
        paddingBottom = bottom;
    }

    /**
     * Sets the padding that will be used on the left side for all table headers.
     *
     * @param paddingLeft The padding on the left side.
     */
    public void setPaddingLeft(final int paddingLeft) {
        this.paddingLeft = paddingLeft;
    }

    /**
     * Sets the padding that will be used on the top side for all table headers.
     *
     * @param paddingTop The padding on the top side.
     */
    public void setPaddingTop(final int paddingTop) {
        this.paddingTop = paddingTop;
    }

    /**
     * Sets the padding that will be used on the right side for all table headers.
     *
     * @param paddingRight The padding on the right side.
     */
    public void setPaddingRight(final int paddingRight) {
        this.paddingRight = paddingRight;
    }

    /**
     * Sets the padding that will be used on the bottom side for all table headers.
     *
     * @param paddingBottom The padding on the bottom side.
     */
    public void setPaddingBottom(final int paddingBottom) {
        this.paddingBottom = paddingBottom;
    }

    /**
     * Sets the text size that will be used for all table headers.
     *
     * @param textSize The text size that shall be used.
     */
    public void setTextSize(final int textSize) {
        this.textSize = textSize;
    }

    /**
     * Sets the typeface that will be used for all table headers.
     *
     * @param typeface The type face that shall be used.
     */
    public void setTypeface(final int typeface) {
        this.typeface = typeface;
    }

    /**
     * Sets the text color that will be used for all table headers.
     *
     * @param textColor The text color that shall be used.
     */
    public void setTextColor(final int textColor) {
        this.textColor = textColor;
    }



    @Override
    public View getHeaderView(final int columnIndex, final ViewGroup parentView)
    {
        final TextView textView = new TextView(getContext());

        if (columnIndex < headers.length)
        {
            textView.setText(headers[columnIndex]);
        }

        textView.setTextColor(textColor);
        textView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        textView.setSingleLine(false);

        textView.setTypeface(textView.getTypeface(), typeface);
        textView.setTextSize(textSize);

        textView.setGravity(Gravity.CENTER);

        /*
        if (columnIndex == 1)
        {
            textView.setGravity(Gravity.CENTER);
        }
        else
        {
            textView.setGravity(Gravity.CENTER|Gravity.START);
        }
        */

        textView.setPadding(paddingLeft, paddingTop , paddingRight, paddingBottom);

        return textView;
    }
}
