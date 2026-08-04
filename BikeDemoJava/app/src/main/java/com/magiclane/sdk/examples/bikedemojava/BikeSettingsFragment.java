/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import com.magiclane.sdk.core.SoundPlayingService;
import com.magiclane.sdk.examples.bikedemojava.databinding.FragmentBikeSettingsBinding;
import java.util.List;

public class BikeSettingsFragment extends Fragment {

    private static final SettingsAdapter settingsAdapter = new SettingsAdapter();
    private MainActivityViewModel viewModel;
    private FragmentBikeSettingsBinding mBinding;

    private FragmentBikeSettingsBinding getBinding() {
        return mBinding;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainActivityViewModel.class);
        mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_bike_settings, container, false);

        getBinding().settingsList.setAdapter(settingsAdapter);
        getBinding().settingsList.setLayoutManager(new LinearLayoutManager(requireContext()));
        settingsAdapter.submitList(buildSettingsList());

        getBinding().bikeSettingsToolbar.setNavigationOnClickListener(v -> requireActivity().getSupportFragmentManager().beginTransaction()
                .remove(BikeSettingsFragment.this)
                .commit());

        // Keep the Voice row in sync with the voice applied to the SDK.
        viewModel.currentVoice.observe(getViewLifecycleOwner(), selection ->
            settingsAdapter.submitList(buildSettingsList()));
        viewModel.refreshCurrentVoice();

        return getBinding().getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }

    private List<SettingsItem> buildSettingsList() {
        return viewModel.getSettingsList(currentVoiceDisplayName(), () ->
            requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new VoiceFragment())
                .addToBackStack(null)
                .commit());
    }

    private String currentVoiceDisplayName() {
        MainActivityViewModel.VoiceSelection selection = viewModel.currentVoice.getValue();
        if (selection == null) {
            return SoundPlayingService.INSTANCE.getTtsPlayerIsInitialized()
                ? getString(R.string.text_to_speech)
                : MainActivityViewModel.DEFAULT_HUMAN_VOICE_NAME;
        }
        return selection.isTts ? getString(R.string.text_to_speech) : selection.name;
    }
}

