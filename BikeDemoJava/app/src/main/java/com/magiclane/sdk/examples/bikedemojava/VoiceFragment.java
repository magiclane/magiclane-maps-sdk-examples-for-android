/*
 * SPDX-FileCopyrightText: 2026 Magic Lane International B.V. <info@magiclane.com>
 * SPDX-License-Identifier: Apache-2.0
 *
 * Contact Magic Lane at <info@magiclane.com> for SDK licensing options.
 */

package com.magiclane.sdk.examples.bikedemojava;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.magiclane.sdk.content.ContentStoreItem;
import com.magiclane.sdk.content.EContentStoreItemStatus;
import com.magiclane.sdk.content.EContentType;
import com.magiclane.sdk.core.EVoiceType;
import com.magiclane.sdk.core.GemError;
import com.magiclane.sdk.core.GemSdk;
import com.magiclane.sdk.core.Image;
import com.magiclane.sdk.core.MapDetails;
import com.magiclane.sdk.core.Parameter;
import com.magiclane.sdk.core.ProgressListener;
import com.magiclane.sdk.core.SdkSettings;
import com.magiclane.sdk.core.SoundPlayingService;
import com.magiclane.sdk.core.Voice;
import com.magiclane.sdk.examples.bikedemojava.databinding.FragmentVoiceBinding;
import com.magiclane.sdk.examples.bikedemojava.databinding.VoiceListItemBinding;
import com.magiclane.sdk.util.GemCall;
import com.magiclane.sdk.util.Util;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import kotlin.Pair;

/**
 * Lists the voices available for navigation / simulation instructions: the Text-to-Speech
 * engine (when it initialized successfully) followed by the human voices catalog from the
 * content store. Tapping a downloaded voice applies it to the SDK; tapping a voice that is
 * not on the device downloads it first.
 */
public class VoiceFragment extends Fragment {

    private static final double BYTES_PER_MEGABYTE = 1_048_576.0;

    /** One row of the voices list: either the Text-to-Speech entry or a human voice. */
    private static class VoiceRow {
        final boolean isTts;
        @Nullable
        final ContentStoreItem item; // null for the Text-to-Speech row

        private VoiceRow(boolean isTts, @Nullable ContentStoreItem item) {
            this.isTts = isTts;
            this.item = item;
        }

        static VoiceRow tts() {
            return new VoiceRow(true, null);
        }

        static VoiceRow human(ContentStoreItem item) {
            return new VoiceRow(false, item);
        }
    }

    private MainActivityViewModel viewModel;
    private FragmentVoiceBinding mBinding;

    // Flag bitmap cache keyed by ISO country code — avoids re-fetching on every list bind.
    private final HashMap<String, Bitmap> flagBitmapsMap = new HashMap<>();

    private final VoicesAdapter voicesAdapter = new VoicesAdapter();
    private final List<VoiceRow> rows = new ArrayList<>();

    // Country ISO code and description of the Text-to-Speech entry, resolved once.
    private final String ttsCountryCode = MainActivityViewModel.TTS_LANGUAGE
        .substring(MainActivityViewModel.TTS_LANGUAGE.indexOf('-') + 1);
    private String ttsDescription = "";

