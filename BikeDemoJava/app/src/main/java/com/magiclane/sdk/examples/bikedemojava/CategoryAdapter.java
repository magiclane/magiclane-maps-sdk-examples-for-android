/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

// Horizontal bar of POI category chips. Tapping a chip selects it and triggers an
// around-position search for that category.
public class CategoryAdapter extends ListAdapter<CategoryItem, CategoryAdapter.CategoryViewHolder> {

    public static final int NO_CATEGORY = -1;

    public interface OnCategoryClickListener {
        void onCategoryClick(int index);
    }

    private int selectedIndex = NO_CATEGORY;
    private OnCategoryClickListener onClickListener;

    public CategoryAdapter() {
        super(new DiffUtil.ItemCallback<CategoryItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull CategoryItem oldItem, @NonNull CategoryItem newItem) {
                return oldItem.getCategoryId() == newItem.getCategoryId();
            }

            @Override
            public boolean areContentsTheSame(@NonNull CategoryItem oldItem, @NonNull CategoryItem newItem) {
                return oldItem.getCategoryId() == newItem.getCategoryId();
            }
        });
    }

    public void setOnCategoryClickListener(OnCategoryClickListener listener) {
        this.onClickListener = listener;
    }

    public void setSelectedIndex(int index) {
        int previous = selectedIndex;
        selectedIndex = index;
        if (previous != NO_CATEGORY) notifyItemChanged(previous);
        if (index != NO_CATEGORY) notifyItemChanged(index);
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_category, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        CategoryItem item = getItem(position);
        boolean isSelected = position == selectedIndex;

        holder.icon.setImageBitmap(item.getIcon());

        if (isSelected) {
            holder.container.setBackgroundResource(R.drawable.rounded_background_primary);
        } else {
            holder.container.setBackground(null);
        }

        holder.container.setOnClickListener(v -> {
            if (onClickListener != null) {
                onClickListener.onCategoryClick(holder.getAdapterPosition());
            }
        });
    }

    public static class CategoryViewHolder extends RecyclerView.ViewHolder {
        final View container;
        final ImageView icon;

        public CategoryViewHolder(@NonNull View view) {
            super(view);
            container = view.findViewById(R.id.category_container);
            icon = view.findViewById(R.id.category_icon);
        }
    }
}
