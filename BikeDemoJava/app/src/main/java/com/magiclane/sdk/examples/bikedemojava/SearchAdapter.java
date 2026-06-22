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
import com.google.android.material.textview.MaterialTextView;

public class SearchAdapter extends ListAdapter<SearchResultItem, SearchAdapter.SearchResultViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(SearchResultItem item);
    }

    private OnItemClickListener onClickListener;

    public SearchAdapter() {
        super(new DiffUtil.ItemCallback<SearchResultItem>() {
            @Override
            public boolean areItemsTheSame(@NonNull SearchResultItem oldItem, @NonNull SearchResultItem newItem) {
                return oldItem == newItem;
            }

            @Override
            public boolean areContentsTheSame(@NonNull SearchResultItem oldItem, @NonNull SearchResultItem newItem) {
                return false;
            }
        });
    }

    public void setOnViewHolderClickListener(OnItemClickListener listener) {
        this.onClickListener = listener;
    }

    @NonNull
    @Override
    public SearchResultViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.searh_result_item, parent, false);
        return new SearchResultViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SearchResultViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    public class SearchResultViewHolder extends RecyclerView.ViewHolder {
        private final MaterialTextView textView;
        private final MaterialTextView descriptionView;
        private final MaterialTextView distanceView;
        private final MaterialTextView unitView;
        private final ImageView itemImage;

        public SearchResultViewHolder(@NonNull View view) {
            super(view);
            textView = view.findViewById(R.id.item_text);
            descriptionView = view.findViewById(R.id.item_description);
            distanceView = view.findViewById(R.id.item_distance);
            unitView = view.findViewById(R.id.item_unit);
            itemImage = view.findViewById(R.id.item_img);
        }

        public void bind(SearchResultItem item) {
            textView.setText(item.getText());
            descriptionView.setText(item.getSubText());
            distanceView.setText(item.getDistance());
            unitView.setText(item.getUnit());
            itemImage.setImageBitmap(item.getBmp());
            itemView.setOnClickListener(v -> {
                if (onClickListener != null) {
                    onClickListener.onItemClick(item);
                }
            });
        }
    }
}

