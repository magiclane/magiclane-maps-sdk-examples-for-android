/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

import android.annotation.SuppressLint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.magiclane.sdk.examples.bikedemojava.databinding.SwitchSettingsItemBinding;
import com.magiclane.sdk.examples.bikedemojava.databinding.SliderSettingsItemBinding;
import com.magiclane.sdk.examples.bikedemojava.databinding.TextSettingsItemBinding;

public class SettingsAdapter extends ListAdapter<SettingsItem, RecyclerView.ViewHolder> {

    private enum ESettingsItemType {
        SWITCH,
        SLIDER,
        TEXT
    }

    public SettingsAdapter() {
        super(new DiffUtil.ItemCallback<>() {
            @Override
            public boolean areItemsTheSame(@NonNull SettingsItem oldItem, @NonNull SettingsItem newItem) {
                return oldItem.getTitle().equals(newItem.getTitle());
            }

            @Override
            public boolean areContentsTheSame(@NonNull SettingsItem oldItem, @NonNull SettingsItem newItem) {
                return false;
            }
        });
    }

    public static class TextItemView extends RecyclerView.ViewHolder {
        private final TextSettingsItemBinding binding;

        public TextItemView(TextSettingsItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        public void bind(SettingsTextItem item) {
            binding.settingItemText.setText(item.getTitle());
            binding.settingItemValue.setText(item.getValue());
            binding.getRoot().setOnClickListener(v -> item.getCallback().run());
        }
    }

    public static class SwitchItemView extends RecyclerView.ViewHolder {
        private final SwitchSettingsItemBinding binding;
        private SettingsSwitchItem.SwitchCallback mCallback;

        public SwitchItemView(SwitchSettingsItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.settingItemSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (getAdapterPosition() == RecyclerView.NO_POSITION) return;
                if (mCallback != null) {
                    mCallback.onChanged(isChecked);
                }
            });
        }

        public void bind(SettingsSwitchItem item) {
            mCallback = item.getCallback();
            binding.settingItemText.setText(item.getTitle());
            binding.settingItemSwitch.setChecked(item.isItIs());
        }
    }

    @SuppressLint("DefaultLocale")
    public static class SliderItemView extends RecyclerView.ViewHolder {
        private final SliderSettingsItemBinding binding;
        private String mUnit = "";
        private SettingsSliderItem.SliderCallback mCallback;

        public SliderItemView(SliderSettingsItemBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            binding.itemSlider.addOnChangeListener((slider, value, fromUser) -> {
                if (!fromUser) return;
                if (getAdapterPosition() == RecyclerView.NO_POSITION) return;
                binding.valueText.setText(String.format("%.1f %s", value, mUnit));
                if (mCallback != null) {
                    mCallback.onChanged(value);
                }
            });
        }

        @SuppressLint("DefaultLocale")
        public void bind(SettingsSliderItem item) {
            mCallback = item.getCallback();
            mUnit = item.getUnit();
            binding.settingItemText.setText(item.getTitle());
            binding.valueFromText.setText(String.format("%d %s", (int) item.getValueFrom(), item.getUnit()).trim());
            binding.valueText.setText(String.format("%.1f %s", item.getValue(), item.getUnit()).trim());
            binding.valueToText.setText(String.format("%d %s", (int) item.getValueTo(), item.getUnit()).trim());
            binding.itemSlider.setValueFrom(item.getValueFrom());
            binding.itemSlider.setValue(item.getValue());
            binding.itemSlider.setValueTo(item.getValueTo());
            binding.itemSlider.setLabelFormatter(value -> String.format("%.1f", value).trim());
        }
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType < 0) {
            return new RecyclerView.ViewHolder(new View(parent.getContext())) {};
        }
        ESettingsItemType type = ESettingsItemType.values()[viewType];
        switch (type) {
            case SWITCH:
                return new SwitchItemView(
                    SwitchSettingsItemBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                    )
                );
            case SLIDER:
                return new SliderItemView(
                    SliderSettingsItemBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                    )
                );
            case TEXT:
                return new TextItemView(
                    TextSettingsItemBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                    )
                );
            default:
                return new RecyclerView.ViewHolder(new View(parent.getContext())) {};
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ESettingsItemType type = ESettingsItemType.values()[getItemViewType(position)];
        switch (type) {
            case SWITCH:
                ((SwitchItemView) holder).bind((SettingsSwitchItem) getItem(position));
                break;
            case SLIDER:
                ((SliderItemView) holder).bind((SettingsSliderItem) getItem(position));
                break;
            case TEXT:
                ((TextItemView) holder).bind((SettingsTextItem) getItem(position));
                break;
        }
    }

    @Override
    public int getItemViewType(int position) {
        SettingsItem item = getItem(position);
        if (item instanceof SettingsSwitchItem) {
            return ESettingsItemType.SWITCH.ordinal();
        }
        if (item instanceof SettingsSliderItem) {
            return ESettingsItemType.SLIDER.ordinal();
        }
        if (item instanceof SettingsTextItem) {
            return ESettingsItemType.TEXT.ordinal();
        }
        return -1;
    }
}