    // Tracks async retrieval of the voices catalog from the content store.
    private final ProgressListener catalogListener = new ProgressListener() {
        @Override
        public void notifyStart(boolean hasProgress) {
            Util.INSTANCE.postOnMain(() -> {
                if (mBinding != null) {
                    mBinding.progressBar.setVisibility(View.VISIBLE);
                }
            });
        }

        @Override
        public void notifyComplete(int errorCode, @NonNull String message) {
            Util.INSTANCE.postOnMain(() -> {
                if (mBinding != null) {
                    mBinding.progressBar.setVisibility(View.GONE);
                }
            });

            if (errorCode == GemError.NoError) {
                GemCall.INSTANCE.execute(() -> {
                    Pair<ArrayList<ContentStoreItem>, Boolean> result =
                        viewModel.contentStore.getStoreContentList(EContentType.HumanVoice);
                    List<ContentStoreItem> voicesList = result != null ? result.getFirst() : null;
                    Util.INSTANCE.postOnMain(() ->
                        viewModel.voicesLivedata.setValue(voicesList != null ? voicesList : Collections.emptyList()));
                    return null;
                });
            } else {
                Util.INSTANCE.postOnMain(() -> {
                    if (!isAdded()) return;
                    showToast(getString(
                        R.string.status_voices_catalog_download_error,
                        GemCall.INSTANCE.runSynced(() -> GemError.INSTANCE.getMessage(errorCode, requireContext()))
                    ));
                });
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        viewModel = new ViewModelProvider(requireActivity()).get(MainActivityViewModel.class);
        mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_voice, container, false);

        mBinding.voicesList.setAdapter(voicesAdapter);
        mBinding.voicesList.setLayoutManager(new LinearLayoutManager(requireContext()));
        mBinding.voicesList.addItemDecoration(new DividerItemDecoration(requireContext(), LinearLayoutManager.VERTICAL));
        mBinding.voicesList.setItemAnimator(null);

        mBinding.voiceToolbar.setNavigationOnClickListener(v ->
            requireActivity().getSupportFragmentManager().popBackStack());

        resolveTtsDescription();
        List<ContentStoreItem> cachedVoices = viewModel.voicesLivedata.getValue();
        rebuildRows(cachedVoices != null ? cachedVoices : Collections.emptyList());

        viewModel.voicesLivedata.observe(getViewLifecycleOwner(), voices -> {
            if (voices != null) {
                rebuildRows(voices);
            }
        });

        // Refresh the checkmarks whenever the applied voice changes.
        viewModel.currentVoice.observe(getViewLifecycleOwner(), selection -> refreshRows());

        viewModel.refreshCurrentVoice();
        loadVoicesCatalog();

        return mBinding.getRoot();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        mBinding = null;
    }

    // The applied voice affects every row (checkmark and on-device indicator), so a
    // full rebind is the appropriate change event here.
    @SuppressLint("NotifyDataSetChanged")
    private void refreshRows() {
        voicesAdapter.notifyDataSetChanged();
    }

    @SuppressLint("NotifyDataSetChanged")
    private void rebuildRows(List<ContentStoreItem> voices) {
        rows.clear();
        if (SoundPlayingService.INSTANCE.getTtsPlayerIsInitialized()) {
            rows.add(VoiceRow.tts());
        }
        for (ContentStoreItem item : voices) {
            rows.add(VoiceRow.human(item));
        }
        voicesAdapter.notifyDataSetChanged();
    }

    private void resolveTtsDescription() {
        String description = GemCall.INSTANCE.execute(() -> {
            String languageCode = MainActivityViewModel.TTS_LANGUAGE
                .substring(0, MainActivityViewModel.TTS_LANGUAGE.indexOf('-'));
            Voice ttsVoice = null;
            ArrayList<Voice> matches = SdkSettings.INSTANCE.getBestVoiceMatch(languageCode, ttsCountryCode);
            if (matches != null) {
                for (Voice voice : matches) {
                    if (voice.getType() == EVoiceType.Computer) {
                        ttsVoice = voice;
                        break;
                    }
                }
            }
            String country = new MapDetails().getCountryName(ttsCountryCode);
            String language = ttsVoice != null && ttsVoice.getLanguage() != null ? ttsVoice.getLanguage().getName() : null;

            StringBuilder builder = new StringBuilder();
            if (country != null && !country.isEmpty()) builder.append(country);
            if (language != null && !language.isEmpty()) {
                if (builder.length() > 0) builder.append(" - ");
                builder.append(language);
            }
            return builder.toString();
        });
        ttsDescription = description != null ? description : "";
    }

    private void loadVoicesCatalog() {
        // The catalog is cached in the view model for the whole session.
        if (viewModel.voicesLivedata.getValue() != null) return;

        GemCall.INSTANCE.execute(() -> {
            int error = viewModel.contentStore.asyncGetStoreContentList(EContentType.HumanVoice, catalogListener);
            if (error != GemError.NoError) {
                // asyncGetStoreContentList can fail immediately (e.g. no network) before the listener fires.
                Util.INSTANCE.postOnMain(() -> {
                    if (!isAdded()) return;
                    showToast(getString(
                        R.string.status_voices_catalog_download_error,
                        GemCall.INSTANCE.runSynced(() -> GemError.INSTANCE.getMessage(error, requireContext()))
                    ));
                });
            }
            return null;
        });
    }

    private void onRowClicked(VoiceRow row, int position) {
        if (row.isTts) {
            viewModel.selectTtsVoice();
            close();
            return;
        }

        ContentStoreItem item = row.item;
        if (item == null) return;

        EContentStoreItemStatus status = GemCall.INSTANCE.execute(item::getStatus);
        if (status == EContentStoreItemStatus.Completed) {
            // Already on the device: apply it right away.
            viewModel.selectHumanVoice(item);
            close();
        } else if (status != EContentStoreItemStatus.DownloadRunning) {
            // DownloadRunning means a download is already in progress; anything else starts one.
            downloadVoice(item, position);
        }
    }

    /** Returns to the settings view once a voice has been applied. */
    private void close() {
        requireActivity().getSupportFragmentManager().popBackStack();
    }

    // Kicks off an async download for voiceItem and handles the immediate result:
    // UpToDate (already downloaded) or error-on-start.
    private void downloadVoice(ContentStoreItem voiceItem, int position) {
        GemCall.INSTANCE.execute(() -> {
            String name = voiceItem.getName();
            String itemName = name != null ? name : "";

            ProgressListener downloadProgressListener = new ProgressListener() {
                @Override
                public void notifyStart(boolean hasProgress) {
                    Util.INSTANCE.postOnMain(() -> notifyItemChanged(position));
                }

                @Override
                public void notifyProgress(int progress) {
                    Util.INSTANCE.postOnMain(() -> notifyItemChanged(position));
                }

                @Override
                public void notifyComplete(int errorCode, @NonNull String message) {
                    Util.INSTANCE.postOnMain(() -> {
                        notifyItemChanged(position);
                        if (!isAdded()) return;
                        if (errorCode == GemError.NoError) {
                            showToast(getString(R.string.status_item_downloaded, itemName));
                        } else {
                            showToast(getString(
                                R.string.status_item_download_error,
                                itemName,
                                GemCall.INSTANCE.runSynced(() -> GemError.INSTANCE.getMessage(errorCode, requireContext()))
                            ));
                        }
                    });
                }
            };

            // asyncDownload returns immediately; NoError means the download started and
            // downloadProgressListener will receive further callbacks.
            int downloadError = voiceItem.asyncDownload(
                downloadProgressListener,
                GemSdk.EDataSavePolicy.UseDefault,
                true
            );

            if (downloadError == GemError.UpToDate) {
                Util.INSTANCE.postOnMain(() -> {
                    if (!isAdded()) return;
                    showToast(getString(R.string.status_item_already_downloaded, itemName));
                    notifyItemChanged(position);
                });
            } else if (downloadError != GemError.NoError) {
                Util.INSTANCE.postOnMain(() -> {
                    if (!isAdded()) return;
                    showToast(getString(
                        R.string.status_download_item_error,
                        GemCall.INSTANCE.runSynced(() -> GemError.INSTANCE.getMessage(downloadError, requireContext()))
                    ));
                });
            }
            return null;
        });
    }

    private void notifyItemChanged(int position) {
        if (mBinding != null && mBinding.voicesList.getAdapter() != null) {
            mBinding.voicesList.getAdapter().notifyItemChanged(position);
        }
    }

    private void showToast(String text) {
        Toast.makeText(requireContext(), text, Toast.LENGTH_SHORT).show();
    }

    @Nullable
    private Bitmap getFlagBitmap(@Nullable String isoCode) {
        if (isoCode == null || isoCode.isEmpty()) return null;
        if (!flagBitmapsMap.containsKey(isoCode)) {
            int size = (int) getResources().getDimension(R.dimen.voice_icon_size);
            Bitmap flagBitmap = GemCall.INSTANCE.execute(() -> {
                Image flag = new MapDetails().getCountryFlag(isoCode);
                return flag != null ? flag.asBitmap(size, size) : null;
            });
            flagBitmapsMap.put(isoCode, flagBitmap);
        }
        return flagBitmapsMap.get(isoCode);
    }

    private class VoicesAdapter extends RecyclerView.Adapter<VoicesAdapter.ViewHolder> {

        class ViewHolder extends RecyclerView.ViewHolder {
            final VoiceListItemBinding binding;

            ViewHolder(VoiceListItemBinding binding) {
                super(binding.getRoot());
                this.binding = binding;
                binding.getRoot().setOnClickListener(v -> {
                    int position = getAdapterPosition();
                    if (position == RecyclerView.NO_POSITION) return;
                    onRowClicked(rows.get(position), position);
                });
            }
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
            return new ViewHolder(VoiceListItemBinding.inflate(
                LayoutInflater.from(viewGroup.getContext()),
                viewGroup,
                false
            ));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
            VoiceRow row = rows.get(position);
            if (row.isTts) {
                bindTts(viewHolder.binding);
            } else if (row.item != null) {
                bindHuman(viewHolder.binding, row.item);
            }
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        private void bindTts(VoiceListItemBinding binding) {
            binding.text.setText(getString(R.string.text_to_speech));
            binding.description.setText(ttsDescription);
            binding.icon.setImageBitmap(getFlagBitmap(ttsCountryCode));
            binding.genderIcon.setImageResource(R.drawable.robot_green);
            binding.itemProgressBar.setVisibility(View.INVISIBLE);
            binding.statusIcon.setVisibility(View.GONE);

            MainActivityViewModel.VoiceSelection current = viewModel.currentVoice.getValue();
            binding.checkIcon.setVisibility(current != null && current.isTts ? View.VISIBLE : View.GONE);
        }

        @SuppressLint("DefaultLocale")
        private void bindHuman(VoiceListItemBinding binding, ContentStoreItem item) {
            binding.text.setText(GemCall.INSTANCE.execute(() ->
                String.format("%s (%.1f MB)", item.getName(), item.getTotalSize() / BYTES_PER_MEGABYTE)));
            binding.description.setText(GemCall.INSTANCE.execute(() ->
                getCountryName(item) + " - " + getParameter(item, "native_language")));
            binding.icon.setImageBitmap(getFlagBitmap(GemCall.INSTANCE.execute(() -> {
                ArrayList<String> countryCodes = item.getCountryCodes();
                return countryCodes != null && !countryCodes.isEmpty() ? countryCodes.get(0) : null;
            })));
            String gender = GemCall.INSTANCE.execute(() -> getParameter(item, "gender").toLowerCase(Locale.ROOT));
            binding.genderIcon.setImageResource("male".equals(gender) ? R.drawable.male : R.drawable.female);

            MainActivityViewModel.VoiceSelection current = viewModel.currentVoice.getValue();
            boolean isSelected = false;
            if (current != null && !current.isTts && !current.filename.isEmpty()) {
                isSelected = current.filename.equals(GemCall.INSTANCE.execute(item::getFileName));
            }

            binding.checkIcon.setVisibility(isSelected ? View.VISIBLE : View.GONE);
            binding.statusIcon.setVisibility(View.GONE);
            binding.itemProgressBar.setVisibility(View.INVISIBLE);

            EContentStoreItemStatus status = GemCall.INSTANCE.execute(item::getStatus);
            if (status == EContentStoreItemStatus.Completed) {
                // Downloaded but not selected: show the on-device indicator.
                binding.statusIcon.setVisibility(isSelected ? View.GONE : View.VISIBLE);
            } else if (status == EContentStoreItemStatus.DownloadRunning) {
                binding.itemProgressBar.setVisibility(View.VISIBLE);
                Integer progress = GemCall.INSTANCE.execute(item::getDownloadProgress);
                binding.itemProgressBar.setProgress(progress != null ? progress : 0);
            }
            // Any other status: not on the device yet, tap to download.
        }

        private String getCountryName(ContentStoreItem item) {
            ArrayList<String> countryCodes = item.getCountryCodes();
            if (countryCodes == null || countryCodes.isEmpty()) return "";
            String countryName = new MapDetails().getCountryName(countryCodes.get(0));
            return countryName != null ? countryName : "";
        }

        private String getParameter(ContentStoreItem item, String parameter) {
            ArrayList<Parameter> parameters = item.getContentParameters();
            if (parameters == null) return "";
            for (Parameter param : parameters) {
                if (param.getName() != null && param.getName().equalsIgnoreCase(parameter)) {
                    return param.getValueString();
                }
            }
            return "";
        }
    }
}
